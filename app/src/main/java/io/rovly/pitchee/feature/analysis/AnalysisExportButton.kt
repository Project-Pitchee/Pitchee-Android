package io.rovly.pitchee.feature.analysis

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.rovly.pitchee.R
import io.rovly.pitchee.data.audio.RecordedAudio
import io.rovly.pitchee.data.model.PitcheeResult
import io.rovly.pitchee.ui.common.TitleIconButton

@Composable
internal fun AnalysisExportButton(
    result: PitcheeResult,
    audio: RecordedAudio?,
    timestampMillis: Long,
    modifier: Modifier = Modifier,
) {
    var showingExport by rememberSaveable { mutableStateOf(false) }

    TitleIconButton(
        iconRes = R.drawable.ic_export,
        contentDescription = stringResource(R.string.analysis_export),
        onClick = { showingExport = true },
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    )

    if (showingExport) {
        Dialog(
            onDismissRequest = { showingExport = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
        ) {
            AnalysisExportScreen(
                result = result,
                audio = audio,
                timestampMillis = timestampMillis,
                onBack = { showingExport = false },
            )
        }
    }
}
