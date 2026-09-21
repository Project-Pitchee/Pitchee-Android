package io.rovly.pitchee.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.rovly.pitchee.R
import java.text.BreakIterator
import java.util.Locale
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

    Box(modifier = Modifier.fillMaxSize()) {
        PitchChart(
            points = state.points,
            replayWindowEndSeconds = state.replayWindowEndSeconds,
            playbackPositionSeconds = state.playbackPositionSeconds,
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.pitch_title),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.pitch_subtitle),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            val passage = passagePreferences.activePassage(languageCode, selectedPassageIndex)
            val passageText = passage.text(languageCode)
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
    modifier: Modifier = Modifier,
) {
    val lineColor = MaterialTheme.colorScheme.onSurface
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val surfaceColor = MaterialTheme.colorScheme.background
    val averageColor = MaterialTheme.colorScheme.primary
    val feminineColor = Color(0xFFFFB6C1)
    val masculineColor = Color(0xFF6495ED)
    val windowSeconds = if (replayWindowEndSeconds != null) {
        REPLAY_WINDOW_SECONDS
    } else {
        WINDOW_SECONDS
    }
    val latestTimestamp = replayWindowEndSeconds
        ?: points.lastOrNull()?.timestampSeconds
        ?: 0.0
    val firstTimestamp = latestTimestamp - windowSeconds
    val averageF0 = points.asSequence()
        .filter { it.timestampSeconds >= firstTimestamp }
        .mapNotNull { it.f0Hz }
        .average()
        .takeIf { it.isFinite() }
        ?.toFloat()
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

        fun yFraction(hz: Float): Float =
            ((hz - MIN_HZ) / (MAX_HZ - MIN_HZ)).coerceIn(0f, 1f)

        fun yDp(hz: Float) = plotBottom - plotHeight * yFraction(hz)

        Canvas(modifier = Modifier.fillMaxSize()) {
            val plotTopPx = plotTop.toPx()
            val plotBottomPx = plotBottom.toPx()
            val plotLeftPx = plotLeft.toPx()
            val plotHeightPx = (plotBottomPx - plotTopPx).coerceAtLeast(1f)
            fun yFor(hz: Float): Float = plotBottomPx - yFraction(hz) * plotHeightPx

            val thresholdY = yFor(165f)
            drawRect(
                color = feminineColor.copy(alpha = 0.20f),
                topLeft = Offset.Zero,
                size = Size(size.width, thresholdY),
            )
            drawRect(
                color = masculineColor.copy(alpha = 0.20f),
                topLeft = Offset(0f, thresholdY),
                size = Size(size.width, size.height - thresholdY),
            )
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        surfaceColor,
                        surfaceColor.copy(alpha = 0.82f),
                        surfaceColor.copy(alpha = 0f),
                    ),
                    startY = 0f,
                    endY = plotTopPx,
                ),
                topLeft = Offset.Zero,
                size = Size(size.width, plotTopPx),
            )

            listOf(100f, 165f, 230f, 350f).forEach { hz ->
                val y = yFor(hz)
                drawLine(
                    color = gridColor.copy(alpha = if (hz == 165f) 0.72f else 0.34f),
                    start = Offset(plotLeftPx, y),
                    end = Offset(size.width, y),
                    strokeWidth = if (hz == 165f) 2.5.dp.toPx() else 1.dp.toPx(),
                )
            }

            if (averageF0 != null) {
                val y = yFor(animatedAverageF0)
                drawLine(
                    color = averageColor.copy(alpha = 0.86f),
                    start = Offset(plotLeftPx, y),
                    end = Offset(size.width, y),
                    strokeWidth = 2.5.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }

            val voiced = points.filter {
                it.f0Hz != null && it.timestampSeconds >= firstTimestamp
            }
            val segments = mutableListOf<List<Offset>>()
            var currentSegment = mutableListOf<Offset>()
            var previousTime = Double.NEGATIVE_INFINITY
            voiced.forEach { point ->
                val hz = point.f0Hz ?: return@forEach
                val x = (plotLeftPx + (
                    (point.timestampSeconds - firstTimestamp) / windowSeconds
                    ).toFloat().coerceIn(0f, 1f) * (size.width - plotLeftPx))
                if (currentSegment.isNotEmpty() &&
                    point.timestampSeconds - previousTime > MAX_GAP_SECONDS
                ) {
                    segments += currentSegment.toList()
                    currentSegment = mutableListOf()
                }
                currentSegment += Offset(x, yFor(hz))
                previousTime = point.timestampSeconds
            }
            if (currentSegment.isNotEmpty()) segments += currentSegment.toList()

            segments.forEach { segment ->
                if (segment.size == 1) {
                    drawCircle(lineColor, radius = 2.2.dp.toPx(), center = segment.first())
                    return@forEach
                }
                val path = Path().apply {
                    moveTo(segment.first().x, segment.first().y)
                    for (index in 0 until segment.lastIndex) {
                        val current = segment[index]
                        val next = segment[index + 1]
                        quadraticTo(
                            current.x,
                            current.y,
                            (current.x + next.x) / 2f,
                            (current.y + next.y) / 2f,
                        )
                    }
                    lineTo(segment.last().x, segment.last().y)
                }
                drawPath(
                    path = path,
                    color = lineColor,
                    style = Stroke(
                        width = 3.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
                segment.forEach { point ->
                    drawCircle(lineColor, radius = 1.6.dp.toPx(), center = point)
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

        listOf(400f, 230f, 165f, 100f, 60f).forEach { hz ->
            Text(
                text = "%.0f".format(hz),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(y = yDp(hz) - 8.dp)
                    .padding(start = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = if (hz == 165f) averageColor else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (averageF0 != null) {
            Text(
                text = loc("平均 %.0f Hz", "Avg %.0f Hz").format(animatedAverageF0),
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

private const val MIN_HZ = 60f
private const val MAX_HZ = 400f
private const val WINDOW_SECONDS = 3.0
private const val REPLAY_WINDOW_SECONDS = 3.0
private const val MAX_GAP_SECONDS = 0.25
