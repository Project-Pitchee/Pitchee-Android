package io.rovly.pitchee.feature.analysis

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechIntervalTrackerTest {
    @Test
    fun overlappingSlidingWindowsAreCountedOnce() {
        val tracker = SpeechIntervalTracker()

        assertEquals(1.0, tracker.add(0.0, 1.0), 0.001)
        assertEquals(1.3, tracker.add(0.7, 1.3), 0.001)
        assertEquals(1.3, tracker.add(0.0, 1.3), 0.001)
    }

    @Test
    fun disjointSpeechSegmentsAreSummed() {
        val tracker = SpeechIntervalTracker()

        tracker.add(0.0, 1.0)
        assertEquals(2.25, tracker.add(1.5, 2.75), 0.001)
    }

    @Test
    fun newIntervalCanBridgeTwoExistingSegments() {
        val tracker = SpeechIntervalTracker()

        tracker.add(0.0, 0.5)
        tracker.add(1.0, 1.5)
        assertEquals(1.5, tracker.add(0.4, 1.1), 0.001)
    }
}
