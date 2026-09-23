package io.rovly.pitchee.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

internal data class LocalizedText(
    val resourceId: Int,
    val arguments: List<Any> = emptyList(),
)

@Composable
internal fun LocalizedText.resolve(): String =
    stringResource(resourceId, *arguments.toTypedArray())

internal enum class ScoreMetric {
    F0,
    TIMBRE,
    NATURALNESS,
}
