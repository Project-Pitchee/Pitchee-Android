package io.rovly.pitchee.feature.voicehealth

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.rovly.pitchee.R
import io.rovly.pitchee.data.audio.RealtimeF0AudioRecorder
import io.rovly.pitchee.data.repository.PitcheeRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    ) : VoiceHealthUiState

    data class Analyzing(
        val progress: PitcheeVoiceHealthProgress? = null,
    ) : VoiceHealthUiState

    data class Success(
        val result: PitcheeVoiceHealthResult,
    ) : VoiceHealthUiState

    data class Error(
        val message: String,
    ) : VoiceHealthUiState
}

internal class VoiceHealthViewModel(
    private val context: Context,
    private val repository: PitcheeRepository,
    private val recorder: RealtimeF0AudioRecorder,
) : ViewModel() {
    private var timerJob: Job? = null
    private var captureJob: Job? = null
    private var vadJob: Job? = null
    private var analysisJob: Job? = null
    private var recordingStartedAtMillis = 0L

    private val bufferLock = Any()
    private var pcmBuffer = FloatArray(INITIAL_BUFFER_SAMPLES)
    private var pcmSize = 0

    private val mutableState = MutableStateFlow<VoiceHealthUiState>(VoiceHealthUiState.Ready)
    val state: StateFlow<VoiceHealthUiState> = mutableState.asStateFlow()

    fun startRecording() {
        if (mutableState.value is VoiceHealthUiState.Recording ||
            mutableState.value is VoiceHealthUiState.Analyzing
        ) {
            return
        }
        clearPcm()
        runCatching { recorder.start() }
            .onSuccess {
                recordingStartedAtMillis = SystemClock.elapsedRealtime()
                mutableState.value = VoiceHealthUiState.Recording(
                    elapsedSeconds = 0f,
                    validSpeechSeconds = 0f,
                )
                startTimer()
                startCapture()
                startVadPolling()
            }
            .onFailure { error ->
                mutableState.value = VoiceHealthUiState.Error(
                    error.message ?: context.getString(R.string.voice_health_analysis_failed),
                )
            }
    }

    fun stopAndAnalyze() {
        val recording = mutableState.value as? VoiceHealthUiState.Recording ?: return
        if (recording.validSpeechSeconds < MINIMUM_SPEECH_SECONDS) {
            cancelRecordingJobs()
            clearPcm()
            mutableState.value = VoiceHealthUiState.Error(
                context.getString(R.string.error_speech_duration_short),
            )
            return
        }

        cancelRecordingJobs()
        val samples = snapshotPcm()
        if (samples.size < MINIMUM_SPEECH_SECONDS * RealtimeF0AudioRecorder.SAMPLE_RATE) {
            clearPcm()
            mutableState.value = VoiceHealthUiState.Error(
                context.getString(R.string.error_speech_duration_short),
            )
            return
        }
        clearPcm()
        mutableState.value = VoiceHealthUiState.Analyzing()
        analysisJob?.cancel()
        analysisJob = viewModelScope.launch(Dispatchers.Default) {
            try {
                val result = repository.analyzeVoiceHealth(
                    samples = samples,
                    sampleRate = RealtimeF0AudioRecorder.SAMPLE_RATE,
                    channels = 1,
                ) { progress ->
                    mutableState.value = VoiceHealthUiState.Analyzing(progress)
                }
                mutableState.value = VoiceHealthUiState.Success(result)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                mutableState.value = VoiceHealthUiState.Error(
                    error.message ?: context.getString(R.string.voice_health_analysis_failed),
                )
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

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive) {
                val elapsed = elapsedSeconds()
                mutableState.update { current ->
                    if (current is VoiceHealthUiState.Recording) {
                        current.copy(elapsedSeconds = elapsed)
                    } else {
                        current
                    }
                }
                if (elapsed >= MAXIMUM_RECORDING_SECONDS) {
                    stopAndAnalyze()
                    break
                }
                delay(TIMER_INTERVAL_MILLIS)
            }
        }
    }

    private fun startCapture() {
        captureJob?.cancel()
        captureJob = viewModelScope.launch(Dispatchers.Default) {
            val readBuffer = FloatArray(READ_BUFFER_SAMPLES)
            while (isActive) {
                val count = runCatching { recorder.read(readBuffer) }.getOrElse { error ->
                    if (isActive) {
                        mutableState.value = VoiceHealthUiState.Error(
                            error.message
                                ?: context.getString(R.string.voice_health_analysis_failed),
                        )
                    }
                    return@launch
                }
                if (count > 0) appendPcm(readBuffer, count)
            }
        }
    }

    private fun startVadPolling() {
        vadJob?.cancel()
        vadJob = viewModelScope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(VAD_POLL_INTERVAL_MILLIS)
                val samples = snapshotPcm()
                if (samples.isEmpty()) continue
                val validSeconds = runCatching {
                    repository.detectVad(
                        samples = samples,
                        sampleRate = RealtimeF0AudioRecorder.SAMPLE_RATE,
                        channels = 1,
                    ).sumOf { (it.endSeconds - it.startSeconds).coerceAtLeast(0.0) }
                }.getOrDefault(0.0)
                mutableState.update { current ->
                    if (current is VoiceHealthUiState.Recording) {
                        current.copy(
                            validSpeechSeconds = validSeconds.toFloat()
                                .coerceIn(0f, MINIMUM_SPEECH_SECONDS),
                        )
                    } else {
                        current
                    }
                }
            }
        }
    }

    private fun cancelRecordingJobs() {
        timerJob?.cancel()
        timerJob = null
        captureJob?.cancel()
        captureJob = null
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

    private fun elapsedSeconds(): Float =
        ((SystemClock.elapsedRealtime() - recordingStartedAtMillis) / 1000f)
            .coerceAtLeast(0f)

    companion object {
        const val MINIMUM_SPEECH_SECONDS = 5f
        private const val MAXIMUM_RECORDING_SECONDS = 20f
        private const val TIMER_INTERVAL_MILLIS = 50L
        private const val VAD_POLL_INTERVAL_MILLIS = 500L
        private const val INITIAL_BUFFER_SAMPLES = RealtimeF0AudioRecorder.SAMPLE_RATE * 5
        private const val READ_BUFFER_SAMPLES = 1600

        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                VoiceHealthViewModel(
                    context = appContext,
                    repository = PitcheeRepository(appContext),
                    recorder = RealtimeF0AudioRecorder(appContext),
                )
            }
        }
    }
}
