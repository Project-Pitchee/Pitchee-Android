package io.rovly.pitchee.update

import io.rovly.pitchee.update.isNewerVersion
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionComparisonTest {
    @Test
    fun comparesStableVersions() {
        assertTrue(isNewerVersion("1.0.0", "v1.0.1"))
        assertFalse(isNewerVersion("1.2.0", "v1.1.9"))
        assertFalse(isNewerVersion("1.2.0", "v1.2.0"))
        assertTrue(isNewerVersion("1.2", "v1.2.1"))
    }

    @Test
    fun comparesNightlyVersionsAndStableTransitions() {
        assertTrue(isNewerVersion("nightly-20260917-2200", "nightly-20260918-2200"))
        assertFalse(isNewerVersion("nightly-20260918-2200", "nightly-20260917-2200"))
        assertTrue(isNewerVersion("1.0.0", "nightly-20260918-2200"))
        assertTrue(isNewerVersion("nightly-20260918-2200", "v1.1.0"))
    }
}
