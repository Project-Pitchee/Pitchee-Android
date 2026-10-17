package io.rovly.pitchee.feature.spectrum

import io.rovly.pitchee.ui.theme.PitcheeColorTokens
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.rovly.pitchee.R
import io.rovly.pitchee.ui.common.ScreenTitle
import kotlin.math.log10
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay

@Composable
internal fun RealtimeSpectrumScreen(onBack: (() -> Unit)? = null) {
    val context = LocalContext.current
    val factory = remember { SpectrumViewModel.factory(context.applicationContext) }
    val viewModel: SpectrumViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (onBack != null) {
        BackHandler(onBack = onBack)
    }
    val lastPageMessage = stringResource(R.string.spectrum_last_page)
    val rewindMessageFormat = stringResource(R.string.spectrum_rewind_seconds)
    var transientRewindMessage by remember { mutableStateOf<String?>(null) }
    var permissionDenied by remember { mutableStateOf(false) }
    val replayTailEndSeconds = state.replayTailEndSeconds
    val playbackPositionSeconds = state.playbackPositionSeconds
    val rewindWindowMessage = if (
        state.mode == SpectrumMode.REPLAYING &&
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

    LaunchedEffect(state.mode) {
        if (state.mode != SpectrumMode.REPLAYING) {
            transientRewindMessage = null
        }
    }
    LaunchedEffect(transientRewindMessage) {
        if (transientRewindMessage != null) {
            delay(1500)
            transientRewindMessage = null
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionDenied = !granted
        if (granted) viewModel.start()
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.stop() }
    }

    fun toggleAnalysis() {
        transientRewindMessage = null
        if (state.mode == SpectrumMode.IDLE) {
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
        } else {
            viewModel.toggleAnalysis()
        }
    }

    fun rewind() {
        val feedback = viewModel.rewindFiveSeconds() ?: return
        transientRewindMessage = if (feedback.isLastPage) lastPageMessage else null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        SpectrumChart(
            frames = state.frames,
            playbackPositionSeconds = state.playbackPositionSeconds,
            replayWindowEndSeconds = state.replayWindowEndSeconds,
            mode = state.mode,
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 24.dp),
        ) {
            ScreenTitle(
                title = stringResource(R.string.spectrum_title),
                subtitle = stringResource(R.string.spectrum_subtitle),
                onBack = onBack,
            )
        }

        if (state.frames.isEmpty()) {
            Text(
                text = if (state.mode == SpectrumMode.PREPARING) {
                    stringResource(R.string.spectrum_preparing)
                } else {
                    stringResource(R.string.spectrum_no_signal)
                },
                modifier = Modifier.align(Alignment.Center),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.End,
        ) {
            rewindStatus?.let { message ->
                Text(
                    text = message,
                    modifier = Modifier.padding(bottom = 10.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                )
            }
            state.error?.let { error ->
                Text(
                    text = error,
                    modifier = Modifier.padding(bottom = 10.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.End,
                )
            }
            if (permissionDenied) {
                Text(
                    text = stringResource(R.string.record_permission_denied),
                    modifier = Modifier.padding(bottom = 10.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.End,
                )
            }
            SpectrumControls(
                mode = state.mode,
                onToggle = ::toggleAnalysis,
                onRewind = ::rewind,
            )
        }

    }
}

@Composable
private fun SpectrumControls(
    mode: SpectrumMode,
    onToggle: () -> Unit,
    onRewind: () -> Unit,
) {
    val showToolbar = mode != SpectrumMode.IDLE
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
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 280f),
        label = "spectrum-controls-width",
    )
    val controlHeight by animateDpAsState(
        targetValue = if (toolbarExpanded) 60.dp else 72.dp,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 280f),
        label = "spectrum-controls-height",
    )
    val controlPadding by animateDpAsState(
        targetValue = if (toolbarExpanded) 6.dp else 12.dp,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 280f),
        label = "spectrum-controls-padding",
    )
    val darkTheme = isSystemInDarkTheme()
    val containerColor by animateColorAsState(
        targetValue = if (toolbarExpanded) {
            if (darkTheme) MaterialTheme.colorScheme.surfaceContainerHigh else DARK_TOOLBAR_BACKGROUND
        } else {
            if (darkTheme) MaterialTheme.colorScheme.primary else DARK_TOOLBAR_COLLAPSED_BACKGROUND
        },
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 280f),
        label = "spectrum-controls-color",
    )
    val contentColor by animateColorAsState(
        targetValue = if (toolbarExpanded) {
            if (darkTheme) MaterialTheme.colorScheme.onSurface else DARK_TOOLBAR_CONTENT
        } else {
            if (darkTheme) MaterialTheme.colorScheme.onPrimary else DARK_TOOLBAR_COLLAPSED_CONTENT
        },
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 280f),
        label = "spectrum-controls-content",
    )
    val resumeAnalysis = mode == SpectrumMode.REPLAYING ||
        mode == SpectrumMode.ANALYSIS_PAUSED

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
                ControlIconButton(
                    iconRes = R.drawable.ic_rewind_arrow,
                    contentDescription = stringResource(R.string.spectrum_rewind_5),
                    onClick = onRewind,
                    animateOnClick = true,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = controlPadding),
                )
            }

            AnimatedContent(
                targetState = when {
                    mode == SpectrumMode.PREPARING -> null
                    mode == SpectrumMode.IDLE || resumeAnalysis -> R.drawable.ic_play
                    else -> R.drawable.ic_pause
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
                label = "spectrum-center-icon",
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
                                enabled = mode != SpectrumMode.PREPARING,
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
                                        mode == SpectrumMode.IDLE ->
                                            R.string.spectrum_start_monitoring
                                        resumeAnalysis ->
                                            R.string.spectrum_resume_analysis
                                        else -> R.string.spectrum_pause
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
private fun ControlIconButton(
    iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    animateOnClick: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var spinCount by remember { mutableIntStateOf(0) }
    val rotation by animateFloatAsState(
        targetValue = if (animateOnClick) spinCount * -360f else 0f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 260f),
        label = "spectrum-seek-rotation",
    )
    FilledIconButton(
        onClick = {
            if (animateOnClick) spinCount++
            onClick()
        },
        modifier = modifier.size(48.dp),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            modifier = Modifier.graphicsLayer { rotationZ = rotation },
        )
    }
}

@Composable
private fun SpectrumChart(
    frames: List<SpectrumFramePoint>,
    playbackPositionSeconds: Double?,
    replayWindowEndSeconds: Double?,
    mode: SpectrumMode,
    modifier: Modifier = Modifier,
) {
    val latestTimestamp = frames.lastOrNull()?.timestampSeconds ?: 0.0
    val targetWindowEnd = replayWindowEndSeconds ?: latestTimestamp
    val windowEndAnimation = remember { Animatable(targetWindowEnd.toFloat()) }
    // Do not animate the render window between replay and live analysis. Each
    // animation frame re-renders the full bitmap and creates a visible backlog.
    LaunchedEffect(targetWindowEnd) {
        windowEndAnimation.snapTo(targetWindowEnd.toFloat())
    }
    val windowEnd = windowEndAnimation.value.toDouble()
    val windowStart = windowEnd - SPECTRUM_WINDOW_SECONDS
    val palette = remember { spectrumPaletteDark() }
    val colorLut = remember(palette) { spectrumColorLut(palette) }
    val renderChannel = remember { Channel<SpectrumRenderRequest>(Channel.CONFLATED) }
    var image by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(renderChannel) {
        for (request in renderChannel) {
            val nextImage = withContext(Dispatchers.Default) {
                createSpectrumBitmap(
                    request.frames,
                    request.windowEnd,
                    request.colorLut,
                )
            }
            if (nextImage != null) {
                image = nextImage
            }
        }
    }
    LaunchedEffect(frames, windowEnd, colorLut) {
        renderChannel.trySend(SpectrumRenderRequest(frames, windowEnd, colorLut))
    }
    DisposableEffect(renderChannel) {
        onDispose { renderChannel.close() }
    }
    val gridColor = Color.White.copy(alpha = 0.18f)
    val axisTextColor = PitcheeColorTokens.SpectrumAxis
    val playheadColor = MaterialTheme.colorScheme.primary
    val chartDescription = if (frames.isEmpty()) {
        stringResource(R.string.spectrum_accessibility_empty)
    } else {
        stringResource(R.string.spectrum_accessibility_active, latestTimestamp)
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .semantics { contentDescription = chartDescription },
    ) {
        val plotLeft = 44.dp
        val plotTop = 104.dp
        val plotHeight = (maxHeight - plotTop).coerceAtLeast(1.dp)

        fun yFraction(hz: Float): Float =
            (log10(hz / SPECTRUM_MIN_HZ) / log10(SPECTRUM_MAX_HZ / SPECTRUM_MIN_HZ))
                .toFloat()
                .coerceIn(0f, 1f)

        fun yDp(hz: Float) = plotTop + plotHeight - plotHeight * yFraction(hz)

        Canvas(modifier = Modifier.fillMaxSize()) {
            val plotLeftPx = plotLeft.toPx()
            val plotTopPx = plotTop.toPx()
            val plotWidthPx = (size.width - plotLeftPx).coerceAtLeast(1f)
            val plotHeightPx = (size.height - plotTopPx).coerceAtLeast(1f)
            fun yFor(hz: Float): Float = plotTopPx + plotHeightPx - yFraction(hz) * plotHeightPx

            drawRect(
                color = SPECTRUM_DARK_BACKGROUND,
                topLeft = Offset(0f, plotTopPx),
                size = Size(size.width, size.height - plotTopPx),
            )
            val spectrumImage = image
            if (spectrumImage != null) {
                drawImage(
                    image = spectrumImage,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(spectrumImage.width, spectrumImage.height),
                    dstOffset = IntOffset(plotLeftPx.roundToInt(), plotTopPx.roundToInt()),
                    dstSize = IntSize(plotWidthPx.roundToInt(), plotHeightPx.roundToInt()),
                    filterQuality = FilterQuality.High,
                )
            }
            FREQUENCY_GRID_HZ.forEach { hz ->
                val y = yFor(hz)
                drawLine(
                    color = gridColor,
                    start = Offset(plotLeftPx, y),
                    end = Offset(size.width, y),
                    strokeWidth = if (hz == 1000f) 1.6.dp.toPx() else 1.dp.toPx(),
                )
            }
            if (playbackPositionSeconds != null) {
                val x = plotLeftPx + (
                    (playbackPositionSeconds - windowStart) / SPECTRUM_WINDOW_SECONDS
                    ).toFloat().coerceIn(0f, 1f) * plotWidthPx
                drawLine(
                    color = playheadColor,
                    start = Offset(x, plotTopPx),
                    end = Offset(x, size.height),
                    strokeWidth = 2.dp.toPx(),
                )
            }
        }

        FREQUENCY_GRID_HZ.forEach { hz ->
            Text(
                text = formatFrequencyAxis(hz),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(y = yDp(hz) - 8.dp)
                    .padding(start = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = axisTextColor,
            )
        }
    }
}

private data class SpectrumRenderRequest(
    val frames: List<SpectrumFramePoint>,
    val windowEnd: Double,
    val colorLut: IntArray,
)

private fun createSpectrumBitmap(
    frames: List<SpectrumFramePoint>,
    windowEnd: Double,
    colorLut: IntArray,
): ImageBitmap? {
    val lastVisibleIndex = upperBoundByTimestamp(frames, windowEnd)
    if (lastVisibleIndex < 0) return null

    val width = SPECTRUM_BITMAP_WIDTH
    val height = SPECTRUM_BITMAP_HEIGHT
    val visibleCount = (lastVisibleIndex + 1).coerceAtMost(width)
    val firstVisibleIndex = lastVisibleIndex - visibleCount + 1
    val columnOffset = width - visibleCount
    val pixels = IntArray(width * height).apply { fill(colorLut[0]) }
    val bandIndices = IntArray(height) { y ->
        (y.toFloat() / (height - 1).coerceAtLeast(1) *
            SpectrumViewModel.SPECTRUM_BAND_COUNT)
            .roundToInt()
            .coerceIn(0, SpectrumViewModel.SPECTRUM_BAND_COUNT - 1)
    }

    for (column in 0 until visibleCount) {
        val bands = frames[firstVisibleIndex + column].bands
        val x = columnOffset + column
        for (y in 0 until height) {
            val decibels = bands[bandIndices[y]]
            val normalized = ((decibels - SPECTRUM_MIN_DB) / SPECTRUM_DB_RANGE)
                .coerceIn(0f, 1f)
            pixels[y * width + x] = colorLut[(normalized * 255f).roundToInt()]
        }
    }

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    return bitmap.asImageBitmap()
}

private fun upperBoundByTimestamp(
    frames: List<SpectrumFramePoint>,
    timestamp: Double,
): Int {
    var low = 0
    var high = frames.size
    while (low < high) {
        val middle = (low + high) ushr 1
        if (frames[middle].timestampSeconds <= timestamp) {
            low = middle + 1
        } else {
            high = middle
        }
    }
    return low - 1
}


private fun spectrumPaletteDark(): SpectrumPalette = SpectrumPalette(
    low = SPECTRUM_DARK_BACKGROUND,
    blue = PitcheeColorTokens.SpectrumBlue,
    cyan = PitcheeColorTokens.SpectrumCyan,
    pink = PitcheeColorTokens.SpectrumPink,
    high = PitcheeColorTokens.SpectrumHigh,
)

private fun spectrumColorLut(palette: SpectrumPalette): IntArray =
    IntArray(256) { index ->
        spectrumColor(index / 255f, palette).toArgb()
    }

private fun spectrumColor(
    value: Float,
    palette: SpectrumPalette,
): Color = when {
    value < 0.28f -> lerp(palette.low, palette.blue, value / 0.28f)
    value < 0.50f -> lerp(
        palette.blue,
        palette.cyan,
        (value - 0.28f) / 0.22f,
    )
    value < 0.68f -> lerp(
        palette.cyan,
        palette.pink,
        (value - 0.50f) / 0.18f,
    )
    else -> lerp(
        palette.pink,
        palette.high,
        (value - 0.68f) / 0.32f,
    )
}

private data class SpectrumPalette(
    val low: Color,
    val blue: Color,
    val cyan: Color,
    val pink: Color,
    val high: Color,
)

private fun formatFrequencyAxis(hz: Float): String =
    if (hz >= 1000f) "%.0fk".format(hz / 1000f) else "%.0f".format(hz)

private val FREQUENCY_GRID_HZ = listOf(8000f, 4000f, 2000f, 1000f, 500f, 250f, 125f)
private val SPECTRUM_DARK_BACKGROUND = PitcheeColorTokens.SpectrumBackground
private val DARK_TOOLBAR_BACKGROUND = PitcheeColorTokens.DarkToolbarBackground
private val DARK_TOOLBAR_CONTENT = PitcheeColorTokens.DarkToolbarContent
private val DARK_TOOLBAR_COLLAPSED_BACKGROUND = PitcheeColorTokens.DarkToolbarCollapsedBackground
private val DARK_TOOLBAR_COLLAPSED_CONTENT = PitcheeColorTokens.DarkToolbarCollapsedContent
private const val SPECTRUM_WINDOW_SECONDS = 5.0
private const val SPECTRUM_BITMAP_WIDTH = 313
private const val SPECTRUM_BITMAP_HEIGHT = 256
private const val SPECTRUM_MIN_DB = -90f
private const val SPECTRUM_DB_RANGE = 75f
private const val SPECTRUM_MIN_HZ = 40f
private const val SPECTRUM_MAX_HZ = 8000f
