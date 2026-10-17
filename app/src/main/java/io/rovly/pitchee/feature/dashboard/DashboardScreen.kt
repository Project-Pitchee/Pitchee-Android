package io.rovly.pitchee.feature.dashboard

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.rovly.pitchee.R
import io.rovly.pitchee.data.history.AnalysisHistoryEntry
import io.rovly.pitchee.feature.analysis.feminineScoreColor
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

internal class DashboardScrollPosition {
    var firstVisibleItemIndex: Int = 0
    var firstVisibleItemScrollOffset: Int = 0
}

@Composable
internal fun DashboardScreen(
    refreshKey: Any? = Unit,
    scrollPosition: DashboardScrollPosition,
) {
    InsightsDashboardScreen(
        refreshKey = refreshKey,
        scrollPosition = scrollPosition,
    )
}

@Composable
internal fun AnalysisHistoryRow(
    entry: AnalysisHistoryEntry,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onPin: () -> Unit,
    modifier: Modifier = Modifier,
    score: Double = entry.finalScore,
    scoreLabelRes: Int = R.string.dashboard_metric_composite,
    scoreUnit: String? = null,
    scoreColor: Color? = null,
) {
    val density = LocalDensity.current
    val maxRevealPx = with(density) { 132.dp.toPx() }
    val revealActivationPx = with(density) { 20.dp.toPx() }
    var offsetX by remember(entry.timestampMillis) { mutableFloatStateOf(0f) }
    var accumulatedDragPx by remember(entry.timestampMillis) { mutableFloatStateOf(0f) }
    var revealActivated by remember(entry.timestampMillis) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun settle() {
        val target = if (offsetX < -maxRevealPx / 2f) -maxRevealPx else 0f
        scope.launch {
            animate(
                initialValue = offsetX,
                targetValue = target,
                animationSpec = spring(dampingRatio = 0.82f, stiffness = 320f),
            ) { value, _ -> offsetX = value }
        }
    }

    fun close() {
        scope.launch {
            animate(
                initialValue = offsetX,
                targetValue = 0f,
                animationSpec = spring(dampingRatio = 0.82f, stiffness = 320f),
            ) { value, _ -> offsetX = value }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(MaterialTheme.shapes.large),
    ) {
        Row(
            modifier = Modifier
                .matchParentSize()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
        ) {
            HistorySwipeAction(
                label = stringResource(
                    if (entry.pinned) R.string.dashboard_unpin_record
                    else R.string.dashboard_pin_record,
                ),
                iconRes = R.drawable.ic_pin,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                onClick = {
                    onPin()
                    close()
                },
            )
            HistorySwipeAction(
                label = stringResource(R.string.dashboard_delete),
                iconRes = R.drawable.ic_delete,
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                onClick = {
                    onDelete()
                    settle()
                },
            )
        }
        Surface(
            onClick = {
                if (offsetX < 0f) settle() else onOpen()
            },
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .pointerInput(entry.timestampMillis) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            accumulatedDragPx = 0f
                            revealActivated = offsetX < 0f
                        },
                        onDragEnd = {
                            revealActivated = false
                            settle()
                        },
                        onDragCancel = {
                            revealActivated = false
                            settle()
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            if (revealActivated) {
                                offsetX = (offsetX + dragAmount)
                                    .coerceIn(-maxRevealPx, 0f)
                            } else {
                                accumulatedDragPx += dragAmount
                                if (accumulatedDragPx <= -revealActivationPx) {
                                    revealActivated = true
                                    val adjustedDrag = accumulatedDragPx + revealActivationPx
                                    offsetX = (offsetX + adjustedDrag)
                                        .coerceIn(-maxRevealPx, 0f)
                                    accumulatedDragPx = 0f
                                }
                            }
                        },
                    )
                },
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.background,
        ) {
            AnalysisHistoryContent(
                entry = entry,
                score = score,
                scoreLabelRes = scoreLabelRes,
                scoreUnit = scoreUnit,
                scoreColor = scoreColor,
            )
        }
    }
}

@Composable
private fun HistorySwipeAction(
    label: String,
    iconRes: Int,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .width(60.dp)
            .fillMaxHeight()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun AnalysisHistoryContent(
    entry: AnalysisHistoryEntry,
    score: Double,
    scoreLabelRes: Int,
    scoreUnit: String?,
    scoreColor: Color?,
) {
    val locale = LocalConfiguration.current.locales[0]
    val time = remember(entry.timestampMillis, locale) {
        DateFormat.getTimeInstance(DateFormat.SHORT, locale)
            .format(Date(entry.timestampMillis))
    }
    val durationSeconds = entry.inputSeconds.coerceAtLeast(0.0).roundToInt()
    val duration = "%d:%02d".format(durationSeconds / 60, durationSeconds % 60)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(42.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_model_analysis),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = time,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (entry.pinned) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.dashboard_pinned),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = duration,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = if (scoreUnit == null) {
                    "%.1f".format(score)
                } else {
                    "%.1f %s".format(score, scoreUnit)
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = scoreColor ?: feminineScoreColor(score),
            )
            Text(
                text = stringResource(scoreLabelRes),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(6.dp))
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            modifier = Modifier.size(18.dp),
        )
    }
}
