package io.rovly.pitchee.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.rovly.pitchee.R
import space.pitchee.core.PitcheePhase
import space.pitchee.core.PitcheeProgress
import space.pitchee.core.PitcheeProgressStage

private const val PRE_ANALYSIS_DECODE_PROGRESS = 0.04f
private const val PRE_ANALYSIS_PROGRESS_WEIGHT = 0.11f

private enum class RecordVisualMode {
    IDLE,
    RECORDING,
    ANALYZING,
}

@Composable
internal fun RecordingStage(
    state: RecordUiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mode = when (state) {
        is RecordUiState.Recording -> RecordVisualMode.RECORDING
        is RecordUiState.Analyzing -> RecordVisualMode.ANALYZING
        else -> RecordVisualMode.IDLE
    }
    val recording = state as? RecordUiState.Recording
    val analyzing = state as? RecordUiState.Analyzing
    val startLabel = stringResource(R.string.start_recording)
    val stopLabel = stringResource(R.string.stop_and_analyze)
    val statusText = when (state) {
        is RecordUiState.Recording -> stringResource(
            R.string.recording_elapsed,
            state.elapsedSeconds,
        )
        is RecordUiState.Analyzing -> {
            val progress = state.progress
            if (progress != null) {
                analysisProgressText(progress)
            } else {
                stringResource(
                    when (state.phase) {
                        PitcheePhase.LOADING_AUDIO -> R.string.analysis_phase_audio
                        PitcheePhase.PREPARING_MODELS -> R.string.analysis_phase_models
                        PitcheePhase.ANALYZING -> R.string.analysis_phase_inference
                        PitcheePhase.COMPLETED -> R.string.analysis_phase_finishing
                    }
                )
            }
        }
        else -> stringResource(R.string.click_to_record)
    }

    val targetButtonSize = when (mode) {
        RecordVisualMode.IDLE -> 96.dp
        RecordVisualMode.RECORDING -> 108.dp
        RecordVisualMode.ANALYZING -> 92.dp
    }
    val buttonSize by animateDpAsState(
        targetValue = targetButtonSize,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 420f),
        label = "record-button-size",
    )
    val primaryColor = MaterialTheme.colorScheme.primary
    val errorColor = MaterialTheme.colorScheme.error
    val buttonColor = when (mode) {
        RecordVisualMode.IDLE -> primaryColor
        RecordVisualMode.RECORDING -> errorColor
        RecordVisualMode.ANALYZING -> MaterialTheme.colorScheme.primaryContainer
    }
    val pulseTransition = rememberInfiniteTransition(label = "recording-pulse")
    val pulse by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.14f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "recording-pulse-scale",
    )
    val waveformAlpha by animateFloatAsState(
        targetValue = if (recording != null) 1f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "recording-waveform-alpha",
    )
    val recordingProgress by animateFloatAsState(
        targetValue = recording?.let {
            (it.elapsedSeconds / RecordViewModel.MAX_RECORDING_SECONDS).coerceIn(0f, 1f)
        } ?: 0f,
        animationSpec = tween(durationMillis = 180),
        label = "recording-progress",
    )
    val analysisProgress by animateFloatAsState(
        targetValue = analyzing?.analysisTargetProgress() ?: 0f,
        animationSpec = spring(
            dampingRatio = 0.86f,
            stiffness = 80f,
            visibilityThreshold = 0.0005f,
        ),
        label = "analysis-progress",
    )
    val progress = if (mode == RecordVisualMode.ANALYZING) {
        analysisProgress
    } else {
        recordingProgress
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            LiveWaveform(
                amplitudes = recording?.amplitudes.orEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .graphicsLayer {
                        alpha = waveformAlpha
                        translationX = (1f - waveformAlpha) * size.width * 0.3f
                    },
                color = errorColor,
            )

            Box(
                modifier = Modifier.size(192.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier.size(132.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (recording != null) {
                        Canvas(
                            modifier = Modifier
                                .size(132.dp)
                                .scale(pulse),
                        ) {
                            drawCircle(errorColor.copy(alpha = 0.10f))
                        }
                        Canvas(Modifier.size(128.dp)) {
                            val stroke = 4.dp.toPx()
                            drawArc(
                                color = errorColor.copy(alpha = 0.18f),
                                startAngle = -90f,
                                sweepAngle = 360f,
                                useCenter = false,
                                style = Stroke(width = stroke, cap = StrokeCap.Round),
                            )
                            drawArc(
                                color = errorColor,
                                startAngle = -90f,
                                sweepAngle = progress * 360f,
                                useCenter = false,
                                style = Stroke(width = stroke, cap = StrokeCap.Round),
                            )
                        }
                    }

                    Surface(
                        onClick = {
                            when (mode) {
                                RecordVisualMode.IDLE -> onStart()
                                RecordVisualMode.RECORDING -> onStop()
                                RecordVisualMode.ANALYZING -> Unit
                            }
                        },
                        enabled = mode != RecordVisualMode.ANALYZING,
                        modifier = Modifier
                            .size(buttonSize)
                            .semantics {
                                contentDescription = if (mode == RecordVisualMode.RECORDING) {
                                    stopLabel
                                } else {
                                    startLabel
                                }
                            },
                        shape = CircleShape,
                        color = buttonColor,
                        contentColor = if (mode == RecordVisualMode.IDLE) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            Color.White
                        },
                        shadowElevation = if (mode == RecordVisualMode.RECORDING) 10.dp else 4.dp,
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            AnimatedContent(
                                targetState = mode,
                                transitionSpec = {
                                    scaleIn(initialScale = 0.55f) togetherWith
                                        scaleOut(targetScale = 0.55f)
                                },
                                label = "record-button-content",
                            ) { currentMode ->
                                when (currentMode) {
                                    RecordVisualMode.IDLE -> Canvas(Modifier.size(34.dp)) {
                                        drawCircle(Color.White)
                                    }
                                    RecordVisualMode.RECORDING -> Canvas(Modifier.size(30.dp)) {
                                        drawRoundRect(
                                            color = Color.White,
                                            cornerRadius = CornerRadius(5.dp.toPx()),
                                        )
                                    }
                                    RecordVisualMode.ANALYZING -> CircularProgressIndicator(
                                        progress = { progress },
                                        modifier = Modifier.size(34.dp),
                                        strokeWidth = 3.dp,
                                        color = primaryColor,
                                    )
                                }
                            }
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .height(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (mode != RecordVisualMode.ANALYZING) {
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }

        Text(
            text = stringResource(R.string.analysis_inference_title),
            modifier = Modifier.align(Alignment.TopCenter),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )

        if (mode == RecordVisualMode.ANALYZING) {
            Text(
                text = statusText,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 46.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        ReadingPassageText(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
        )
    }
}

@Composable
private fun ReadingPassageText(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val languageCode = configuration.locales[0].language
    val preferences = remember(context) { PassagePreferences(context) }
    var selectedIndex by rememberSaveable(preferences.officialIndex()) {
        mutableIntStateOf(preferences.officialIndex())
    }
    val passage = preferences.activePassage(selectedIndex)
    val passageText = passage.text(context)
    val passageStyle = MaterialTheme.typography.bodyMedium
    val textMeasurer = rememberTextMeasurer(cacheSize = 4)
    var widthPx by remember { mutableIntStateOf(0) }
    var heightPx by remember { mutableIntStateOf(0) }
    val pages = remember(passageText, widthPx, heightPx, passageStyle, configuration.locales) {
        adaptivePassagePages(
            text = passageText,
            measurer = textMeasurer,
            style = passageStyle,
            widthPx = widthPx,
            heightPx = heightPx,
            locale = configuration.locales[0],
            wordAware = true,
        )
    }
    var pageIndex by rememberSaveable(passageText) { mutableIntStateOf(0) }
    val currentPageIndex = pageIndex.coerceIn(pages.indices)

    Box(
        modifier = modifier
            .padding(bottom = 6.dp)
            .height(72.dp)
            .onSizeChanged {
                widthPx = it.width
                heightPx = it.height
            }
            .clickable(
                enabled = pages.size > 1,
                onClickLabel = stringResource(R.string.next_page),
            ) {
                pageIndex = (currentPageIndex + 1) % pages.size
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = pages[currentPageIndex],
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center),
            style = passageStyle,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = PASSAGE_MAX_LINES,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun RecordUiState.Analyzing.analysisTargetProgress(): Float {
    val coreProgress = progress?.overallFraction ?: return when (phase) {
        PitcheePhase.LOADING_AUDIO -> PRE_ANALYSIS_DECODE_PROGRESS
        PitcheePhase.PREPARING_MODELS,
        PitcheePhase.ANALYZING,
        -> PRE_ANALYSIS_PROGRESS_WEIGHT
        PitcheePhase.COMPLETED -> 1f
    }
    return PRE_ANALYSIS_PROGRESS_WEIGHT +
        (1f - PRE_ANALYSIS_PROGRESS_WEIGHT) * coreProgress
}

@Composable
private fun analysisProgressText(progress: PitcheeProgress): String =
    stringResource(progressStageLabel(progress.stage))

private fun progressStageLabel(stage: PitcheeProgressStage): Int = when (stage) {
    PitcheeProgressStage.LOADING_AUDIO -> R.string.progress_loading_audio
    PitcheeProgressStage.RESAMPLING_AUDIO -> R.string.progress_resampling_audio
    PitcheeProgressStage.ANALYZING_F0 -> R.string.progress_analyzing_f0
    PitcheeProgressStage.DETECTING_SPEECH -> R.string.progress_detecting_speech
    PitcheeProgressStage.PREPARING_VFP_WINDOWS -> R.string.progress_preparing_vfp
    PitcheeProgressStage.EXTRACTING_VFP_EMBEDDINGS -> R.string.progress_extracting_vfp
    PitcheeProgressStage.CLASSIFYING_VFP_WINDOWS -> R.string.progress_classifying_vfp
    PitcheeProgressStage.PREPARING_NATURALNESS_WINDOWS ->
        R.string.progress_preparing_naturalness
    PitcheeProgressStage.EXTRACTING_NATURALNESS_EMBEDDINGS ->
        R.string.progress_extracting_naturalness
    PitcheeProgressStage.SCORING_NATURALNESS_WINDOWS ->
        R.string.progress_scoring_naturalness
    PitcheeProgressStage.CALCULATING_SCORES -> R.string.progress_calculating_scores
    PitcheeProgressStage.SERIALIZING_RESULT -> R.string.progress_serializing
    PitcheeProgressStage.COMPLETED -> R.string.progress_completed
}
