package io.rovly.pitchee.feature.analysis

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.rovly.pitchee.R
import io.rovly.pitchee.data.audio.RecordedAudio
import io.rovly.pitchee.data.history.AnalysisHistoryEntry
import io.rovly.pitchee.data.history.HistoryStore
import io.rovly.pitchee.data.model.FeminineTimeline
import io.rovly.pitchee.data.model.PitcheeResult
import io.rovly.pitchee.data.model.scoreSpeechSegments
import io.rovly.pitchee.data.repository.PitcheeRepository
import io.rovly.pitchee.ui.common.ScreenTitle
import io.rovly.pitchee.ui.common.SwipeBackContainer
import java.io.File
import space.pitchee.core.PitcheeScoreProfile

private sealed interface HistoryDetailState {
    data object Loading : HistoryDetailState
    data class Loaded(
        val result: io.rovly.pitchee.data.model.PitcheeResult,
        val audio: RecordedAudio?,
        val previousScore: Double?,
        val previousMetrics: PreviousMetrics?,
    ) : HistoryDetailState
    data class Error(val message: String) : HistoryDetailState
}

@Composable
internal fun AnalysisHistoryDetailScreen(
    entry: AnalysisHistoryEntry,
    store: HistoryStore,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val repository = remember { PitcheeRepository(context.applicationContext) }
    var state by remember(entry.timestampMillis) {
        mutableStateOf<HistoryDetailState>(HistoryDetailState.Loading)
    }
    var showingRules by remember { mutableStateOf(false) }
    val missingResultMessage = stringResource(R.string.error_analysis_result_missing)
    val openAnalysisMessage = stringResource(R.string.error_analysis_open)

    DisposableEffect(repository) {
        onDispose { repository.close() }
    }

    LaunchedEffect(entry.timestampMillis, missingResultMessage, openAnalysisMessage) {
        state = runCatching {
            val result = store.analysisResult(entry)
                ?: error(missingResultMessage)
            val audio = store.analysisAudioFile(entry)?.let { audioFile ->
                runCatching {
                    val pcm = repository.decode(Uri.fromFile(audioFile))
                    RecordedAudio.from(audioFile, pcm)
                }.getOrNull()
            }
            val previousEntry = store.analyses()
                .filter { it.timestampMillis < entry.timestampMillis }
                .filter {
                    (it.scoreProfile ?: PitcheeScoreProfile.FEMINIZATION.jsonValue) ==
                        (entry.scoreProfile ?: PitcheeScoreProfile.FEMINIZATION.jsonValue)
                }
                .maxByOrNull { it.timestampMillis }
            HistoryDetailState.Loaded(
                result = result,
                audio = audio,
                previousScore = previousEntry?.finalScore,
                previousMetrics = previousEntry?.let {
                    PreviousMetrics(
                        standardScore = it.standardScore,
                        naturalnessScore = it.naturalnessScore,
                        meanF0Hz = it.meanF0Hz,
                    )
                },
            )
        }.getOrElse { error ->
            HistoryDetailState.Error(error.message ?: openAnalysisMessage)
        }
    }

    BackHandler {
        if (showingRules) {
            showingRules = false
        } else {
            onBack()
        }
    }

    SwipeBackContainer(
        enabled = true,
        onBack = {
            if (showingRules) showingRules = false else onBack()
        },
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            when (val current = state) {
            HistoryDetailState.Loading -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.weight(1f))
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.history_detail_loading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                }
            }

            is HistoryDetailState.Error -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                ) {
                    ScreenTitle(
                        title = stringResource(R.string.history_detail_error_title),
                        onBack = onBack,
                    )
                    Spacer(Modifier.height(20.dp))
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = current.message,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.weight(1f))
                }
            }

            is HistoryDetailState.Loaded -> {
                if (showingRules) {
                    HistoryRulesView(
                        result = current.result,
                        previousScore = current.previousScore,
                        previousMetrics = current.previousMetrics,
                        onBack = { showingRules = false },
                    )
                } else {
                    HistoryResultView(
                        result = current.result,
                        audio = current.audio,
                        timestampMillis = entry.timestampMillis,
                        previousScore = current.previousScore,
                        previousMetrics = current.previousMetrics,
                        onBack = onBack,
                        onOpenRules = { showingRules = true },
                    )
                }
            }
            }
        }
    }
}

@Composable
private fun HistoryResultView(
    result: io.rovly.pitchee.data.model.PitcheeResult,
    audio: RecordedAudio?,
    timestampMillis: Long,
    previousScore: Double?,
    previousMetrics: PreviousMetrics?,
    onBack: () -> Unit,
    onOpenRules: () -> Unit,
) {
    val timeline = remember(result, audio?.durationSeconds) {
        audio?.let { FeminineTimeline.from(result, it.durationSeconds) }
    }
    val segmentScores = remember(result) { scoreSpeechSegments(result) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
    ) {
        ScreenTitle(
            title = stringResource(R.string.analysis_result_title),
            onBack = onBack,
            trailingContent = {
                AnalysisExportButton(
                    result = result,
                    audio = audio,
                    timestampMillis = timestampMillis,
                )
            },
        )
        Spacer(Modifier.height(14.dp))
        ScoreResultContent(
            result = result,
            previousScore = previousScore,
            previousMetrics = previousMetrics,
            animateScore = false,
            onOpenRules = onOpenRules,
            audioPlayer = audio?.let { recordedAudio ->
                {
                    RecordedAudioTimeline(
                        audio = recordedAudio,
                        f0Windows = result.f0.windows,
                        segmentScores = segmentScores,
                        metricsTimeline = timeline,
                    )
                }
            } ?: { DisabledAudioPlayerButton() },
        )
    }
}

@Composable
private fun HistoryRulesView(
    result: io.rovly.pitchee.data.model.PitcheeResult,
    previousScore: Double?,
    previousMetrics: PreviousMetrics?,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
    ) {
        ScreenTitle(
            title = stringResource(R.string.history_detail_rules),
            subtitle = stringResource(R.string.history_detail_rules_subtitle),
            onBack = onBack,
        )
        Spacer(Modifier.height(20.dp))
        ScoreRulesContent(
            result = result,
            previousScore = previousScore,
            previousMetrics = previousMetrics,
        )
    }
}
