package io.rovly.pitchee.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import io.rovly.pitchee.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.rovly.pitchee.data.RealtimeF0AudioRecorder
import java.util.ArrayDeque
import kotlin.math.max
import kotlin.math.min
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import space.pitchee.core.PitcheeSpectrum

enum class SpectrumMode {
    IDLE,
    PREPARING,
    ANALYZING,
    ANALYSIS_PAUSED,
    REPLAYING,
}

data class SpectrumFramePoint(
    val timestampSeconds: Double,
    val bands: FloatArray,
)

data class SpectrumUiState(
    val mode: SpectrumMode = SpectrumMode.IDLE,
    val frames: List<SpectrumFramePoint> = emptyList(),
    val playbackPositionSeconds: Double? = null,
    val replayWindowStartSeconds: Double? = null,
    val replayWindowEndSeconds: Double? = null,
    val replayTailEndSeconds: Double? = null,
    val error: String? = null,
)

data class SpectrumRewindFeedback(
    val secondsFromLatest: Double,
    val isLastPage: Boolean,
)

class SpectrumViewModel(
    private val context: Context,
    private val recorder: RealtimeF0AudioRecorder,
) : ViewModel() {
    private var liveJob: Job? = null
    private var playbackJob: Job? = null
    private var spectrum: PitcheeSpectrum? = null
    private var audioTrack: AudioTrack? = null
    private val sessionLock = Any()
    @Volatile
    private var playbackGeneration = 0
    @Volatile
    private var liveGeneration = 0
    private var lastControlAtMillis = 0L
    private val frameBuffer = ArrayDeque<SpectrumFramePoint>()
    private val audioRing = FloatArray((SAMPLE_RATE * HISTORY_SECONDS).toInt())
    private var ringWriteIndex = 0

    @Volatile
    private var totalSamples = 0L
    private var replayTailSample = 0L
    private val mutableState = MutableStateFlow(SpectrumUiState())
    val state: StateFlow<SpectrumUiState> = mutableState.asStateFlow()

    fun start() {
        if (mutableState.value.mode != SpectrumMode.IDLE) return
        resetSession()
        startAnalysisInternal()
    }

    fun rewindFiveSeconds(): SpectrumRewindFeedback? {
        if (mutableState.value.mode == SpectrumMode.IDLE) return null
        if (!acceptControlEvent()) return null
        stopLiveCapture(keepMicrophoneOpen = true)
        stopPlaybackInternal()

        val currentWindowStart = mutableState.value.replayWindowStartSeconds
        val currentWindowEnd = mutableState.value.replayWindowEndSeconds
            ?: totalSamples.toDouble() / SAMPLE_RATE
        val liveTail = totalSamples.toDouble() / SAMPLE_RATE

        if (replayTailSample <= 0L) {
            replayTailSample = totalSamples
        }
        val oldestSeconds = oldestSample().toDouble() / SAMPLE_RATE
        val replayTailSeconds = replayTailSample.toDouble() / SAMPLE_RATE
        if (currentWindowStart != null &&
            currentWindowStart <= oldestSeconds + END_EPSILON_SECONDS
        ) {
            val replayEnd = min(oldestSeconds + WINDOW_SECONDS, replayTailSeconds)
            startReplayWindow(
                startSeconds = oldestSeconds,
                endSeconds = replayEnd,
            )
            return SpectrumRewindFeedback(
                secondsFromLatest = replayEnd - oldestSeconds,
                isLastPage = true,
            )
        }

        val targetEnd = if (currentWindowStart != null) {
            currentWindowStart
        } else {
            min(currentWindowEnd, replayTailSeconds)
        }
        val targetStart = max(oldestSeconds, targetEnd - WINDOW_SECONDS)
        if (targetEnd - targetStart <= END_EPSILON_SECONDS) {
            val replayEnd = min(oldestSeconds + WINDOW_SECONDS, replayTailSeconds)
            startReplayWindow(
                startSeconds = oldestSeconds,
                endSeconds = replayEnd,
            )
            return SpectrumRewindFeedback(
                secondsFromLatest = replayEnd - oldestSeconds,
                isLastPage = true,
            )
        }

        startReplayWindow(
            startSeconds = targetStart,
            endSeconds = min(targetEnd, liveTail),
        )
        return SpectrumRewindFeedback(
            secondsFromLatest = (targetEnd - targetStart).coerceAtLeast(0.0),
            isLastPage = targetStart <= oldestSeconds + END_EPSILON_SECONDS,
        )
    }

    fun toggleAnalysis() {
        if (!acceptControlEvent()) return
        when (mutableState.value.mode) {
            SpectrumMode.IDLE -> start()
            SpectrumMode.PREPARING, SpectrumMode.ANALYZING -> pauseAnalysis()
            SpectrumMode.ANALYSIS_PAUSED, SpectrumMode.REPLAYING -> resumeAnalysis()
        }
    }

    fun stop() {
        resetSession()
        mutableState.value = SpectrumUiState()
    }

    private fun resetSession() {
        stopLiveCapture()
        stopPlaybackInternal()
        spectrum?.close()
        spectrum = null
        synchronized(sessionLock) {
            frameBuffer.clear()
            audioRing.fill(0f)
            ringWriteIndex = 0
            totalSamples = 0L
            replayTailSample = 0L
        }
        mutableState.value = SpectrumUiState()
    }

    @Synchronized
    private fun startAnalysisInternal() {
        if (mutableState.value.mode == SpectrumMode.ANALYZING ||
            mutableState.value.mode == SpectrumMode.PREPARING
        ) {
            return
        }
        stopLiveCapture(keepMicrophoneOpen = true)
        stopPlaybackInternal()
        replayTailSample = 0L
        val generation = ++liveGeneration
        mutableState.update {
            it.copy(
                mode = SpectrumMode.PREPARING,
                playbackPositionSeconds = null,
                replayWindowStartSeconds = null,
                replayWindowEndSeconds = null,
                replayTailEndSeconds = null,
                error = null,
            )
        }
        liveJob = viewModelScope.launch(Dispatchers.Default) {
            try {
                val stream = spectrum ?: PitcheeSpectrum.create(
                    fftSize = FFT_SIZE,
                    hopSamples = HOP_SAMPLES,
                    minHz = MIN_HZ,
                    maxHz = MAX_HZ,
                    smoothing = SPECTRUM_SMOOTHING,
                ).also { spectrum = it }
                recorder.resume()
                if (generation != liveGeneration) return@launch
                mutableState.update { it.copy(mode = SpectrumMode.ANALYZING) }
                val buffer = FloatArray(READ_SAMPLES)
                while (isActive && generation == liveGeneration) {
                    val count = recorder.read(buffer)
                    if (count <= 0) continue
                    val chunk = if (count == buffer.size) buffer.copyOf() else buffer.copyOf(count)
                    synchronized(sessionLock) {
                        if (isActive && generation == liveGeneration) {
                            appendAudio(chunk)
                            stream.process(chunk) {
                                    timestampSeconds,
                                    magnitudes,
                                    firstBinIndex,
                                    binHz,
                                    _,
                                    _,
                                    _,
                                    _,
                                ->
                                val point = SpectrumFramePoint(
                                    timestampSeconds = timestampSeconds,
                                    bands = downsampleSpectrum(
                                        magnitudes = magnitudes,
                                        firstBinIndex = firstBinIndex.toInt(),
                                        binHz = binHz,
                                    ),
                                )
                                frameBuffer.addLast(point)
                                while (frameBuffer.isNotEmpty() &&
                                    frameBuffer.first().timestampSeconds <
                                    point.timestampSeconds - HISTORY_SECONDS
                                ) {
                                    frameBuffer.removeFirst()
                                }
                                while (frameBuffer.size > MAX_FRAMES) {
                                    frameBuffer.removeFirst()
                                }
                                val frames = frameBuffer.toList()
                                if (generation == liveGeneration) {
                                    mutableState.update { current ->
                                        current.copy(frames = frames, error = null)
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (generation == liveGeneration) {
                    mutableState.update {
                        it.copy(
                            mode = SpectrumMode.ANALYSIS_PAUSED,
                            error = error.message,
                        )
                    }
                }
            }
        }
    }

    private fun pauseAnalysis() {
        stopLiveCapture()
        mutableState.update {
            it.copy(
                mode = SpectrumMode.ANALYSIS_PAUSED,
                playbackPositionSeconds = null,
                replayWindowStartSeconds = null,
                replayWindowEndSeconds = null,
                replayTailEndSeconds = null,
            )
        }
    }

    private fun resumeAnalysis() {
        stopPlaybackInternal()
        startAnalysisInternal()
    }

    private fun stopLiveCapture(keepMicrophoneOpen: Boolean = false) {
        synchronized(sessionLock) {
            liveGeneration++
            liveJob?.cancel()
            liveJob = null
            if (keepMicrophoneOpen) {
                recorder.pause()
            } else {
                recorder.stop()
            }
        }
    }

    private fun startReplayWindow(
        startSeconds: Double,
        endSeconds: Double,
    ) {
        val startSample = (startSeconds * SAMPLE_RATE).toLong()
            .coerceAtLeast(oldestSample())
            .coerceAtMost(totalSamples)
        val endSample = (endSeconds * SAMPLE_RATE).toLong()
            .coerceIn(startSample, totalSamples)
        if (endSample <= startSample) {
            resumeAnalysis()
            return
        }
        val generation = ++playbackGeneration
        updateReplayState(
            positionSample = startSample,
            windowStartSample = startSample,
            windowEndSample = endSample,
            tailSample = replayTailSample,
            generation = generation,
        )
        playbackJob = viewModelScope.launch(Dispatchers.Default) {
            var track: AudioTrack? = null
            var positionJob: Job? = null
            var reachedTail = false
            try {
                if (!isActive || generation != playbackGeneration) return@launch
                track = createAudioTrack()
                if (!isActive || generation != playbackGeneration) return@launch
                val activeTrack = track
                audioTrack = activeTrack
                activeTrack.play()
                positionJob = launch {
                    while (isActive && generation == playbackGeneration) {
                        val playedSamples = activeTrack.playbackHeadPosition
                            .toLong()
                            .coerceAtLeast(0L)
                        val sourcePosition = (
                            startSample + playedSamples
                            ).coerceAtMost(replayTailSample)
                        updateReplayPosition(
                            positionSample = sourcePosition,
                            replayStartSample = startSample,
                            generation = generation,
                        )
                        if (sourcePosition >= replayTailSample - END_EPSILON_SAMPLES) break
                        delay(PLAYBACK_UPDATE_MILLIS)
                    }
                }

                var writePosition = startSample
                while (
                    isActive &&
                    generation == playbackGeneration &&
                    writePosition < replayTailSample
                ) {
                    val count = min(
                        PLAYBACK_CHUNK_SAMPLES,
                        (replayTailSample - writePosition).toInt(),
                    )
                    val samples = copyAudio(writePosition, count)
                    val written = activeTrack.write(
                        samples,
                        0,
                        samples.size,
                        AudioTrack.WRITE_BLOCKING,
                    )
                    check(written >= 0) { context.getString(R.string.error_audio_playback_failed, written) }
                    writePosition += written
                }

                while (
                    isActive &&
                    generation == playbackGeneration &&
                    startSample + activeTrack.playbackHeadPosition.toLong() <
                    replayTailSample - END_EPSILON_SAMPLES
                ) {
                    delay(PLAYBACK_UPDATE_MILLIS)
                }
                if (generation == playbackGeneration) reachedTail = true
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (generation == playbackGeneration) {
                    mutableState.update { it.copy(error = error.message) }
                }
            } finally {
                positionJob?.cancel()
                runCatching { track?.pause() }
                runCatching { track?.flush() }
                runCatching { track?.release() }
                if (audioTrack === track) audioTrack = null
                if (generation == playbackGeneration) playbackJob = null
            }
            if (reachedTail && generation == playbackGeneration) resumeAnalysis()
        }
    }

    private fun updateReplayPosition(
        positionSample: Long,
        replayStartSample: Long,
        generation: Int,
    ) {
        if (generation != playbackGeneration) return
        val windowSamples = (WINDOW_SECONDS * SAMPLE_RATE).toLong()
        val elapsed = (positionSample - replayStartSample).coerceAtLeast(0L)
        val windowStart = replayStartSample + (elapsed / windowSamples) * windowSamples
        val windowEnd = min(windowStart + windowSamples, replayTailSample)
        mutableState.update {
            it.copy(
                mode = SpectrumMode.REPLAYING,
                playbackPositionSeconds = positionSample.toDouble() / SAMPLE_RATE,
                replayWindowStartSeconds = windowStart.toDouble() / SAMPLE_RATE,
                replayWindowEndSeconds = windowEnd.toDouble() / SAMPLE_RATE,
                replayTailEndSeconds = replayTailSample.toDouble() / SAMPLE_RATE,
            )
        }
    }

    private fun updateReplayPosition(
        positionSample: Long,
        generation: Int,
    ) {
        if (generation != playbackGeneration) return
        mutableState.update {
            it.copy(
                mode = SpectrumMode.REPLAYING,
                playbackPositionSeconds = positionSample.toDouble() / SAMPLE_RATE,
            )
        }
    }

    private fun updateReplayState(
        positionSample: Long,
        windowStartSample: Long,
        windowEndSample: Long,
        tailSample: Long,
        generation: Int? = null,
    ) {
        if (generation != null && generation != playbackGeneration) return
        mutableState.update {
            it.copy(
                mode = SpectrumMode.REPLAYING,
                playbackPositionSeconds = positionSample.toDouble() / SAMPLE_RATE,
                replayWindowStartSeconds = windowStartSample.toDouble() / SAMPLE_RATE,
                replayWindowEndSeconds = windowEndSample.toDouble() / SAMPLE_RATE,
                replayTailEndSeconds = tailSample.toDouble() / SAMPLE_RATE,
            )
        }
    }

    private fun stopPlaybackInternal() {
        playbackGeneration++
        val job = playbackJob
        playbackJob = null
        job?.cancel()
        audioTrack?.let { track ->
            runCatching { track.pause() }
            runCatching { track.flush() }
        }
        audioTrack = null
    }

    private fun appendAudio(samples: FloatArray) {
        samples.forEach { sample ->
            audioRing[ringWriteIndex] = sample
            ringWriteIndex = (ringWriteIndex + 1) % audioRing.size
        }
        totalSamples += samples.size
    }

    private fun oldestSample(): Long =
        max(0L, totalSamples - audioRing.size.toLong())

    private fun copyAudio(startSample: Long, count: Int): FloatArray {
        val oldest = oldestSample()
        val safeStart = startSample.coerceAtLeast(oldest)
        val safeCount = min(count, (totalSamples - safeStart).toInt())
        val output = FloatArray(safeCount)
        var ringIndex = (safeStart % audioRing.size).toInt()
        for (index in output.indices) {
            output[index] = audioRing[ringIndex]
            ringIndex = (ringIndex + 1) % audioRing.size
        }
        return output
    }

    private fun createAudioTrack(): AudioTrack {
        val minimumBuffer = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
        )
        check(minimumBuffer > 0) { context.getString(R.string.error_realtime_playback_unsupported) }
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .build(),
            )
            .setBufferSizeInBytes(
                max(minimumBuffer, PLAYBACK_CHUNK_SAMPLES * Float.SIZE_BYTES * 4),
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
            .also {
                check(it.state == AudioTrack.STATE_INITIALIZED) {
                    it.release()
                    context.getString(R.string.error_audio_playback_init)
                }
            }
    }

    private fun downsampleSpectrum(
        magnitudes: FloatArray,
        firstBinIndex: Int,
        binHz: Float,
    ): FloatArray {
        val bands = FloatArray(SPECTRUM_BAND_COUNT)
        if (magnitudes.isEmpty() || binHz <= 0f) return bands
        val minHz = (firstBinIndex * binHz).coerceAtLeast(1f)
        val maxHz = (
            (firstBinIndex + magnitudes.lastIndex) * binHz
            ).coerceAtLeast(minHz + 1f)
        val logRange = ln(maxHz / minHz)
        for (index in 0 until SPECTRUM_BAND_COUNT) {
            val highRatio = 1f - index.toFloat() / SPECTRUM_BAND_COUNT
            val lowRatio = 1f - (index + 1).toFloat() / SPECTRUM_BAND_COUNT
            val highBin = (
                minHz * exp(highRatio * logRange) / binHz - firstBinIndex
                ).roundToInt().coerceIn(0, magnitudes.lastIndex)
            val lowBin = (
                minHz * exp(lowRatio * logRange) / binHz - firstBinIndex
                ).roundToInt().coerceIn(0, magnitudes.lastIndex)
            val firstBin = minOf(lowBin, highBin)
            val lastBin = maxOf(lowBin, highBin)
            var peak = Float.NEGATIVE_INFINITY
            for (bin in firstBin..lastBin) {
                if (magnitudes[bin] > peak) peak = magnitudes[bin]
            }
            bands[index] = if (peak.isFinite()) peak else magnitudes[highBin]
        }
        return bands
    }

    private fun acceptControlEvent(): Boolean {
        val now = System.nanoTime() / 1_000_000L
        if (now - lastControlAtMillis < CONTROL_DEBOUNCE_MILLIS) return false
        lastControlAtMillis = now
        return true
    }

    override fun onCleared() {
        resetSession()
    }

    companion object {
        private const val FFT_SIZE = 4096
        private const val HOP_SAMPLES = 256
        private const val MIN_HZ = 40
        private const val MAX_HZ = 8000
        private const val READ_SAMPLES = HOP_SAMPLES
        const val WINDOW_SECONDS = 5.0
        private const val SAMPLE_RATE = 16_000
        private const val HISTORY_SECONDS = 30.0
        const val SPECTRUM_BAND_COUNT = 256
        private const val PLAYBACK_CHUNK_SAMPLES = 1024
        private const val PLAYBACK_UPDATE_MILLIS = 8L
        private const val END_EPSILON_SECONDS = 0.01
        private const val END_EPSILON_SAMPLES = 160L
        private const val CONTROL_DEBOUNCE_MILLIS = 300L
        private const val SPECTRUM_SMOOTHING = 0.35f
        // 30 seconds at 16 kHz / 256 samples is about 1875 frames.
        private const val MAX_FRAMES = 1880

        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SpectrumViewModel(
                    context = context.applicationContext,
                    recorder = RealtimeF0AudioRecorder(context.applicationContext),
                )
            }
        }
    }
}
