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
import io.rovly.pitchee.data.HistoryStore
import io.rovly.pitchee.data.PitcheeRepository
import io.rovly.pitchee.data.RealtimeF0AudioRecorder
import io.rovly.pitchee.data.RealtimeF0HistoryEntry
import kotlin.math.max
import kotlin.math.min
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
import space.pitchee.core.PitcheeRealtimeF0

data class F0Point(
    val timestampSeconds: Double,
    val f0Hz: Float?,
    val confidence: Float,
)

data class PitchSessionSummary(
    val meanF0Hz: Double?,
    val maleRatio: Float,
    val femaleRatio: Float,
    val sampleCount: Int,
)

data class PitchRewindFeedback(
    val secondsFromLatest: Double,
    val isLastPage: Boolean,
)

data class PitchUiState(
    val running: Boolean = false,
    val preparing: Boolean = false,
    val replaying: Boolean = false,
    val points: List<F0Point> = emptyList(),
    val playbackPositionSeconds: Double? = null,
    val replayWindowStartSeconds: Double? = null,
    val replayWindowEndSeconds: Double? = null,
    val replayTailEndSeconds: Double? = null,
    val error: String? = null,
) {
    val currentHz: Float?
        get() {
            if (!running) return null
            val latestTimestamp = points.lastOrNull()?.timestampSeconds ?: return null
            val lastVoiced = points.asReversed().firstOrNull { it.f0Hz != null } ?: return null
            return lastVoiced.f0Hz.takeIf {
                latestTimestamp - lastVoiced.timestampSeconds <= CURRENT_F0_HOLD_SECONDS
            }
        }

    private companion object {
        const val CURRENT_F0_HOLD_SECONDS = 2.0
    }
}

class PitchViewModel(
    private val context: Context,
    private val repository: PitcheeRepository,
    private val recorder: RealtimeF0AudioRecorder,
    private val historyStore: HistoryStore,
) : ViewModel() {
    private var analysisJob: Job? = null
    private var playbackJob: Job? = null
    private var realtimeStream: PitcheeRealtimeF0? = null
    private var audioTrack: AudioTrack? = null
    private val sessionLock = Any()

    @Volatile
    private var runGeneration = 0

    @Volatile
    private var playbackGeneration = 0
    private var sessionRecorded = false
    private var sessionF0Sum = 0.0
    private var sessionF0Count = 0
    private var sessionLowCount = 0
    private var sessionHighCount = 0
    private var lastSummary: PitchSessionSummary? = null
    private var lastControlAtMillis = 0L
    private val audioRing = FloatArray((SAMPLE_RATE * HISTORY_SECONDS).toInt())
    private var ringWriteIndex = 0

    @Volatile
    private var totalSamples = 0L
    private var replayTailSample = 0L
    private val mutableState = MutableStateFlow(PitchUiState())
    val state: StateFlow<PitchUiState> = mutableState.asStateFlow()

    fun start() {
        if (analysisJob?.isActive == true) return
        resetSession()
        startAnalysisInternal()
    }

    fun rewindFiveSeconds(): PitchRewindFeedback? {
        val current = mutableState.value
        if (!current.running && !current.preparing && !current.replaying) return null
        if (!acceptControlEvent()) return null

        stopLiveCapture()
        stopPlaybackInternal()
        val currentWindowStart = mutableState.value.replayWindowStartSeconds
        val currentWindowEnd = mutableState.value.replayWindowEndSeconds
            ?: totalSamples.toDouble() / SAMPLE_RATE
        val liveTail = totalSamples.toDouble() / SAMPLE_RATE
        if (replayTailSample <= 0L) replayTailSample = totalSamples

        val oldestSeconds = oldestSample().toDouble() / SAMPLE_RATE
        val replayTailSeconds = replayTailSample.toDouble() / SAMPLE_RATE
        if (currentWindowStart != null &&
            currentWindowStart <= oldestSeconds + END_EPSILON_SECONDS
        ) {
            val replayEnd = min(oldestSeconds + REPLAY_WINDOW_SECONDS, replayTailSeconds)
            startReplayWindow(
                startSeconds = oldestSeconds,
                endSeconds = replayEnd,
            )
            return PitchRewindFeedback(
                secondsFromLatest = replayEnd - oldestSeconds,
                isLastPage = true,
            )
        }

        val targetEnd = if (currentWindowStart != null) {
            currentWindowStart
        } else {
            min(currentWindowEnd, replayTailSeconds)
        }
        val targetStart = max(oldestSeconds, targetEnd - REPLAY_WINDOW_SECONDS)
        if (targetEnd - targetStart <= END_EPSILON_SECONDS) {
            val replayEnd = min(oldestSeconds + REPLAY_WINDOW_SECONDS, replayTailSeconds)
            startReplayWindow(
                startSeconds = oldestSeconds,
                endSeconds = replayEnd,
            )
            return PitchRewindFeedback(
                secondsFromLatest = replayEnd - oldestSeconds,
                isLastPage = true,
            )
        }

        startReplayWindow(
            startSeconds = targetStart,
            endSeconds = min(targetEnd, liveTail),
        )
        return PitchRewindFeedback(
            secondsFromLatest = (targetEnd - targetStart).coerceAtLeast(0.0),
            isLastPage = targetStart <= oldestSeconds + END_EPSILON_SECONDS,
        )
    }

    fun resumeAnalysis() {
        stopPlaybackInternal()
        startAnalysisInternal()
    }

    fun finishSession(): PitchSessionSummary {
        recordSession()
        runGeneration++
        analysisJob?.cancel()
        analysisJob = null
        stopPlaybackInternal()
        recorder.stop()
        realtimeStream?.close()
        realtimeStream = null
        mutableState.update {
            it.copy(
                running = false,
                preparing = false,
                replaying = false,
                playbackPositionSeconds = null,
                replayWindowStartSeconds = null,
                replayWindowEndSeconds = null,
                replayTailEndSeconds = null,
            )
        }
        return lastSummary ?: PitchSessionSummary(
            meanF0Hz = null,
            maleRatio = 0f,
            femaleRatio = 0f,
            sampleCount = 0,
        )
    }

    fun clearDisplay() {
        val current = mutableState.value
        if (!current.running && !current.preparing && !current.replaying) {
            mutableState.value = PitchUiState()
        }
    }

    private fun resetSession() {
        runGeneration++
        analysisJob?.cancel()
        analysisJob = null
        stopPlaybackInternal()
        recorder.stop()
        realtimeStream?.close()
        realtimeStream = null
        synchronized(sessionLock) {
            audioRing.fill(0f)
            ringWriteIndex = 0
            totalSamples = 0L
            replayTailSample = 0L
        }
        mutableState.value = PitchUiState()
    }

    @Synchronized
    private fun startAnalysisInternal() {
        runGeneration++
        analysisJob?.cancel()
        analysisJob = null
        recorder.pause()
        replayTailSample = 0L
        val generation = runGeneration
        mutableState.update {
            it.copy(
                running = false,
                preparing = true,
                replaying = false,
                playbackPositionSeconds = null,
                replayWindowStartSeconds = null,
                replayWindowEndSeconds = null,
                replayTailEndSeconds = null,
                error = null,
            )
        }
        analysisJob = viewModelScope.launch(Dispatchers.Default) {
            val stream = realtimeStream ?: repository.createRealtimeF0().also {
                realtimeStream = it
            }
            try {
                recorder.resume()
                if (generation != runGeneration) return@launch
                mutableState.update { it.copy(running = true, preparing = false) }
                val buffer = FloatArray(READ_SAMPLES)
                while (isActive && generation == runGeneration) {
                    val count = recorder.read(buffer)
                    if (count <= 0) continue
                    val chunk = if (count == buffer.size) buffer.copyOf() else buffer.copyOf(count)
                    synchronized(sessionLock) {
                        if (isActive && generation == runGeneration) {
                            appendAudio(chunk)
                            stream.process(chunk) { timestamp, f0Hz, confidence, voiced ->
                                val measuredF0 = f0Hz.takeIf { voiced && it.isFinite() }
                                if (measuredF0 != null) {
                                    sessionF0Sum += measuredF0
                                    sessionF0Count++
                                    if (measuredF0 > F0_BOUNDARY_HZ) {
                                        sessionHighCount++
                                    } else {
                                        sessionLowCount++
                                    }
                                }
                                val point = F0Point(
                                    timestampSeconds = timestamp,
                                    f0Hz = measuredF0,
                                    confidence = confidence,
                                )
                                mutableState.update { current ->
                                    current.copy(
                                        points = (current.points + point).takeLast(MAX_POINTS),
                                        error = null,
                                    )
                                }
                            }
                        }
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (isActive && generation == runGeneration) {
                    mutableState.update {
                        it.copy(
                            running = false,
                            preparing = false,
                            error = error.message ?: context.getString(R.string.error_realtime_pitch_failed),
                        )
                    }
                }
            } finally {
                if (generation == runGeneration) {
                    recordSession()
                    recorder.stop()
                }
            }
        }
    }

    private fun stopLiveCapture() {
        runGeneration++
        val job = analysisJob
        analysisJob = null
        job?.cancel()
        recorder.pause()
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
            startAnalysisInternal()
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
        val windowSamples = (REPLAY_WINDOW_SECONDS * SAMPLE_RATE).toLong()
        val elapsed = (positionSample - replayStartSample).coerceAtLeast(0L)
        val windowStart = replayStartSample + (elapsed / windowSamples) * windowSamples
        val windowEnd = min(windowStart + windowSamples, replayTailSample)
        mutableState.update {
            it.copy(
                running = false,
                preparing = false,
                replaying = true,
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
                replaying = true,
                running = false,
                preparing = false,
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
                running = false,
                preparing = false,
                replaying = true,
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

    private fun acceptControlEvent(): Boolean {
        val now = System.nanoTime() / 1_000_000L
        if (now - lastControlAtMillis < CONTROL_DEBOUNCE_MILLIS) return false
        lastControlAtMillis = now
        return true
    }

    private fun recordSession() {
        if (sessionRecorded) return
        val total = sessionLowCount + sessionHighCount
        lastSummary = PitchSessionSummary(
            meanF0Hz = if (sessionF0Count > 0) sessionF0Sum / sessionF0Count else null,
            maleRatio = if (total > 0) sessionLowCount.toFloat() / total else 0f,
            femaleRatio = if (total > 0) sessionHighCount.toFloat() / total else 0f,
            sampleCount = total,
        )
        if (sessionF0Count > 0) {
            historyStore.addRealtimeF0(
                RealtimeF0HistoryEntry(
                    timestampMillis = System.currentTimeMillis(),
                    meanF0Hz = sessionF0Sum / sessionF0Count,
                ),
            )
        }
        sessionRecorded = true
    }

    override fun onCleared() {
        recordSession()
        runGeneration++
        val job = analysisJob
        analysisJob = null
        job?.cancel()
        stopPlaybackInternal()
        recorder.stop()
        realtimeStream?.close()
        realtimeStream = null
        if (job == null) repository.close() else job.invokeOnCompletion { repository.close() }
    }

    companion object {
        private const val F0_BOUNDARY_HZ = 165f
        private const val READ_SAMPLES = 512
        private const val SAMPLE_RATE = RealtimeF0AudioRecorder.SAMPLE_RATE
        private const val HISTORY_SECONDS = 30.0
        private const val REPLAY_WINDOW_SECONDS = 3.0
        private const val MAX_POINTS = 1880
        private const val PLAYBACK_CHUNK_SAMPLES = 1024
        private const val PLAYBACK_UPDATE_MILLIS = 8L
        private const val END_EPSILON_SECONDS = 0.01
        private const val END_EPSILON_SAMPLES = 160L
        private const val CONTROL_DEBOUNCE_MILLIS = 300L

        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                PitchViewModel(
                    context = appContext,
                    repository = PitcheeRepository(appContext),
                    recorder = RealtimeF0AudioRecorder(appContext),
                    historyStore = HistoryStore(appContext),
                )
            }
        }
    }
}
