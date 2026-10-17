package io.rovly.pitchee.feature.piano

import android.media.AudioFormat
import com.github.nisrulz.zentone.SampleRate
import com.github.nisrulz.zentone.ZenTone
import com.github.nisrulz.zentone.wavegenerators.WaveByteArrayGenerator
import java.io.Closeable
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh

internal data class PianoNote(
    val midi: Int,
    val displayName: String,
) {
    val frequency: Float
        get() = (440.0 * 2.0.pow((midi - 69) / 12.0)).toFloat()

    companion object {
        val allNotes: List<PianoNote> = listOf(
            "F#2" to 42,
            "G2" to 43,
            "G#2" to 44,
            "A2" to 45,
            "A#2" to 46,
            "B2" to 47,
            "C3" to 48,
            "C#3" to 49,
            "D3" to 50,
            "D#3" to 51,
            "E3" to 52,
            "F3" to 53,
            "F#3" to 54,
            "G3" to 55,
            "G#3" to 56,
            "A3" to 57,
            "A#3" to 58,
            "B3" to 59,
            "C4" to 60,
            "C#4" to 61,
            "D4" to 62,
            "D#4" to 63,
            "E4" to 64,
            "F4" to 65,
        ).map { (name, midi) -> PianoNote(midi, name) }
    }
}

/**
 * Tone playback built on ZenTone (com.github.nisrulz:zentone).
 *
 * Two things about the library need wrapping:
 *
 * 1. It ships a bare full-scale sine and stops by pausing/flushing the track, so
 *    we supply our own [WaveByteArrayGenerator]: a struck-string envelope at a
 *    level a phone speaker reproduces cleanly, with a release ramp so the note is
 *    already silent when the track is cut.
 * 2. `play()` and `stop()` are not safe against each other. `stop()` sets the
 *    playing flag, joins the writer and pauses; if a `play()` from another thread
 *    lands in that window the writer ends up writing to a paused track,
 *    `AudioTrack.write()` returns 0 and ZenTone throws on its own dispatcher —
 *    which kills the process. Every ZenTone call therefore goes through one
 *    dedicated audio thread, so play and stop can never interleave.
 */
internal class PianoSoundEngine : Closeable {
    private val audio: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "piano-audio").apply { priority = Thread.MAX_PRIORITY }
        }

    // Touch these only from the audio thread.
    private val voices = mutableMapOf<Int, Voice>()
    private val pendingStops = mutableMapOf<Int, ScheduledFuture<*>>()

    @Volatile
    private var closed = false

    private class Voice(
        val tone: ZenTone,
        val generator: PianoWaveGenerator,
    )

    /**
     * Do not create every note's AudioTrack up front. Android limits how many
     * simultaneous AudioTracks an app can hold; prewaking 24 voices makes the
     * later notes silently fail to initialize.
     */
    fun prepare() = Unit

    fun noteOn(note: PianoNote) = submit {
        val voice = voices.getOrPut(note.midi) { newVoice() }
        pendingStops.remove(note.midi)?.cancel(false)

        if (voice.tone.isPlaying) {
            // Restrike the ringing string instead of restarting the track: no
            // blocking stop(), no gap, and it is what a re-struck string does.
            voice.generator.restrike()
            return@submit
        }
        voice.generator.restart()
        runCatching {
            voice.tone.play(
                frequency = note.frequency,
                volume = VOLUME,
                waveByteArrayGenerator = voice.generator,
            )
        }
    }

    /**
     * Asks for a release ramp and only tears the track down once the ramp has had
     * time to play out, which is what keeps the note-off click-free.
     */
    fun noteOff(note: PianoNote) = submit {
        val voice = voices[note.midi] ?: return@submit
        voice.generator.requestRelease()
        pendingStops.remove(note.midi)?.cancel(false)
        pendingStops[note.midi] = audio.schedule(
            {
                pendingStops.remove(note.midi)
                releaseVoice(note.midi, voice)
            },
            RELEASE_STOP_MILLIS,
            TimeUnit.MILLISECONDS,
        )
    }

    fun stopAll() = submit {
        pendingStops.values.forEach { it.cancel(false) }
        pendingStops.clear()
        voices.entries.toList().forEach { (midi, voice) -> releaseVoice(midi, voice) }
    }

    override fun close() {
        if (closed) return
        closed = true
        submit {
            pendingStops.values.forEach { it.cancel(false) }
            pendingStops.clear()
            voices.entries.toList().forEach { (midi, voice) -> releaseVoice(midi, voice) }
        }
        audio.shutdown()
    }

    private fun submit(block: () -> Unit) {
        if (closed) return
        runCatching { audio.execute(block) }
    }

    private fun newVoice(): Voice = Voice(
        tone = ZenTone(sampleRate = SAMPLE_RATE, channelMask = CHANNEL_MASK),
        generator = PianoWaveGenerator(),
    )

    private fun releaseVoice(midi: Int, voice: Voice) {
        runCatching { voice.tone.release() }
        voices.remove(midi, voice)
    }

    private companion object {
        val SAMPLE_RATE = SampleRate.Hz48000
        const val CHANNEL_MASK = AudioFormat.CHANNEL_OUT_MONO

        /** ZenTone's own volume scale, 0-100. */
        const val VOLUME = 100

        /** Wait for the ramp plus whatever the track had buffered ahead of it. */
        const val RELEASE_STOP_MILLIS = 60L
    }
}

/**
 * Struck-string envelope around a sine.
 *
 * [WaveByteArrayGenerator.calculateData] is called once per frame in playback
 * order, so the envelope is advanced there and is sample accurate.
 *
 * The decay matters as much as the waveform: a held note that stays at full
 * level keeps the phone speaker driven hard, and the distortion is most audible
 * on the notes the speaker actually reproduces well (the higher ones). Decaying
 * to a quieter sustain the way a struck string does keeps it clean.
 */
private class PianoWaveGenerator : WaveByteArrayGenerator {
    @Volatile
    private var releasing = false

    @Volatile
    private var restrikeRequested = false
    private var frames = 0L
    private var releaseGain = 1.0

    fun restart() {
        releasing = false
        restrikeRequested = false
        frames = 0L
        releaseGain = 1.0
    }

    fun requestRelease() {
        releasing = true
    }

    fun restrike() {
        restrikeRequested = true
    }

    override fun calculateData(angle: Double, amplitude: Int): Double {
        if (restrikeRequested) {
            // Applied on the generator's own thread, so the envelope state never
            // races with the playback coroutine.
            restrikeRequested = false
            releasing = false
            frames = 0L
            releaseGain = 1.0
        }
        frames += 1
        if (releasing) {
            releaseGain = (releaseGain - RELEASE_STEP).coerceAtLeast(0.0)
        }
        val seconds = frames.toDouble() / SAMPLE_RATE
        val attack = (seconds / ATTACK_SECONDS).coerceAtMost(1.0)
        val decay = SUSTAIN_FLOOR +
            (1.0 - SUSTAIN_FLOOR) * exp(-DECAY_RATE * seconds)
        val raw = amplitude * attack * decay * releaseGain * sin(angle)
        // Soft saturation raises perceived loudness without hard-clipping the
        // waveform into the square edges that made the higher notes sound harsh.
        return tanh(raw * DRIVE) / tanh(DRIVE)
    }

    private companion object {
        const val SAMPLE_RATE = 48_000

        /** Soft-drive amount. Keeps the output bounded while lifting quiet notes. */
        const val DRIVE = 1.8

        /** Fraction of the peak the note keeps while the key is held. */
        const val SUSTAIN_FLOOR = 0.65
        const val DECAY_RATE = 0.9

        const val ATTACK_SECONDS = 0.006
        const val RELEASE_SECONDS = 0.008
        const val RELEASE_STEP = 1.0 / (SAMPLE_RATE * RELEASE_SECONDS)
    }
}
