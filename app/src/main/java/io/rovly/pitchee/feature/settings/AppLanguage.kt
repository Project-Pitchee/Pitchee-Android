package io.rovly.pitchee.feature.settings

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

internal data class AppLanguageOption(
    val tag: String,
    val displayName: String,
)

internal object AppLanguage {
    val supported = listOf(
        AppLanguageOption(LANGUAGE_ZH, "中文（简体）"),
        AppLanguageOption(LANGUAGE_ZH_TW, "繁體中文"),
        AppLanguageOption(LANGUAGE_EN, "English"),
        AppLanguageOption("ja", "日本語"),
        AppLanguageOption("ko", "한국어"),
        AppLanguageOption("es", "Español"),
        AppLanguageOption("fr", "Français"),
        AppLanguageOption("de", "Deutsch"),
        AppLanguageOption("it", "Italiano"),
        AppLanguageOption("pt", "Português"),
        AppLanguageOption("ru", "Русский"),
        AppLanguageOption("uk", "Українська"),
        AppLanguageOption("pl", "Polski"),
        AppLanguageOption("nl", "Nederlands"),
        AppLanguageOption("sv", "Svenska"),
        AppLanguageOption("tr", "Türkçe"),
        AppLanguageOption("ar", "العربية"),
        AppLanguageOption("hi", "हिन्दी"),
        AppLanguageOption("id", "Bahasa Indonesia"),
        AppLanguageOption("ms", "Bahasa Melayu"),
        AppLanguageOption("vi", "Tiếng Việt"),
        AppLanguageOption("th", "ไทย"),
    )

    fun savedTag(context: Context): String? =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, null)
            ?.takeIf { saved -> supported.any { it.tag == saved } }

    fun set(context: Context, tag: String) {
        val normalized = supported.firstOrNull { it.tag == tag }?.tag ?: LANGUAGE_EN
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

    fun selectedTag(context: Context): String {
        savedTag(context)?.let { return it }
        val locale = context.resources.configuration.locales[0]
        if (locale.language == Locale.CHINESE.language) {
            return when (locale.script) {
                "Hant" -> LANGUAGE_ZH_TW
                else -> if (
                    locale.country in setOf("TW", "HK", "MO")
                ) {
                    LANGUAGE_ZH_TW
                } else {
                    LANGUAGE_ZH
                }
            }
        }
        return supported.firstOrNull { option ->
            Locale.forLanguageTag(option.tag).language == locale.language
        }?.tag ?: LANGUAGE_EN
    }

    fun displayName(tag: String): String =
        supported.firstOrNull { it.tag == tag }?.displayName ?: "English"

    const val LANGUAGE_ZH = "zh-CN"
    const val LANGUAGE_ZH_TW = "zh-TW"
    const val LANGUAGE_EN = "en"

    private const val PREFERENCES_NAME = "language_preferences"
    private const val KEY_LANGUAGE = "language"
}
