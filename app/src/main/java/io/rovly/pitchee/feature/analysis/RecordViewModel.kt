package io.rovly.pitchee.feature.analysis

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.SystemClock
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
import io.rovly.pitchee.data.model.hasSpeechSecondsAtLeast
import io.rovly.pitchee.data.model.scoreSpeechSegments
import io.rovly.pitchee.data.repository.PitcheeRepository
import io.rovly.pitchee.feature.settings.toScoreProfile
import io.rovly.pitchee.feature.settings.VoicePreferencePreferences
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import space.pitchee.core.PitcheePhase
import space.pitchee.core.PitcheeProgress
import space.pitchee.core.PitcheeProgressStage
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
        val message: String,
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
    private var timerJob: Job? = null
    private var retainedAudio: RecordedAudio? = null
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
            mutableState.value is RecordUiState.Analyzing
        ) {
            return
        }

        discardRetainedAudio()
        try {
            recorder.start()
        } catch (error: Throwable) {
            mutableState.value = RecordUiState.Error(
                error.message ?: context.getString(R.string.error_recording_start)
            )
            return
        }

        val amplitudes = mutableListOf(0f)
        mutableState.value = RecordUiState.Recording(0f, amplitudes)
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            val startedAt = SystemClock.elapsedRealtime()
            while (isActive && mutableState.value is RecordUiState.Recording) {
                delay(TIMER_INTERVAL_MS)
                val elapsed = (SystemClock.elapsedRealtime() - startedAt) / 1000f
                val previousAmplitude = amplitudes.lastOrNull() ?: 0f
                val nextAmplitude = recorder.currentAmplitude()
                amplitudes += previousAmplitude * 0.45f + nextAmplitude * 0.55f
                mutableState.value = RecordUiState.Recording(
                    elapsedSeconds = elapsed.coerceAtMost(MAX_RECORDING_SECONDS),
                    amplitudes = amplitudes.toList(),
                )
                if (elapsed >= MAX_RECORDING_SECONDS) {
                    stopAndAnalyze()
                    return@launch
                }
            }
        }
    }

    fun stopAndAnalyze() {
        val recording = mutableState.value as? RecordUiState.Recording ?: return

        timerJob?.cancel()
        timerJob = null
        val file = try {
            recorder.stop()
        } catch (error: Throwable) {
            mutableState.value = RecordUiState.Error(error.message ?: context.getString(R.string.error_recording_failed))
            return
        }

        if (recording.elapsedSeconds < MINIMUM_RECORDING_SECONDS) {
            file.delete()
            mutableState.value = RecordUiState.Error(context.getString(R.string.error_recording_duration_short))
            return
        }

        mutableState.value = RecordUiState.Analyzing(PitcheePhase.LOADING_AUDIO)
        viewModelScope.launch {
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
                if (!result.hasSpeechSecondsAtLeast(MINIMUM_SPEECH_SECONDS)) {
                    recordedAudio.file.delete()
                    retainedAudio = null
                    mutableState.value = RecordUiState.Error(
                        message = context.getString(R.string.error_speech_duration_short)
                    )
                } else {
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
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                val audio = recordedAudio
                if (audio == null) file.delete()
                mutableState.value = RecordUiState.Error(
                    message = error.message ?: context.getString(R.string.error_analysis_failed),
                    audio = audio,
                )
            }
        }
    }

    fun reset() {
        if (mutableState.value is RecordUiState.Analyzing) return
        discardRetainedAudio()
        mutableState.value = RecordUiState.Ready
    }

    fun consumeScoreAnimation(token: Long): Boolean {
        if (consumedScoreAnimationToken == token) return false
        consumedScoreAnimationToken = token
        return true
    }

    override fun onCleared() {
        timerJob?.cancel()
        recorder.cancel()
        discardRetainedAudio()
        repository.close()
    }

    private fun discardRetainedAudio() {
        retainedAudio?.file?.delete()
        retainedAudio = null
    }

    private fun PitcheeProgressStage.toPhase(): PitcheePhase = when (this) {
        PitcheeProgressStage.LOADING_AUDIO,
        PitcheeProgressStage.RESAMPLING_AUDIO,
        -> PitcheePhase.LOADING_AUDIO
        PitcheeProgressStage.COMPLETED -> PitcheePhase.COMPLETED
        else -> PitcheePhase.ANALYZING
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
        const val MAX_RECORDING_SECONDS = 20f
        const val MINIMUM_SPEECH_SECONDS = 5.0
        private const val MINIMUM_RECORDING_SECONDS = 5f
        private const val TIMER_INTERVAL_MS = 16L
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
