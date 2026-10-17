package io.rovly.pitchee.data.model

import space.pitchee.core.PitcheeAnalyzer

data class SpeechSegmentScore(
    val startSeconds: Double,
    val endSeconds: Double,
    val score: Double,
)

fun scoreSpeechSegments(result: PitcheeResult): List<SpeechSegmentScore> =
    result.vad.segments.map { segment ->
        val standard = weightedAverage(
            windows = result.vfp.windows.map { window ->
                WeightedMetricWindow(
                    startSeconds = window.startSeconds,
                    endSeconds = window.endSeconds,
                    value = window.standardScore,
                )
            },
            segment = segment,
        ) ?: result.vfp.standardScore
        val naturalness = weightedAverage(
            windows = result.naturalness.windows.map { window ->
                WeightedMetricWindow(
                    startSeconds = window.startSeconds,
                    endSeconds = window.endSeconds,
                    value = window.score,
                )
            },
            segment = segment,
        ) ?: result.naturalness.score
        val f0 = weightedAverage(
            windows = result.f0.windows.map { window ->
                WeightedMetricWindow(
                    startSeconds = window.startSeconds,
                    endSeconds = window.endSeconds,
                    value = window.f0Hz,
                )
            },
            segment = segment,
        )
        SpeechSegmentScore(
            startSeconds = segment.startSeconds,
            endSeconds = segment.endSeconds,
            score = PitcheeAnalyzer.scoreValue(
                scoreProfile = result.scoreProfile,
                vfpStandardScore = standard,
                naturalnessScore = naturalness,
                f0Hz = f0,
            ),
        )
    }

private data class WeightedMetricWindow(
    val startSeconds: Double,
    val endSeconds: Double,
    val value: Double?,
)

private fun weightedAverage(
    windows: List<WeightedMetricWindow>,
    segment: SpeechSegment,
): Double? {
    var weightedSum = 0.0
    var totalWeight = 0.0
    windows.forEach { window ->
        val overlap = minOf(window.endSeconds, segment.endSeconds) -
            maxOf(window.startSeconds, segment.startSeconds)
        if (overlap <= 0.0) return@forEach
        val value = window.value ?: return@forEach
        weightedSum += value * overlap
        totalWeight += overlap
    }
    return if (totalWeight > 0.0) weightedSum / totalWeight else null
}
