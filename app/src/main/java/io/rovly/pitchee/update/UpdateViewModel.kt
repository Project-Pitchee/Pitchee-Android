package io.rovly.pitchee.update

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.rovly.pitchee.R
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data object UpToDate : UpdateUiState
    data class Available(val release: GitHubRelease) : UpdateUiState
    data class Downloading(
        val release: GitHubRelease,
        val progress: Float?,
    ) : UpdateUiState
    data class ReadyToInstall(
        val release: GitHubRelease,
        val file: File,
    ) : UpdateUiState
    data class Error(val message: String) : UpdateUiState
}

class UpdateViewModel(
    context: Context,
    private val client: GitHubReleaseClient = GitHubReleaseClient(context.applicationContext),
) : ViewModel() {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(
        UPDATE_PREFERENCES,
        Context.MODE_PRIVATE,
    )
    private val mutableState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    private val mutableAutoCheck = MutableStateFlow(
        preferences.getBoolean(KEY_AUTO_CHECK, true),
    )

    val state: StateFlow<UpdateUiState> = mutableState.asStateFlow()
    val autoCheck: StateFlow<Boolean> = mutableAutoCheck.asStateFlow()

    fun checkOnLaunch() {
        if (!mutableAutoCheck.value || isSnoozedForWeek()) {
            mutableState.value = UpdateUiState.Idle
            return
        }
        checkForUpdates(manual = false)
    }

    fun checkForUpdates(manual: Boolean = true) {
        if (!manual && !mutableAutoCheck.value) return
        if (!manual && isSnoozedForWeek()) {
            mutableState.value = UpdateUiState.Idle
            return
        }
        if (mutableState.value is UpdateUiState.Checking ||
            mutableState.value is UpdateUiState.Downloading
        ) {
            return
        }
        viewModelScope.launch {
            mutableState.value = UpdateUiState.Checking
            val result = runCatching {
                client.latestUpdate(appContext.appVersionName())
            }
            mutableState.value = result.fold(
                onSuccess = { release ->
                    when {
                        release == null -> UpdateUiState.UpToDate
                        !manual && release.tagName == notifiedReleaseTag() -> UpdateUiState.Idle
                        else -> {
                            if (!manual) {
                                preferences.edit()
                                    .putString(KEY_NOTIFIED_RELEASE, release.tagName)
                                    .apply()
                            }
                            UpdateUiState.Available(release)
                        }
                    }
                },
                onFailure = {
                    UpdateUiState.Error(appContext.getString(R.string.update_check_failed))
                },
            )
        }
    }

    fun setAutoCheck(enabled: Boolean) {
        mutableAutoCheck.value = enabled
        preferences.edit().putBoolean(KEY_AUTO_CHECK, enabled).apply()
    }

    fun dismissUpdate(snoozeForWeek: Boolean = false) {
        val release = (mutableState.value as? UpdateUiState.Available)?.release ?: return
        preferences.edit()
            .putString(KEY_NOTIFIED_RELEASE, release.tagName)
            .apply()
        if (snoozeForWeek) snoozeUpdatesForWeek()
        mutableState.value = UpdateUiState.Idle
    }

    fun snoozeUpdatesForWeek() {
        preferences.edit()
            .putLong(
                KEY_SNOOZE_UNTIL,
                System.currentTimeMillis() + SNOOZE_DURATION_MILLIS,
            )
            .apply()
    }

    fun hideUpdate() {
        if (mutableState.value is UpdateUiState.Available) {
            mutableState.value = UpdateUiState.Idle
        }
    }

    fun download(release: GitHubRelease) {
        if (mutableState.value is UpdateUiState.Downloading) return
        preferences.edit().remove(KEY_NOTIFIED_RELEASE).apply()
        viewModelScope.launch {
            mutableState.value = UpdateUiState.Downloading(release, null)
            val result = runCatching {
                downloadApk(release)
            }
            mutableState.value = result.fold(
                onSuccess = { file -> UpdateUiState.ReadyToInstall(release, file) },
                onFailure = {
                    UpdateUiState.Error(appContext.getString(R.string.update_download_failed))
                },
            )
        }
    }

    fun markInstallLaunched() {
        preferences.edit().remove(KEY_NOTIFIED_RELEASE).apply()
        mutableState.value = UpdateUiState.Idle
    }

    private fun notifiedReleaseTag(): String? =
        preferences.getString(KEY_NOTIFIED_RELEASE, null)

    private fun isSnoozedForWeek(): Boolean {
        val until = preferences.getLong(KEY_SNOOZE_UNTIL, 0L)
        if (until > System.currentTimeMillis()) return true
        if (until > 0L) preferences.edit().remove(KEY_SNOOZE_UNTIL).apply()
        return false
    }

    private suspend fun downloadApk(release: GitHubRelease): File = withContext(Dispatchers.IO) {
        val directory = File(appContext.cacheDir, UPDATE_DIRECTORY).apply {
            check(mkdirs() || isDirectory) { appContext.getString(R.string.error_update_cache_create) }
        }
        val target = File(directory, "pitchee-${release.tagName.safeFileName()}.apk")
        val temporary = File(directory, "${target.name}.part")
        val connection = (URL(release.apkUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 60_000
            setRequestProperty("User-Agent", "Pitchee-Android")
        }
        try {
            if (connection.responseCode !in 200..299) {
                error(appContext.getString(R.string.error_update_download_http, connection.responseCode))
            }
            val totalBytes = connection.contentLengthLong.takeIf { it > 0L }
            var downloadedBytes = 0L
            var lastProgressPercent = -1
            connection.inputStream.use { input ->
                FileOutputStream(temporary).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloadedBytes += read
                        val progress = totalBytes?.let {
                            (downloadedBytes.toFloat() / it).coerceIn(0f, 1f)
                        }
                        val percent = progress?.let { (it * 100f).toInt() } ?: -1
                        if (percent != lastProgressPercent) {
                            lastProgressPercent = percent
                            mutableState.value = UpdateUiState.Downloading(release, progress)
                        }
                    }
                }
            }
            if (target.exists()) target.delete()
            check(temporary.renameTo(target)) { appContext.getString(R.string.error_update_save) }
            target
        } finally {
            connection.disconnect()
            if (temporary.exists()) temporary.delete()
        }
    }

    private fun String.safeFileName(): String =
        replace(Regex("[^A-Za-z0-9._-]"), "_")

    companion object {
        private const val UPDATE_PREFERENCES = "update_preferences"
        private const val KEY_AUTO_CHECK = "auto_check"
        private const val KEY_NOTIFIED_RELEASE = "notified_release"
        private const val KEY_SNOOZE_UNTIL = "snooze_until"
        private const val SNOOZE_DURATION_MILLIS = 7L * 24L * 60L * 60L * 1000L
        private const val UPDATE_DIRECTORY = "updates"

        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer { UpdateViewModel(context.applicationContext) }
        }
    }
}

private fun Context.appVersionName(): String =
    runCatching {
        packageManager.getPackageInfo(packageName, 0).versionName
    }.getOrNull().orEmpty().ifBlank { "1.0" }
