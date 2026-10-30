package io.rovly.pitchee.feature.voicehealth

import android.content.Context
import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.rovly.pitchee.R
import io.rovly.pitchee.data.audio.RealtimeF0AudioRecorder
import io.rovly.pitchee.data.repository.PitcheeRepository
import io.rovly.pitchee.feature.analysis.SpeechIntervalTracker
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.sqrt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import space.pitchee.core.PitcheeVoiceHealthProgress
import space.pitchee.core.PitcheeVoiceHealthResult

sealed interface VoiceHealthUiState {
    data object Ready : VoiceHealthUiState

    data class Recording(
        val elapsedSeconds: Float,
        val validSpeechSeconds: Float,
        val amplitudes: List<Float>,
    ) : VoiceHealthUiState

    data class Analyzing(
        val progress: PitcheeVoiceHealthProgress? = null,
    ) : VoiceHealthUiState

    data class Success(
        val result: PitcheeVoiceHealthResult,
    ) : VoiceHealthUiState

    data class Error(
        val message: String? = null,
        @get:StringRes val messageRes: Int? = null,
    ) : VoiceHealthUiState
}

internal class VoiceHealthViewModel(
    private val repository: PitcheeRepository,
    private val recorder: RealtimeF0AudioRecorder,
) : ViewModel() {
    private var captureJob: Job? = null
    private var vadJob: Job? = null
    private var vadChannel: Channel<VoiceHealthVadWindow>? = null
    private var analysisJob: Job? = null
    private var stopping = false
    private val captureActive = AtomicBoolean(false)

    @Volatile
    private var validSpeechSeconds = 0.0

    @Volatile
    private var capturedSamples = 0L

    private val bufferLock = Any()
    private var pcmBuffer = FloatArray(INITIAL_BUFFER_SAMPLES)
    private var pcmSize = 0
    private val vadRollingSamples = FloatArray(VAD_WINDOW_SAMPLES)
    private var vadRollingWriteIndex = 0
    private var vadRollingCount = 0
    private var samplesSinceLastVad = 0

    private val mutableState = MutableStateFlow<VoiceHealthUiState>(VoiceHealthUiState.Ready)
    val state: StateFlow<VoiceHealthUiState> = mutableState.asStateFlow()

    fun startRecording() {
        if (mutableState.value is VoiceHealthUiState.Recording ||
            mutableState.value is VoiceHealthUiState.Analyzing ||
            stopping
        ) {
            return
        }
        clearPcm()
        runCatching { recorder.start() }
            .onSuccess {
                captureActive.set(true)
                capturedSamples = 0L
                validSpeechSeconds = 0.0
                resetVadWindow()
                val channel = Channel<VoiceHealthVadWindow>(Channel.UNLIMITED)
                vadChannel = channel
                vadJob = startVadJob(channel)
                val amplitudes = mutableListOf(0f)
                mutableState.value = VoiceHealthUiState.Recording(
                    elapsedSeconds = 0f,
                    validSpeechSeconds = 0f,
                    amplitudes = amplitudes,
                )
                startCapture(amplitudes)
            }
            .onFailure {
                mutableState.value = VoiceHealthUiState.Error(
                    messageRes = R.string.voice_health_analysis_failed,
                )
            }
    }

    fun stopAndAnalyze() {
        if (mutableState.value !is VoiceHealthUiState.Recording || stopping) return
        stopping = true
        captureActive.set(false)
        recorder.pause()
        viewModelScope.launch {
            try {
                captureJob?.join()
                captureJob = null
                enqueueVadSnapshot(force = true)
                vadChannel?.close()
                vadChannel = null
                vadJob?.join()
                vadJob = null
                recorder.stop()

                if (validSpeechSeconds < MINIMUM_SPEECH_SECONDS) {
                    clearPcm()
                    mutableState.value = VoiceHealthUiState.Error(
                        messageRes = R.string.error_speech_duration_short,
                    )
                    return@launch
                }
                val samples = snapshotPcm()
                if (samples.size < MINIMUM_SPEECH_SECONDS * SAMPLE_RATE) {
                    clearPcm()
                    mutableState.value = VoiceHealthUiState.Error(
                        messageRes = R.string.error_speech_duration_short,
                    )
                    return@launch
                }
                clearPcm()
                mutableState.value = VoiceHealthUiState.Analyzing()
                analysisJob?.cancel()
                analysisJob = viewModelScope.launch(Dispatchers.Default) {
                    analyzeSamples(samples)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                recorder.stop()
                clearPcm()
                mutableState.value = VoiceHealthUiState.Error(
                    message = error.message,
                    messageRes = R.string.voice_health_analysis_failed
                        .takeIf { error.message.isNullOrBlank() },
                )
            } finally {
                stopping = false
            }
        }
    }

    fun reset() {
        if (mutableState.value is VoiceHealthUiState.Analyzing) return
        cancelRecordingJobs()
        analysisJob?.cancel()
        analysisJob = null
        clearPcm()
        mutableState.value = VoiceHealthUiState.Ready
    }

    fun cancel() {
        cancelRecordingJobs()
        analysisJob?.cancel()
        analysisJob = null
        clearPcm()
        mutableState.value = VoiceHealthUiState.Ready
    }

    override fun onCleared() {
        cancelRecordingJobs()
        analysisJob?.cancel()
        repository.close()
    }

    private fun startCapture(amplitudes: MutableList<Float>) {
        captureJob?.cancel()
        captureJob = viewModelScope.launch(Dispatchers.IO) {
            val readBuffer = FloatArray(READ_BUFFER_SAMPLES)
            try {
                while (isActive && captureActive.get()) {
                    val count = recorder.read(readBuffer)
                    if (count <= 0) {
                        if (!captureActive.get()) break
                        throw IllegalStateException()
                    }
                    ingestCapturedAudio(readBuffer, count, amplitudes)
                    if (capturedSamples / SAMPLE_RATE.toDouble() >= MAXIMUM_RECORDING_SECONDS) {
                        captureActive.set(false)
                        viewModelScope.launch(Dispatchers.Main.immediate) {
                            stopAndAnalyze()
                        }
                        break
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (captureActive.getAndSet(false)) {
                    recorder.pause()
                    vadChannel?.close()
                    vadChannel = null
                    vadJob?.cancel()
                    vadJob = null
                    clearPcm()
                    mutableState.value = VoiceHealthUiState.Error(
                        message = error.message,
                        messageRes = R.string.voice_health_analysis_failed
                            .takeIf { error.message.isNullOrBlank() },
                    )
                }
            }
        }
    }

    private fun startVadJob(channel: Channel<VoiceHealthVadWindow>): Job =
        viewModelScope.launch(Dispatchers.Default) {
            val tracker = SpeechIntervalTracker()
            for (window in channel) {
                val validSeconds = try {
                    repository.detectVad(
                        samples = window.samples,
                        sampleRate = SAMPLE_RATE,
                        channels = 1,
                    ).forEach { segment ->
                        val windowStart = window.startSample.toDouble() / SAMPLE_RATE
                        tracker.add(
                            startSeconds = windowStart + segment.startSeconds,
                            endSeconds = windowStart + segment.endSeconds,
                        )
                    }
                    tracker.totalSeconds
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    Log.w(TAG, "Incremental VAD failed", error)
                    continue
                }
                validSpeechSeconds = validSeconds
                mutableState.update { current ->
                    if (current is VoiceHealthUiState.Recording) {
                        current.copy(
                            validSpeechSeconds = validSeconds.toFloat(),
                        )
                    } else {
                        current
                    }
                }
            }
        }

    private fun ingestCapturedAudio(
        samples: FloatArray,
        count: Int,
        amplitudes: MutableList<Float>,
    ) {
        appendPcm(samples, count)
        var peak = 0f
        for (index in 0 until count) {
            val sample = samples[index]
            val absolute = abs(sample)
            if (absolute > peak) peak = absolute
            vadRollingSamples[vadRollingWriteIndex] = sample
            vadRollingWriteIndex = (vadRollingWriteIndex + 1) % vadRollingSamples.size
            if (vadRollingCount < vadRollingSamples.size) vadRollingCount++
        }
        capturedSamples += count
        samplesSinceLastVad += count
        while (samplesSinceLastVad >= VAD_STEP_SAMPLES) {
            enqueueVadSnapshot(force = false)
            samplesSinceLastVad -= VAD_STEP_SAMPLES
        }

        val amplitude = sqrt(peak)
        val previousAmplitude = amplitudes.lastOrNull() ?: 0f
        amplitudes += previousAmplitude * 0.45f + amplitude * 0.55f
        val elapsedSeconds = (
            capturedSamples / SAMPLE_RATE.toDouble()
            ).toFloat().coerceAtMost(MAXIMUM_RECORDING_SECONDS.toFloat())
        val amplitudeSnapshot = amplitudes.toList()
        mutableState.update { current ->
            if (current is VoiceHealthUiState.Recording) {
                current.copy(
                    elapsedSeconds = elapsedSeconds,
                    validSpeechSeconds = validSpeechSeconds.toFloat(),
                    amplitudes = amplitudeSnapshot,
                )
            } else {
                current
            }
        }
    }

    private fun enqueueVadSnapshot(force: Boolean) {
        if (vadRollingCount == 0) return
        if (!force && samplesSinceLastVad < VAD_STEP_SAMPLES) return
        val samples = FloatArray(vadRollingCount)
        val first = (
            vadRollingWriteIndex - vadRollingCount + vadRollingSamples.size
            ) % vadRollingSamples.size
        for (index in samples.indices) {
            samples[index] = vadRollingSamples[
                (first + index) % vadRollingSamples.size
            ]
        }
        vadChannel?.trySend(
            VoiceHealthVadWindow(
                samples = samples,
                startSample = capturedSamples - vadRollingCount,
            ),
        )
    }

    private fun resetVadWindow() {
        vadRollingSamples.fill(0f)
        vadRollingWriteIndex = 0
        vadRollingCount = 0
        samplesSinceLastVad = 0
    }

    private suspend fun analyzeSamples(samples: FloatArray) {
        try {
            val result = repository.analyzeVoiceHealth(
                samples = samples,
                sampleRate = SAMPLE_RATE,
                channels = 1,
            ) { progress ->
                mutableState.value = VoiceHealthUiState.Analyzing(progress)
            }
            mutableState.value = VoiceHealthUiState.Success(result)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            mutableState.value = VoiceHealthUiState.Error(
                message = error.message,
                messageRes = R.string.voice_health_analysis_failed
                    .takeIf { error.message.isNullOrBlank() },
            )
        }
    }

    private fun cancelRecordingJobs() {
        captureActive.set(false)
        recorder.pause()
        captureJob?.cancel()
        captureJob = null
        vadChannel?.close()
        vadChannel = null
        vadJob?.cancel()
        vadJob = null
        recorder.stop()
    }

    private fun appendPcm(samples: FloatArray, count: Int) {
        synchronized(bufferLock) {
            ensureCapacity(pcmSize + count)
            samples.copyInto(pcmBuffer, destinationOffset = pcmSize, endIndex = count)
            pcmSize += count
        }
    }

    private fun snapshotPcm(): FloatArray = synchronized(bufferLock) {
        pcmBuffer.copyOf(pcmSize)
    }

    private fun clearPcm() {
        synchronized(bufferLock) {
            pcmSize = 0
        }
    }

    private fun ensureCapacity(required: Int) {
        if (required <= pcmBuffer.size) return
        var newSize = pcmBuffer.size
        while (newSize < required) newSize *= 2
        pcmBuffer = pcmBuffer.copyOf(newSize)
    }

    companion object {
        const val MINIMUM_SPEECH_SECONDS = 5f
        private const val MAXIMUM_RECORDING_SECONDS = 20f
        private const val VAD_UPDATE_MILLIS = 300
        private const val VAD_WINDOW_SECONDS = 0.9
        private const val VAD_STEP_SAMPLES =
            RealtimeF0AudioRecorder.SAMPLE_RATE * VAD_UPDATE_MILLIS / 1_000
        private val VAD_WINDOW_SAMPLES =
            (RealtimeF0AudioRecorder.SAMPLE_RATE * VAD_WINDOW_SECONDS).toInt()
        private const val SAMPLE_RATE = RealtimeF0AudioRecorder.SAMPLE_RATE
        private const val TAG = "PitcheeHealthVad"
        private const val INITIAL_BUFFER_SAMPLES = RealtimeF0AudioRecorder.SAMPLE_RATE * 5
        private const val READ_BUFFER_SAMPLES = 400

        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                VoiceHealthViewModel(
                    repository = PitcheeRepository(appContext),
                    recorder = RealtimeF0AudioRecorder(appContext),
                )
            }
        }
    }
}

private data class VoiceHealthVadWindow(
    val samples: FloatArray,
    val startSample: Long,
)
