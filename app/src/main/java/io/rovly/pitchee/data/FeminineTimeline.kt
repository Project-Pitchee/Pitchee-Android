package io.rovly.pitchee.data

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

data class FeminineTimelinePoint(
    val score: Float,
    val vfpScore: Float,
    val naturalnessScore: Float,
    val f0Hz: Float?,
)

class FeminineTimeline private constructor(
    private val points: Array<FeminineTimelinePoint?>,
    val durationSeconds: Double,
) {
    val binDurationSeconds: Double
        get() = if (points.isEmpty()) 0.0 else durationSeconds / points.size

    fun scoreAt(seconds: Double): Float? = pointAt(seconds)?.score

    fun pointAt(seconds: Double): FeminineTimelinePoint? {
        if (seconds !in 0.0..durationSeconds || points.isEmpty()) return null
        val index = (seconds / durationSeconds * points.size)
            .toInt()
            .coerceIn(0, points.lastIndex)
        return points[index]
    }

    internal fun pointAtBin(index: Int): FeminineTimelinePoint? = points.getOrNull(index)

    val size: Int
        get() = points.size

    companion object {
        private const val DEFAULT_BIN_COUNT = 2_048

        fun from(
            result: PitcheeResult,
            durationSeconds: Double,
            binCount: Int = DEFAULT_BIN_COUNT,
        ): FeminineTimeline {
            require(durationSeconds > 0.0)
            require(binCount > 0)

            val vfp = WeightedBins(binCount)
            result.vfp.windows.forEach { window ->
                vfp.add(window.startSeconds, window.endSeconds, window.standardScore, durationSeconds)
            }

            val naturalness = WeightedBins(binCount)
            result.naturalness.windows.forEach { window ->
                naturalness.add(window.startSeconds, window.endSeconds, window.score, durationSeconds)
            }

            val f0 = WeightedBins(binCount)
            result.f0.windows.forEach { window ->
                window.f0Hz?.let {
                    f0.add(window.startSeconds, window.endSeconds, it, durationSeconds)
                }
            }

            val points = Array<FeminineTimelinePoint?>(binCount) { index ->
                val centerSeconds = (index + 0.5) * durationSeconds / binCount
                val vfpScore = vfp.average(index) ?: return@Array null
                if (!isSpeech(centerSeconds, result.vad.segments)) return@Array null

                val naturalnessScore = naturalness.average(index)
                    ?: result.naturalness.score
                val f0Hz = f0.average(index)
                FeminineTimelinePoint(
                    score = calculateCompositeScore(
                        vfpScore = vfpScore,
                        naturalnessScore = naturalnessScore,
                        f0Hz = f0Hz,
                    ).toFloat(),
                    vfpScore = vfpScore.toFloat(),
                    naturalnessScore = naturalnessScore.toFloat(),
                    f0Hz = f0Hz?.toFloat(),
                )
            }
            return FeminineTimeline(points, durationSeconds)
        }

        private fun isSpeech(seconds: Double, segments: List<SpeechSegment>): Boolean =
            segments.any { seconds >= it.startSeconds && seconds < it.endSeconds }

        private fun calculateCompositeScore(
            vfpScore: Double,
            naturalnessScore: Double,
            f0Hz: Double?,
        ): Double {
            val standard = vfpScore.coerceIn(0.0, 100.0)
            val naturalness = naturalnessScore.coerceIn(0.0, 100.0)
            if (f0Hz == null || f0Hz <= 0.0) return standard

            val standardRatio = standard / 100.0
            val naturalnessRatio = ((naturalness - 40.0) / 50.0).coerceIn(0.0, 1.0)
            val f0Ratio = ((f0Hz - 110.0) / 90.0).coerceIn(0.0, 1.0)
            val base = 100.0 * (
                0.50 * standardRatio +
                    0.20 * naturalnessRatio +
                    0.15 * f0Ratio +
                    0.15 * standardRatio * naturalnessRatio * f0Ratio
                )

            val cap = when {
                f0Hz > 165.0 && naturalness < 50.0 -> 30.0
                f0Hz <= 165.0 && naturalness >= 50.0 -> 59.0
                f0Hz <= 165.0 && naturalness < 50.0 -> 20.0
                f0Hz > 165.0 && naturalness >= 50.0 && standard < 50.0 -> 59.0
                else -> null
            }
            var score = base
            if (f0Hz > 165.0 && naturalness > 80.0 && standard > 50.0) {
                val strength = minOf(
                    (f0Hz - 165.0) / 25.0,
                    (naturalness - 80.0) / 20.0,
                    (standard - 50.0) / 30.0,
                    1.0,
                )
                score = max(score, 60.0 + 40.0 * strength)
            }
            if (cap != null) score = min(score, cap)
            return score.coerceIn(0.0, 100.0)
        }
    }
}

private class WeightedBins(private val count: Int) {
    private val sums = DoubleArray(count)
    private val weights = DoubleArray(count)

    fun add(
        startSeconds: Double,
        endSeconds: Double,
        value: Double,
        durationSeconds: Double,
    ) {
        if (endSeconds <= startSeconds || value.isNaN()) return
        val firstBin = floor(startSeconds / durationSeconds * count)
            .toInt()
            .coerceIn(0, count - 1)
        val lastBin = ceil(endSeconds / durationSeconds * count)
            .toInt()
            .coerceIn(firstBin + 1, count)
        val binDuration = durationSeconds / count

        for (bin in firstBin until lastBin) {
            val binStart = bin * binDuration
            val binEnd = binStart + binDuration
            val overlap = min(endSeconds, binEnd) - max(startSeconds, binStart)
            if (overlap <= 0.0) continue
            sums[bin] += value * overlap
            weights[bin] += overlap
        }
    }

    fun average(index: Int): Double? =
        if (weights[index] > 0.0) sums[index] / weights[index] else null
}
