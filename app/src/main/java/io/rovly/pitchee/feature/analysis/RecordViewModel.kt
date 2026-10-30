package io.rovly.pitchee.feature.analysis

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.rovly.pitchee.R
import io.rovly.pitchee.data.audio.AudioRecorder
import io.rovly.pitchee.data.audio.RecordedAudio
import io.rovly.pitchee.data.history.AnalysisHistoryEntry
import io.rovly.pitchee.data.history.HistoryStore
import io.rovly.pitchee.data.model.PitcheeResult
import io.rovly.pitchee.data.model.SpeechSegmentScore
import io.rovly.pitchee.data.model.scoreSpeechSegments
import io.rovly.pitchee.data.repository.PitcheeRepository
import io.rovly.pitchee.feature.settings.toScoreProfile
import io.rovly.pitchee.feature.settings.VoicePreferencePreferences
import java.io.File
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
import space.pitchee.core.PitcheePhase
import space.pitchee.core.PitcheeProgress
import space.pitchee.core.PitcheeScoreProfile

data class PreviousMetrics(
    val standardScore: Double,
    val naturalnessScore: Double,
    val meanF0Hz: Double?,
)

sealed interface RecordUiState {
    data object Ready : RecordUiState

    data class Recording(
        val elapsedSeconds: Float,
        val speechSeconds: Float,
        val amplitudes: List<Float>,
    ) : RecordUiState

    data class Analyzing(
        val phase: PitcheePhase,
        val progress: PitcheeProgress? = null,
        val audio: RecordedAudio? = null,
    ) : RecordUiState

    data class Success(
        val result: PitcheeResult,
        val audio: RecordedAudio,
        val previousScore: Double?,
        val previousMetrics: PreviousMetrics?,
        val segmentScores: List<SpeechSegmentScore>,
        val scoreAnimationToken: Long,
    ) : RecordUiState

    data class Error(
        val message: String? = null,
        @get:StringRes val messageRes: Int? = null,
        val audio: RecordedAudio? = null,
    ) : RecordUiState
}

internal class RecordViewModel(
    private val context: Context,
    private val repository: PitcheeRepository,
    private val recorder: AudioRecorder,
    private val scoreHistory: SharedPreferences,
    private val historyStore: HistoryStore,
    private val voicePreferences: VoicePreferencePreferences,
) : ViewModel() {
    private var captureJob: Job? = null
    private var vadJob: Job? = null
    private var vadChannel: Channel<VadWindow>? = null
    private var retainedAudio: RecordedAudio? = null
    private var stopping = false

    @Volatile
    private var captureActive = false

    @Volatile
    private var latestSpeechSeconds = 0.0

    @Volatile
    private var capturedSamples = 0L

    private val vadRollingSamples = FloatArray(VAD_WINDOW_SAMPLES)
    private var vadRollingWriteIndex = 0
    private var vadRollingCount = 0
    private var samplesSinceLastVad = 0

    private var lastResultScore: Double? = scoreHistory
        .getFloat(KEY_LAST_RESULT_SCORE, NO_PREVIOUS_SCORE)
        .takeUnless { it == NO_PREVIOUS_SCORE }
        ?.toDouble()
    private var lastResultProfile = PitcheeScoreProfile.fromJsonValue(
        scoreHistory.getString(KEY_LAST_SCORE_PROFILE, null),
    )
    private var lastResultMetrics: PreviousMetrics? = scoreHistory.readPreviousMetrics()
    private var resultSequence = 0L
    private var consumedScoreAnimationToken: Long? = null

    private val mutableState = MutableStateFlow<RecordUiState>(RecordUiState.Ready)
    val state: StateFlow<RecordUiState> = mutableState.asStateFlow()

    fun startRecording() {
        if (mutableState.value is RecordUiState.Recording ||
            mutableState.value is RecordUiState.Analyzing ||
            stopping
        ) {
            return
        }

        discardRetainedAudio()
        try {
            recorder.start()
        } catch (_: Throwable) {
            mutableState.value = RecordUiState.Error(
                messageRes = R.string.error_recording_start,
            )
            return
        }

        captureActive = true
        capturedSamples = 0L
        latestSpeechSeconds = 0.0
        resetVadWindow()
        val channel = Channel<VadWindow>(Channel.UNLIMITED)
        vadChannel = channel
        vadJob = startVadJob(channel)
        val amplitudes = mutableListOf(0f)
        mutableState.value = RecordUiState.Recording(
            elapsedSeconds = 0f,
            speechSeconds = 0f,
            amplitudes = amplitudes,
        )
        captureJob?.cancel()
        captureJob = viewModelScope.launch(Dispatchers.IO) {
            val pcm = ShortArray(AudioRecorder.CAPTURE_BUFFER_SAMPLES)
            try {
                while (isActive && captureActive) {
                    val count = recorder.readAndWrite(pcm)
                    if (count <= 0) {
                        if (!captureActive) break
                        throw IllegalStateException(
                            context.getString(R.string.error_recording_failed),
                        )
                    }
                    ingestCapturedAudio(pcm, count, amplitudes)
                    if (capturedSamples / AudioRecorder.SAMPLE_RATE.toDouble() >=
                        MAX_RECORDING_SECONDS
                    ) {
                        captureActive = false
                        viewModelScope.launch(Dispatchers.Main.immediate) {
                            stopAndAnalyze()
                        }
                        break
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (captureActive) handleCaptureFailure(error)
            }
        }
    }

    fun stopAndAnalyze() {
        if (mutableState.value !is RecordUiState.Recording || stopping) return
        stopping = true
        captureActive = false
        recorder.stopCapture()
        viewModelScope.launch {
            var file: File? = null
            try {
                captureJob?.join()
                captureJob = null
                enqueueVadSnapshot(force = true)
                vadChannel?.close()
                vadChannel = null
                vadJob?.join()
                vadJob = null

                val finishedFile = recorder.finish()
                file = finishedFile
                val elapsedSeconds = capturedSamples / AudioRecorder.SAMPLE_RATE.toDouble()
                if (elapsedSeconds < MINIMUM_RECORDING_SECONDS ||
                    latestSpeechSeconds < MINIMUM_SPEECH_SECONDS
                ) {
                    finishedFile.delete()
                    file = null
                    mutableState.value = RecordUiState.Error(
                        messageRes = R.string.error_speech_duration_short,
                    )
                    return@launch
                }

                mutableState.value = RecordUiState.Analyzing(PitcheePhase.LOADING_AUDIO)
                analyzeRecordedFile(finishedFile)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                recorder.cancel()
                file?.delete()
                mutableState.value = RecordUiState.Error(
                    message = error.message,
                    messageRes = R.string.error_recording_failed,
                )
            } finally {
                stopping = false
            }
        }
    }

    private suspend fun analyzeRecordedFile(file: File) {
        var recordedAudio: RecordedAudio? = null
        try {
            val pcm = repository.decode(Uri.fromFile(file), maxSeconds = null) { phase ->
                mutableState.value = RecordUiState.Analyzing(phase)
            }
            recordedAudio = RecordedAudio.from(file, pcm).also {
                retainedAudio = it
            }
            mutableState.value = RecordUiState.Analyzing(
                phase = PitcheePhase.PREPARING_MODELS,
                audio = recordedAudio,
            )
            val scoreProfile = voicePreferences.preference().toScoreProfile()
            val result = repository.analyzePcm(
                samples = pcm.samples,
                sampleRate = pcm.sampleRate,
                channels = pcm.channels,
                scoreProfile = scoreProfile,
                onProgress = { progress ->
                    mutableState.value = RecordUiState.Analyzing(
                        phase = progress.stage.toPhase(),
                        progress = progress,
                        audio = recordedAudio,
                    )
                },
            )
            val previousScore = lastResultScore.takeIf { lastResultProfile == scoreProfile }
            val previousMetrics = lastResultMetrics
            val segmentScores = scoreSpeechSegments(result)
            val currentMetrics = PreviousMetrics(
                standardScore = result.vfp.standardScore,
                naturalnessScore = result.naturalness.score,
                meanF0Hz = result.f0.meanHz,
            )
            lastResultScore = result.composite.finalScore
            lastResultProfile = scoreProfile
            lastResultMetrics = currentMetrics
            historyStore.addAnalysis(
                entry = AnalysisHistoryEntry(
                    timestampMillis = System.currentTimeMillis(),
                    finalScore = result.composite.finalScore,
                    standardScore = currentMetrics.standardScore,
                    naturalnessScore = currentMetrics.naturalnessScore,
                    meanF0Hz = currentMetrics.meanF0Hz,
                    f0VariationHz = result.f0.standardDeviationHz,
                    inputSeconds = recordedAudio.durationSeconds,
                    scoreProfile = scoreProfile.jsonValue,
                ),
                result = result,
                sourceAudio = recordedAudio.file,
            )
            scoreHistory.edit()
                .putFloat(KEY_LAST_RESULT_SCORE, result.composite.finalScore.toFloat())
                .putFloat(KEY_LAST_STANDARD_SCORE, currentMetrics.standardScore.toFloat())
                .putFloat(
                    KEY_LAST_NATURALNESS_SCORE,
                    currentMetrics.naturalnessScore.toFloat(),
                )
                .putFloat(
                    KEY_LAST_MEAN_F0_HZ,
                    currentMetrics.meanF0Hz?.toFloat() ?: NO_PREVIOUS_F0,
                )
                .putString(KEY_LAST_SCORE_PROFILE, scoreProfile.jsonValue)
                .apply()
            mutableState.value = RecordUiState.Success(
                result = result,
                audio = recordedAudio,
                previousScore = previousScore,
                previousMetrics = previousMetrics,
                segmentScores = segmentScores,
                scoreAnimationToken = ++resultSequence,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            val audio = recordedAudio
            if (audio == null) file.delete()
            mutableState.value = RecordUiState.Error(
                message = error.message,
                messageRes = R.string.error_analysis_failed
                    .takeIf { error.message.isNullOrBlank() },
                audio = audio,
            )
        }
    }

    private fun startVadJob(channel: Channel<VadWindow>): Job =
        viewModelScope.launch(Dispatchers.Default) {
            val tracker = SpeechIntervalTracker()
            for (window in channel) {
                val segments = try {
                    repository.detectVad(
                        samples = window.samples,
                        sampleRate = AudioRecorder.SAMPLE_RATE,
                        channels = 1,
                    )
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    Log.w(TAG, "Incremental VAD failed", error)
                    continue
                }
                val windowStart = window.startSample.toDouble() /
                    AudioRecorder.SAMPLE_RATE
                var speechSeconds = tracker.totalSeconds
                segments.forEach { segment ->
                    speechSeconds = tracker.add(
                        startSeconds = windowStart + segment.startSeconds,
                        endSeconds = windowStart + segment.endSeconds,
                    )
                }
                latestSpeechSeconds = speechSeconds
                mutableState.update { current ->
                    if (current is RecordUiState.Recording) {
                        current.copy(speechSeconds = speechSeconds.toFloat())
                    } else {
                        current
                    }
                }
            }
        }

    private fun ingestCapturedAudio(
        pcm: ShortArray,
        count: Int,
        amplitudes: MutableList<Float>,
    ) {
        var peak = 0f
        for (index in 0 until count) {
            val sample = pcm[index] / 32768f
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
            capturedSamples / AudioRecorder.SAMPLE_RATE.toDouble()
            ).toFloat().coerceAtMost(MAX_RECORDING_SECONDS)
        val snapshot = amplitudes.toList()
        mutableState.update { current ->
            if (current is RecordUiState.Recording) {
                current.copy(
                    elapsedSeconds = elapsedSeconds,
                    speechSeconds = latestSpeechSeconds.toFloat(),
                    amplitudes = snapshot,
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
        val endSample = capturedSamples
        vadChannel?.trySend(
            VadWindow(
                samples = samples,
                startSample = endSample - vadRollingCount,
            ),
        )
    }

    private fun resetVadWindow() {
        vadRollingSamples.fill(0f)
        vadRollingWriteIndex = 0
        vadRollingCount = 0
        samplesSinceLastVad = 0
    }

    private fun handleCaptureFailure(error: Throwable) {
        captureActive = false
        recorder.stopCapture()
        recorder.cancel()
        vadChannel?.close()
        vadChannel = null
        vadJob?.cancel()
        vadJob = null
        mutableState.value = RecordUiState.Error(
            message = error.message,
            messageRes = R.string.error_recording_failed,
        )
    }

    fun reset() {
        if (mutableState.value is RecordUiState.Analyzing) return
        captureActive = false
        recorder.stopCapture()
        captureJob?.cancel()
        captureJob = null
        vadChannel?.close()
        vadChannel = null
        vadJob?.cancel()
        vadJob = null
        recorder.cancel()
        discardRetainedAudio()
        mutableState.value = RecordUiState.Ready
    }

    fun consumeScoreAnimation(token: Long): Boolean {
        if (consumedScoreAnimationToken == token) return false
        consumedScoreAnimationToken = token
        return true
    }

    override fun onCleared() {
        captureActive = false
        recorder.stopCapture()
        captureJob?.cancel()
        vadChannel?.close()
        vadJob?.cancel()
        recorder.cancel()
        discardRetainedAudio()
        repository.close()
    }

    private fun discardRetainedAudio() {
        retainedAudio?.file?.delete()
        retainedAudio = null
    }

    private fun SharedPreferences.readPreviousMetrics(): PreviousMetrics? {
        if (!contains(KEY_LAST_STANDARD_SCORE) || !contains(KEY_LAST_NATURALNESS_SCORE)) {
            return null
        }
        val meanF0Hz = if (contains(KEY_LAST_MEAN_F0_HZ)) {
            getFloat(KEY_LAST_MEAN_F0_HZ, NO_PREVIOUS_F0)
                .takeUnless { it == NO_PREVIOUS_F0 }
                ?.toDouble()
        } else {
            null
        }
        return PreviousMetrics(
            standardScore = getFloat(KEY_LAST_STANDARD_SCORE, 0f).toDouble(),
            naturalnessScore = getFloat(KEY_LAST_NATURALNESS_SCORE, 0f).toDouble(),
            meanF0Hz = meanF0Hz,
        )
    }

    companion object {
        const val MAX_RECORDING_SECONDS = 60f
        const val MINIMUM_SPEECH_SECONDS = 5.0
        private const val MINIMUM_RECORDING_SECONDS = 5f
        private const val VAD_UPDATE_MILLIS = 300
        private const val VAD_WINDOW_SECONDS = 0.9
        private const val VAD_STEP_SAMPLES =
            AudioRecorder.SAMPLE_RATE * VAD_UPDATE_MILLIS / 1_000
        private val VAD_WINDOW_SAMPLES =
            (AudioRecorder.SAMPLE_RATE * VAD_WINDOW_SECONDS).toInt()
        private const val TAG = "PitcheeIncrementalVad"
        private const val SCORE_HISTORY_NAME = "score_history"
        private const val KEY_LAST_RESULT_SCORE = "last_result_score"
        private const val KEY_LAST_STANDARD_SCORE = "last_standard_score"
        private const val KEY_LAST_NATURALNESS_SCORE = "last_naturalness_score"
        private const val KEY_LAST_MEAN_F0_HZ = "last_mean_f0_hz"
        private const val KEY_LAST_SCORE_PROFILE = "last_score_profile"
        private const val NO_PREVIOUS_SCORE = -1f
        private const val NO_PREVIOUS_F0 = -1f

        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                RecordViewModel(
                    context = appContext,
                    repository = PitcheeRepository(appContext),
                    recorder = AudioRecorder(appContext),
                    scoreHistory = appContext.getSharedPreferences(
                        SCORE_HISTORY_NAME,
                        Context.MODE_PRIVATE,
                    ),
                    historyStore = HistoryStore(appContext),
                    voicePreferences = VoicePreferencePreferences(appContext),
                )
            }
        }
    }
}

private data class VadWindow(
    val samples: FloatArray,
    val startSample: Long,
)
