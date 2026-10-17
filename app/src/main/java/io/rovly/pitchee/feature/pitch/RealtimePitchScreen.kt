package io.rovly.pitchee.feature.pitch

import io.rovly.pitchee.ui.theme.PitcheeColorTokens
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.rovly.pitchee.R
import io.rovly.pitchee.data.preferences.CorePreferences
import io.rovly.pitchee.data.preferences.PitchChartStyle
import io.rovly.pitchee.data.preferences.PitchDisplayPreferences
import io.rovly.pitchee.ui.common.ScreenTitle
import io.rovly.pitchee.feature.settings.PASSAGE_MAX_LINES
import io.rovly.pitchee.feature.settings.PassagePreferences
import io.rovly.pitchee.feature.settings.adaptivePassagePages
import java.text.BreakIterator
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

@Composable
internal fun RealtimePitchScreen() {
    val context = LocalContext.current
    val languageCode = LocalConfiguration.current.locales[0].language
    val factory = remember { PitchViewModel.factory(context.applicationContext) }
    val viewModel: PitchViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    var permissionDenied by remember { mutableStateOf(false) }
    var transientRewindMessage by remember { mutableStateOf<String?>(null) }
    val lastPageMessage = stringResource(R.string.pitch_last_page)
    val rewindMessageFormat = stringResource(R.string.pitch_rewind_seconds)
    val replayTailEndSeconds = state.replayTailEndSeconds
    val playbackPositionSeconds = state.playbackPositionSeconds
    val rewindWindowMessage = if (
        state.replaying &&
        replayTailEndSeconds != null &&
        playbackPositionSeconds != null
    ) {
        rewindMessageFormat.format(
            (replayTailEndSeconds - playbackPositionSeconds).coerceAtLeast(0.0),
        )
    } else {
        null
    }
    val rewindStatus = transientRewindMessage ?: rewindWindowMessage

    LaunchedEffect(state.replaying) {
        if (!state.replaying) transientRewindMessage = null
    }
    LaunchedEffect(transientRewindMessage) {
        if (transientRewindMessage != null) {
            delay(1500)
            transientRewindMessage = null
        }
    }
    val pitchDisplayPreferences = remember(context) { PitchDisplayPreferences(context) }
    val corePreferences = remember(context) { CorePreferences(context) }
    val chartStyle = pitchDisplayPreferences.style()
    val gapSeconds = pitchDisplayPreferences.gapThresholdMs() / 1000.0
    // The chart follows the analyser's F0 range, with a little headroom on both
    // sides so a value sitting on the limit is still visible.
    val minF0Hz = corePreferences.minF0Hz()
    val maxF0Hz = corePreferences.maxF0Hz()
    val rangeValid = minF0Hz <= maxF0Hz
    val axisMinHz = if (rangeValid) (minF0Hz - F0_RANGE_HEADROOM_LOW_HZ) else 0f
    val axisMaxHz = if (rangeValid) (maxF0Hz + F0_RANGE_HEADROOM_HIGH_HZ) else 1f
    val passagePreferences = remember(context) { PassagePreferences(context) }
    var selectedPassageIndex by rememberSaveable(passagePreferences.officialIndex()) {
        mutableIntStateOf(passagePreferences.officialIndex())
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionDenied = !granted
        if (granted) viewModel.start()
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.finishSession() }
    }

    fun toggleMonitoring() {
        transientRewindMessage = null
        if (state.replaying) {
            viewModel.resumeAnalysis()
            return
        }
        if (state.running || state.preparing) {
            viewModel.finishSession()
            viewModel.clearDisplay()
            return
        }
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            permissionDenied = false
            viewModel.start()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun rewind() {
        val feedback = viewModel.rewindFiveSeconds() ?: return
        transientRewindMessage = if (feedback.isLastPage) lastPageMessage else null
    }

    val pitchChartDescription = state.currentHz?.let {
        stringResource(R.string.pitch_accessibility_current, it)
    } ?: stringResource(R.string.pitch_accessibility_waiting)
    Box(modifier = Modifier.fillMaxSize()) {
        if (rangeValid) {
            PitchChart(
                points = state.points,
                replayWindowEndSeconds = state.replayWindowEndSeconds,
                playbackPositionSeconds = state.playbackPositionSeconds,
                chartStyle = chartStyle,
                maxGapSeconds = gapSeconds,
                minHz = axisMinHz,
                maxHz = axisMaxHz,
                modifier = Modifier
                    .fillMaxSize()
                    .semantics { contentDescription = pitchChartDescription },
            )
        } else {
            RangeErrorNotice(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = 32.dp),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                // The chart runs edge to edge, so this column clears the status
                // bar on its own.
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ScreenTitle(
                title = stringResource(R.string.pitch_title),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(18.dp))
            val currentHz = state.currentHz
            Text(
                text = currentHz?.let { "%.0f Hz".format(it) } ?: "--",
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Black,
                color = if (currentHz == null) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
            Text(
                text = if (currentHz == null) {
                    stringResource(R.string.pitch_no_voice)
                } else {
                    stringResource(R.string.pitch_current_f0)
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            state.error?.let { error ->
                Text(
                    text = error,
                    modifier = Modifier.padding(bottom = 10.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }
            if (permissionDenied) {
                Text(
                    text = stringResource(R.string.record_permission_denied),
                    modifier = Modifier.padding(bottom = 10.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }
            rewindStatus?.let { message ->
                Text(
                    text = message,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                )
            }
            if (state.preparing) {
                Text(
                    text = stringResource(R.string.pitch_preparing),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(10.dp))
            }
            val showToolbar = state.running || state.preparing || state.replaying
            val passage = passagePreferences.activePassage(selectedPassageIndex)
            val passageText = passage.text(context)
            val passageStyle = MaterialTheme.typography.bodyMedium
            val textMeasurer = rememberTextMeasurer(cacheSize = 8)
            val density = LocalDensity.current
            val locale = LocalConfiguration.current.locales[0]
            var passageWidthPx by remember { mutableIntStateOf(0) }
            var passageHeightPx by remember { mutableIntStateOf(0) }
            var fullPassageWidthPx by remember { mutableIntStateOf(0) }
            val toolbarDeltaPx = with(density) { 42.dp.roundToPx() }
            val compactPassageWidthPx = (
                fullPassageWidthPx - toolbarDeltaPx
                ).coerceAtLeast(1)
            val expansionProgress = if (
                fullPassageWidthPx > 0 && toolbarDeltaPx > 0
            ) {
                ((fullPassageWidthPx - passageWidthPx).toFloat() / toolbarDeltaPx)
                    .coerceIn(0f, 1f)
            } else {
                0f
            }
            val fullPages = remember(
                passageText,
                fullPassageWidthPx,
                passageHeightPx,
                passageStyle,
                locale,
            ) {
                adaptivePassagePages(
                    text = passageText,
                    measurer = textMeasurer,
                    style = passageStyle,
                    widthPx = fullPassageWidthPx,
                    heightPx = passageHeightPx,
                    locale = locale,
                    wordAware = true,
                )
            }
            val compactPages = remember(
                passageText,
                compactPassageWidthPx,
                passageHeightPx,
                passageStyle,
                locale,
            ) {
                adaptivePassagePages(
                    text = passageText,
                    measurer = textMeasurer,
                    style = passageStyle,
                    widthPx = compactPassageWidthPx,
                    heightPx = passageHeightPx,
                    locale = locale,
                    wordAware = true,
                )
            }
            val pages = remember(
                passageText,
                passageWidthPx,
                passageHeightPx,
                expansionProgress,
                fullPages,
                compactPages,
                passageStyle,
                locale,
            ) {
                transitionPassagePages(
                    text = passageText,
                    measurer = textMeasurer,
                    style = passageStyle,
                    widthPx = passageWidthPx,
                    heightPx = passageHeightPx,
                    locale = locale,
                    fullPages = fullPages,
                    compactPages = compactPages,
                    progress = expansionProgress,
                )
            }
            var pageIndex by rememberSaveable(passageText) { mutableIntStateOf(0) }
            val currentPageIndex = pageIndex.coerceIn(pages.indices)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .onSizeChanged { size ->
                            passageWidthPx = size.width
                            passageHeightPx = size.height
                            if (!showToolbar) {
                                fullPassageWidthPx = size.width
                            }
                        },
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
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
                            modifier = Modifier.fillMaxWidth(),
                            style = passageStyle,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = PASSAGE_MAX_LINES,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.size(8.dp))
                PitchMonitorControls(
                    running = state.running,
                    preparing = state.preparing,
                    replaying = state.replaying,
                    onToggle = ::toggleMonitoring,
                    onRewind = ::rewind,
                )
            }
        }
    }
}

private fun transitionPassagePages(
    text: String,
    measurer: TextMeasurer,
    style: TextStyle,
    widthPx: Int,
    heightPx: Int,
    locale: Locale,
    fullPages: List<String>,
    compactPages: List<String>,
    progress: Float,
): List<String> {
    if (fullPages.isEmpty() || compactPages.isEmpty()) {
        return adaptivePassagePages(
            text = text,
            measurer = measurer,
            style = style,
            widthPx = widthPx,
            heightPx = heightPx,
            locale = locale,
            wordAware = true,
        )
    }
    if (progress <= 0.001f) {
        return adaptivePassagePages(
            text = text,
            measurer = measurer,
            style = style,
            widthPx = widthPx,
            heightPx = heightPx,
            locale = locale,
            wordAware = true,
        )
    }
    if (progress >= 0.999f) return compactPages

    val from = fullPages.first().length
    val to = compactPages.first().length
    val characterCut = (from + (to - from) * progress)
        .roundToInt()
        .coerceIn(1, text.length)
    val firstPage = text.substring(0, characterCut).trimEnd()
    val remaining = text.substring(characterCut).trimStart()
    val remainingPages = adaptivePassagePages(
        text = remaining,
        measurer = measurer,
        style = style,
        widthPx = widthPx,
        heightPx = heightPx,
        locale = locale,
        wordAware = true,
    )
    return if (remaining.isEmpty()) listOf(firstPage) else listOf(firstPage) + remainingPages
}

private fun wordBoundaryBefore(
    text: String,
    end: Int,
    locale: Locale,
): Int {
    if (end <= 0) return 1
    if (end >= text.length) return text.length
    val iterator = BreakIterator.getWordInstance(locale)
    iterator.setText(text)
    if (iterator.isBoundary(end)) return end
    val boundary = iterator.preceding(end)
    return boundary.coerceAtLeast(1)
}

@Composable
private fun PitchMonitorControls(
    running: Boolean,
    preparing: Boolean,
    replaying: Boolean,
    onToggle: () -> Unit,
    onRewind: () -> Unit,
) {
    val showToolbar = running || preparing || replaying
    var toolbarExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(showToolbar) {
        if (showToolbar) {
            toolbarExpanded = false
            delay(110)
            toolbarExpanded = true
        } else {
            toolbarExpanded = false
        }
    }

    val controlWidth by animateDpAsState(
        targetValue = if (toolbarExpanded) 114.dp else 72.dp,
        animationSpec = spring(dampingRatio = 0.92f, stiffness = 520f),
        label = "pitch-controls-width",
    )
    val controlHeight by animateDpAsState(
        targetValue = if (toolbarExpanded) 60.dp else 72.dp,
        animationSpec = spring(dampingRatio = 0.92f, stiffness = 520f),
        label = "pitch-controls-height",
    )
    val controlPadding by animateDpAsState(
        targetValue = if (toolbarExpanded) 6.dp else 12.dp,
        animationSpec = spring(dampingRatio = 0.92f, stiffness = 520f),
        label = "pitch-controls-padding",
    )
    val containerColor by animateColorAsState(
        targetValue = if (toolbarExpanded) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.primary
        },
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 280f),
        label = "pitch-controls-color",
    )
    val contentColor by animateColorAsState(
        targetValue = if (toolbarExpanded) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onPrimary
        },
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 280f),
        label = "pitch-controls-content",
    )

    Surface(
        modifier = Modifier
            .width(controlWidth)
            .height(controlHeight),
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
        tonalElevation = 3.dp,
        shadowElevation = 5.dp,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (showToolbar) {
                PitchRewindButton(
                    onClick = onRewind,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = controlPadding),
                )
            }

            AnimatedContent(
                targetState = when {
                    preparing -> null
                    running -> R.drawable.ic_pause
                    else -> R.drawable.ic_play
                },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = controlPadding)
                    .size(48.dp)
                    .zIndex(1f),
                transitionSpec = {
                    scaleIn(
                        spring(dampingRatio = 0.72f, stiffness = 360f),
                        initialScale = 0.68f,
                    ) togetherWith scaleOut(
                        spring(dampingRatio = 0.72f, stiffness = 360f),
                        targetScale = 0.68f,
                    )
                },
                label = "pitch-center-icon",
            ) { icon ->
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    tonalElevation = 3.dp,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                enabled = !preparing,
                                onClick = onToggle,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (icon == null) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.5.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Icon(
                                painter = painterResource(icon),
                                contentDescription = stringResource(
                                    when {
                                        running -> R.string.pitch_stop_monitoring
                                        replaying -> R.string.pitch_resume_analysis
                                        else -> R.string.pitch_start_monitoring
                                    },
                                ),
                                modifier = Modifier.size(30.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PitchRewindButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var spinCount by remember { mutableIntStateOf(0) }
    val rotation by animateFloatAsState(
        targetValue = spinCount * -360f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 260f),
        label = "pitch-rewind-rotation",
    )
    FilledIconButton(
        onClick = {
            spinCount++
            onClick()
        },
        modifier = modifier.size(48.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(R.drawable.ic_rewind_arrow),
                contentDescription = stringResource(R.string.pitch_rewind_3),
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer { rotationZ = rotation },
            )
//            Text(
//                text = "3",
//                modifier = Modifier
//                    .offset(x = 0.5.dp, y = 3.dp)
//                    .graphicsLayer { rotationZ = rotation },
//                color = MaterialTheme.colorScheme.onPrimary,
//                fontSize = 9.sp,
//                lineHeight = 9.sp,
//                fontWeight = FontWeight.Bold,
//            )
        }
    }
}

@Composable
private fun PitchChart(
    points: List<F0Point>,
    replayWindowEndSeconds: Double?,
    playbackPositionSeconds: Double?,
    chartStyle: PitchChartStyle,
    maxGapSeconds: Double,
    minHz: Float,
    maxHz: Float,
    modifier: Modifier = Modifier,
) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val averageColor = MaterialTheme.colorScheme.primary
    val feminineColor = PitcheeColorTokens.Feminine
    val masculineColor = PitcheeColorTokens.Masculine
    val traceColor = MaterialTheme.colorScheme.onSurface
    val windowSeconds = if (replayWindowEndSeconds != null) {
        REPLAY_WINDOW_SECONDS
    } else {
        WINDOW_SECONDS
    }
    // The window end is pinned to the newest sample. Frames now arrive once per
    // hop, so the trace already advances at ~62 Hz; interpolating on top of that
    // only buys a gap at the rim or a stretched last segment, never new detail.
    val latestTimestamp = replayWindowEndSeconds
        ?: points.lastOrNull()?.timestampSeconds
        ?: 0.0
    val firstTimestamp = latestTimestamp - windowSeconds
    val averageF0 = averageF0InWindow(points, firstTimestamp)
    val animatedAverageF0 by animateFloatAsState(
        targetValue = averageF0 ?: 0f,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 90f),
        label = "average-f0",
    )

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val plotTop = 164.dp
        val plotBottom = maxHeight - 132.dp
        val plotHeight = (plotBottom - plotTop).coerceAtLeast(1.dp)
        val plotLeft = 42.dp

        val axisTicks = remember(minHz, maxHz) { pitchAxisTicks(minHz, maxHz) }
        val density = LocalDensity.current
        val plotTopPx = with(density) { plotTop.toPx() }
        val plotBottomPx = with(density) { plotBottom.toPx() }
        val plotLeftPx = with(density) { plotLeft.toPx() }
        val plotHeightPx = (plotBottomPx - plotTopPx).coerceAtLeast(1f)
        val plotWidthPx = (constraints.maxWidth - plotLeftPx).coerceAtLeast(1f)

        fun yFraction(hz: Float): Float =
            ((hz - minHz) / (maxHz - minHz)).coerceIn(0f, 1f)

        fun yDp(hz: Float) = plotBottom - plotHeight * yFraction(hz)
        fun yForPx(hz: Float): Float = plotBottomPx - yFraction(hz) * plotHeightPx

        // Build the expensive spline path once per F0 snapshot. The average-line
        // animation can then invalidate the Canvas at display refresh without
        // rebuilding every cubic segment on each frame.
        val traceGeometry = remember(
            points,
            firstTimestamp,
            windowSeconds,
            plotLeftPx,
            plotWidthPx,
            plotBottomPx,
            plotHeightPx,
            minHz,
            maxHz,
            chartStyle,
            maxGapSeconds,
        ) {
            buildF0TraceGeometry(
                points = points,
                firstTimestamp = firstTimestamp,
                windowSeconds = windowSeconds,
                plotLeftPx = plotLeftPx,
                plotWidthPx = plotWidthPx,
                yFor = ::yForPx,
                style = chartStyle,
                maxGapSeconds = maxGapSeconds,
            )
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    val thresholdY = yForPx(F0_BOUNDARY_HZ)
                    val feminineBackground = feminineColor.copy(alpha = 0.20f)
                    val masculineBackground = masculineColor.copy(alpha = 0.20f)
                    val gridLines = axisTicks.map { hz ->
                        CachedGridLine(
                            y = yForPx(hz),
                            color = gridColor.copy(
                                alpha = if (hz == F0_BOUNDARY_HZ) 0.72f else 0.34f,
                            ),
                            strokeWidth = if (hz == F0_BOUNDARY_HZ) {
                                2.5.dp.toPx()
                            } else {
                                1.dp.toPx()
                            },
                        )
                    }
                    onDrawBehind {
                        drawRect(
                            color = feminineBackground,
                            topLeft = Offset.Zero,
                            size = Size(size.width, thresholdY),
                        )
                        drawRect(
                            color = masculineBackground,
                            topLeft = Offset(0f, thresholdY),
                            size = Size(size.width, size.height - thresholdY),
                        )
                        gridLines.forEach { line ->
                            drawLine(
                                color = line.color,
                                start = Offset(plotLeftPx, line.y),
                                end = Offset(size.width, line.y),
                                strokeWidth = line.strokeWidth,
                            )
                        }
                    }
                },
        ) {}

        Canvas(modifier = Modifier.fillMaxSize()) {

            if (averageF0 != null) {
                val y = yForPx(animatedAverageF0)
                drawLine(
                    color = averageColor.copy(alpha = 0.86f),
                    start = Offset(plotLeftPx, y),
                    end = Offset(size.width, y),
                    strokeWidth = 2.5.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }

            clipRect(
                left = plotLeftPx,
                top = 0f,
                right = size.width,
                bottom = size.height,
            ) {
                traceGeometry.path?.let { path ->
                    drawPath(
                        path = path,
                        color = traceColor,
                        style = Stroke(
                            width = 3.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                        ),
                    )
                }
                traceGeometry.dots.forEach { point ->
                    drawCircle(F0_DOT_COLOR, DOT_RADIUS_DP.dp.toPx(), point)
                }
            }

            playbackPositionSeconds?.let { positionSeconds ->
                val x = (
                    (positionSeconds - firstTimestamp) / windowSeconds
                    ).toFloat().coerceIn(0f, 1f) * size.width
                drawLine(
                    color = averageColor,
                    start = Offset(x, plotTopPx),
                    end = Offset(x, size.height),
                    strokeWidth = 2.dp.toPx(),
                )
            }
        }

        axisTicks.forEach { hz ->
            Text(
                text = "%.0f".format(hz),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(y = yDp(hz) - 8.dp)
                    .padding(start = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = if (hz == F0_BOUNDARY_HZ) {
                    averageColor
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }

        if (averageF0 != null) {
            Text(
                text = stringResource(
                    R.string.pitch_average_f0_format,
                    animatedAverageF0,
                ),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(y = yDp(animatedAverageF0) - 18.dp)
                    .padding(end = 10.dp),
                style = MaterialTheme.typography.labelMedium,
                color = averageColor,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}


private data class CachedGridLine(
    val y: Float,
    val color: Color,
    val strokeWidth: Float,
)

private data class F0TraceGeometry(
    val path: Path?,
    val dots: List<Offset>,
)

/**
 * Converts one F0 snapshot into reusable drawing geometry.
 *
 * [PitchChartStyle.CURVE] uses a uniform cubic B-spline: every piece depends on
 * four consecutive samples, so it is smooth (C2) and a finished piece never
 * changes when the next sample arrives. The spline approximates instead of
 * interpolating, which is what keeps noisy per-frame estimates from turning
 * into a zig-zag. [PitchChartStyle.DOTS] plots each estimate as it comes.
 */
private fun buildF0TraceGeometry(
    points: List<F0Point>,
    firstTimestamp: Double,
    windowSeconds: Double,
    plotLeftPx: Float,
    plotWidthPx: Float,
    yFor: (Float) -> Float,
    style: PitchChartStyle,
    maxGapSeconds: Double,
): F0TraceGeometry {
    val segments = mutableListOf<List<Offset>>()
    var current = mutableListOf<Offset>()
    var previousTime = Double.NEGATIVE_INFINITY
    for (index in 0 until points.size) {
        val point = points[index]
        val hz = point.f0Hz ?: continue
        // Keep one window of samples past the visible range and let the canvas
        // clip them, so a sample slides off the edge instead of blinking out.
        if (point.timestampSeconds < firstTimestamp - windowSeconds) continue
        if (current.isNotEmpty() && point.timestampSeconds - previousTime > maxGapSeconds) {
            segments += current.toList()
            current = mutableListOf()
        }
        val x = plotLeftPx + (
            (point.timestampSeconds - firstTimestamp) / windowSeconds
            ).toFloat() * plotWidthPx
        current += Offset(x, yFor(hz))
        previousTime = point.timestampSeconds
    }
    if (current.isNotEmpty()) segments += current.toList()

    val curvePath = if (style == PitchChartStyle.CURVE) Path() else null
    val dots = mutableListOf<Offset>()
    segments.forEach { segment ->
        if (style == PitchChartStyle.DOTS || segment.size == 1) {
            dots += segment
            return@forEach
        }
        val path = curvePath ?: return@forEach
        // Mirror the ends so the spline reaches the first and the newest sample
        // instead of stopping two samples short of them. Control points are
        // addressed by index rather than copied into a list, to keep per-frame
        // allocations down.
        val plainLast = segment.lastIndex
        val controlCount = segment.size + 2
        fun controlAt(index: Int): Offset = when (index) {
            0 -> segment[0] * 2f - segment[1]
            controlCount - 1 -> segment[plainLast] * 2f - segment[plainLast - 1]
            else -> segment[index - 1]
        }
        fun splineAt(index: Int): Offset {
            val a = controlAt(index)
            val b = controlAt(index + 1)
            val c = controlAt(index + 2)
            return Offset(
                (a.x + 4f * b.x + c.x) / 6f,
                (a.y + 4f * b.y + c.y) / 6f,
            )
        }
        val last = controlCount - 1
        if (last < 3) {
            path.moveTo(segment[0].x, segment[0].y)
            for (index in 1..plainLast) path.lineTo(segment[index].x, segment[index].y)
        } else {
            val first = splineAt(0)
            path.moveTo(first.x, first.y)
            for (index in 0..last - 3) {
                val p0 = controlAt(index)
                val p1 = controlAt(index + 1)
                val p2 = controlAt(index + 2)
                val p3 = controlAt(index + 3)
                val start = splineAt(index)
                val end = splineAt(index + 1)
                // Time only moves forward, so a piece may never run backwards.
                // Uneven sample spacing can push the B-spline handles past each
                // other, which is what folds the curve into a left/right facing
                // U; pinning them inside the piece keeps x monotonic.
                val left = minOf(start.x, end.x)
                val right = maxOf(start.x, end.x)
                path.cubicTo(
                    (p1.x + (p2.x - p0.x) / 6f).coerceIn(left, right),
                    p1.y + (p2.y - p0.y) / 6f,
                    (p2.x - (p3.x - p1.x) / 6f).coerceIn(left, right),
                    p2.y - (p3.y - p1.y) / 6f,
                    end.x,
                    end.y,
                )
            }
        }
    }
    return F0TraceGeometry(path = curvePath, dots = dots)
}

/** B-spline position between control points at [index] and [index] + 1. */
private fun splinePoint(points: List<Offset>, index: Int): Offset {
    val a = points[index]
    val b = points[index + 1]
    val c = points[index + 2]
    return Offset(
        (a.x + 4f * b.x + c.x) / 6f,
        (a.y + 4f * b.y + c.y) / 6f,
    )
}

private fun averageF0InWindow(points: List<F0Point>, firstTimestamp: Double): Float? {
    var sum = 0.0
    var count = 0
    for (index in 0 until points.size) {
        val point = points[index]
        if (point.timestampSeconds < firstTimestamp) continue
        val hz = point.f0Hz ?: continue
        sum += hz
        count++
    }
    if (count == 0) return null
    val average = sum / count
    return if (average.isFinite()) average.toFloat() else null
}

private const val DOT_RADIUS_DP = 2.6f
private val F0_DOT_COLOR = Color(0xFFFF9500)

// Headroom around the configured SwiftF0 range, so a value sitting exactly on a
// limit is still drawn inside the plot.
private const val F0_RANGE_HEADROOM_LOW_HZ = 30f
private const val F0_RANGE_HEADROOM_HIGH_HZ = 50f

/** Male/female split, always drawn even when it falls outside the range. */
private const val F0_BOUNDARY_HZ = 165f

/**
 * Roughly five round grid lines inside the visible range, plus the 165 Hz
 * boundary when it is in view.
 */
private fun pitchAxisTicks(minHz: Float, maxHz: Float): List<Float> {
    val span = (maxHz - minHz).coerceAtLeast(1f)
    val step = when {
        span <= 120f -> 20f
        span <= 250f -> 50f
        span <= 600f -> 100f
        else -> 200f
    }
    val ticks = mutableListOf<Float>()
    var value = ceil(minHz / step) * step
    while (value <= maxHz) {
        ticks += value
        value += step
    }
    if (F0_BOUNDARY_HZ in minHz..maxHz &&
        ticks.none { abs(it - F0_BOUNDARY_HZ) < 0.5f }
    ) {
        ticks += F0_BOUNDARY_HZ
    }
    return ticks.sorted()
}

/** Shown instead of the chart when the configured F0 range makes no sense. */
@Composable
private fun RangeErrorNotice(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ) {
            Text(
                text = stringResource(R.string.pitch_range_invalid),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
private const val WINDOW_SECONDS = 3.0
private const val REPLAY_WINDOW_SECONDS = 3.0
