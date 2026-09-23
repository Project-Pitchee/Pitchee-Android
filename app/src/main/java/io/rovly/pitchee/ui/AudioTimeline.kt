package io.rovly.pitchee.ui

import android.media.MediaPlayer
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.rovly.pitchee.R
import io.rovly.pitchee.data.F0Window
import io.rovly.pitchee.data.FeminineTimeline
import io.rovly.pitchee.data.RecordedAudio
import io.rovly.pitchee.data.SpeechSegmentScore
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

private val ScoreColors = listOf(
    Color(0xFFD55362),
    Color(0xFFE47A36),
    Color(0xFFD9B53D),
    Color(0xFF57A773),
    Color(0xFF168C80),
)

private data class F0Sample(
    val seconds: Double,
    val hz: Float,
    val segmentIndex: Int = 0,
    val runIndex: Int = 0,
)

private const val F0_SAMPLE_STEP_SECONDS = 0.004
private const val F0_MAXIMUM_HZ = 600f
private const val F0_MINIMUM_DISPLAY_HZ = 300f
private const val F0_MAXIMUM_GAP_SECONDS = 0.35

@Composable
internal fun DisabledAudioPlayerButton(modifier: Modifier = Modifier) {
    val darkTheme = isSystemInDarkTheme()
    FilledIconButton(
        onClick = {},
        enabled = false,
        modifier = modifier.size(52.dp),
        colors = IconButtonDefaults.filledIconButtonColors(
            disabledContainerColor = if (darkTheme) {
                Color(0xFF20344F)
            } else {
                Color(0xFFD6E6FF)
            },
            disabledContentColor = if (darkTheme) {
                Color(0xFF8C98AA)
            } else {
                Color(0xFF7C8799)
            },
        ),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_play),
            contentDescription = stringResource(R.string.audio_unavailable),
        )
    }
}

internal fun feminineScoreColor(score: Double): Color = when {
    score < 20.0 -> ScoreColors[0]
    score < 40.0 -> ScoreColors[1]
    score < 60.0 -> ScoreColors[2]
    score < 80.0 -> ScoreColors[3]
    else -> ScoreColors[4]
}

@Composable
internal fun LiveWaveform(
    amplitudes: List<Float>,
    modifier: Modifier = Modifier,
    color: Color? = null,
) {
    val barColor = color ?: MaterialTheme.colorScheme.primary
    val centerLineColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
    Canvas(modifier = modifier) {
        val spacing = 5.dp.toPx()
        val maxBars = (size.width / spacing).toInt().coerceAtLeast(1)
        val visible = amplitudes.takeLast(maxBars)
        val centerY = size.height / 2f
        drawLine(
            color = centerLineColor,
            start = Offset(0f, centerY),
            end = Offset(size.width, centerY),
            strokeWidth = 1.dp.toPx(),
        )
        visible.forEachIndexed { index, amplitude ->
            val height = (amplitude.coerceIn(0f, 1f) * size.height * 0.82f)
                .coerceAtLeast(2.dp.toPx())
            val recency = index.toFloat() / max(visible.lastIndex, 1)
            val x = size.width - (visible.size - index - 0.5f) * spacing
            drawLine(
                color = barColor.copy(alpha = 0.22f + 0.72f * recency),
                start = Offset(x, centerY - height / 2f),
                end = Offset(x, centerY + height / 2f),
                strokeWidth = 2.4.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
internal fun RecordedAudioTimeline(
    audio: RecordedAudio,
    f0Windows: List<F0Window> = emptyList(),
    segmentScores: List<SpeechSegmentScore> = emptyList(),
    metricsTimeline: FeminineTimeline? = null,
    modifier: Modifier = Modifier,
) {
    val darkTheme = isSystemInDarkTheme()
    val playerAccent = if (darkTheme) Color(0xFF7DB3FF) else Color(0xFF3B82F6)
    val playerAccentContent = if (darkTheme) Color(0xFF102443) else Color.White
    val playerBackground = if (darkTheme) Color(0xFF172A46) else Color(0xFFE4F0FF)
    val playerContent = if (darkTheme) Color(0xFFEAF3FF) else Color(0xFF16345C)

    val player = remember(audio.file) { MediaPlayer() }
    var isPrepared by remember(audio.file) { mutableStateOf(false) }
    var isPlaying by remember(audio.file) { mutableStateOf(false) }
    var positionMs by remember(audio.file) { mutableLongStateOf(0L) }
    var durationMs by remember(audio.file) { mutableIntStateOf(0) }
    var expanded by rememberSaveable(audio.file.absolutePath) { mutableStateOf(false) }
    var playWhenReady by remember(audio.file) { mutableStateOf(false) }
    var scrubbing by remember(audio.file) { mutableStateOf(false) }
    var resumeAfterScrub by remember(audio.file) { mutableStateOf(false) }
    val windowSeconds = min(2.0, audio.durationSeconds).coerceAtLeast(0.05)

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    LaunchedEffect(player, audio.file) {
        runCatching {
            player.reset()
            player.setDataSource(audio.file.absolutePath)
            player.setOnPreparedListener {
                durationMs = it.duration
                isPrepared = true
            }
            player.setOnCompletionListener {
                isPlaying = false
                positionMs = it.duration.toLong()
            }
            player.prepareAsync()
        }.onFailure {
            isPrepared = false
        }
    }

    LaunchedEffect(isPlaying, isPrepared) {
        while (isPlaying && isPrepared) {
            withFrameNanos { }
            positionMs = player.currentPosition.toLong()
        }
    }

    LaunchedEffect(isPrepared, playWhenReady) {
        if (!isPrepared || !playWhenReady) return@LaunchedEffect
        if (positionMs >= durationMs - 100) {
            player.seekTo(0)
            positionMs = 0L
        }
        player.start()
        isPlaying = true
        playWhenReady = false
    }

    fun seekTo(seconds: Double) {
        if (!isPrepared) return
        val target = (seconds.coerceIn(0.0, audio.durationSeconds) * 1000.0).toInt()
        player.seekTo(target)
        positionMs = target.toLong()
    }

    fun togglePlayback() {
        if (!isPrepared) return
        if (isPlaying) {
            player.pause()
            isPlaying = false
        } else {
            if (positionMs >= durationMs - 100) seekTo(0.0)
            player.start()
            isPlaying = true
        }
    }

    val cornerRadius by animateDpAsState(
        targetValue = if (expanded) 20.dp else 26.dp,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 260f),
        label = "audio-player-corner",
    )
    val containerColor by animateColorAsState(
        targetValue = if (expanded) playerBackground else playerAccent,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 260f),
        label = "audio-player-color",
    )
    val sizeModifier = if (expanded) Modifier.fillMaxWidth() else Modifier.size(52.dp)

    if (isPrepared) {
        Surface(
            modifier = modifier
                .animateContentSize(
                    animationSpec = spring(dampingRatio = 0.76f, stiffness = 220f),
                )
                .then(sizeModifier)
                .clickable(enabled = !expanded) {
                    expanded = true
                    playWhenReady = true
                },
            shape = RoundedCornerShape(cornerRadius),
            color = containerColor,
            contentColor = if (expanded) playerContent else playerAccentContent,
            shadowElevation = if (expanded) 0.dp else 3.dp,
        ) {
            if (expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FilledIconButton(
                            onClick = ::togglePlayback,
                            modifier = Modifier.size(44.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = playerAccent,
                                contentColor = playerAccentContent,
                            ),
                        ) {
                            Icon(
                                painter = painterResource(
                                    if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                                ),
                                contentDescription = stringResource(
                                    if (isPlaying) R.string.pause else R.string.play,
                                ),
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Slider(
                            value = (positionMs / 1000f)
                                .coerceIn(0f, audio.durationSeconds.toFloat()),
                            onValueChange = { seconds ->
                                if (!scrubbing) {
                                    scrubbing = true
                                    resumeAfterScrub = isPlaying
                                    if (isPlaying) {
                                        player.pause()
                                        isPlaying = false
                                    }
                                }
                                seekTo(seconds.toDouble())
                            },
                            onValueChangeFinished = {
                                scrubbing = false
                                if (resumeAfterScrub) {
                                    player.start()
                                    isPlaying = true
                                }
                                resumeAfterScrub = false
                            },
                            valueRange = 0f..audio.durationSeconds.toFloat().coerceAtLeast(0.01f),
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = playerAccent,
                                activeTrackColor = playerAccent,
                                inactiveTrackColor = playerContent.copy(alpha = 0.18f),
                            ),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(248.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth(),
                        ) {
                            BasicWaveform(
                                audio = audio,
                                segmentScores = segmentScores,
                                positionSeconds = positionMs / 1000.0,
                                windowSeconds = windowSeconds,
                            )
                        }
                        F0Track(
                            f0Windows = f0Windows,
                            positionSeconds = positionMs / 1000.0,
                            windowSeconds = windowSeconds,
                            accent = playerAccent,
                            thresholdColor = playerContent.copy(alpha = 0.34f),
                            thresholdLabelColor = playerContent.copy(alpha = 0.38f),
                            metricsTimeline = metricsTimeline,
                            segmentScores = segmentScores,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier.size(52.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_play),
                        contentDescription = stringResource(R.string.expand_and_play),
                    )
                }
            }
        }
    } else {
        DisabledAudioPlayerButton(modifier)
    }
}

@Composable
private fun F0Track(
    f0Windows: List<F0Window>,
    positionSeconds: Double,
    windowSeconds: Double,
    accent: Color,
    thresholdColor: Color,
    thresholdLabelColor: Color,
    metricsTimeline: FeminineTimeline?,
    segmentScores: List<SpeechSegmentScore>,
    modifier: Modifier = Modifier,
) {
    val pink = Color(0xFFFFB6C1)
    val blue = Color(0xFF6495ED)
    val allF0Samples = remember(f0Windows, segmentScores) {
        f0Windows.mapNotNull { window ->
            val hz = window.f0Hz?.toFloat() ?: return@mapNotNull null
            val seconds = (window.startSeconds + window.endSeconds) / 2.0
            val segmentIndex = if (segmentScores.isEmpty()) {
                0
            } else {
                segmentScores.indexOfFirst { segment ->
                    seconds >= segment.startSeconds && seconds < segment.endSeconds
                }
            }
            if (segmentIndex < 0) return@mapNotNull null
            F0Sample(
                seconds = seconds,
                hz = hz,
                segmentIndex = segmentIndex,
            )
        }.sortedBy { it.seconds }
    }
    val smoothF0Path = remember(allF0Samples) {
        smoothF0Samples(allF0Samples, F0_MAXIMUM_GAP_SECONDS)
    }
    val targetF0 = remember(positionSeconds, smoothF0Path) {
        f0SampleAt(positionSeconds, smoothF0Path)?.hz
    }

    BoxWithConstraints(modifier = modifier) {
        val thresholdHz = 165f
        val maximumHz = remember(smoothF0Path) {
            val peakHz = smoothF0Path.maxOfOrNull { it.hz } ?: F0_MINIMUM_DISPLAY_HZ
            (ceil(peakHz / 50f) * 50f).coerceIn(F0_MINIMUM_DISPLAY_HZ, F0_MAXIMUM_HZ)
        }
        val thresholdY = maxHeight * (1f - thresholdHz / maximumHz)
        val viewportStart = positionSeconds - windowSeconds / 2.0

        Canvas(Modifier.fillMaxSize()) {
            drawLine(
                color = thresholdColor,
                start = Offset(0f, thresholdY.toPx()),
                end = Offset(size.width, thresholdY.toPx()),
                strokeWidth = 1.5.dp.toPx(),
            )

            fun xFor(seconds: Double): Float =
                ((seconds - viewportStart) / windowSeconds).toFloat() * size.width

            fun yFor(hz: Float): Float =
                size.height - hz.coerceIn(0f, maximumHz) / maximumHz * size.height

            val samples = smoothF0Path.filter { sample ->
                sample.seconds >= viewportStart - F0_MAXIMUM_GAP_SECONDS &&
                    sample.seconds <= viewportStart + windowSeconds + F0_MAXIMUM_GAP_SECONDS
            }

            fun drawSegment(
                start: Offset,
                end: Offset,
                startHz: Float,
                endHz: Float,
            ) {
                val startAbove = startHz > thresholdHz
                val endAbove = endHz > thresholdHz
                when {
                    startAbove && endAbove -> drawLine(
                        color = pink,
                        start = start,
                        end = end,
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round,
                    )

                    !startAbove && !endAbove -> drawLine(
                        color = blue,
                        start = start,
                        end = end,
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round,
                    )

                    else -> {
                        val crossingFraction = ((thresholdHz - startHz) / (endHz - startHz))
                            .coerceIn(0f, 1f)
                        val crossing = Offset(
                            x = start.x + (end.x - start.x) * crossingFraction,
                            y = yFor(thresholdHz),
                        )
                        val upperStart = if (startAbove) start else crossing
                        val upperEnd = if (startAbove) crossing else end
                        val lowerStart = if (startAbove) crossing else start
                        val lowerEnd = if (startAbove) end else crossing
                        drawLine(
                            color = pink,
                            start = upperStart,
                            end = upperEnd,
                            strokeWidth = 3.dp.toPx(),
                            cap = StrokeCap.Round,
                        )
                        drawLine(
                            color = blue,
                            start = lowerStart,
                            end = lowerEnd,
                            strokeWidth = 3.dp.toPx(),
                            cap = StrokeCap.Round,
                        )
                    }
                }
            }

            samples.zipWithNext().forEach { (previous, current) ->
                if (previous.segmentIndex != current.segmentIndex ||
                    previous.runIndex != current.runIndex
                ) {
                    return@forEach
                }
                drawSegment(
                    start = Offset(xFor(previous.seconds), yFor(previous.hz)),
                    end = Offset(xFor(current.seconds), yFor(current.hz)),
                    startHz = previous.hz,
                    endHz = current.hz,
                )
            }

            drawLine(
                color = accent,
                start = Offset(size.width / 2f, 0f),
                end = Offset(size.width / 2f, size.height),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }

        Text(
            text = stringResource(R.string.timeline_threshold_165),
            modifier = Modifier.offset(y = thresholdY + 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = thresholdLabelColor,
        )

        if (targetF0 != null) {
            val hz = targetF0
            val color = if (hz > thresholdHz) pink else blue
            val currentY = maxHeight * (1f - hz.coerceIn(0f, maximumHz) / maximumHz)
            val labelY = (currentY + 20.dp).coerceIn(0.dp, maxHeight - 36.dp)
            Surface(
                modifier = Modifier.offset(
                    x = maxWidth / 2 + 12.dp,
                    y = labelY,
                ),
                shape = RoundedCornerShape(4.dp),
                color = Color(0xFF253247),
                contentColor = color,
                shadowElevation = 0.dp,
                tonalElevation = 0.dp,
            ) {
                Text(
                    text = stringResource(R.string.timeline_f0_value, hz),
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        val playbackMetrics = metricsTimeline?.pointAt(positionSeconds)
        val playbackScore = playbackMetrics?.score
            ?: segmentScores.firstOrNull { segment ->
                positionSeconds >= segment.startSeconds &&
                    positionSeconds < segment.endSeconds
            }?.score
        val animatedPlaybackScore by animateFloatAsState(
            targetValue = (playbackScore?.toFloat() ?: 0f).coerceIn(0f, 100f),
            animationSpec = spring(
                dampingRatio = 0.88f,
                stiffness = 220f,
                visibilityThreshold = 0.05f,
            ),
            label = "playback-score-position",
        )
        val waveformHeight = minOf(136.dp, maxHeight)
        val waveformTop = maxHeight - waveformHeight
        val markerHeight = minOf(36.dp, waveformHeight)
        val markerWidth = 58.dp
        val scoreProgress = animatedPlaybackScore / 100f
        val markerCenterY = waveformTop + waveformHeight * (1f - scoreProgress)
        val markerY = (markerCenterY - markerHeight / 2f)
            .coerceIn(waveformTop, waveformTop + waveformHeight - markerHeight)
        val markerX = (
            maxWidth / 2 - markerWidth - 12.dp
            ).coerceAtLeast(0.dp)
        Surface(
            modifier = Modifier
                .offset(x = markerX, y = markerY)
                .width(markerWidth)
                .height(markerHeight),
            shape = RoundedCornerShape(4.dp),
            color = Color(0xFF253247),
            contentColor = Color(0xFFEAF3FF),
            shadowElevation = 0.dp,
            tonalElevation = 0.dp,
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column {
                    Text(
                        text = stringResource(
                            R.string.timeline_standard_value,
                            playbackMetrics?.vfpScore?.roundToInt() ?: "--",
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(
                            R.string.timeline_naturalness_value,
                            playbackMetrics?.naturalnessScore?.roundToInt() ?: "--",
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}


private fun smoothF0Samples(
    samples: List<F0Sample>,
    maximumGapSeconds: Double,
): List<F0Sample> {
    if (samples.size < 2) return samples

    val smoothed = mutableListOf<F0Sample>()
    var runStart = 0
    var runId = 0
    for (index in 1..samples.size) {
        val runEnded = index == samples.size ||
            samples[index].segmentIndex != samples[index - 1].segmentIndex ||
            samples[index].seconds - samples[index - 1].seconds > maximumGapSeconds
        if (!runEnded) continue

        val run = samples.subList(runStart, index)
        if (run.size == 1) {
            smoothed += run.first().copy(runIndex = runId)
        } else {
            val currentRunId = runId
            run.forEachIndexed { runIndex, current ->
                if (runIndex == 0) smoothed += current.copy(runIndex = currentRunId)
                if (runIndex == run.lastIndex) return@forEachIndexed

                val previous = run[maxOf(0, runIndex - 1)]
                val next = run[runIndex + 1]
                val afterNext = run[minOf(run.lastIndex, runIndex + 2)]
                val steps = ceil((next.seconds - current.seconds) / F0_SAMPLE_STEP_SECONDS)
                    .toInt()
                    .coerceAtLeast(1)
                repeat(steps) { stepIndex ->
                    val t = (stepIndex + 1f) / steps
                    val t2 = t * t
                    val t3 = t2 * t
                    val hz = 0.5f * (
                        2f * current.hz +
                            (-previous.hz + next.hz) * t +
                            (2f * previous.hz - 5f * current.hz + 4f * next.hz - afterNext.hz) * t2 +
                            (-previous.hz + 3f * current.hz - 3f * next.hz + afterNext.hz) * t3
                        )
                    smoothed += F0Sample(
                        seconds = current.seconds + (next.seconds - current.seconds) * t,
                        hz = hz.coerceIn(0f, F0_MAXIMUM_HZ),
                        segmentIndex = current.segmentIndex,
                        runIndex = currentRunId,
                    )
                }
            }
        }
        runStart = index
        runId++
    }
    return smoothed
}

private fun f0SampleAt(seconds: Double, samples: List<F0Sample>): F0Sample? =
    samples.minByOrNull { sample -> abs(sample.seconds - seconds) }
        ?.takeIf { sample -> abs(sample.seconds - seconds) <= 0.2 }

@Composable
private fun BasicWaveform(
    audio: RecordedAudio,
    segmentScores: List<SpeechSegmentScore>,
    positionSeconds: Double,
    windowSeconds: Double,
) {
    val waveform = audio.waveform
    if (waveform.isEmpty()) return

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(136.dp),
    ) {
        val duration = audio.durationSeconds.coerceAtLeast(0.001)
        val viewportStart = positionSeconds - windowSeconds / 2.0
        val viewportEnd = viewportStart + windowSeconds
        val centerY = size.height / 2f
        val targetBarSpacingPx = 2.dp.toPx()
        val targetBarCount = (size.width / targetBarSpacingPx)
            .roundToInt()
            .coerceAtLeast(1)
        val lastBucket = waveform.lastIndex
        val bucketStep = (waveform.size.toFloat() / targetBarCount)
            .roundToInt()
            .coerceAtLeast(1)
        val firstCandidate = floor(viewportStart / duration * lastBucket)
            .toInt()
            .coerceIn(0, lastBucket)
        val firstBucket = firstCandidate / bucketStep * bucketStep
        val lastCandidate = ceil(viewportEnd / duration * lastBucket)
            .toInt()
            .coerceIn(0, lastBucket)
        val lastBucketToDraw = (
            lastCandidate / bucketStep * bucketStep + bucketStep
        ).coerceAtMost(lastBucket)

        var bucket = firstBucket
        while (bucket <= lastBucketToDraw) {
            val time = if (lastBucket == 0) 0.0 else bucket.toDouble() / lastBucket * duration
            val x = ((time - viewportStart) / windowSeconds).toFloat() * size.width
            if (x >= -targetBarSpacingPx && x <= size.width + targetBarSpacingPx) {
                val amplitude = waveform[bucket].coerceIn(0f, 1f)
                val score = segmentScores.firstOrNull { segment ->
                    time >= segment.startSeconds && time < segment.endSeconds
                }?.score
                val barColor = score?.let(::feminineScoreColor) ?: Color(0xFF9CA3AF)
                val halfHeight = if (score == null) {
                    1.2.dp.toPx()
                } else {
                    (sqrt(amplitude) * size.height * 0.48f)
                        .coerceAtLeast(2.5.dp.toPx())
                }
                drawLine(
                    color = barColor.copy(alpha = if (score == null) 0.42f else 1f),
                    start = Offset(x, centerY - halfHeight),
                    end = Offset(x, centerY + halfHeight),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
            bucket += bucketStep
        }
    }
}
