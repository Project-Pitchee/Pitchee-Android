package io.rovly.pitchee.ui

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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.rovly.pitchee.R
import io.rovly.pitchee.data.AnalysisHistoryEntry
import io.rovly.pitchee.data.FeminineTimeline
import io.rovly.pitchee.data.HistoryStore
import io.rovly.pitchee.data.PitcheeRepository
import io.rovly.pitchee.data.RecordedAudio
import io.rovly.pitchee.data.scoreSpeechSegments
import java.io.File

private sealed interface HistoryDetailState {
    data object Loading : HistoryDetailState
    data class Loaded(
        val result: io.rovly.pitchee.data.PitcheeResult,
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
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = current.message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onBack) {
                        Text(stringResource(R.string.history_detail_back))
                    }
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
    result: io.rovly.pitchee.data.PitcheeResult,
    audio: RecordedAudio?,
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
        FilledTonalButton(
            onClick = onBack,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        ) {
            Text(stringResource(R.string.history_detail_back))
        }
        Spacer(Modifier.height(8.dp))
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
    result: io.rovly.pitchee.data.PitcheeResult,
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
        TextButton(onClick = onBack) {
            Text(stringResource(R.string.history_detail_back_result))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.history_detail_rules),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.history_detail_rules_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        ScoreRulesContent(
            result = result,
            previousScore = previousScore,
            previousMetrics = previousMetrics,
        )
    }
}
