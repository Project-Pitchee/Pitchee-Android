package io.rovly.pitchee

import io.rovly.pitchee.update.isNewerVersion
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubReleaseClientTest {
    @Test
    fun comparesStableAndNightlyVersions() {
        assertTrue(isNewerVersion("1.0", "nightly-20260920-1831"))
        assertTrue(isNewerVersion("1.0.0", "v1.0.1"))
        assertFalse(isNewerVersion("1.0.1", "v1.0.0"))
    }
}
