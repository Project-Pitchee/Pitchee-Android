package io.rovly.pitchee.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.geometry.Offset
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.rovly.pitchee.R
import kotlin.math.log10
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun RealtimeSpectrumScreen() {
    val context = LocalContext.current
    val factory = remember { SpectrumViewModel.factory(context.applicationContext) }
    val viewModel: SpectrumViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lastPageMessage = stringResource(R.string.spectrum_last_page)
    val rewindMessageFormat = stringResource(R.string.spectrum_rewind_seconds)
    var rewindMessage by remember { mutableStateOf<String?>(null) }
    var permissionDenied by remember { mutableStateOf(false) }

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
        rewindMessage = null
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
        rewindMessage = if (feedback.isLastPage) {
            lastPageMessage
        } else {
            rewindMessageFormat.format(feedback.secondsFromLatest)
        }
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
            Text(
                text = stringResource(R.string.spectrum_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.spectrum_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            rewindMessage?.let { message ->
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
    val containerColor by animateColorAsState(
        targetValue = if (showToolbar) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.primary
        },
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 280f),
        label = "spectrum-controls-color",
    )
    val contentColor by animateColorAsState(
        targetValue = if (showToolbar) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onPrimary
        },
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 280f),
        label = "spectrum-controls-content",
    )

    Surface(
        modifier = Modifier.animateContentSize(
            animationSpec = spring(dampingRatio = 0.78f, stiffness = 280f),
        ),
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
        tonalElevation = 3.dp,
        shadowElevation = 5.dp,
    ) {
        AnimatedContent(
            targetState = showToolbar,
            transitionSpec = {
                val animation = spring<Float>(dampingRatio = 0.78f, stiffness = 320f)
                scaleIn(animation, initialScale = 0.72f) togetherWith
                    scaleOut(animation, targetScale = 0.72f)
            },
            label = "spectrum-controls",
        ) { expanded ->
            if (expanded) {
                SpectrumToolbarContent(
                    mode = mode,
                    onToggle = onToggle,
                    onRewind = onRewind,
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clickable(onClick = onToggle),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_play),
                        contentDescription = stringResource(R.string.spectrum_start_monitoring),
                        modifier = Modifier.size(30.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SpectrumToolbarContent(
    mode: SpectrumMode,
    onToggle: () -> Unit,
    onRewind: () -> Unit,
) {
    val resumeAnalysis = mode == SpectrumMode.REPLAYING ||
        mode == SpectrumMode.ANALYSIS_PAUSED
    val centerIcon = if (resumeAnalysis) R.drawable.ic_play else R.drawable.ic_pause
    Row(
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ControlIconButton(
            iconRes = R.drawable.ic_rewind,
            contentDescription = stringResource(R.string.spectrum_rewind_5),
            onClick = onRewind,
            animateOnClick = true,
        )
        AnimatedContent(
            targetState = if (mode == SpectrumMode.PREPARING) null else centerIcon,
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
            FilledIconButton(
                onClick = onToggle,
                enabled = mode != SpectrumMode.PREPARING,
                modifier = Modifier.size(48.dp),
            ) {
                if (icon == null) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.5.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Icon(
                        painter = painterResource(icon),
                        contentDescription = stringResource(
                            if (resumeAnalysis) {
                                R.string.spectrum_resume_analysis
                            } else {
                                R.string.spectrum_pause
                            },
                        ),
                    )
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
        modifier = Modifier.size(48.dp),
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
    var previousMode by remember { mutableStateOf(mode) }
    var animateWindowToLive by remember { mutableStateOf(false) }
    LaunchedEffect(mode) {
        if (mode == SpectrumMode.PREPARING && previousMode == SpectrumMode.REPLAYING) {
            animateWindowToLive = true
        } else if (mode == SpectrumMode.REPLAYING || mode == SpectrumMode.ANALYSIS_PAUSED) {
            animateWindowToLive = false
        }
        previousMode = mode
    }
    LaunchedEffect(targetWindowEnd, mode, animateWindowToLive) {
        if (mode == SpectrumMode.ANALYZING && animateWindowToLive) {
            windowEndAnimation.animateTo(
                targetValue = targetWindowEnd.toFloat(),
                animationSpec = spring(dampingRatio = 0.82f, stiffness = 220f),
            )
            animateWindowToLive = false
        } else {
            windowEndAnimation.snapTo(targetWindowEnd.toFloat())
        }
    }
    val windowEnd = windowEndAnimation.value.toDouble()
    val windowStart = windowEnd - SPECTRUM_WINDOW_SECONDS
    var image by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(frames, windowStart, windowEnd) {
        image = withContext(Dispatchers.Default) {
            createSpectrumBitmap(frames, windowStart, windowEnd)
        }
    }
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.62f)
    val playheadColor = MaterialTheme.colorScheme.primary

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
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

            val spectrumImage = image
            if (spectrumImage != null) {
                drawImage(
                    image = spectrumImage,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(spectrumImage.width, spectrumImage.height),
                    dstOffset = IntOffset(plotLeftPx.roundToInt(), plotTopPx.roundToInt()),
                    dstSize = IntSize(plotWidthPx.roundToInt(), plotHeightPx.roundToInt()),
                    filterQuality = FilterQuality.Low,
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun createSpectrumBitmap(
    frames: List<SpectrumFramePoint>,
    windowStart: Double,
    windowEnd: Double,
): ImageBitmap? {
    val visibleFrames = frames.filter {
        it.timestampSeconds in windowStart..windowEnd
    }
    if (visibleFrames.isEmpty()) return null
    val width = SPECTRUM_BITMAP_WIDTH
    val height = SPECTRUM_BITMAP_HEIGHT
    val pixels = IntArray(width * height)
    val firstTimestamp = visibleFrames.first().timestampSeconds
    val lastTimestamp = visibleFrames.last().timestampSeconds
    val frameSpan = (lastTimestamp - firstTimestamp).coerceAtLeast(0.001)
    var frameIndex = 0

    for (x in 0 until width) {
        val timestamp = windowStart + x.toDouble() / (width - 1) * SPECTRUM_WINDOW_SECONDS
        while (frameIndex < visibleFrames.lastIndex &&
            visibleFrames[frameIndex + 1].timestampSeconds <= timestamp
        ) {
            frameIndex++
        }
        val frame = visibleFrames[frameIndex]
        val hasFrame = timestamp >= firstTimestamp && timestamp <= lastTimestamp
        for (y in 0 until height) {
            val bandIndex = if (height <= 1) {
                0
            } else {
                (y.toFloat() / (height - 1) * frame.bands.lastIndex)
                    .roundToInt()
                    .coerceIn(0, frame.bands.lastIndex)
            }
            val decibels = frame.bands.getOrElse(bandIndex) { SPECTRUM_FLOOR_DB }
            val normalized = ((decibels - SPECTRUM_MIN_DB) / SPECTRUM_DB_RANGE)
                .coerceIn(0f, 1f)
            pixels[y * width + x] = if (hasFrame) {
                spectrumColor(normalized).toArgb()
            } else {
                SpectrumLow.toArgb()
            }
        }
    }

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    return bitmap.asImageBitmap()
}

private fun spectrumColor(value: Float): Color = when {
    value < 0.28f -> lerp(SpectrumLow, SpectrumBlue, value / 0.28f)
    value < 0.50f -> lerp(SpectrumBlue, SpectrumCyan, (value - 0.28f) / 0.22f)
    value < 0.68f -> lerp(SpectrumCyan, SpectrumPink, (value - 0.50f) / 0.18f)
    else -> lerp(SpectrumPink, SpectrumHigh, (value - 0.68f) / 0.32f)
}

private fun formatFrequencyAxis(hz: Float): String =
    if (hz >= 1000f) "%.0fk".format(hz / 1000f) else "%.0f".format(hz)

private val FREQUENCY_GRID_HZ = listOf(8000f, 4000f, 2000f, 1000f, 500f, 250f, 125f)
private val SpectrumLow = Color(0xFF111827)
private val SpectrumBlue = Color(0xFF2357D8)
private val SpectrumCyan = Color(0xFF19B9C8)
private val SpectrumPink = Color(0xFFE75480)
private val SpectrumHigh = Color(0xFFFFD9A0)
private const val SPECTRUM_WINDOW_SECONDS = 5.0
private const val SPECTRUM_BITMAP_WIDTH = 128
private const val SPECTRUM_BITMAP_HEIGHT = 56
private const val SPECTRUM_FLOOR_DB = -120f
private const val SPECTRUM_MIN_DB = -90f
private const val SPECTRUM_DB_RANGE = 75f
private const val SPECTRUM_MIN_HZ = 40f
private const val SPECTRUM_MAX_HZ = 8000f
