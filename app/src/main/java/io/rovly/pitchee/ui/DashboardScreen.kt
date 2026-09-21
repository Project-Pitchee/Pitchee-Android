package io.rovly.pitchee.ui

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import io.rovly.pitchee.R
import io.rovly.pitchee.data.AnalysisHistoryEntry
import io.rovly.pitchee.data.HistoryStore
import io.rovly.pitchee.data.RealtimeF0HistoryEntry
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first

private data class DashboardData(
    val realtimeF0: List<RealtimeF0HistoryEntry> = emptyList(),
    val analyses: List<AnalysisHistoryEntry> = emptyList(),
    val averageF0ResetAtMillis: Long = 0L,
)

private val ResetContainerLight = Color(0xFFF7E0E2)
private val ResetContentLight = Color(0xFF6B343A)
private val ResetContainerDark = Color(0xFF493034)
private val ResetContentDark = Color(0xFFF0C8CC)

internal class DashboardScrollPosition {
    var firstVisibleItemIndex: Int = 0
    var firstVisibleItemScrollOffset: Int = 0
}

@Composable
internal fun DashboardScreen(
    refreshKey: Any? = Unit,
    scrollPosition: DashboardScrollPosition,
) {
    val context = LocalContext.current
    val store = remember(context) { HistoryStore(context) }
    var data by remember { mutableStateOf(DashboardData()) }
    var selectedEntry by remember { mutableStateOf<AnalysisHistoryEntry?>(null) }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = scrollPosition.firstVisibleItemIndex,
        initialFirstVisibleItemScrollOffset = scrollPosition.firstVisibleItemScrollOffset,
    )
    var scrollPositionRestored by remember {
        mutableStateOf(
            scrollPosition.firstVisibleItemIndex == 0 &&
                scrollPosition.firstVisibleItemScrollOffset == 0,
        )
    }

    LaunchedEffect(refreshKey) {
        data = DashboardData(
            realtimeF0 = store.realtimeF0(),
            analyses = store.analyses(),
            averageF0ResetAtMillis = store.averageF0ResetAtMillis(),
        )
    }

    LaunchedEffect(data.realtimeF0, data.analyses, scrollPositionRestored) {
        if (!scrollPositionRestored && (data.realtimeF0.isNotEmpty() || data.analyses.isNotEmpty())) {
            snapshotFlow { listState.layoutInfo.totalItemsCount }
                .first { it >= scrollPosition.firstVisibleItemIndex || it > 0 }
            listState.scrollToItem(
                index = scrollPosition.firstVisibleItemIndex.coerceAtLeast(0),
                scrollOffset = scrollPosition.firstVisibleItemScrollOffset.coerceAtLeast(0),
            )
            scrollPositionRestored = true
        }
    }

    LaunchedEffect(listState, scrollPositionRestored) {
        if (!scrollPositionRestored) return@LaunchedEffect
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }.collect { (index, offset) ->
            scrollPosition.firstVisibleItemIndex = index
            scrollPosition.firstVisibleItemScrollOffset = offset
        }
    }

    val f0Sources = buildList<Pair<Long, Double>> {
        data.realtimeF0.forEach { entry ->
            if (entry.timestampMillis > data.averageF0ResetAtMillis) {
                add(entry.timestampMillis to entry.meanF0Hz)
            }
        }
        data.analyses.forEach { entry ->
            val meanF0Hz = entry.meanF0Hz
            if (meanF0Hz != null && entry.timestampMillis > data.averageF0ResetAtMillis) {
                add(entry.timestampMillis to meanF0Hz)
            }
        }
    }
    val averageF0 = f0Sources.takeIf { it.isNotEmpty() }?.map { it.second }?.average()
    val latestF0 = f0Sources.maxByOrNull { it.first }?.second
    val darkTheme = isSystemInDarkTheme()

    AnimatedContent(
        targetState = selectedEntry,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        transitionSpec = {
            if (targetState != null) {
                slideInHorizontally(
                    animationSpec = spring(dampingRatio = 0.86f, stiffness = 300f),
                    initialOffsetX = { it },
                ) togetherWith slideOutHorizontally(
                    animationSpec = spring(dampingRatio = 0.9f, stiffness = 340f),
                    targetOffsetX = { -it / 3 },
                )
            } else {
                slideInHorizontally(
                    animationSpec = spring(dampingRatio = 0.86f, stiffness = 300f),
                    initialOffsetX = { -it / 3 },
                ) togetherWith slideOutHorizontally(
                    animationSpec = spring(dampingRatio = 0.9f, stiffness = 340f),
                    targetOffsetX = { it },
                )
            }
        },
        contentKey = { it?.timestampMillis ?: -1L },
        label = "dashboard-history-page",
    ) { entry ->
        if (entry != null) {
            AnalysisHistoryDetailScreen(
                entry = entry,
                store = store,
                onBack = { selectedEntry = null },
            )
        } else {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
                ) {
                    item(key = "dashboard-header") {
                        Text(
                            text = stringResource(R.string.dashboard_title),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(20.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.Start,
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = stringResource(R.string.dashboard_average_f0),
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        textAlign = TextAlign.Start,
                                    )
                                    FilledTonalButton(
                                        onClick = {
                                            val resetAtMillis = System.currentTimeMillis()
                                            store.resetAverageF0(resetAtMillis)
                                            data = data.copy(
                                                realtimeF0 = emptyList(),
                                                averageF0ResetAtMillis = resetAtMillis,
                                            )
                                        },
                                        enabled = f0Sources.isNotEmpty(),
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = if (darkTheme) {
                                                ResetContainerDark
                                            } else {
                                                ResetContainerLight
                                            },
                                            contentColor = if (darkTheme) {
                                                ResetContentDark
                                            } else {
                                                ResetContentLight
                                            },
                                        ),
                                    ) {
                                        Text(stringResource(R.string.dashboard_reset))
                                    }
                                }
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = averageF0?.let { "%.0f Hz".format(it) } ?: "--",
                                    modifier = averageF0?.let { Modifier.offset(x = (-6).dp) } ?: Modifier.offset(x = (-2).dp),
                                    style = MaterialTheme.typography.displayMedium,
                                    fontWeight = FontWeight.Black,
                                    textAlign = TextAlign.Start,
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = when {
                                        f0Sources.isEmpty() -> {
                                            stringResource(R.string.dashboard_no_realtime)
                                        }
                                        latestF0 == null -> {
                                            stringResource(R.string.dashboard_no_valid_f0)
                                        }
                                        else -> {
                                            stringResource(R.string.dashboard_latest_f0, latestF0)
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth(),
//                                        .padding(start = 1.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Start,
                                )
                            }
                        }
                    }
                    item(key = "dashboard-metrics") {
                        Spacer(Modifier.height(24.dp))
                        AnalysisMetricSection(entries = data.analyses)
                        Spacer(Modifier.height(28.dp))
                        Text(
                            text = stringResource(R.string.dashboard_analysis_history),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                    if (data.analyses.isEmpty()) {
                        item(key = "dashboard-empty") {
                            Text(
                                text = stringResource(R.string.dashboard_no_analysis),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        items(
                            items = data.analyses.take(20),
                            key = { it.timestampMillis },
                        ) { historyEntry ->
                            AnalysisHistoryRow(
                                entry = historyEntry,
                                onOpen = { selectedEntry = historyEntry },
                                onDelete = {
                                    store.removeAnalysis(historyEntry.timestampMillis)
                                    data = data.copy(
                                        analyses = data.analyses.filterNot {
                                            it.timestampMillis == historyEntry.timestampMillis
                                        },
                                    )
                                },
                                onPin = {
                                    store.setAnalysisPinned(
                                        timestampMillis = historyEntry.timestampMillis,
                                        pinned = !historyEntry.pinned,
                                    )
                                    data = data.copy(analyses = store.analyses())
                                },
                                modifier = Modifier.animateItem(
                                    fadeInSpec = null,
                                    placementSpec = spring(
                                        dampingRatio = 0.78f,
                                        stiffness = 260f,
                                    ),
                                    fadeOutSpec = null,
                                ),
                            )
                        }
                    }
                    item(key = "dashboard-bottom") {
                        Spacer(Modifier.height(20.dp))
                    }
                }
            }
        }
    }
}

private enum class AnalysisMetric(
    val labelRes: Int,
) {
    F0(R.string.dashboard_metric_f0),
    NATURALNESS(R.string.dashboard_metric_naturalness),
    STANDARD(R.string.dashboard_metric_standard),
    COMPOSITE(R.string.dashboard_metric_composite),
}

@Composable
private fun AnalysisMetricSection(entries: List<AnalysisHistoryEntry>) {
    var selectedOrdinal by rememberSaveable { mutableIntStateOf(AnalysisMetric.F0.ordinal) }
    val selected = AnalysisMetric.entries[selectedOrdinal]
    val chronological = entries
        .sortedBy { it.timestampMillis }
        .takeLast(30)
    val points = chronological.mapNotNull { entry ->
        val value = when (selected) {
            AnalysisMetric.F0 -> entry.meanF0Hz?.toFloat()
            AnalysisMetric.NATURALNESS -> entry.naturalnessScore.toFloat()
            AnalysisMetric.STANDARD -> entry.standardScore.toFloat()
            AnalysisMetric.COMPOSITE -> entry.finalScore.toFloat()
        } ?: return@mapNotNull null
        AnalysisChartPoint(
            timestampMillis = entry.timestampMillis,
            value = value,
        )
    }
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.dashboard_metric_chart),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AnalysisMetric.entries.forEach { metric ->
                FilterChip(
                    selected = selected == metric,
                    onClick = { selectedOrdinal = metric.ordinal },
                    label = { Text(stringResource(metric.labelRes)) },
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        if (points.isEmpty()) {
            Text(
                text = stringResource(R.string.dashboard_metric_no_data),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            AnalysisMetricChart(
                points = points,
                metric = selected,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(168.dp),
            )
        }
    }
}

@Composable
private fun AnalysisMetricChart(
    points: List<AnalysisChartPoint>,
    metric: AnalysisMetric,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val lineColor = MaterialTheme.colorScheme.primary
    val pointColor = MaterialTheme.colorScheme.secondary
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.48f)
    val crosshairColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
    val selectedPointColor = MaterialTheme.colorScheme.primary
    var selectedIndex by remember(points) { mutableStateOf<Int?>(null) }

    BoxWithConstraints(modifier = modifier) {
        val xFor: (Int) -> androidx.compose.ui.unit.Dp = { index ->
            if (points.size <= 1) {
                maxWidth / 2
            } else {
                maxWidth * index / (points.size - 1)
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(points) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        selectedIndex = nearestPointIndex(
                            x = down.position.x,
                            width = size.width,
                            count = points.size,
                        )
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            selectedIndex = nearestPointIndex(
                                x = change.position.x,
                                width = size.width,
                                count = points.size,
                            )
                            if (!change.pressed) break
                        }
                        selectedIndex = null
                    }
                },
        ) {
            val values = points.map { it.value }
            val minimum = when (metric) {
                AnalysisMetric.F0 -> (values.minOrNull() ?: 0f) - 10f
                else -> 0f
            }
            val maximum = when (metric) {
                AnalysisMetric.F0 -> (values.maxOrNull() ?: 1f) + 10f
                else -> 100f
            }
            val range = max(maximum - minimum, 1f)
            val stepX = if (points.size == 1) 0f else size.width / (points.size - 1)

            for (line in 0..2) {
                val y = size.height * line / 2f
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx(),
                )
            }

            fun point(index: Int): Offset = Offset(
                x = stepX * index,
                y = size.height - ((points[index].value - minimum) / range) * size.height,
            )

            if (points.size == 1) {
                drawCircle(pointColor, radius = 4.dp.toPx(), center = point(0))
            } else {
                drawPath(
                    path = smoothChartPath(points.indices.map(::point)),
                    color = lineColor,
                    style = Stroke(
                        width = 2.8.dp.toPx(),
                        cap = StrokeCap.Round,
                    ),
                )
                points.indices.forEach { index ->
                    drawCircle(pointColor, radius = 3.dp.toPx(), center = point(index))
                }
            }

            selectedIndex?.let { index ->
                val selectedPoint = point(index)
                drawLine(
                    color = crosshairColor,
                    start = Offset(selectedPoint.x, 0f),
                    end = Offset(selectedPoint.x, size.height),
                    strokeWidth = 1.2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(6.dp.toPx(), 6.dp.toPx()),
                    ),
                )
                drawCircle(
                    color = selectedPointColor,
                    radius = 4.5.dp.toPx(),
                    center = selectedPoint,
                )
            }
        }

        selectedIndex?.let { index ->
            val point = points[index]
            val date = remember(point.timestampMillis, locale) {
                SimpleDateFormat("M/d", locale).format(Date(point.timestampMillis))
            }
            val value = when (metric) {
                AnalysisMetric.F0 -> "%.0f Hz".format(point.value)
                else -> "%.1f".format(point.value)
            }
            val tooltipWidth = 62.dp
            val tooltipX = (xFor(index) - tooltipWidth / 2)
                .coerceIn(0.dp, maxWidth - tooltipWidth)
            Surface(
                modifier = Modifier
                    .offset(x = tooltipX, y = 0.dp)
                    .width(tooltipWidth),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                tonalElevation = 3.dp,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = date,
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

private data class AnalysisChartPoint(
    val timestampMillis: Long,
    val value: Float,
)

private fun smoothChartPath(points: List<Offset>): Path = Path().apply {
    if (points.isEmpty()) return@apply
    moveTo(points.first().x, points.first().y)
    if (points.size == 1) return@apply

    for (index in 0 until points.lastIndex) {
        val previous = points[(index - 1).coerceAtLeast(0)]
        val current = points[index]
        val next = points[index + 1]
        val afterNext = points[(index + 2).coerceAtMost(points.lastIndex)]
        cubicTo(
            current.x + (next.x - previous.x) / 6f,
            current.y + (next.y - previous.y) / 6f,
            next.x - (afterNext.x - current.x) / 6f,
            next.y - (afterNext.y - current.y) / 6f,
            next.x,
            next.y,
        )
    }
}

private fun nearestPointIndex(
    x: Float,
    width: Int,
    count: Int,
): Int {
    if (count <= 1 || width <= 0) return 0
    return ((x / width) * (count - 1))
        .roundToInt()
        .coerceIn(0, count - 1)
}


@Composable
private fun AnalysisHistoryRow(
    entry: AnalysisHistoryEntry,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onPin: () -> Unit,
    modifier: Modifier = Modifier,
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
            .clip(RoundedCornerShape(16.dp)),
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
                }
                .clickable {
                    if (offsetX < 0f) settle() else onOpen()
                },
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.background,
        ) {
            AnalysisHistoryContent(entry)
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
        shape = RoundedCornerShape(16.dp),
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
private fun AnalysisHistoryContent(entry: AnalysisHistoryEntry) {
    val locale = LocalConfiguration.current.locales[0]
    val timestamp = remember(entry.timestampMillis, locale) {
        DateFormat.getDateTimeInstance(
            DateFormat.SHORT,
            DateFormat.SHORT,
            locale,
        ).format(Date(entry.timestampMillis))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 14.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = timestamp,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "%s %.1f   %s %.1f   %s".format(
                    "S",
                    entry.standardScore,
                    "N",
                    entry.naturalnessScore,
                    entry.meanF0Hz?.let { "F0 %.0f".format(it) } ?: "F0 --",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (entry.pinned) {
            Text(
                text = stringResource(R.string.dashboard_pinned),
                modifier = Modifier.padding(end = 10.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = "%.1f".format(entry.finalScore),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black,
            color = feminineScoreColor(entry.finalScore),
            textAlign = TextAlign.End,
        )
    }
}
