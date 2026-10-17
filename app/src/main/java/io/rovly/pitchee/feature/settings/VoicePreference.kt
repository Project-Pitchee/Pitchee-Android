package io.rovly.pitchee.feature.settings

import android.content.Context
import space.pitchee.core.PitcheeScoreProfile

internal enum class VoicePreference(val rawValue: String) {
    MASCULINE("masculine"),
    FEMININE("feminine"),
    ;

    companion object {
        fun fromRaw(value: String?): VoicePreference? =
            entries.firstOrNull { it.rawValue == value }
    }
}

internal fun VoicePreference.toScoreProfile(): PitcheeScoreProfile = when (this) {
    VoicePreference.FEMININE -> PitcheeScoreProfile.FEMINIZATION
    VoicePreference.MASCULINE -> PitcheeScoreProfile.MASCULINIZATION
}

internal class VoicePreferencePreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun preference(): VoicePreference =
        VoicePreference.fromRaw(preferences.getString(KEY_VOICE_PREFERENCE, null))
            ?: VoicePreference.FEMININE

    fun setPreference(preference: VoicePreference) {
        preferences.edit()
            .putString(KEY_VOICE_PREFERENCE, preference.rawValue)
            .apply()
    }

    fun hasCompletedOnboarding(): Boolean =
        preferences.getInt(KEY_ONBOARDING_VERSION, 0) >= CURRENT_ONBOARDING_VERSION

    fun completeOnboarding(preference: VoicePreference) {
        preferences.edit()
            .putString(KEY_VOICE_PREFERENCE, preference.rawValue)
            .putInt(KEY_ONBOARDING_VERSION, CURRENT_ONBOARDING_VERSION)
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "voice_preferences"
        const val KEY_ONBOARDING_VERSION = "onboarding_completed_version"
        const val KEY_VOICE_PREFERENCE = "voice_preference"
        const val CURRENT_ONBOARDING_VERSION = 2
    }
}
