package io.rovly.pitchee.feature.settings

import android.content.Context
import io.rovly.pitchee.R

internal data class ReadingPassage(
    val titleRes: Int,
    val textRes: Int? = null,
    val index: Int = 0,
    private val customText: String? = null,
) {
    fun title(context: Context): String =
        context.resources.getStringArray(titleRes)[index]

    fun text(context: Context): String =
        customText ?: context.resources.getStringArray(requireNotNull(textRes))[index]
}

internal val readingPassages = List(OFFICIAL_PASSAGE_COUNT) { index ->
    ReadingPassage(
        titleRes = R.array.reading_passage_titles,
        textRes = R.array.reading_passage_texts,
        index = index,
    )
}

private const val OFFICIAL_PASSAGE_COUNT = 6
