package io.rovly.pitchee.ui

import android.content.Context
import io.rovly.pitchee.R

internal enum class PassageSource {
    OFFICIAL,
    CUSTOM,
}

internal class PassagePreferences(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun source(): PassageSource = when (
        preferences.getString(KEY_SOURCE, PassageSource.OFFICIAL.name)
    ) {
        PassageSource.CUSTOM.name -> PassageSource.CUSTOM
        else -> PassageSource.OFFICIAL
    }

    fun officialIndex(): Int = preferences
        .getInt(KEY_OFFICIAL_INDEX, 0)
        .coerceIn(readingPassages.indices)

    fun customText(): String = preferences.getString(KEY_CUSTOM_TEXT, "").orEmpty()

    fun activePassage(
        officialIndex: Int = officialIndex(),
    ): ReadingPassage = when (source()) {
        PassageSource.OFFICIAL -> readingPassages[officialIndex.coerceIn(readingPassages.indices)]
        PassageSource.CUSTOM -> {
            val text = customText().ifBlank {
                readingPassages[officialIndex()].text(appContext)
            }
            ReadingPassage(
                titleRes = R.string.passage_custom_title,
                customText = text,
            )
        }
    }

    fun setSource(source: PassageSource) {
        preferences.edit().putString(KEY_SOURCE, source.name).apply()
    }

    fun setOfficialIndex(index: Int) {
        preferences.edit()
            .putInt(KEY_OFFICIAL_INDEX, index.coerceIn(readingPassages.indices))
            .apply()
    }

    fun setCustomText(text: String) {
        preferences.edit().putString(KEY_CUSTOM_TEXT, text.trim()).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "passage_preferences"
        const val KEY_SOURCE = "source"
        const val KEY_OFFICIAL_INDEX = "official_index"
        const val KEY_CUSTOM_TEXT = "custom_text"
    }
}
