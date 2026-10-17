package io.rovly.pitchee.data.history

import android.content.Context
import io.rovly.pitchee.R
import io.rovly.pitchee.data.model.PitcheeResult
import java.io.File
import java.util.Calendar
import org.json.JSONArray
import org.json.JSONObject

data class AnalysisHistoryEntry(
    val timestampMillis: Long,
    val finalScore: Double,
    val standardScore: Double,
    val naturalnessScore: Double,
    val meanF0Hz: Double?,
    val f0VariationHz: Double? = null,
    val inputSeconds: Double = 0.0,
    val pinned: Boolean = false,
    val audioFileName: String? = null,
    val resultFileName: String? = null,
    val scoreProfile: String? = null,
)

data class RealtimeF0HistoryEntry(
    val timestampMillis: Long,
    val meanF0Hz: Double,
)

class HistoryStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun analyses(): List<AnalysisHistoryEntry> =
        readArray(KEY_ANALYSES).objects().map { value ->
            AnalysisHistoryEntry(
                timestampMillis = value.optLong("timestamp"),
                finalScore = value.optDouble("final"),
                standardScore = value.optDouble("standard"),
                naturalnessScore = value.optDouble("naturalness"),
                meanF0Hz = value.optNullableDouble("f0"),
                f0VariationHz = value.optNullableDouble("f0_variation"),
                inputSeconds = value.optDouble("input_seconds", 0.0),
                pinned = value.optBoolean("pinned", false),
                audioFileName = value.optString("audio_file").takeIf { it.isNotBlank() },
                resultFileName = value.optString("result_file").takeIf { it.isNotBlank() },
                scoreProfile = value.optString("score_profile").takeIf { it.isNotBlank() },
            )
        }.sortedWith(
            compareByDescending<AnalysisHistoryEntry> { it.pinned }
                .thenByDescending { it.timestampMillis },
        )

    fun realtimeF0(): List<RealtimeF0HistoryEntry> =
        readArray(KEY_REALTIME_F0).objects().map { value ->
            RealtimeF0HistoryEntry(
                timestampMillis = value.optLong("timestamp"),
                meanF0Hz = value.optDouble("mean_f0"),
            )
        }.filter { it.meanF0Hz.isFinite() }.sortedByDescending { it.timestampMillis }

    fun averageF0ResetAtMillis(): Long = preferences.getLong(KEY_AVERAGE_F0_RESET_AT, 0L)

    fun openedDayTimestamps(): List<Long> =
        preferences.getStringSet(KEY_OPENED_DAYS, emptySet())
            .orEmpty()
            .mapNotNull { it.toLongOrNull() }
            .sorted()

    fun recordOpenedDay(nowMillis: Long = System.currentTimeMillis()) {
        val values = openedDayTimestamps().toMutableSet()
        values += startOfDay(nowMillis)
        preferences.edit()
            .putStringSet(KEY_OPENED_DAYS, values.map(Long::toString).toSet())
            .apply()
    }

    fun addAnalysis(
        entry: AnalysisHistoryEntry,
        result: PitcheeResult,
        sourceAudio: File,
    ): AnalysisHistoryEntry {
        val directory = File(appContext.filesDir, ANALYSIS_DIRECTORY).apply {
            check(mkdirs() || isDirectory) { appContext.getString(R.string.error_history_directory) }
        }
        val baseName = entry.timestampMillis.toString()
        val extension = sourceAudio.extension.takeIf { it.isNotBlank() } ?: "m4a"
        val audioFile = File(directory, "$baseName.$extension")
        val resultFile = File(directory, "$baseName.json")
        sourceAudio.copyTo(audioFile, overwrite = true)
        resultFile.writeText(result.rawJson)
        val storedEntry = entry.copy(
            audioFileName = audioFile.name,
            resultFileName = resultFile.name,
            scoreProfile = entry.scoreProfile ?: result.scoreProfile.jsonValue,
        )
        val values = analyses().toMutableList()
        values.add(0, storedEntry)
        writeArray(
            KEY_ANALYSES,
            JSONArray().apply {
                values.take(MAX_ENTRIES).forEach { put(it.toJson()) }
            },
        )
        return storedEntry
    }

    fun analysisAudioFile(entry: AnalysisHistoryEntry): File? =
        entry.audioFileName?.let { File(File(appContext.filesDir, ANALYSIS_DIRECTORY), it) }
            ?.takeIf { it.isFile && it.length() > 0L }

    fun analysisResult(entry: AnalysisHistoryEntry): PitcheeResult? =
        entry.resultFileName
            ?.let { File(File(appContext.filesDir, ANALYSIS_DIRECTORY), it) }
            ?.takeIf { it.isFile }
            ?.let { runCatching { PitcheeResult.fromJson(it.readText()) }.getOrNull() }

    fun setAnalysisPinned(timestampMillis: Long, pinned: Boolean) {
        val values = analyses().map { entry ->
            if (entry.timestampMillis == timestampMillis) entry.copy(pinned = pinned) else entry
        }
        writeArray(
            KEY_ANALYSES,
            JSONArray().apply {
                values.forEach { put(it.toJson()) }
            },
        )
    }

    fun removeAnalysis(timestampMillis: Long) {
        val values = analyses()
        val removed = values.firstOrNull { it.timestampMillis == timestampMillis }
        removed?.audioFileName?.let {
            File(File(appContext.filesDir, ANALYSIS_DIRECTORY), it).delete()
        }
        removed?.resultFileName?.let {
            File(File(appContext.filesDir, ANALYSIS_DIRECTORY), it).delete()
        }
        writeArray(
            KEY_ANALYSES,
            JSONArray().apply {
                values
                    .filterNot { it.timestampMillis == timestampMillis }
                    .forEach { put(it.toJson()) }
            },
        )
    }

    fun resetAverageF0(resetAtMillis: Long) {
        preferences.edit()
            .putLong(KEY_AVERAGE_F0_RESET_AT, resetAtMillis)
            .remove(KEY_REALTIME_F0)
            .apply()
    }

    fun addRealtimeF0(entry: RealtimeF0HistoryEntry) {
        val values = realtimeF0().toMutableList()
        values.add(0, entry)
        writeArray(
            KEY_REALTIME_F0,
            JSONArray().apply {
                values.take(MAX_ENTRIES).forEach { put(it.toJson()) }
            },
        )
    }

    private fun readArray(key: String): JSONArray =
        runCatching { JSONArray(preferences.getString(key, "[]")) }.getOrElse { JSONArray() }

    private fun writeArray(key: String, values: JSONArray) {
        preferences.edit().putString(key, values.toString()).apply()
    }

    private fun AnalysisHistoryEntry.toJson() = JSONObject().apply {
        put("timestamp", timestampMillis)
        put("final", finalScore)
        put("standard", standardScore)
        put("naturalness", naturalnessScore)
        if (meanF0Hz == null) put("f0", JSONObject.NULL) else put("f0", meanF0Hz)
        if (f0VariationHz == null) put("f0_variation", JSONObject.NULL) else put("f0_variation", f0VariationHz)
        put("input_seconds", inputSeconds)
        put("pinned", pinned)
        audioFileName?.let { put("audio_file", it) }
        resultFileName?.let { put("result_file", it) }
        scoreProfile?.let { put("score_profile", it) }
    }

    private fun RealtimeF0HistoryEntry.toJson() = JSONObject().apply {
        put("timestamp", timestampMillis)
        put("mean_f0", meanF0Hz)
    }

    private fun JSONObject.optNullableDouble(key: String): Double? =
        if (isNull(key)) null else optDouble(key).takeIf { it.isFinite() }

    private fun JSONArray.objects(): List<JSONObject> = buildList {
        for (index in 0 until length()) {
            optJSONObject(index)?.let(::add)
        }
    }

    private fun startOfDay(timestampMillis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = timestampMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private companion object {
        const val PREFERENCES_NAME = "history"
        const val KEY_ANALYSES = "analyses"
        const val KEY_REALTIME_F0 = "realtime_f0"
        const val KEY_AVERAGE_F0_RESET_AT = "average_f0_reset_at"
        const val KEY_OPENED_DAYS = "opened_days"
        const val ANALYSIS_DIRECTORY = "analysis_history"
        const val MAX_ENTRIES = 100
    }
}
