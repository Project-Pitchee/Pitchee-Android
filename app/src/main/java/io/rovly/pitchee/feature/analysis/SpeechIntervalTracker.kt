package io.rovly.pitchee.feature.analysis

/**
 * Merges VAD intervals from overlapping analysis windows. Only the union is
 * counted, so the same speech seen in two adjacent windows is counted once.
 */
internal class SpeechIntervalTracker {
    private val intervals = mutableListOf<SpeechInterval>()

    val totalSeconds: Double
        get() = intervals.sumOf { it.endSeconds - it.startSeconds }

    fun add(startSeconds: Double, endSeconds: Double): Double {
        if (!startSeconds.isFinite() ||
            !endSeconds.isFinite() ||
            endSeconds <= startSeconds
        ) {
            return totalSeconds
        }

        val merged = mutableListOf<SpeechInterval>()
        (intervals + SpeechInterval(startSeconds, endSeconds))
            .sortedBy { it.startSeconds }
            .forEach { candidate ->
                val previous = merged.lastOrNull()
                if (previous == null ||
                    candidate.startSeconds > previous.endSeconds + MERGE_EPSILON_SECONDS
                ) {
                    merged += candidate
                } else {
                    merged[merged.lastIndex] = SpeechInterval(
                        startSeconds = previous.startSeconds,
                        endSeconds = maxOf(previous.endSeconds, candidate.endSeconds),
                    )
                }
            }
        intervals.clear()
        intervals += merged
        return totalSeconds
    }

    private data class SpeechInterval(
        val startSeconds: Double,
        val endSeconds: Double,
    )

    private companion object {
        const val MERGE_EPSILON_SECONDS = 0.001
    }
}
