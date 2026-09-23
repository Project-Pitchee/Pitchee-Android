package io.rovly.pitchee.update

import android.os.Build
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import io.rovly.pitchee.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

data class GitHubRelease(
    val tagName: String,
    val displayName: String,
    val notes: String,
    val publishedAt: String,
    val isPrerelease: Boolean,
    val apkUrl: String,
)

class GitHubReleaseClient(
    private val context: android.content.Context,
    private val repository: String = "project-pitchee/Pitchee-Android",
) {
    suspend fun latestUpdate(currentVersionName: String): GitHubRelease? =
        withContext(Dispatchers.IO) {
            fetchReleases().firstOrNull { release ->
                releaseMatchesChannel(currentVersionName, release) &&
                    isNewerVersion(currentVersionName, release.tagName)
            }
        }

    private fun fetchReleases(): List<GitHubRelease> {
        val connection = (
            URL("https://api.github.com/repos/$repository/releases?per_page=30")
                .openConnection() as HttpURLConnection
            ).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 20_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "Pitchee-Android")
            }
        try {
            if (connection.responseCode !in 200..299) {
                throw IOException(
                    context.getString(R.string.github_update_check_failed, connection.responseCode),
                )
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val array = JSONArray(body)
            return buildList {
                for (index in 0 until array.length()) {
                    val release = array.optJSONObject(index) ?: continue
                    if (release.optBoolean("draft")) continue
                    val apkAsset = release.optJSONArray("assets")
                        ?.firstApkAsset()
                        ?: continue
                    add(
                        GitHubRelease(
                            tagName = release.optString("tag_name"),
                            displayName = release.optString("name")
                                .ifBlank { release.optString("tag_name") },
                            notes = release.optString("body"),
                            publishedAt = release.optString("published_at")
                                .ifBlank { release.optString("created_at") },
                            isPrerelease = release.optBoolean("prerelease"),
                            apkUrl = apkAsset,
                        )
                    )
                }
            }.sortedByDescending { it.publishedAt }
        } finally {
            connection.disconnect()
        }
    }

    private fun JSONArray.firstApkAsset(): String? {
        val assets = buildList {
            for (index in 0 until length()) {
                val asset = optJSONObject(index) ?: continue
                val name = asset.optString("name")
                if (!name.endsWith(".apk", ignoreCase = true)) continue
                val url = asset.optString("browser_download_url")
                if (url.isNotBlank()) add(name to url)
            }
        }
        return selectApkAsset(assets, Build.SUPPORTED_ABIS.toList())
    }
}

internal fun selectApkAsset(
    assets: List<Pair<String, String>>,
    supportedAbis: List<String>,
): String? {
    val abiSpecific = assets.firstOrNull { (name, _) ->
        supportedAbis.any { abi -> name.contains(abi, ignoreCase = true) }
    }
    return abiSpecific?.second ?: assets.firstOrNull { (name, _) ->
        name.contains("universal", ignoreCase = true)
    }?.second ?: assets.firstOrNull()?.second
}

internal fun releaseMatchesChannel(
    currentVersionName: String,
    release: GitHubRelease,
): Boolean {
    val nightlyChannel = currentVersionName
        .trim()
        .removePrefix("v")
        .startsWith(NIGHTLY_PREFIX)
    return release.isPrerelease == nightlyChannel
}

internal fun isNewerVersion(currentVersionName: String, releaseTag: String): Boolean {
    val current = currentVersionName.trim().removePrefix("v")
    val remote = releaseTag.trim().removePrefix("v")
    if (remote == current) return false

    val currentNightly = current.startsWith(NIGHTLY_PREFIX)
    val remoteNightly = remote.startsWith(NIGHTLY_PREFIX)
    if (remoteNightly != currentNightly) return true
    if (currentNightly && remoteNightly) return remote > current
    return compareVersions(remote, current) > 0
}

private fun compareVersions(left: String, right: String): Int {
    val leftParts = left.substringBefore('-').split('.')
    val rightParts = right.substringBefore('-').split('.')
    val count = maxOf(leftParts.size, rightParts.size)
    repeat(count) { index ->
        val leftValue = leftParts.getOrNull(index)?.toIntOrNull() ?: 0
        val rightValue = rightParts.getOrNull(index)?.toIntOrNull() ?: 0
        if (leftValue != rightValue) return leftValue.compareTo(rightValue)
    }
    return 0
}

private const val NIGHTLY_PREFIX = "nightly-"
