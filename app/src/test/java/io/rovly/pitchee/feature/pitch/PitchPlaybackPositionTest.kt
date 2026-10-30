package io.rovly.pitchee.feature.pitch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PitchPlaybackPositionTest {
    private val points = listOf(
        F0Point(timestampSeconds = 1.00, f0Hz = 180f, confidence = 0.98f),
        F0Point(timestampSeconds = 1.04, f0Hz = 205f, confidence = 0.97f),
        F0Point(timestampSeconds = 1.08, f0Hz = null, confidence = 0.2f),
        F0Point(timestampSeconds = 1.40, f0Hz = 190f, confidence = 0.96f),
    )

    @Test
    fun playbackReadoutUsesSampleAtOrBeforeCursor() {
        assertEquals(205f, f0AtPlaybackPosition(points, 1.06))
    }

    @Test
    fun playbackReadoutClearsAfterTheHoldWindow() {
        assertNull(f0AtPlaybackPosition(points, 1.24))
    }

    @Test
    fun playbackReadoutDoesNotCarryPitchAcrossSilence() {
        assertNull(f0AtPlaybackPosition(points, 1.08))
    }

    @Test
    fun pausedRealtimeStateKeepsTheLatestVoicedReadout() {
        val state = PitchUiState(paused = true, points = points)
        assertEquals(190f, state.currentHz)
    }

    @Test
    fun replayStateReadoutTracksThePlayhead() {
        val state = PitchUiState(
            replaying = true,
            playbackPositionSeconds = 1.06,
            points = points,
        )
        assertEquals(205f, state.currentHz)
    }
}
