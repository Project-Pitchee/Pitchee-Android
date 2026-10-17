package io.rovly.pitchee.data

import io.rovly.pitchee.data.model.AudioMetrics
import io.rovly.pitchee.data.model.CompositeScore
import io.rovly.pitchee.data.model.F0Metrics
import io.rovly.pitchee.data.model.F0Window
import io.rovly.pitchee.data.model.FeminineTimeline
import io.rovly.pitchee.data.model.NaturalnessMetrics
import io.rovly.pitchee.data.model.NaturalnessWindow
import io.rovly.pitchee.data.model.PitcheeResult
import io.rovly.pitchee.data.model.SpeechSegment
import io.rovly.pitchee.data.model.VfpMetrics
import io.rovly.pitchee.data.model.VfpWindow
import io.rovly.pitchee.data.model.VoiceActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FeminineTimelineTest {
    @Test
    fun usesSourceTimelineWindowsAndMasksSilence() {
        val result = result()

        val timeline = FeminineTimeline.from(result, durationSeconds = 4.0, binCount = 400)

        assertEquals(20f, timeline.pointAt(1.5)?.vfpScore ?: -1f, 0.1f)
        assertEquals(80f, timeline.pointAt(2.5)?.vfpScore ?: -1f, 0.1f)
        assertEquals(170f, timeline.pointAt(2.5)?.f0Hz ?: -1f, 0.1f)
        assertNull(timeline.scoreAt(0.5))
        assertNull(timeline.scoreAt(3.5))
    }

    private fun result() = PitcheeResult(
        schemaVersion = 2,
        modelVersion = "test",
        audio = AudioMetrics(16_000, 1, 4.0, 4.0),
        vad = VoiceActivity(
            segmentCount = 1,
            speechSeconds = 2.0,
            sileroSegmentCount = 1,
            discardedBreathLikeCount = 0,
            trimmedSegmentCount = 0,
            segments = listOf(
                SpeechSegment(
                    startSeconds = 1.0,
                    endSeconds = 3.0,
                    speechStartSeconds = 0.0,
                    speechEndSeconds = 2.0,
                )
            ),
        ),
        f0 = F0Metrics(
            windowSeconds = 0.1,
            meanHz = 170.0,
            standardDeviationHz = 5.0,
            voicedFrameCount = 100,
            voicedWindowCount = 20,
            windows = listOf(
                F0Window(1.0, 2.0, 160.0),
                F0Window(2.0, 3.0, 170.0),
            ),
        ),
        vfp = VfpMetrics(
            standardScore = 50.0,
            windowCount = 2,
            windowDurationSeconds = 1.0,
            windows = listOf(
                VfpWindow(1.0, 2.0, 20.0),
                VfpWindow(2.0, 3.0, 80.0),
            ),
        ),
        naturalness = NaturalnessMetrics(
            score = 70.0,
            windowCount = 1,
            windowDurationSeconds = 2.0,
            windows = listOf(NaturalnessWindow(1.0, 3.0, 70.0)),
        ),
        composite = CompositeScore(
            baseScore = 50.0,
            finalScore = 50.0,
            cap = null,
            rule = "continuous",
            limited = false,
            boosted = false,
        ),
        rawJson = "{}",
    )
}
