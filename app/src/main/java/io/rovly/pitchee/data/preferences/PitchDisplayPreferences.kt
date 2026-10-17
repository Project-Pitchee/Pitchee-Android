package io.rovly.pitchee.data.preferences

import android.content.Context

/** How the realtime F0 trace is drawn. */
enum class PitchChartStyle {
    CURVE,
    DOTS,
}

class PitchDisplayPreferences(context: Context) {
    private val appContext = context.applicationContext
    private val preferences =
        appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun style(): PitchChartStyle = when (
        preferences.getString(KEY_STYLE, PitchChartStyle.CURVE.name)
    ) {
        PitchChartStyle.DOTS.name -> PitchChartStyle.DOTS
        else -> PitchChartStyle.CURVE
    }

    fun setStyle(style: PitchChartStyle) {
        preferences.edit().putString(KEY_STYLE, style.name).apply()
    }

    /**
     * Silence longer than this breaks the trace into a new stroke, in
     * milliseconds. Shorter values cut sooner.
     */
    fun gapThresholdMs(): Int = preferences
        .getInt(KEY_GAP_MS, DEFAULT_GAP_MS)
        .coerceIn(MIN_GAP_MS, MAX_GAP_MS)

    fun setGapThresholdMs(value: Int) {
        preferences.edit()
            .putInt(KEY_GAP_MS, value.coerceIn(MIN_GAP_MS, MAX_GAP_MS))
            .apply()
    }

    fun resetGapThresholdMs() {
        preferences.edit().remove(KEY_GAP_MS).apply()
    }

    /** How many F0 frames per second the realtime analyser should target. */
    fun f0SampleRate(): Int {
        if (!preferences.contains(KEY_F0_RATE) &&
            preferences.contains(KEY_LEGACY_UPDATE_FPS)
        ) {
            // Keep the value selected while the old setting was named
            // "Update FPS" instead of silently resetting it during the rename.
            preferences.edit()
                .putInt(
                    KEY_F0_RATE,
                    preferences.getInt(KEY_LEGACY_UPDATE_FPS, DEFAULT_F0_RATE),
                )
                .remove(KEY_LEGACY_UPDATE_FPS)
                .apply()
        }
        return preferences
            .getInt(KEY_F0_RATE, DEFAULT_F0_RATE)
            .coerceIn(MIN_F0_RATE, MAX_F0_RATE)
    }

    fun setF0SampleRate(value: Int) {
        preferences.edit()
            .putInt(KEY_F0_RATE, value.coerceIn(MIN_F0_RATE, MAX_F0_RATE))
            .apply()
    }

    fun resetF0SampleRate() {
        preferences.edit().remove(KEY_F0_RATE).apply()
    }

    companion object {
        /** Rightmost slider position: cuts the trace at the shortest silence. */
        const val MIN_GAP_MS = 50

        /** Leftmost slider position: lets the trace run through long pauses. */
        const val MAX_GAP_MS = 1000
        const val DEFAULT_GAP_MS = 200
        const val MIN_F0_RATE = 20
        const val MAX_F0_RATE = 60
        const val DEFAULT_F0_RATE = 30

        private const val PREFERENCES_NAME = "pitch_display_preferences"
        private const val KEY_STYLE = "style"
        // v2: the range and default changed, so old stored values are ignored.
        private const val KEY_GAP_MS = "gap_ms_v2"
        private const val KEY_F0_RATE = "f0_sample_rate"
        private const val KEY_LEGACY_UPDATE_FPS = "update_fps"
    }
}
