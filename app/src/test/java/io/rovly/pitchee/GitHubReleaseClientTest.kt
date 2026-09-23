package io.rovly.pitchee

import io.rovly.pitchee.update.GitHubRelease
import io.rovly.pitchee.update.isNewerVersion
import io.rovly.pitchee.update.releaseMatchesChannel
import io.rovly.pitchee.update.selectApkAsset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubReleaseClientTest {
    @Test
    fun keepsStableAndNightlyChannelsSeparate() {
        val stable = release("v1.1.0", prerelease = false)
        val nightly = release("nightly-20260920-1831", prerelease = true)

        assertTrue(releaseMatchesChannel("1.0", stable))
        assertFalse(releaseMatchesChannel("1.0", nightly))
        assertTrue(releaseMatchesChannel("nightly-20260919-1827", nightly))
        assertFalse(releaseMatchesChannel("nightly-20260919-1827", stable))
    }

    @Test
    fun selectsDeviceAbiBeforeUniversalApk() {
        val assets = listOf(
            "app-arm64-v8a-release.apk" to "arm64",
            "app-x86_64-release.apk" to "x86",
            "app-universal-release.apk" to "universal",
        )

        assertEquals("arm64", selectApkAsset(assets, listOf("arm64-v8a")))
        assertEquals("x86", selectApkAsset(assets, listOf("x86_64")))
        assertEquals(
            "universal",
            selectApkAsset(assets, listOf("armeabi-v7a")),
        )
    }

    @Test
    fun comparesStableAndNightlyVersions() {
        assertTrue(isNewerVersion("1.0", "nightly-20260920-1831"))
        assertTrue(isNewerVersion("1.0.0", "v1.0.1"))
        assertFalse(isNewerVersion("1.0.1", "v1.0.0"))
    }

    private fun release(tag: String, prerelease: Boolean) = GitHubRelease(
        tagName = tag,
        displayName = tag,
        notes = "",
        publishedAt = "",
        isPrerelease = prerelease,
        apkUrl = "https://example.com/app.apk",
    )
}
