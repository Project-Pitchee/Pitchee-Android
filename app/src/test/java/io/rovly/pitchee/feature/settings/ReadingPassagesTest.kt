package io.rovly.pitchee.feature.settings

import io.rovly.pitchee.R
import io.rovly.pitchee.feature.settings.readingPassages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingPassagesTest {
    @Test
    fun includesElevenLocalizedPassages() {
        assertEquals(11, readingPassages.size)
        assertTrue(
            readingPassages.all {
                it.titleRes == R.array.reading_passage_titles &&
                    it.textRes == R.array.reading_passage_texts
            },
        )
        assertEquals(
            (0 until readingPassages.size).toSet(),
            readingPassages.map { it.index }.toSet(),
        )
    }
}
