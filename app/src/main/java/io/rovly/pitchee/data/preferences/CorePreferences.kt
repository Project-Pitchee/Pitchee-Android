package io.rovly.pitchee.data.preferences

import android.content.Context
import space.pitchee.core.PitcheeThresholds

/**
 * User-tunable Core parameters. These are exactly the values handed to
 * `pitchee_analyzer_create`, so no threshold is baked into the pipeline.
 */
class CorePreferences(context: Context) {
    private val appContext = context.applicationContext
    private val preferences =
        appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun thresholds(): PitcheeThresholds {
        val min = minF0Hz()
        val max = maxF0Hz().coerceAtLeast(min + F0_GAP_HZ)
        return PitcheeThresholds(
            intraOpThreads = threads(),
            minF0Hz = min,
            maxF0Hz = max,
            minConfidence = minConfidence(),
        )
    }

    fun threads(): Int = preferences
        .getInt(KEY_THREADS, PitcheeThresholds.DEFAULT_THREADS)
        .coerceIn(MIN_THREADS, maxThreads())

    fun minF0Hz(): Float = preferences
        .getFloat(KEY_MIN_F0_HZ, PitcheeThresholds.DEFAULT_MIN_F0_HZ)
        .coerceIn(MIN_F0_HZ, MAX_F0_HZ - F0_GAP_HZ)

    fun maxF0Hz(): Float = preferences
        .getFloat(KEY_MAX_F0_HZ, PitcheeThresholds.DEFAULT_MAX_F0_HZ)
        .coerceIn(MIN_F0_HZ + F0_GAP_HZ, MAX_F0_HZ)

    fun minConfidence(): Float = preferences
        .getFloat(KEY_MIN_CONFIDENCE, PitcheeThresholds.DEFAULT_MIN_CONFIDENCE)
        .coerceIn(MIN_CONFIDENCE, MAX_CONFIDENCE)

    fun setThreads(value: Int) {
        preferences.edit().putInt(KEY_THREADS, value.coerceIn(MIN_THREADS, maxThreads())).apply()
    }

    fun setMinF0Hz(value: Float) {
        preferences.edit().putFloat(KEY_MIN_F0_HZ, value.coerceIn(MIN_F0_HZ, MAX_F0_HZ)).apply()
    }

    fun setMaxF0Hz(value: Float) {
        preferences.edit().putFloat(KEY_MAX_F0_HZ, value.coerceIn(MIN_F0_HZ, MAX_F0_HZ)).apply()
    }

    fun setMinConfidence(value: Float) {
        preferences.edit()
            .putFloat(KEY_MIN_CONFIDENCE, value.coerceIn(MIN_CONFIDENCE, MAX_CONFIDENCE))
            .apply()
    }

    fun reset() {
        preferences.edit().clear().apply()
    }

    companion object {
        const val MIN_THREADS = 1
        const val MAX_THREADS = 8
        const val MIN_F0_HZ = 50f
        const val MAX_F0_HZ = 1000f
        const val F0_GAP_HZ = 20f
        const val MIN_CONFIDENCE = 0.3f
        const val MAX_CONFIDENCE = 0.99f

        fun maxThreads(): Int =
            Runtime.getRuntime().availableProcessors().coerceIn(MIN_THREADS, MAX_THREADS)

        private const val PREFERENCES_NAME = "core_preferences"
        private const val KEY_THREADS = "threads"
        private const val KEY_MIN_F0_HZ = "min_f0_hz"
        private const val KEY_MAX_F0_HZ = "max_f0_hz"
        private const val KEY_MIN_CONFIDENCE = "min_confidence"
    }
}
