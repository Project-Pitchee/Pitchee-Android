package io.rovly.pitchee.feature.analysis

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.rovly.pitchee.R
import io.rovly.pitchee.data.audio.RecordedAudio
import io.rovly.pitchee.data.model.PitcheeResult
import io.rovly.pitchee.ui.common.ScreenTitle
import io.rovly.pitchee.ui.common.TitleIconButton
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun AnalysisExportScreen(
    result: PitcheeResult,
    audio: RecordedAudio?,
    timestampMillis: Long,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedCharts by remember(result) {
        mutableStateOf(ExportChartOption.entries.toSet())
    }
    var selectedMetrics by remember(result) {
        mutableStateOf(ExportMetricOption.entries.toSet())
    }
    var pdfFile by remember { mutableStateOf<File?>(null) }
    var previewPages by remember { mutableStateOf<List<ImageBitmap>>(emptyList()) }
    var previewPage by remember { mutableIntStateOf(0) }
    var generating by remember { mutableStateOf(true) }
    var showFormatDialog by remember { mutableStateOf(false) }
    var pendingImageExport by remember { mutableStateOf(false) }
    var generationVersion by remember { mutableIntStateOf(0) }
    val exportFailedMessage = stringResource(R.string.analysis_export_failed)
    val imageSavedMessage = stringResource(R.string.analysis_export_images_saved)
    val imageSaveFailedMessage = stringResource(R.string.analysis_export_image_save_failed)
    val imagePermissionDeniedMessage = stringResource(R.string.analysis_export_storage_permission_denied)

    fun saveImages() {
        val pages = previewPages
        if (pages.isEmpty()) {
            Toast.makeText(context, imageSaveFailedMessage, Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                AnalysisExporter.savePreviewImages(
                    context = context.applicationContext,
                    pages = pages,
                    timestampMillis = timestampMillis,
                )
            }
            Toast.makeText(
                context,
                if (saved > 0) imageSavedMessage else imageSaveFailedMessage,
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    val imagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val shouldSave = pendingImageExport
        pendingImageExport = false
        if (granted && shouldSave) {
            saveImages()
        } else if (shouldSave) {
            Toast.makeText(context, imagePermissionDeniedMessage, Toast.LENGTH_SHORT).show()
        }
    }

    fun requestImageExport() {
        val needsPermission = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            pendingImageExport = true
            imagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            saveImages()
        }
    }

    BackHandler(onBack = onBack)

    LaunchedEffect(selectedCharts, selectedMetrics, result, audio, timestampMillis) {
        val requestVersion = ++generationVersion
        generating = true
        val selection = ExportSelection(
            charts = selectedCharts,
            metrics = selectedMetrics,
        )
        try {
            delay(180)
            val (file, pages) = withContext(Dispatchers.IO) {
                val generated = AnalysisExporter.createPdf(
                    context = context.applicationContext,
                    result = result,
                    audio = audio,
                    timestampMillis = timestampMillis,
                    selection = selection,
                )
                generated to AnalysisExporter.renderPreview(context.applicationContext, generated)
            }
            if (requestVersion != generationVersion) return@LaunchedEffect
            pdfFile = file
            previewPages = pages
            previewPage = previewPage.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            if (requestVersion == generationVersion) {
                Toast.makeText(context, exportFailedMessage, Toast.LENGTH_SHORT).show()
            }
        } finally {
            if (requestVersion == generationVersion) {
                generating = false
            }
        }
    }

    // The surface fills the whole window so the background reaches under the
    // system bars; only the content is inset.
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        ) {
            item(key = "export-title") {
                ScreenTitle(
                    title = stringResource(R.string.analysis_export_title),
                    onBack = onBack,
                    trailingContent = {
                        TitleIconButton(
                            iconRes = R.drawable.ic_export,
                            contentDescription = stringResource(R.string.analysis_export_share),
                            onClick = { showFormatDialog = true },
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    },
                )
                Spacer(Modifier.height(18.dp))
            }

            item(key = "export-preview") {
                ExportPreviewCard(
                    pages = previewPages,
                    currentPage = previewPage,
                    generating = generating,
                    onPrevious = { previewPage = (previewPage - 1).coerceAtLeast(0) },
                    onNext = {
                        previewPage = (previewPage + 1).coerceAtMost((previewPages.size - 1).coerceAtLeast(0))
                    },
                )
                Spacer(Modifier.height(22.dp))
            }

            item(key = "export-charts-title") {
                ExportSectionTitle(stringResource(R.string.analysis_export_charts))
            }
            items(ExportChartOption.entries, key = { it.name }) { option ->
                ExportSelectionRow(
                    title = stringResource(option.titleRes),
                    detail = stringResource(option.detailRes),
                    checked = option in selectedCharts,
                    onCheckedChange = { checked ->
                        selectedCharts = if (checked) selectedCharts + option else selectedCharts - option
                    },
                )
            }

            item(key = "export-data-title") {
                Spacer(Modifier.height(16.dp))
                ExportSectionTitle(stringResource(R.string.analysis_export_data))
            }
            ExportMetricGroup.entries.forEach { group ->
                val options = ExportMetricOption.entries.filter { it.group == group }
                item(key = "export-group-${group.name}") {
                    Text(
                        text = stringResource(group.titleRes),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 14.dp, bottom = 2.dp),
                    )
                }
                items(options, key = { it.name }) { option ->
                    ExportSelectionRow(
                        title = stringResource(option.titleRes),
                        detail = stringResource(option.detailRes),
                        checked = option in selectedMetrics,
                        onCheckedChange = { checked ->
                            selectedMetrics = if (checked) selectedMetrics + option else selectedMetrics - option
                        },
                    )
                }
            }

            item(key = "export-bottom") {
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showFormatDialog) {
        AlertDialog(
            onDismissRequest = { showFormatDialog = false },
            title = { Text(stringResource(R.string.analysis_export_choose_format)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showFormatDialog = false
                        val file = pdfFile
                        if (file == null) {
                            Toast.makeText(context, exportFailedMessage, Toast.LENGTH_SHORT).show()
                        } else {
                            runCatching { AnalysisExporter.sharePdf(context, file) }
                                .onFailure {
                                    Toast.makeText(context, exportFailedMessage, Toast.LENGTH_SHORT).show()
                                }
                        }
                    },
                ) {
                    Text(stringResource(R.string.analysis_export_format_pdf))
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = { showFormatDialog = false },
                    ) {
                        Text(stringResource(R.string.analysis_export_cancel))
                    }
                    TextButton(
                        onClick = {
                            showFormatDialog = false
                            requestImageExport()
                        },
                    ) {
                        Text(stringResource(R.string.analysis_export_format_image))
                    }
                }
            },
        )
    }
}

@Composable
private fun ExportPreviewCard(
    pages: List<ImageBitmap>,
    currentPage: Int,
    generating: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.analysis_export_preview),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(595f / 842f)
                    .clip(MaterialTheme.shapes.small)
                    .background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                val bitmap = pages.getOrNull(currentPage)
                if (bitmap != null && !generating) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                }
            }
            if (pages.size > 1) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    IconButton(
                        onClick = onPrevious,
                        enabled = currentPage > 0,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_chevron_right),
                            contentDescription = stringResource(R.string.analysis_export_previous),
                            modifier = Modifier.rotate(180f),
                        )
                    }
                    Text(
                        text = stringResource(
                            R.string.analysis_export_page_indicator,
                            currentPage + 1,
                            pages.size,
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    IconButton(
                        onClick = onNext,
                        enabled = currentPage < pages.size - 1,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_chevron_right),
                            contentDescription = stringResource(R.string.analysis_export_next),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExportSectionTitle(title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ExportSelectionRow(
    title: String,
    detail: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
