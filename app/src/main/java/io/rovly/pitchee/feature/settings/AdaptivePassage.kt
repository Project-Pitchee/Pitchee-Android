package io.rovly.pitchee.feature.settings

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import java.text.BreakIterator
import java.util.Locale

internal fun adaptivePassagePages(
    text: String,
    measurer: TextMeasurer,
    style: TextStyle,
    widthPx: Int,
    heightPx: Int,
    locale: Locale,
    wordAware: Boolean,
): List<String> {
    if (text.isBlank() || widthPx <= 0 || heightPx <= 0) return listOf(text)
    val pages = mutableListOf<String>()
    var start = 0
    while (start < text.length) {
        val remaining = text.substring(start)
        val layout = measurer.measure(
            text = AnnotatedString(remaining),
            style = style,
            overflow = TextOverflow.Clip,
            softWrap = true,
            maxLines = PASSAGE_MAX_LINES,
            constraints = Constraints(
                maxWidth = widthPx,
                maxHeight = heightPx,
            ),
        )
        var end = if (layout.hasVisualOverflow) {
            layout.getLineEnd(PASSAGE_MAX_LINES - 1, visibleEnd = true)
        } else {
            remaining.length
        }
        if (wordAware) {
            end = wordBoundaryBefore(remaining, end, locale)
        }
        if (end <= 0) end = 1
        pages += remaining.substring(0, end).trimEnd()
        start += end
        while (start < text.length && text[start].isWhitespace()) start++
    }
    return pages.ifEmpty { listOf(text) }
}

private fun wordBoundaryBefore(
    text: String,
    end: Int,
    locale: Locale,
): Int {
    if (end <= 0) return 1
    if (end >= text.length) return text.length
    val iterator = BreakIterator.getWordInstance(locale)
    iterator.setText(text)
    if (iterator.isBoundary(end)) return end
    val boundary = iterator.preceding(end)
    return boundary.coerceAtLeast(1)
}

internal const val PASSAGE_MAX_LINES = 3
