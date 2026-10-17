package io.rovly.pitchee.feature.dashboard

import io.rovly.pitchee.ui.theme.PitcheeColorTokens
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.rovly.pitchee.R
import io.rovly.pitchee.data.history.AnalysisHistoryEntry
import io.rovly.pitchee.data.history.HistoryStore
import io.rovly.pitchee.data.history.RealtimeF0HistoryEntry
import io.rovly.pitchee.ui.common.ScreenTitle
import io.rovly.pitchee.ui.common.SegmentedChoiceRow
import io.rovly.pitchee.ui.common.ScreenTransition
import io.rovly.pitchee.ui.theme.metricHero
import io.rovly.pitchee.ui.theme.metricMedium
import io.rovly.pitchee.feature.analysis.AnalysisHistoryDetailScreen
import java.text.SimpleDateFormat
import java.text.DateFormatSymbols
import java.util.Calendar
import java.util.Date
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first

private data class InsightsData(
    val analyses: List<AnalysisHistoryEntry> = emptyList(),
    val realtimeF0: List<RealtimeF0HistoryEntry> = emptyList(),
    val openedDays: List<Long> = emptyList(),
    val averageF0ResetAtMillis: Long = 0L,
)

private data class InsightsPoint(
    val timestampMillis: Long,
    val value: Float,
)

private sealed interface InsightsRoute {
    data object Overview : InsightsRoute
    data object History : InsightsRoute
    data object Activity : InsightsRoute
    data class Metric(val metric: InsightsMetric) : InsightsRoute
    data class Detail(
        val entry: AnalysisHistoryEntry,
        val returnRoute: InsightsRoute,
    ) : InsightsRoute
}

private enum class DashboardRange(
    val labelRes: Int,
    private val days: Int?,
) {
    SEVEN(R.string.dashboard_range_7_days, 7),
    THIRTY(R.string.dashboard_range_30_days, 30),
    NINETY(R.string.dashboard_range_90_days, 90),
    ALL(R.string.dashboard_range_all, null),
    ;

    fun startMillis(nowMillis: Long): Long? {
        val days = days ?: return null
        return Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, 1 - days)
        }.timeInMillis
    }

    fun contains(timestampMillis: Long, nowMillis: Long): Boolean {
        if (timestampMillis > nowMillis) return false
        return startMillis(nowMillis)?.let { timestampMillis >= it } ?: true
    }
}

private enum class InsightsMetric(
    val titleRes: Int,
    val descriptionRes: Int,
    val includesUnit: Boolean,
) {
    COMPOSITE(
        R.string.dashboard_metric_composite,
        R.string.dashboard_metric_composite_description,
        false,
    ),
    NATURALNESS(
        R.string.dashboard_metric_naturalness,
        R.string.dashboard_metric_naturalness_description,
        false,
    ),
    PITCH(
        R.string.dashboard_metric_f0,
        R.string.dashboard_metric_pitch_description,
        true,
    ),
    TIMBRE(
        R.string.dashboard_metric_standard,
        R.string.dashboard_metric_timbre_description,
        false,
    ),
    VARIATION(
        R.string.dashboard_metric_variation,
        R.string.dashboard_metric_variation_description,
        true,
    ),
}

private val InsightBlue = PitcheeColorTokens.Info
private val InsightOrange = PitcheeColorTokens.Warning
private val InsightAmber = PitcheeColorTokens.InsightAmber

@Composable
internal fun InsightsDashboardScreen(
    refreshKey: Any?,
    scrollPosition: DashboardScrollPosition,
) {
    val context = LocalContext.current
    val store = remember(context) { HistoryStore(context) }
    var data by remember { mutableStateOf(InsightsData()) }
    var selectedRangeOrdinal by rememberSaveable { mutableIntStateOf(DashboardRange.SEVEN.ordinal) }
    var route by remember { mutableStateOf<InsightsRoute>(InsightsRoute.Overview) }
    var forward by remember { mutableStateOf(true) }
    // Bumped on every navigation so the two screens in a transition never share
    // a zIndex (two pops in a row used to tie, and the parent won the tie).
    var zStep by remember { mutableIntStateOf(0) }
    val selectedRange = DashboardRange.entries[selectedRangeOrdinal]
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

    fun reload() {
        val analyses = store.analyses().map { entry ->
            if (entry.f0VariationHz == null) {
                entry.copy(
                    f0VariationHz = store.analysisResult(entry)?.f0?.standardDeviationHz,
                )
            } else {
                entry
            }
        }
        data = InsightsData(
            analyses = analyses,
            realtimeF0 = store.realtimeF0(),
            openedDays = store.openedDayTimestamps(),
            averageF0ResetAtMillis = store.averageF0ResetAtMillis(),
        )
    }

    LaunchedEffect(refreshKey) {
        store.recordOpenedDay()
        reload()
    }

    LaunchedEffect(data.analyses, scrollPositionRestored) {
        if (!scrollPositionRestored && data.analyses.isNotEmpty()) {
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

    fun navigateTo(target: InsightsRoute) {
        forward = routeDepth(target) >= routeDepth(route)
        zStep += 1
        route = target
    }
    ScreenTransition(
        targetState = route,
        forward = forward,
        contentZIndex = if (forward) zStep.toFloat() else -zStep.toFloat(),
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) { currentRoute ->
        when (currentRoute) {
            InsightsRoute.Overview -> InsightsOverview(
                data = data,
                selectedRange = selectedRange,
                onRangeSelected = { selectedRangeOrdinal = it.ordinal },
                listState = listState,
                onOpenHistory = { navigateTo(InsightsRoute.History) },
                onOpenActivity = { navigateTo(InsightsRoute.Activity) },
                onOpenMetric = { navigateTo(InsightsRoute.Metric(it)) },
            )

            InsightsRoute.History -> InsightsHistory(
                data = data,
                selectedRange = DashboardRange.ALL,
                onBack = { navigateTo(InsightsRoute.Overview) },
                onOpen = { navigateTo(InsightsRoute.Detail(it, currentRoute)) },
                onDelete = { entry ->
                    store.removeAnalysis(entry.timestampMillis)
                    data = data.copy(
                        analyses = data.analyses.filterNot {
                            it.timestampMillis == entry.timestampMillis
                        },
                    )
                },
                onPin = { entry ->
                    store.setAnalysisPinned(entry.timestampMillis, !entry.pinned)
                    reload()
                },
            )

            InsightsRoute.Activity -> InsightsActivity(
                data = data,
                onBack = { navigateTo(InsightsRoute.Overview) },
                onOpenHistory = { navigateTo(InsightsRoute.History) },
                onOpen = { navigateTo(InsightsRoute.Detail(it, InsightsRoute.Activity)) },
                onDelete = { entry ->
                    store.removeAnalysis(entry.timestampMillis)
                    data = data.copy(
                        analyses = data.analyses.filterNot {
                            it.timestampMillis == entry.timestampMillis
                        },
                    )
                },
                onPin = { entry ->
                    store.setAnalysisPinned(entry.timestampMillis, !entry.pinned)
                    reload()
                },
            )

            is InsightsRoute.Metric -> InsightsMetricDetail(
                data = data,
                metric = currentRoute.metric,
                selectedRange = selectedRange,
                onRangeSelected = { selectedRangeOrdinal = it.ordinal },
                onBack = { navigateTo(InsightsRoute.Overview) },
                onOpen = {
                    navigateTo(
                        InsightsRoute.Detail(
                            entry = it,
                            returnRoute = InsightsRoute.Metric(currentRoute.metric),
                        ),
                    )
                },
                onDelete = { entry ->
                    store.removeAnalysis(entry.timestampMillis)
                    data = data.copy(
                        analyses = data.analyses.filterNot {
                            it.timestampMillis == entry.timestampMillis
                        },
                    )
                },
                onPin = { entry ->
                    store.setAnalysisPinned(entry.timestampMillis, !entry.pinned)
                    reload()
                },
            )

            is InsightsRoute.Detail -> AnalysisHistoryDetailScreen(
                entry = currentRoute.entry,
                store = store,
                onBack = { navigateTo(currentRoute.returnRoute) },
            )
        }
    }
}

@Composable
private fun InsightsOverview(
    data: InsightsData,
    selectedRange: DashboardRange,
    onRangeSelected: (DashboardRange) -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onOpenHistory: () -> Unit,
    onOpenActivity: () -> Unit,
    onOpenMetric: (InsightsMetric) -> Unit,
) {
    val now = remember(data.analyses.size, selectedRange) { System.currentTimeMillis() }
    val visible = remember(data.analyses, selectedRange, now) {
        data.analyses.filter { selectedRange.contains(it.timestampMillis, now) }
    }
    val dailyBest = remember(visible) { dailyBest(visible) }
    val averageF0 = remember(
        data.realtimeF0,
        data.analyses,
        data.averageF0ResetAtMillis,
        selectedRange,
        now,
    ) {
        f0Sources(data, selectedRange, now).averageOrNull()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
    ) {
        item(key = "insights-title") {
            ScreenTitle(title = stringResource(R.string.dashboard_title))
            Spacer(Modifier.height(14.dp))
            DashboardRangePicker(
                selected = selectedRange,
                onSelected = onRangeSelected,
            )
            Spacer(Modifier.height(16.dp))
        }
        item(key = "insights-summary") {
            SummaryCards(
                analysisCount = visible.size,
                onOpenActivity = onOpenActivity,
            )
        }
        item(key = "insights-overall") {
            Spacer(Modifier.height(16.dp))
            OverallScoreCard(
                entries = dailyBest,
                onOpen = { onOpenMetric(InsightsMetric.COMPOSITE) },
            )
        }
        item(key = "insights-metrics") {
            Spacer(Modifier.height(12.dp))
            MetricCards(
                entries = dailyBest,
                averageF0 = averageF0,
                onOpenMetric = onOpenMetric,
            )
        }
        if (visible.isEmpty()) {
            item(key = "insights-empty") {
                Spacer(Modifier.height(12.dp))
                DashboardCard {
                    Text(
                        text = stringResource(R.string.dashboard_no_analysis),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item(key = "insights-bottom") {
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SummaryCards(
    analysisCount: Int,
    onOpenActivity: () -> Unit,
) {
    DashboardSummaryCard(
        count = analysisCount,
        onClick = onOpenActivity,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun DashboardSummaryCard(
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.dashboard_assessment_count, count),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = stringResource(R.string.dashboard_open_history),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun OverallScoreCard(
    entries: List<AnalysisHistoryEntry>,
    onOpen: () -> Unit,
) {
    val latest = entries.lastOrNull()
    val baselineEntries = entries.dropLast(1)
    val baseline = baselineEntries.map { it.finalScore }.averageOrNull()
    val points = entries.map {
        InsightsPoint(it.timestampMillis, it.finalScore.toFloat())
    }
    DashboardCard(onClick = onOpen) {
        Column {
            Text(
                text = stringResource(R.string.dashboard_metric_composite),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = latest?.let { "%.0f".format(it.finalScore) }
                        ?: stringResource(R.string.dashboard_no_value),
                    color = if (latest == null) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    style = MaterialTheme.typography.metricHero,
                )
                if (latest != null && baseline != null) {
                    Spacer(Modifier.width(10.dp))
                    ChangeText(
                        current = latest.finalScore,
                        baseline = baseline,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(
                    R.string.dashboard_baseline_average,
                    baseline?.let { "%.0f".format(it) }
                        ?: stringResource(R.string.dashboard_no_value),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (points.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                DashboardTrendChart(
                    points = points,
                    lineColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(126.dp),
                    yMinimum = 0f,
                    yMaximum = 100f,
                    interactive = true,
                )
                Spacer(Modifier.height(4.dp))
                TrendDateLabels(entries = entries)
            }
        }
    }
}

@Composable
private fun MetricCards(
    entries: List<AnalysisHistoryEntry>,
    averageF0: Double?,
    onOpenMetric: (InsightsMetric) -> Unit,
) {
    val previous = entries.dropLast(1)
    val latest = entries.lastOrNull()
    val naturalnessPoints = entries.map {
        InsightsPoint(it.timestampMillis, it.naturalnessScore.toFloat())
    }
    val pitchEntries = entries.filter { it.meanF0Hz != null }
    val pitchPoints = pitchEntries.map {
        InsightsPoint(it.timestampMillis, it.meanF0Hz!!.toFloat())
    }
    val timbrePoints = entries.map {
        InsightsPoint(it.timestampMillis, it.standardScore.toFloat())
    }
    val pitchRange = pitchRange(pitchPoints)

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DashboardMetricCard(
                title = stringResource(InsightsMetric.NATURALNESS.titleRes),
                value = latest?.naturalnessScore?.let { "%.0f".format(it) }
                    ?: stringResource(R.string.dashboard_no_value),
                current = latest?.naturalnessScore,
                baseline = previous.map { it.naturalnessScore }.averageOrNull(),
                points = naturalnessPoints,
                yMinimum = 0f,
                yMaximum = 100f,
                tint = metricTint(InsightsMetric.NATURALNESS),
                onClick = { onOpenMetric(InsightsMetric.NATURALNESS) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
            DashboardMetricCard(
                title = stringResource(InsightsMetric.TIMBRE.titleRes),
                value = latest?.standardScore?.let { "%.0f".format(it) }
                    ?: stringResource(R.string.dashboard_no_value),
                current = latest?.standardScore,
                baseline = previous.map { it.standardScore }.averageOrNull(),
                points = timbrePoints,
                yMinimum = 0f,
                yMaximum = 100f,
                tint = metricTint(InsightsMetric.TIMBRE),
                onClick = { onOpenMetric(InsightsMetric.TIMBRE) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }
        Spacer(Modifier.height(12.dp))
        DashboardMetricCard(
            title = stringResource(InsightsMetric.PITCH.titleRes),
            value = latest?.meanF0Hz?.let { "%.1f".format(it) }
                ?: stringResource(R.string.dashboard_no_value),
            current = latest?.meanF0Hz,
            baseline = averageF0,
            points = pitchPoints,
            yMinimum = pitchRange.first,
            yMaximum = pitchRange.second,
            tint = metricTint(InsightsMetric.PITCH),
            onClick = { onOpenMetric(InsightsMetric.PITCH) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun DashboardMetricCard(
    title: String,
    value: String,
    current: Double?,
    baseline: Double?,
    points: List<InsightsPoint>,
    yMinimum: Float,
    yMaximum: Float,
    tint: Color,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    DashboardCard(
        modifier = modifier,
        onClick = onClick,
    ) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    color = if (current == null) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        tint
                    },
                    style = MaterialTheme.typography.metricMedium,
                )
                if (current != null && baseline != null) {
                    Spacer(Modifier.width(7.dp))
                    ChangeText(current = current, baseline = baseline)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(
                    R.string.dashboard_baseline_average,
                    baseline?.let { "%.1f".format(it) }
                        ?: stringResource(R.string.dashboard_no_value),
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (points.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                DashboardTrendChart(
                    points = points,
                    lineColor = tint,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp),
                    yMinimum = yMinimum,
                    yMaximum = yMaximum,
                    interactive = false,
                )
            }
        }
    }
}

@Composable
private fun DashboardCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    val color = MaterialTheme.colorScheme.surfaceContainerLow
    val body: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            content()
        }
    }
    if (onClick == null) {
        Surface(modifier = modifier, shape = shape, color = color) { body() }
    } else {
        // The clickable Surface overload puts the ripple inside the clip, so the
        // ink follows the card's rounded corners instead of spilling past them.
        Surface(onClick = onClick, modifier = modifier, shape = shape, color = color) {
            body()
        }
    }
}

@Composable
private fun ChangeText(
    current: Double,
    baseline: Double,
) {
    val change = percentChange(current, baseline) ?: return
    if (abs(change) < 0.5) return
    Text(
        text = stringResource(
            if (change > 0) R.string.dashboard_change_up else R.string.dashboard_change_down,
            abs(change),
        ),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = if (change > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
    )
}

@Composable
private fun TrendDateLabels(entries: List<AnalysisHistoryEntry>) {
    if (entries.size < 2) return
    val locale = LocalConfiguration.current.locales[0]
    val formatter = remember(locale) { SimpleDateFormat("M/d", locale) }
    Row(Modifier.fillMaxWidth()) {
        Text(
            text = formatter.format(Date(entries.first().timestampMillis)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = formatter.format(Date(entries.last().timestampMillis)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DashboardRangePicker(
    selected: DashboardRange,
    onSelected: (DashboardRange) -> Unit,
) {
    SegmentedChoiceRow(
        options = DashboardRange.entries,
        selected = selected,
        onSelected = onSelected,
        label = { range -> stringResource(range.labelRes) },
    )
}

@Composable
private fun InsightsMetricDetail(
    data: InsightsData,
    metric: InsightsMetric,
    selectedRange: DashboardRange,
    onRangeSelected: (DashboardRange) -> Unit,
    onBack: () -> Unit,
    onOpen: (AnalysisHistoryEntry) -> Unit,
    onDelete: (AnalysisHistoryEntry) -> Unit,
    onPin: (AnalysisHistoryEntry) -> Unit,
) {
    val now = System.currentTimeMillis()
    val visible = remember(data.analyses, selectedRange, now) {
        data.analyses.filter { selectedRange.contains(it.timestampMillis, now) }
    }
    val daily = remember(visible) { dailyBest(visible) }
    val samples = remember(daily, metric) {
        daily.mapNotNull { entry ->
            metricValue(entry, metric)?.let { value ->
                InsightsPoint(entry.timestampMillis, value.toFloat())
            }
        }
    }
    val latestEntry = daily.asReversed().firstOrNull { metricValue(it, metric) != null }
    val latestValue = latestEntry?.let { metricValue(it, metric) }
    val average = samples.map { it.value.toDouble() }.averageOrNull()
    val minimum = samples.minOfOrNull { it.value }?.toDouble()
    val maximum = samples.maxOfOrNull { it.value }?.toDouble()
    val tint = metricTint(metric)
    val yRange = if (metric == InsightsMetric.PITCH || metric == InsightsMetric.VARIATION) {
        pitchRange(samples)
    } else {
        0f to 100f
    }
    val locale = LocalConfiguration.current.locales[0]
    val dateFormatter = remember(locale) { SimpleDateFormat("yyyy MMMM d HH:mm", locale) }
    val groupDateFormatter = remember(locale) { SimpleDateFormat("yyyy MMMM d EEEE", locale) }
    val relatedAssessments = remember(daily, metric) {
        daily.filter { metricValue(it, metric) != null }
            .sortedByDescending { it.timestampMillis }
    }
    val groupedAssessments = remember(relatedAssessments) {
        relatedAssessments.groupBy { startOfDay(it.timestampMillis) }
            .toSortedMap(compareByDescending { it })
    }

    BackHandler(onBack = onBack)
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        ) {
            item(key = "metric-title") {
                ScreenTitle(
                    title = stringResource(metric.titleRes),
                    onBack = onBack,
                )
                Spacer(Modifier.height(14.dp))
                DashboardRangePicker(
                    selected = selectedRange,
                    onSelected = onRangeSelected,
                )
                Spacer(Modifier.height(16.dp))
            }
            item(key = "metric-summary") {
                DashboardCard {
                    Column {
                        Text(
                            text = stringResource(metric.titleRes),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = tint,
                        )
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = stringResource(R.string.dashboard_metric_latest),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = formatMetricValue(latestValue, metric),
                                color = if (latestValue == null) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    tint
                                },
                                style = MaterialTheme.typography.metricHero,
                            )
                            if (latestValue != null && metric.includesUnit) {
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Hz",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        latestEntry?.let {
                            Text(
                                text = dateFormatter.format(Date(it.timestampMillis)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (samples.isNotEmpty()) {
                            Spacer(Modifier.height(16.dp))
                            DashboardTrendChart(
                                points = samples,
                                lineColor = tint,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp),
                                yMinimum = yRange.first,
                                yMaximum = yRange.second,
                                interactive = true,
                            )
                            Spacer(Modifier.height(16.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Spacer(Modifier.height(14.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                MetricStat(
                                    label = stringResource(R.string.dashboard_metric_average),
                                    value = formatMetricValue(average, metric),
                                    modifier = Modifier.weight(1f),
                                )
                                MetricStat(
                                    label = stringResource(R.string.dashboard_metric_minimum),
                                    value = formatMetricValue(minimum, metric),
                                    modifier = Modifier.weight(1f),
                                )
                                MetricStat(
                                    label = stringResource(R.string.dashboard_metric_maximum),
                                    value = formatMetricValue(maximum, metric),
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            item(key = "metric-explanation") {
                DashboardCard {
                    Column {
                        Text(
                            text = stringResource(R.string.dashboard_metric_explanation),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(metric.descriptionRes) + stringResource(R.string.dashboard_metric_daily_best_note),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
            }
            if (samples.isEmpty()) {
                item(key = "metric-empty") {
                    DashboardCard {
                        Text(
                            text = stringResource(R.string.dashboard_metric_empty_description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                item(key = "metric-records-title") {
                    Text(
                        text = stringResource(R.string.dashboard_metric_records),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(6.dp))
                }
                groupedAssessments.forEach { (day, entries) ->
                    item(key = "metric-records-date-$day") {
                        Text(
                            text = groupDateFormatter.format(Date(day)),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 14.dp, bottom = 6.dp),
                        )
                    }
                    items(
                        items = entries,
                        key = { it.timestampMillis },
                    ) { entry ->
                        val value = metricValue(entry, metric)
                        if (value != null) {
                            AnalysisHistoryRow(
                                entry = entry,
                                onOpen = { onOpen(entry) },
                                onDelete = { onDelete(entry) },
                                onPin = { onPin(entry) },
                                score = value,
                                scoreLabelRes = metric.titleRes,
                                scoreUnit = if (metric.includesUnit) "Hz" else null,
                                scoreColor = tint,
                            )
                        }
                    }
                }
            }
            item(key = "metric-bottom") {
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun MetricStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun InsightsActivity(
    data: InsightsData,
    onBack: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpen: (AnalysisHistoryEntry) -> Unit,
    onDelete: (AnalysisHistoryEntry) -> Unit,
    onPin: (AnalysisHistoryEntry) -> Unit,
) {
    val now = System.currentTimeMillis()
    val visible = data.analyses
    val openedDates = remember(data.openedDays) {
        data.openedDays.toSet()
    }
    val recordingDates = remember(visible) {
        visible.map { startOfDay(it.timestampMillis) }.toSet()
    }
    var displayedMonth by remember { mutableStateOf(startOfMonth(now)) }
    var selectedDay by remember { mutableStateOf(startOfDay(now)) }
    val selectedAssessments = remember(visible, selectedDay) {
        visible.filter { startOfDay(it.timestampMillis) == selectedDay }
            .sortedByDescending { it.timestampMillis }
    }
    val locale = LocalConfiguration.current.locales[0]
    val monthFormatter = remember(locale) { SimpleDateFormat("yyyy MMMM", locale) }
    val selectedDayFormatter = remember(locale) {
        SimpleDateFormat("yyyy MMMM d EEEE", locale)
    }
    val earliestMonth = remember(data.openedDays, data.analyses, now) {
        val earliest = buildList {
            data.openedDays.minOrNull()?.let(::add)
            data.analyses.minOfOrNull { it.timestampMillis }?.let(::add)
        }.minOrNull() ?: now
        startOfMonth(earliest)
    }
    val currentMonth = startOfMonth(now)

    BackHandler(onBack = onBack)
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        ) {
            item(key = "activity-title") {
                ScreenTitle(
                    title = stringResource(R.string.dashboard_activity_title),
                    onBack = onBack,
                )
                Spacer(Modifier.height(18.dp))
            }
            item(key = "activity-view-all") {
                DashboardCard(
                    onClick = onOpenHistory,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.dashboard_view_all_records),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            painter = painterResource(R.drawable.ic_chevron_right),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            item(key = "activity-calendar") {
                DashboardCard {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = monthFormatter.format(Date(displayedMonth)),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.weight(1f))
                            IconButton(
                                onClick = { displayedMonth = addMonths(displayedMonth, -1) },
                                enabled = displayedMonth > earliestMonth,
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_chevron_right),
                                    contentDescription = stringResource(R.string.dashboard_previous_month),
                                    modifier = Modifier.rotate(180f),
                                )
                            }
                            IconButton(
                                onClick = { displayedMonth = addMonths(displayedMonth, 1) },
                                enabled = displayedMonth < currentMonth,
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_chevron_right),
                                    contentDescription = stringResource(R.string.dashboard_next_month),
                                )
                            }
                        }
                        WeekdayHeader()
                        Spacer(Modifier.height(8.dp))
                        monthCells(displayedMonth).chunked(7).forEach { week ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                week.forEach { day ->
                                    if (day == null) {
                                        Spacer(Modifier.weight(1f).height(48.dp))
                                    } else {
                                        CalendarDay(
                                            timestamp = day,
                                            selected = day == selectedDay,
                                            opened = day in openedDates,
                                            recorded = day in recordingDates,
                                            enabled = day <= now,
                                            onClick = { selectedDay = day },
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(18.dp),
                        ) {
                            CalendarLegend(
                                color = InsightOrange,
                                label = stringResource(R.string.dashboard_opened_legend),
                            )
                            CalendarLegend(
                                color = InsightBlue,
                                label = stringResource(R.string.dashboard_recorded_legend),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
            }
            item(key = "activity-selected-day") {
                Text(
                    text = selectedDayFormatter.format(Date(selectedDay)),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
            }
            if (selectedAssessments.isEmpty()) {
                item(key = "activity-selected-empty") {
                    DashboardCard {
                        Text(
                            text = stringResource(R.string.dashboard_no_recordings),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                items(
                    items = selectedAssessments,
                    key = { it.timestampMillis },
                ) { entry ->
                    AnalysisHistoryRow(
                        entry = entry,
                        onOpen = { onOpen(entry) },
                        onDelete = { onDelete(entry) },
                        onPin = { onPin(entry) },
                    )
                }
            }
            item(key = "activity-bottom") {
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun WeekdayHeader() {
    val locale = LocalConfiguration.current.locales[0]
    val calendar = remember(locale) { Calendar.getInstance(locale) }
    val symbols = DateFormatSymbols.getInstance(locale).shortWeekdays
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(7) { index ->
            val symbolIndex = ((index + calendar.firstDayOfWeek - 1) % 7) + 1
            Text(
                text = symbols[symbolIndex],
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun CalendarDay(
    timestamp: Long,
    selected: Boolean,
    opened: Boolean,
    recorded: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = MaterialTheme.shapes.small
    val calendar = remember(timestamp) { Calendar.getInstance().apply { timeInMillis = timestamp } }
    Box(
        modifier = modifier
            .height(48.dp)
            .alpha(if (enabled) 1f else 0.25f)
            .clip(shape)
            .background(
                when {
                    selected -> MaterialTheme.colorScheme.primaryContainer
                    opened -> InsightOrange.copy(alpha = 0.14f)
                    else -> Color.Transparent
                },
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = calendar.get(Calendar.DAY_OF_MONTH).toString(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            )
            Canvas(Modifier.size(6.dp)) {
                if (recorded) drawCircle(InsightBlue)
                else if (opened) drawCircle(InsightOrange)
            }
        }
    }
}

@Composable
private fun CalendarLegend(
    color: Color,
    label: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(7.dp)) { drawCircle(color) }
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun InsightsHistory(
    data: InsightsData,
    selectedRange: DashboardRange,
    onBack: () -> Unit,
    onOpen: (AnalysisHistoryEntry) -> Unit,
    onDelete: (AnalysisHistoryEntry) -> Unit,
    onPin: (AnalysisHistoryEntry) -> Unit,
) {
    val now = remember(data.analyses.size, selectedRange) { System.currentTimeMillis() }
    val visible = remember(data.analyses, selectedRange, now) {
        data.analyses.filter { selectedRange.contains(it.timestampMillis, now) }
    }
    val pinned = remember(visible) {
        visible.filter { it.pinned }.sortedByDescending { it.timestampMillis }
    }
    val grouped = remember(visible) {
        visible.filterNot { it.pinned }
            .groupBy { startOfDay(it.timestampMillis) }
            .toSortedMap(compareByDescending { it })
    }
    val locale = LocalConfiguration.current.locales[0]
    val dateFormatter = remember(locale) { SimpleDateFormat("yyyy MMMM d EEEE", locale) }
    BackHandler(onBack = onBack)
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        ) {
            item(key = "history-back") {
                ScreenTitle(
                    title = stringResource(R.string.dashboard_analysis_history),
                    onBack = onBack,
                )
                Spacer(Modifier.height(14.dp))
            }
            if (visible.isEmpty()) {
                item(key = "history-empty") {
                    DashboardCard {
                        Text(
                            text = stringResource(R.string.dashboard_no_analysis),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                if (pinned.isNotEmpty()) {
                    item(key = "history-pinned-title") {
                        Text(
                            text = stringResource(R.string.dashboard_pinned_section),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 14.dp, bottom = 6.dp),
                        )
                    }
                    items(
                        items = pinned,
                        key = { it.timestampMillis },
                    ) { entry ->
                        AnalysisHistoryRow(
                            entry = entry,
                            onOpen = { onOpen(entry) },
                            onDelete = { onDelete(entry) },
                            onPin = { onPin(entry) },
                        )
                    }
                }
                grouped.forEach { (day, entries) ->
                    item(key = "history-date-$day") {
                        Text(
                            text = dateFormatter.format(Date(day)),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 14.dp, bottom = 6.dp),
                        )
                    }
                    items(
                        items = entries.sortedByDescending { it.timestampMillis },
                        key = { it.timestampMillis },
                    ) { entry ->
                        AnalysisHistoryRow(
                            entry = entry,
                            onOpen = { onOpen(entry) },
                            onDelete = { onDelete(entry) },
                            onPin = { onPin(entry) },
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
            }
            item(key = "history-bottom") {
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun DashboardTrendChart(
    points: List<InsightsPoint>,
    lineColor: Color,
    modifier: Modifier = Modifier,
    yMinimum: Float,
    yMaximum: Float,
    interactive: Boolean,
) {
    if (points.isEmpty()) return
    val locale = LocalConfiguration.current.locales[0]
    val tooltipDate = remember(locale) { SimpleDateFormat("M/d", locale) }
    val crosshairColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
    val range = max(yMaximum - yMinimum, 1f)
    var selectedIndex by remember(points) { mutableStateOf<Int?>(null) }
    val chartDescription = stringResource(
        R.string.dashboard_chart_accessibility_format,
        points.size,
        "%.1f".format(points.minOf { it.value }),
        "%.1f".format(points.maxOf { it.value }),
    )

    BoxWithConstraints(
        modifier = modifier.semantics { contentDescription = chartDescription },
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(points, interactive) {
                    if (!interactive) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        selectedIndex = nearestIndex(down.position.x, size.width, points.size)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            selectedIndex = nearestIndex(change.position.x, size.width, points.size)
                            if (!change.pressed) break
                        }
                        selectedIndex = null
                    }
                },
        ) {
            val stepX = if (points.size == 1) 0f else size.width / (points.size - 1)
            fun point(index: Int): Offset {
                val fraction = ((points[index].value - yMinimum) / range).coerceIn(0f, 1f)
                return Offset(
                    x = if (points.size == 1) size.width / 2f else stepX * index,
                    y = size.height - fraction * size.height,
                )
            }

            val linePath = smoothPath(points.indices.map(::point))
            drawPath(
                path = linePath,
                color = lineColor,
                style = Stroke(
                    width = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                ),
            )
            if (points.size == 1) {
                drawCircle(lineColor, radius = 3.5.dp.toPx(), center = point(0))
            }
            selectedIndex?.let { index ->
                val selected = point(index)
                drawLine(
                    color = crosshairColor,
                    start = Offset(selected.x, 0f),
                    end = Offset(selected.x, size.height),
                    strokeWidth = 1.dp.toPx(),
                )
                drawCircle(lineColor, radius = 4.5.dp.toPx(), center = selected)
            }
        }

        selectedIndex?.let { index ->
            val point = points[index]
            val tooltipWidth = 70.dp
            val x = (
                maxWidth * index / max(points.lastIndex, 1) - tooltipWidth / 2
                ).coerceIn(0.dp, maxWidth - tooltipWidth)
            Surface(
                modifier = Modifier
                    .offset(x = x, y = 0.dp)
                    .width(tooltipWidth),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                tonalElevation = 4.dp,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = tooltipDate.format(Date(point.timestampMillis)),
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Text(
                        text = "%.1f".format(point.value),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

private fun smoothPath(points: List<Offset>): Path = Path().apply {
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

private fun nearestIndex(
    x: Float,
    width: Int,
    count: Int,
): Int {
    if (count <= 1 || width <= 0) return 0
    return ((x / width) * (count - 1))
        .roundToInt()
        .coerceIn(0, count - 1)
}

private fun dailyBest(entries: List<AnalysisHistoryEntry>): List<AnalysisHistoryEntry> =
    entries.groupBy { startOfDay(it.timestampMillis) }
        .values
        .mapNotNull { dayEntries ->
            dayEntries.maxWithOrNull(
                compareBy<AnalysisHistoryEntry> { it.finalScore }
                    .thenBy { it.timestampMillis },
            )
        }
        .sortedBy { it.timestampMillis }

private fun startOfDay(timestampMillis: Long): Long = Calendar.getInstance().apply {
    timeInMillis = timestampMillis
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun startOfMonth(timestampMillis: Long): Long = Calendar.getInstance().apply {
    timeInMillis = timestampMillis
    set(Calendar.DAY_OF_MONTH, 1)
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun addMonths(timestampMillis: Long, amount: Int): Long = Calendar.getInstance().apply {
    timeInMillis = timestampMillis
    add(Calendar.MONTH, amount)
}.timeInMillis

private fun monthCells(monthTimestamp: Long): List<Long?> {
    val calendar = Calendar.getInstance().apply {
        timeInMillis = monthTimestamp
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val firstDayOffset = (calendar.get(Calendar.DAY_OF_WEEK) - calendar.firstDayOfWeek + 7) % 7
    val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    return buildList<Long?> {
        repeat(firstDayOffset) { add(null) }
        repeat(daysInMonth) { dayIndex ->
            calendar.set(Calendar.DAY_OF_MONTH, dayIndex + 1)
            add(calendar.timeInMillis)
        }
    }.let { cells ->
        if (cells.size % 7 == 0) cells else cells + List(7 - cells.size % 7) { null }
    }
}

private fun metricValue(entry: AnalysisHistoryEntry, metric: InsightsMetric): Double? = when (metric) {
    InsightsMetric.COMPOSITE -> entry.finalScore
    InsightsMetric.NATURALNESS -> entry.naturalnessScore
    InsightsMetric.PITCH -> entry.meanF0Hz
    InsightsMetric.TIMBRE -> entry.standardScore
    InsightsMetric.VARIATION -> entry.f0VariationHz
}

@Composable
private fun metricTint(metric: InsightsMetric): Color = when (metric) {
    InsightsMetric.COMPOSITE -> MaterialTheme.colorScheme.primary
    InsightsMetric.NATURALNESS -> MaterialTheme.colorScheme.secondary
    InsightsMetric.PITCH -> MaterialTheme.colorScheme.tertiary
    InsightsMetric.TIMBRE -> InsightAmber
    InsightsMetric.VARIATION -> MaterialTheme.colorScheme.secondary
}

private fun formatMetricValue(value: Double?, metric: InsightsMetric): String {
    if (value == null || !value.isFinite()) return "--"
    return if (metric.includesUnit) "%.1f".format(value) else "%.0f".format(value)
}

private fun List<Double>.averageOrNull(): Double? =
    takeIf { it.isNotEmpty() }?.average()

private fun percentChange(current: Double, baseline: Double): Double? {
    if (!current.isFinite() || !baseline.isFinite() || baseline == 0.0) return null
    return (current - baseline) / abs(baseline) * 100.0
}

private fun pitchRange(points: List<InsightsPoint>): Pair<Float, Float> {
    if (points.isEmpty()) return 0f to 1f
    val minimum = points.minOf { it.value }
    val maximum = points.maxOf { it.value }
    val padding = max((maximum - minimum) * 0.2f, 1f)
    return max(0f, minimum - padding) to (maximum + padding)
}

private fun f0Sources(
    data: InsightsData,
    range: DashboardRange,
    now: Long,
): List<Double> = buildList {
    data.realtimeF0.forEach { entry ->
        if (entry.timestampMillis > data.averageF0ResetAtMillis &&
            range.contains(entry.timestampMillis, now)
        ) {
            add(entry.meanF0Hz)
        }
    }
    data.analyses.forEach { entry ->
        val meanF0Hz = entry.meanF0Hz
        if (meanF0Hz != null &&
            entry.timestampMillis > data.averageF0ResetAtMillis &&
            range.contains(entry.timestampMillis, now)
        ) {
            add(meanF0Hz)
        }
    }
}

private fun routeDepth(route: InsightsRoute): Int = when (route) {
    InsightsRoute.Overview -> 0
    InsightsRoute.History,
    InsightsRoute.Activity,
    is InsightsRoute.Metric,
    -> 1
    is InsightsRoute.Detail -> 2
}
