package io.rovly.pitchee.feature.voicehealth

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.rovly.pitchee.R
import io.rovly.pitchee.ui.common.ScreenTitle
import io.rovly.pitchee.ui.common.VoiceCaptureStage
import io.rovly.pitchee.ui.theme.PitcheeColorTokens
import io.rovly.pitchee.ui.theme.metricHero
import space.pitchee.core.PitcheeVoiceHealthProgress
import space.pitchee.core.PitcheeVoiceHealthResult
import space.pitchee.core.PitcheeVoiceHealthStage

@Composable
internal fun VoiceHealthScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val factory = remember { VoiceHealthViewModel.factory(context.applicationContext) }
    val viewModel: VoiceHealthViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    var permissionDenied by remember { mutableStateOf(false) }
    val permissionDeniedMessage = stringResource(R.string.record_permission_denied)

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionDenied = !granted
        if (granted) viewModel.startRecording()
    }

    fun startRecording() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            permissionDenied = false
            viewModel.startRecording()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    BackHandler {
        viewModel.cancel()
        onBack()
    }
    DisposableEffect(Unit) {
        onDispose { viewModel.cancel() }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        val current = state
        val captureVisible = current is VoiceHealthUiState.Ready ||
            current is VoiceHealthUiState.Recording
        if (captureVisible) {
            Box(modifier = Modifier.fillMaxSize()) {
                val recording = current as? VoiceHealthUiState.Recording
                VoiceCaptureStage(
                    active = recording != null,
                    elapsedSeconds = recording?.elapsedSeconds ?: 0f,
                    speechSeconds = recording?.validSpeechSeconds ?: 0f,
                    requiredSpeechSeconds = VoiceHealthViewModel.MINIMUM_SPEECH_SECONDS,
                    amplitudes = recording?.amplitudes.orEmpty(),
                    idleTitle = stringResource(R.string.click_to_record),
                    idleSubtitle = stringResource(R.string.voice_health_minimum_duration),
                    recordingHint = stringResource(R.string.voice_health_stop),
                    onAction = {
                        if (current is VoiceHealthUiState.Recording) {
                            viewModel.stopAndAnalyze()
                        } else {
                            startRecording()
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    footerMessage = if (permissionDenied) permissionDeniedMessage else null,
                )
                ScreenTitle(
                    title = stringResource(R.string.voice_health_title),
                    onBack = onBack,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 24.dp),
            ) {
                ScreenTitle(
                    title = stringResource(R.string.voice_health_title),
                    onBack = onBack,
                )
                Spacer(Modifier.height(22.dp))

                when (current) {
                    is VoiceHealthUiState.Analyzing -> VoiceHealthAnalyzing(current.progress)
                    is VoiceHealthUiState.Success -> VoiceHealthSuccess(
                        result = current.result,
                        onAgain = viewModel::reset,
                    )
                    is VoiceHealthUiState.Error -> VoiceHealthError(
                        message = current.messageRes?.let { stringResource(it) }
                            ?: current.message.orEmpty(),
                        onRetry = viewModel::reset,
                    )
                    VoiceHealthUiState.Ready,
                    is VoiceHealthUiState.Recording,
                    -> Unit
                }
            }
        }
    }
}

@Composable
private fun VoiceHealthCaptureStage(
    recording: VoiceHealthUiState.Recording?,
    permissionDenied: Boolean,
    permissionDeniedMessage: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = recording != null
    val speechProgress by animateFloatAsState(
        targetValue = recording?.let {
            (it.validSpeechSeconds / VoiceHealthViewModel.MINIMUM_SPEECH_SECONDS)
                .coerceIn(0f, 1f)
        } ?: 0f,
        animationSpec = tween(durationMillis = 260),
        label = "voice-health-speech-progress",
    )
    val pulseTransition = rememberInfiniteTransition(label = "voice-health-pulse")
    val pulse by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "voice-health-pulse-scale",
    )
    val primary = MaterialTheme.colorScheme.primary
    val error = MaterialTheme.colorScheme.error
    val buttonColor = if (active) error else primary
    val buttonContent = if (active) MaterialTheme.colorScheme.onError
    else MaterialTheme.colorScheme.onPrimary

    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        VoiceHealthWaveform(
            amplitudes = recording?.amplitudes.orEmpty(),
            color = error,
            active = active,
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(220.dp),
        )

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(204.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (active) {
                Canvas(
                    modifier = Modifier
                        .size(184.dp)
                        .graphicsLayer {
                            scaleX = pulse
                            scaleY = pulse
                        },
                ) {
                    drawCircle(
                        color = error.copy(alpha = 0.09f),
                        radius = size.minDimension / 2f,
                    )
                }
            }
            Canvas(Modifier.size(172.dp)) {
                val stroke = 5.dp.toPx()
                drawArc(
                    color = primary.copy(alpha = 0.16f),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                if (active) {
                    drawArc(
                        color = primary,
                        startAngle = -90f,
                        sweepAngle = speechProgress * 360f,
                        useCenter = false,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }
            }
            Surface(
                onClick = onClick,
                modifier = Modifier.size(118.dp),
                shape = CircleShape,
                color = buttonColor,
                contentColor = buttonContent,
                shadowElevation = if (active) 12.dp else 5.dp,
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    if (active) {
                        Canvas(Modifier.size(32.dp)) {
                            drawRoundRect(
                                color = Color.White,
                                cornerRadius = CornerRadius(5.dp.toPx()),
                            )
                        }
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.ic_voice_health),
                            contentDescription = stringResource(R.string.start_recording),
                            modifier = Modifier.size(44.dp),
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = 170.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (recording != null) {
                Text(
                    text = formatClock(recording.elapsedSeconds),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Black,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    text = stringResource(
                        R.string.voice_health_valid_speech,
                        recording.validSpeechSeconds,
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (speechProgress >= 1f) {
                        PitcheeColorTokens.Success
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = if (speechProgress >= 1f) FontWeight.Bold else FontWeight.Medium,
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    text = stringResource(R.string.voice_health_stop),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = stringResource(R.string.click_to_record),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    text = stringResource(R.string.voice_health_minimum_duration),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (permissionDenied) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = permissionDeniedMessage.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun VoiceHealthAnalyzing(progress: PitcheeVoiceHealthProgress?) {
    val fraction = voiceHealthOverallFraction(progress)
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(42.dp), strokeWidth = 3.dp)
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.voice_health_analyzing),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(14.dp))
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = voiceHealthStageLabel(progress),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun VoiceHealthSuccess(
    result: PitcheeVoiceHealthResult,
    onAgain: () -> Unit,
) {
    val good = !result.isDisordered
    val accent = if (good) PitcheeColorTokens.Success else PitcheeColorTokens.Warning
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(
                    if (good) {
                        R.string.voice_health_result_good
                    } else {
                        R.string.voice_health_result_attention
                    },
                ),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = accent,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "%.1f".format(result.healthScore),
                style = MaterialTheme.typography.metricHero,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(
                    R.string.voice_health_result_threshold,
                    result.decisionThreshold,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            VoiceHealthMetricRow(
                label = stringResource(R.string.voice_health_metric_hnr),
                value = formatMetric(result.features.hnrDb, 1, " dB"),
            )
            VoiceHealthMetricRow(
                label = stringResource(R.string.voice_health_metric_cpps),
                value = formatMetric(result.features.cppsDb, 1, " dB"),
            )
            VoiceHealthMetricRow(
                label = stringResource(R.string.voice_health_metric_jitter),
                value = formatMetric(result.features.jitterLocal, 3, ""),
            )
            VoiceHealthMetricRow(
                label = stringResource(R.string.voice_health_metric_shimmer),
                value = formatMetric(result.features.shimmerLocal, 3, ""),
            )
            VoiceHealthMetricRow(
                label = stringResource(R.string.voice_health_metric_f0),
                value = formatMetric(result.features.f0Median, 1, " Hz"),
            )
            VoiceHealthMetricRow(
                label = stringResource(R.string.voice_health_metric_intensity),
                value = formatMetric(result.features.intensityMedian, 1, " dB"),
            )
        }
    }
    Spacer(Modifier.height(18.dp))
    FilledTonalButton(
        onClick = onAgain,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.voice_health_record_again))
    }
}

@Composable
private fun VoiceHealthError(
    message: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.voice_health_analysis_failed),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(18.dp))
        FilledTonalButton(onClick = onRetry) {
            Text(stringResource(R.string.voice_health_record_again))
        }
    }
}

@Composable
private fun VoiceHealthWaveform(
    amplitudes: List<Float>,
    color: Color,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    val alpha by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "voice-health-waveform-alpha",
    )
    Canvas(modifier = modifier.graphicsLayer { this.alpha = alpha }) {
        if (!active) return@Canvas
        val values = amplitudes.takeLast(MAX_WAVEFORM_BARS)
        val step = size.width / MAX_WAVEFORM_BARS
        val maxHeight = size.height * 0.46f
        for (index in 0 until MAX_WAVEFORM_BARS) {
            val amplitude = if (values.isEmpty()) {
                0f
            } else {
                values[(index * values.size / MAX_WAVEFORM_BARS).coerceAtMost(values.lastIndex)]
                    .coerceIn(0f, 1f)
            }
            val halfHeight = 2.dp.toPx() + maxHeight * amplitude
            val x = step * (index + 0.5f)
            drawLine(
                color = color.copy(alpha = 0.30f + amplitude * 0.45f),
                start = androidx.compose.ui.geometry.Offset(x, center.y - halfHeight),
                end = androidx.compose.ui.geometry.Offset(x, center.y + halfHeight),
                strokeWidth = (step * 0.34f).coerceIn(1.5.dp.toPx(), 4.dp.toPx()),
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun VoiceHealthMetricRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun voiceHealthStageLabel(progress: PitcheeVoiceHealthProgress?): String = when (
    progress?.stage
) {
    null,
    PitcheeVoiceHealthStage.LOADING_AUDIO,
    -> stringResource(R.string.progress_loading_audio)
    PitcheeVoiceHealthStage.RESAMPLING_AUDIO -> stringResource(R.string.progress_resampling_audio)
    PitcheeVoiceHealthStage.ANALYZING_F0 -> stringResource(R.string.progress_analyzing_f0)
    PitcheeVoiceHealthStage.DETECTING_SPEECH -> stringResource(R.string.progress_detecting_speech)
    PitcheeVoiceHealthStage.PREPARING_VOICE_HEALTH ->
        stringResource(R.string.voice_health_stage_preparing)
    PitcheeVoiceHealthStage.EXTRACTING_VOICE_HEALTH_EMBEDDINGS ->
        stringResource(R.string.voice_health_stage_extracting)
    PitcheeVoiceHealthStage.SCORING_VOICE_HEALTH ->
        stringResource(R.string.voice_health_stage_scoring)
    PitcheeVoiceHealthStage.COMPLETED -> stringResource(R.string.voice_health_stage_completed)
}

private fun voiceHealthOverallFraction(progress: PitcheeVoiceHealthProgress?): Float {
    if (progress == null) return 0f
    val (start, weight) = when (progress.stage) {
        PitcheeVoiceHealthStage.LOADING_AUDIO -> 0.00f to 0.02f
        PitcheeVoiceHealthStage.RESAMPLING_AUDIO -> 0.02f to 0.05f
        PitcheeVoiceHealthStage.ANALYZING_F0 -> 0.07f to 0.08f
        PitcheeVoiceHealthStage.DETECTING_SPEECH -> 0.15f to 0.10f
        PitcheeVoiceHealthStage.PREPARING_VOICE_HEALTH -> 0.25f to 0.05f
        PitcheeVoiceHealthStage.EXTRACTING_VOICE_HEALTH_EMBEDDINGS -> 0.30f to 0.55f
        PitcheeVoiceHealthStage.SCORING_VOICE_HEALTH -> 0.85f to 0.14f
        PitcheeVoiceHealthStage.COMPLETED -> 0.99f to 0.01f
    }
    return (start + weight * progress.stageFraction.coerceIn(0f, 1f)).coerceIn(0f, 1f)
}

private fun formatMetric(value: Double, decimals: Int, suffix: String): String =
    if (value.isFinite()) {
        "%.${decimals}f%s".format(value, suffix)
    } else {
        "--"
    }

private fun formatClock(seconds: Float): String {
    val wholeSeconds = seconds.coerceAtLeast(0f).toInt()
    return "%02d:%02d".format(wholeSeconds / 60, wholeSeconds % 60)
}

private const val MAX_WAVEFORM_BARS = 72
