package io.rovly.pitchee.ui.common

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.rovly.pitchee.R

@Composable
internal fun VoiceCaptureStage(
    active: Boolean,
    elapsedSeconds: Float,
    speechSeconds: Float,
    requiredSpeechSeconds: Float,
    amplitudes: List<Float>,
    idleTitle: String,
    idleSubtitle: String,
    recordingHint: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    footerMessage: String? = null,
) {
    val speechProgress by animateFloatAsState(
        targetValue = (speechSeconds / requiredSpeechSeconds.coerceAtLeast(0.01f))
            .coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 260),
        label = "voice-capture-progress",
    )
    val pulseTransition = rememberInfiniteTransition(label = "voice-capture-pulse")
    val pulse by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "voice-capture-pulse-scale",
    )
    val primary = MaterialTheme.colorScheme.primary
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val error = MaterialTheme.colorScheme.error

    Box(modifier = modifier.fillMaxSize()) {
        VoiceCaptureWaveform(
            amplitudes = amplitudes,
            color = primary,
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
                        color = primaryContainer.copy(alpha = 0.46f),
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
                onClick = onAction,
                modifier = Modifier.size(118.dp),
                shape = CircleShape,
                color = primary,
                contentColor = onPrimary,
                shadowElevation = if (active) 12.dp else 5.dp,
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    if (active) {
                        Canvas(Modifier.size(32.dp)) {
                            drawRoundRect(
                                color = onPrimary,
                                cornerRadius = CornerRadius(5.dp.toPx()),
                            )
                        }
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.ic_mic),
                            contentDescription = idleTitle,
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
            if (active) {
                Text(
                    text = formatCaptureClock(elapsedSeconds),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Black,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    text = stringResource(
                        R.string.voice_capture_progress,
                        speechSeconds,
                        requiredSpeechSeconds,
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (speechProgress >= 1f) {
                        primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = if (speechProgress >= 1f) FontWeight.Bold else FontWeight.Medium,
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    text = recordingHint,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = idleTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    text = idleSubtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            footerMessage?.let { message ->
                Spacer(Modifier.height(12.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun VoiceCaptureWaveform(
    amplitudes: List<Float>,
    color: Color,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    val alpha by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "voice-capture-waveform-alpha",
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
                start = Offset(x, center.y - halfHeight),
                end = Offset(x, center.y + halfHeight),
                strokeWidth = (step * 0.34f).coerceIn(1.5.dp.toPx(), 4.dp.toPx()),
                cap = StrokeCap.Round,
            )
        }
    }
}

private fun formatCaptureClock(seconds: Float): String {
    val wholeSeconds = seconds.coerceAtLeast(0f).toInt()
    return "%02d:%02d".format(wholeSeconds / 60, wholeSeconds % 60)
}

private const val MAX_WAVEFORM_BARS = 72
