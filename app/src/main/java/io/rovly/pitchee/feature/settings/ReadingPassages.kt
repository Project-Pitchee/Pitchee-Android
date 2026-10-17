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

internal val readingPassages = listOf(
    ReadingPassage(
        titleRes = R.array.reading_passage_titles,
        textRes = R.array.reading_passage_texts,
        index = 0,
    ),
    ReadingPassage(
        titleRes = R.array.reading_passage_titles,
        textRes = R.array.reading_passage_texts,
        index = 1,
    ),
    ReadingPassage(
        titleRes = R.array.reading_passage_titles,
        textRes = R.array.reading_passage_texts,
        index = 2,
    ),
    ReadingPassage(
        titleRes = R.array.reading_passage_titles,
        textRes = R.array.reading_passage_texts,
        index = 3,
    ),
    ReadingPassage(
        titleRes = R.array.reading_passage_titles,
        textRes = R.array.reading_passage_texts,
        index = 4,
    ),
    ReadingPassage(
        titleRes = R.array.reading_passage_titles,
        textRes = R.array.reading_passage_texts,
        index = 5,
    ),
    ReadingPassage(
        titleRes = R.array.reading_passage_titles,
        textRes = R.array.reading_passage_texts,
        index = 6,
    ),
    ReadingPassage(
        titleRes = R.array.reading_passage_titles,
        textRes = R.array.reading_passage_texts,
        index = 7,
    ),
    ReadingPassage(
        titleRes = R.array.reading_passage_titles,
        textRes = R.array.reading_passage_texts,
        index = 8,
    ),
    ReadingPassage(
        titleRes = R.array.reading_passage_titles,
        textRes = R.array.reading_passage_texts,
        index = 9,
    ),
    ReadingPassage(
        titleRes = R.array.reading_passage_titles,
        textRes = R.array.reading_passage_texts,
        index = 10,
    ),
)
