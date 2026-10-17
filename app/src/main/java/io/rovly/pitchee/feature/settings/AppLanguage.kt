package io.rovly.pitchee.feature.settings

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

internal object AppLanguage {
    fun savedTag(context: Context): String? =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, null)
            ?.takeIf { it == LANGUAGE_ZH || it == LANGUAGE_EN }

    fun set(context: Context, tag: String) {
        val normalized = if (tag.startsWith("en")) LANGUAGE_EN else LANGUAGE_ZH
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, normalized)
            .apply()
        AppCompatDelegate.setApplicationLocales(
            LocaleListCompat.forLanguageTags(normalized),
        )
    }

    fun restore(context: Context) {
        val tag = savedTag(context) ?: return
        AppCompatDelegate.setApplicationLocales(
            LocaleListCompat.forLanguageTags(tag),
        )
    }

    const val LANGUAGE_ZH = "zh-CN"
    const val LANGUAGE_EN = "en"

    private const val PREFERENCES_NAME = "language_preferences"
    private const val KEY_LANGUAGE = "language"
}
