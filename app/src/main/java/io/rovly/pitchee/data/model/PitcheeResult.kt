package io.rovly.pitchee.data.model

import org.json.JSONArray
import org.json.JSONObject
import space.pitchee.core.PitcheeScoreProfile

data class PitcheeResult(
    val schemaVersion: Int,
    val modelVersion: String,
    val audio: AudioMetrics,
    val vad: VoiceActivity,
    val f0: F0Metrics,
    val vfp: VfpMetrics,
    val naturalness: NaturalnessMetrics,
    val composite: CompositeScore,
    val rawJson: String,
    val scoreProfile: PitcheeScoreProfile = PitcheeScoreProfile.FEMINIZATION,
    val voiceQuality: VoiceQualityMetrics? = null,
) {
    companion object {
        fun fromJson(json: String): PitcheeResult {
            val root = JSONObject(json)
            val schemaVersion = root.getInt("schema_version")
            check(schemaVersion in MIN_SUPPORTED_SCHEMA_VERSION..MAX_SUPPORTED_SCHEMA_VERSION) {
                "Unsupported PitcheeCore schema: $schemaVersion"
            }
            return PitcheeResult(
                schemaVersion = schemaVersion,
                modelVersion = root.getString("model_version"),
                audio = root.getJSONObject("audio").toAudioMetrics(),
                vad = root.getJSONObject("vad").toVoiceActivity(),
                f0 = root.getJSONObject("f0").toF0Metrics(),
                vfp = root.getJSONObject("vfp").toVfpMetrics(),
                naturalness = root.getJSONObject("naturalness").toNaturalnessMetrics(),
                composite = root.getJSONObject("composite").toCompositeScore(),
                rawJson = json,
                scoreProfile = PitcheeScoreProfile.fromJsonValue(
                    root.optString("score_profile").takeIf { it.isNotBlank() },
                ),
                voiceQuality = root.optJSONObject("voice_quality")?.toVoiceQualityMetrics(),
            )
        }

        private const val MIN_SUPPORTED_SCHEMA_VERSION = 2
        private const val MAX_SUPPORTED_SCHEMA_VERSION = 4
    }
}

data class AudioMetrics(
    val sourceSampleRate: Int,
    val sourceChannels: Int,
    val inputSeconds: Double,
    val analyzedSeconds: Double,
)

data class VoiceActivity(
    val segmentCount: Int,
    val speechSeconds: Double,
    val sileroSegmentCount: Int,
    val discardedBreathLikeCount: Int,
    val trimmedSegmentCount: Int,
    val segments: List<SpeechSegment>,
)

data class SpeechSegment(
    val startSeconds: Double,
    val endSeconds: Double,
    val speechStartSeconds: Double,
    val speechEndSeconds: Double,
)

data class F0Metrics(
    val windowSeconds: Double,
    val meanHz: Double?,
    val standardDeviationHz: Double?,
    val voicedFrameCount: Int,
    val voicedWindowCount: Int,
    val windows: List<F0Window>,
)

data class F0Window(
    val startSeconds: Double,
    val endSeconds: Double,
    val f0Hz: Double?,
)

data class VfpMetrics(
    val standardScore: Double,
    val windowCount: Int,
    val windowDurationSeconds: Double,
    val windows: List<VfpWindow>,
)

data class VfpWindow(
    val startSeconds: Double,
    val endSeconds: Double,
    val standardScore: Double,
)

data class NaturalnessMetrics(
    val score: Double,
    val windowCount: Int,
    val windowDurationSeconds: Double,
    val windows: List<NaturalnessWindow>,
)

data class NaturalnessWindow(
    val startSeconds: Double,
    val endSeconds: Double,
    val score: Double,
)

data class CompositeScore(
    val baseScore: Double,
    val finalScore: Double,
    val cap: Double?,
    val rule: String,
    val limited: Boolean,
    val boosted: Boolean,
)

data class VoiceQualityMetrics(
    val algorithm: String,
    val hnrDb: Double?,
    val hnrWindowCount: Int,
    val hnrStdDb: Double?,
    val windowSeconds: Double,
    val windows: List<VoiceQualityWindow>,
)

data class VoiceQualityWindow(
    val startSeconds: Double,
    val endSeconds: Double,
    val hnrDb: Double?,
)

private fun JSONObject.toAudioMetrics() = AudioMetrics(
    sourceSampleRate = getInt("source_sample_rate"),
    sourceChannels = getInt("source_channels"),
    inputSeconds = getDouble("input_seconds"),
    analyzedSeconds = getDouble("analyzed_seconds"),
)

private fun JSONObject.toVoiceActivity() = VoiceActivity(
    segmentCount = getInt("segment_count"),
    speechSeconds = getDouble("speech_seconds"),
    sileroSegmentCount = getInt("silero_segment_count"),
    discardedBreathLikeCount = getInt("discarded_breath_like_count"),
    trimmedSegmentCount = getInt("trimmed_segment_count"),
    segments = getJSONArray("segments").mapObjects { it.toSpeechSegment() },
)

private fun JSONObject.toSpeechSegment() = SpeechSegment(
    startSeconds = getDouble("start_seconds"),
    endSeconds = getDouble("end_seconds"),
    speechStartSeconds = getDouble("speech_start_seconds"),
    speechEndSeconds = getDouble("speech_end_seconds"),
)

private fun JSONObject.toF0Metrics() = F0Metrics(
    windowSeconds = getDouble("window_seconds"),
    meanHz = nullableDouble("mean_hz"),
    standardDeviationHz = nullableDouble("standard_deviation_hz"),
    voicedFrameCount = getInt("voiced_frame_count"),
    voicedWindowCount = getInt("voiced_window_count"),
    windows = getJSONArray("windows").mapObjects { it.toF0Window() },
)

private fun JSONObject.toF0Window() = F0Window(
    startSeconds = getDouble("start_seconds"),
    endSeconds = getDouble("end_seconds"),
    f0Hz = nullableDouble("f0_hz"),
)

private fun JSONObject.toVfpMetrics() = VfpMetrics(
    standardScore = getDouble("vfp_standard_score"),
    windowCount = getInt("window_count"),
    windowDurationSeconds = getDouble("window_duration_seconds"),
    windows = getJSONArray("windows").mapObjects { it.toVfpWindow() },
)

private fun JSONObject.toVfpWindow() = VfpWindow(
    startSeconds = getDouble("start_seconds"),
    endSeconds = getDouble("end_seconds"),
    standardScore = getDouble("vfp_standard_score"),
)

private fun JSONObject.toNaturalnessMetrics() = NaturalnessMetrics(
    score = getDouble("score"),
    windowCount = getInt("window_count"),
    windowDurationSeconds = getDouble("window_duration_seconds"),
    windows = getJSONArray("windows").mapObjects { it.toNaturalnessWindow() },
)

private fun JSONObject.toNaturalnessWindow() = NaturalnessWindow(
    startSeconds = getDouble("start_seconds"),
    endSeconds = getDouble("end_seconds"),
    score = getDouble("score"),
)

private fun JSONObject.toCompositeScore() = CompositeScore(
    baseScore = getDouble("base_score"),
    finalScore = getDouble("final_score"),
    cap = nullableDouble("cap"),
    rule = getString("rule"),
    limited = getBoolean("limited"),
    boosted = getBoolean("boosted"),
)

private fun JSONObject.toVoiceQualityMetrics() = VoiceQualityMetrics(
    algorithm = getString("algorithm"),
    hnrDb = nullableDouble("hnr_db"),
    hnrWindowCount = getInt("hnr_window_count"),
    hnrStdDb = nullableDouble("hnr_std_db"),
    windowSeconds = getDouble("window_seconds"),
    windows = getJSONArray("windows").mapObjects { it.toVoiceQualityWindow() },
)

private fun JSONObject.toVoiceQualityWindow() = VoiceQualityWindow(
    startSeconds = getDouble("start_seconds"),
    endSeconds = getDouble("end_seconds"),
    hnrDb = nullableDouble("hnr_db"),
)

private fun JSONObject.nullableDouble(name: String): Double? =
    if (isNull(name)) null else getDouble(name)

private inline fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> =
    List(length()) { transform(getJSONObject(it)) }

fun PitcheeResult.hasSpeechSecondsAtLeast(minimumSeconds: Double): Boolean {
    require(minimumSeconds >= 0.0)
    return vad.speechSeconds >= minimumSeconds
}
