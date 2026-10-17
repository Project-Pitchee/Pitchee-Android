package io.rovly.pitchee.feature.analysis

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.FileProvider
import io.rovly.pitchee.R
import io.rovly.pitchee.data.audio.RecordedAudio
import io.rovly.pitchee.data.model.PitcheeResult
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import space.pitchee.core.PitcheeScoreProfile

internal enum class ExportChartOption(
    val titleRes: Int,
    val detailRes: Int,
) {
    F0(
        R.string.analysis_export_chart_f0,
        R.string.analysis_export_chart_f0_detail,
    ),
    VFP(
        R.string.analysis_export_chart_vfp,
        R.string.analysis_export_chart_vfp_detail,
    ),
    NATURALNESS(
        R.string.analysis_export_chart_naturalness,
        R.string.analysis_export_chart_naturalness_detail,
    ),
}

internal enum class ExportMetricGroup(val titleRes: Int) {
    RECORDING(R.string.analysis_export_group_recording),
    PITCH(R.string.analysis_export_group_pitch),
    LOUDNESS(R.string.analysis_export_group_loudness),
    RESULT(R.string.analysis_export_group_result),
}

internal enum class ExportMetricOption(
    val group: ExportMetricGroup,
    val titleRes: Int,
    val detailRes: Int,
) {
    INPUT_DURATION(
        ExportMetricGroup.RECORDING,
        R.string.analysis_export_metric_input_duration,
        R.string.analysis_export_metric_detail_timing,
    ),
    ANALYZED_DURATION(
        ExportMetricGroup.RECORDING,
        R.string.analysis_export_metric_analyzed_duration,
        R.string.analysis_export_metric_detail_timing,
    ),
    SPEECH_DURATION(
        ExportMetricGroup.RECORDING,
        R.string.analysis_export_metric_speech_duration,
        R.string.analysis_export_metric_detail_timing,
    ),
    SAMPLE_RATE(
        ExportMetricGroup.RECORDING,
        R.string.analysis_export_metric_sample_rate,
        R.string.analysis_export_metric_detail_audio_format,
    ),
    CHANNELS(
        ExportMetricGroup.RECORDING,
        R.string.analysis_export_metric_channels,
        R.string.analysis_export_metric_detail_audio_format,
    ),
    MEAN_PITCH(
        ExportMetricGroup.PITCH,
        R.string.analysis_export_metric_mean_pitch,
        R.string.analysis_export_metric_detail_pitch,
    ),
    MEDIAN_PITCH(
        ExportMetricGroup.PITCH,
        R.string.analysis_export_metric_median_pitch,
        R.string.analysis_export_metric_detail_pitch,
    ),
    PITCH_STANDARD_DEVIATION(
        ExportMetricGroup.PITCH,
        R.string.analysis_export_metric_pitch_standard_deviation,
        R.string.analysis_export_metric_detail_pitch,
    ),
    PITCH_RANGE(
        ExportMetricGroup.PITCH,
        R.string.analysis_export_metric_pitch_range,
        R.string.analysis_export_metric_detail_pitch,
    ),
    VOICED_WINDOWS(
        ExportMetricGroup.PITCH,
        R.string.analysis_export_metric_voiced_windows,
        R.string.analysis_export_metric_detail_pitch,
    ),
    AVERAGE_LOUDNESS(
        ExportMetricGroup.LOUDNESS,
        R.string.analysis_export_metric_average_loudness,
        R.string.analysis_export_metric_detail_loudness,
    ),
    MEDIAN_LOUDNESS(
        ExportMetricGroup.LOUDNESS,
        R.string.analysis_export_metric_median_loudness,
        R.string.analysis_export_metric_detail_loudness,
    ),
    LOUDNESS_RANGE(
        ExportMetricGroup.LOUDNESS,
        R.string.analysis_export_metric_loudness_range,
        R.string.analysis_export_metric_detail_loudness,
    ),
    FINAL_SCORE(
        ExportMetricGroup.RESULT,
        R.string.analysis_export_metric_final_score,
        R.string.analysis_export_metric_detail_score,
    ),
    STANDARD_SCORE(
        ExportMetricGroup.RESULT,
        R.string.analysis_export_metric_standard_score,
        R.string.analysis_export_metric_detail_score,
    ),
    NATURALNESS_SCORE(
        ExportMetricGroup.RESULT,
        R.string.analysis_export_metric_naturalness_score,
        R.string.analysis_export_metric_detail_score,
    ),
}

internal data class ExportSelection(
    val charts: Set<ExportChartOption> = ExportChartOption.entries.toSet(),
    val metrics: Set<ExportMetricOption> = ExportMetricOption.entries.toSet(),
)

internal object AnalysisExporter {
    fun createPdf(
        context: Context,
        result: PitcheeResult,
        audio: RecordedAudio?,
        timestampMillis: Long,
        selection: ExportSelection,
    ): File {
        val exportDirectory = File(context.cacheDir, "exports").apply { mkdirs() }
        val fileName = "Pitchee-Report-${
            SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(timestampMillis))
        }.pdf"
        val outputFile = File(exportDirectory, fileName)
        val statistics = ExportStatistics.from(result, audio)
        val pages = paginate(selection, statistics)

        val document = PdfDocument()
        try {
            pages.forEachIndexed { index, pageModel ->
                val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, index + 1).create()
                val page = document.startPage(pageInfo)
                drawPage(
                    context = context,
                    canvas = page.canvas,
                    page = pageModel,
                    result = result,
                    statistics = statistics,
                )
                document.finishPage(page)
            }
            FileOutputStream(outputFile).use(document::writeTo)
        } finally {
            document.close()
        }
        runCatching {
            PdfLinkAnnotator.addUriLink(outputFile, PITCHEE_DEV_URL)
        }
        return outputFile
    }

    fun renderPreview(context: Context, file: File): List<ImageBitmap> {
        val previewWidth = 900
        val previews = mutableListOf<ImageBitmap>()
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                repeat(renderer.pageCount) { index ->
                    renderer.openPage(index).use { page ->
                        val bitmap = Bitmap.createBitmap(
                            previewWidth,
                            (previewWidth * page.height.toFloat() / page.width).roundToInt(),
                            Bitmap.Config.ARGB_8888,
                        )
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        previews += bitmap.asImageBitmap()
                    }
                }
            }
        }
        return previews
    }

    fun savePreviewImages(
        context: Context,
        pages: List<ImageBitmap>,
        timestampMillis: Long,
    ): Int {
        if (pages.isEmpty()) return 0
        val resolver = context.contentResolver
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(timestampMillis))
        var savedCount = 0

        pages.forEachIndexed { index, image ->
            val values = ContentValues().apply {
                put(
                    MediaStore.Images.Media.DISPLAY_NAME,
                    "Pitchee-Report-$timestamp-${index + 1}.png",
                )
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        "${Environment.DIRECTORY_PICTURES}/Pitchee",
                    )
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val uri = runCatching {
                resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            }.getOrNull() ?: return@forEachIndexed

            val wroteFile = runCatching {
                resolver.openOutputStream(uri)?.use { output ->
                    image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, output)
                } ?: false
            }.getOrDefault(false)

            if (wroteFile) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    runCatching { resolver.update(uri, values, null, null) }
                }
                savedCount += 1
            } else {
                runCatching { resolver.delete(uri, null, null) }
            }
        }
        return savedCount
    }

    fun sharePdf(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(
                shareIntent,
                context.getString(R.string.analysis_export_share_title),
            ),
        )
    }

    private fun paginate(
        selection: ExportSelection,
        statistics: ExportStatistics,
    ): List<ExportReportPage> {
        val charts = ExportChartOption.entries.filter { it in selection.charts }
        val metrics = ExportMetricOption.entries.filter { it in selection.metrics }
        val pages = mutableListOf<ExportReportPage>()
        var remainingCharts = charts
        var remainingMetrics = metrics
        var firstPage = true

        while (remainingCharts.isNotEmpty() || remainingMetrics.isNotEmpty()) {
            val includeResultChart = firstPage && charts.isNotEmpty()
            val chartCount = if (remainingCharts.isEmpty()) {
                0
            } else {
                var fittingCount = 0
                for (count in 1..remainingCharts.size) {
                    if (
                        estimatedBodyHeight(
                            charts = remainingCharts.take(count),
                            metrics = emptyList(),
                            includeResultChart = includeResultChart,
                            pass = statistics.isPass,
                        ) > AVAILABLE_BODY_HEIGHT
                    ) {
                        break
                    }
                    fittingCount = count
                }
                fittingCount.coerceAtLeast(1)
            }
            val pageCharts = remainingCharts.take(chartCount)
            val fittingMetricCount = largestFittingMetricCount(
                charts = pageCharts,
                metrics = remainingMetrics,
                includeResultChart = includeResultChart,
                pass = statistics.isPass,
            )
            val metricCount = when {
                remainingMetrics.isEmpty() -> 0
                fittingMetricCount > 0 -> fittingMetricCount
                pageCharts.isEmpty() && !includeResultChart -> 1
                else -> 0
            }
            pages += ExportReportPage(
                charts = pageCharts,
                metrics = remainingMetrics.take(metricCount),
                includeResultChart = includeResultChart,
            )
            remainingCharts = remainingCharts.drop(chartCount)
            remainingMetrics = remainingMetrics.drop(metricCount)
            firstPage = false

            if (chartCount == 0 && metricCount == 0) break
        }

        if (pages.isEmpty()) {
            pages += ExportReportPage(emptyList(), emptyList(), includeResultChart = false)
        }

        return pages.mapIndexed { index, page ->
            page.copy(pageNumber = index + 1, pageCount = pages.size)
        }
    }

    private fun largestFittingMetricCount(
        charts: List<ExportChartOption>,
        metrics: List<ExportMetricOption>,
        includeResultChart: Boolean,
        pass: Boolean,
    ): Int {
        var fittingCount = 0
        for (count in 1..metrics.size) {
            val candidate = metrics.take(count)
            if (
                estimatedBodyHeight(
                    charts = charts,
                    metrics = candidate,
                    includeResultChart = includeResultChart,
                    pass = pass,
                ) > AVAILABLE_BODY_HEIGHT
            ) {
                break
            }
            fittingCount = count
        }
        return fittingCount
    }

    private fun estimatedBodyHeight(
        charts: List<ExportChartOption>,
        metrics: List<ExportMetricOption>,
        includeResultChart: Boolean,
        pass: Boolean,
    ): Float {
        var height = 0f
        if (charts.isNotEmpty() || includeResultChart) {
            height += SECTION_HEIGHT
            if (pass) height += PASS_BANNER_HEIGHT + 8f
            if (includeResultChart) height += RESULT_CHART_HEIGHT
            if (charts.isNotEmpty()) height += CURVES_CHART_HEIGHT
            val chartBlocks = (if (charts.isNotEmpty()) 1 else 0) + (if (includeResultChart) 1 else 0)
            height += chartBlocks * 12f
        }
        if (metrics.isNotEmpty()) {
            if (charts.isNotEmpty() || includeResultChart) height += 14f
            height += SECTION_HEIGHT
            ExportMetricGroup.entries.forEach { group ->
                val count = metrics.count { it.group == group }
                if (count == 0) return@forEach
                height += METRIC_GROUP_HEADING_HEIGHT
                height += ceil(count / 2f) * METRIC_ROW_HEIGHT
                height += 8f
            }
        }
        return height
    }

    private fun drawPage(
        context: Context,
        canvas: Canvas,
        page: ExportReportPage,
        result: PitcheeResult,
        statistics: ExportStatistics,
    ) {
        canvas.drawColor(Color.WHITE)
        val paints = ReportPaints()
        var y = PAGE_MARGIN

        if (page.pageNumber == 1) {
            drawHeader(context, canvas, paints)
            y += HEADER_HEIGHT
            canvas.drawLine(PAGE_MARGIN, y, PAGE_WIDTH - PAGE_MARGIN, y, paints.divider)
            y += 18f
        }

        if (page.charts.isNotEmpty() || page.includeResultChart) {
            y = drawCharts(
                context = context,
                canvas = canvas,
                paints = paints,
                charts = page.charts,
                includeResultChart = page.includeResultChart,
                result = result,
                statistics = statistics,
                startY = y,
            )
        }
        if (page.metrics.isNotEmpty()) {
            if (page.charts.isNotEmpty()) y += 12f
            y = drawMetrics(context, canvas, paints, page.metrics, result, statistics, y)
        }
        if (page.charts.isEmpty() && page.metrics.isEmpty()) {
            drawEmptyState(context, canvas, paints, y)
        }

        drawFooter(context, canvas, paints, page)
    }

    private fun drawHeader(
        context: Context,
        canvas: Canvas,
        paints: ReportPaints,
    ) {
        canvas.drawText(
            context.getString(R.string.analysis_export_report_title),
            PAGE_MARGIN,
            PAGE_MARGIN + 16f,
            paints.reportTitle,
        )
        val brand = context.getString(R.string.analysis_export_report_brand)
        val brandWidth = paints.brand.measureText(brand)
        val logoSize = 28f
        val logoGap = 8f
        val logoLeft = PAGE_WIDTH - PAGE_MARGIN - logoSize - logoGap - brandWidth
        val logoRect = RectF(
            logoLeft,
            PAGE_MARGIN - 3f,
            logoLeft + logoSize,
            PAGE_MARGIN + logoSize - 3f,
        )
        val logo = BitmapFactory.decodeResource(context.resources, R.drawable.pitchee_logo)
        if (logo != null) {
            val logoPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            val logoClip = Path().apply {
                addRoundRect(logoRect, 7f, 7f, Path.Direction.CW)
            }
            canvas.save()
            canvas.clipPath(logoClip)
            canvas.drawBitmap(logo, null, logoRect, logoPaint)
            canvas.restore()
            logo.recycle()
        } else {
            canvas.drawRoundRect(logoRect, 6f, 6f, paints.accent)
            drawCenteredText(canvas, "P", logoRect.centerX(), logoRect.centerY(), paints.brandIcon)
        }
        val brandBaseline = logoRect.centerY() - (paints.brand.ascent() + paints.brand.descent()) / 2f
        canvas.drawText(brand, logoRect.right + logoGap, brandBaseline, paints.brand)
    }

    private fun drawCharts(
        context: Context,
        canvas: Canvas,
        paints: ReportPaints,
        charts: List<ExportChartOption>,
        includeResultChart: Boolean,
        result: PitcheeResult,
        statistics: ExportStatistics,
        startY: Float,
    ): Float {
        var y = startY
        canvas.drawText(context.getString(R.string.analysis_export_charts), PAGE_MARGIN, y + 12f, paints.section)
        y += SECTION_HEIGHT

        if (statistics.isPass) {
            drawPassBanner(context, canvas, paints, result, y)
            y += PASS_BANNER_HEIGHT + 8f
        }

        if (includeResultChart) {
            drawResultChart(context, canvas, paints, result, statistics, y)
            y += RESULT_CHART_HEIGHT + 12f
        }
        if (charts.isNotEmpty()) {
            drawCombinedCurveChart(context, canvas, paints, charts, result, statistics, y)
            y += CURVES_CHART_HEIGHT + 12f
        }
        return y
    }

    private fun drawPassBanner(
        context: Context,
        canvas: Canvas,
        paints: ReportPaints,
        result: PitcheeResult,
        top: Float,
    ) {
        val rect = RectF(
            PAGE_MARGIN,
            top,
            PAGE_WIDTH - PAGE_MARGIN,
            top + PASS_BANNER_HEIGHT,
        )
        canvas.drawRoundRect(rect, 12f, 12f, paints.passBackground)
        val iconCenterX = rect.left + 20f
        val iconCenterY = rect.centerY()
        canvas.drawCircle(iconCenterX, iconCenterY, 9f, paints.passIcon)
        val checkPath = Path().apply {
            moveTo(iconCenterX - 4f, iconCenterY)
            lineTo(iconCenterX - 1f, iconCenterY + 3f)
            lineTo(iconCenterX + 5f, iconCenterY - 4f)
        }
        canvas.drawPath(checkPath, paints.passCheck)

        val message = if (result.scoreProfile == PitcheeScoreProfile.MASCULINIZATION) {
            context.getString(R.string.analysis_export_pass_masculine)
        } else {
            context.getString(R.string.analysis_export_pass_feminine)
        }
        val textBaseline = iconCenterY - (paints.passText.ascent() + paints.passText.descent()) / 2f
        canvas.drawText(message, iconCenterX + 18f, textBaseline, paints.passText)
    }

    private fun drawCombinedCurveChart(
        context: Context,
        canvas: Canvas,
        paints: ReportPaints,
        charts: List<ExportChartOption>,
        result: PitcheeResult,
        statistics: ExportStatistics,
        top: Float,
    ) {
        canvas.drawRoundRect(
            RectF(PAGE_MARGIN, top, PAGE_WIDTH - PAGE_MARGIN, top + CURVES_CHART_HEIGHT - 8f),
            10f,
            10f,
            paints.card,
        )
        canvas.drawText(
            context.getString(R.string.analysis_export_combined_curve_title),
            PAGE_MARGIN + 16f,
            top + 28f,
            paints.section,
        )

        var legendX = PAGE_MARGIN + 16f
        val legendY = top + 60f
        if (ExportChartOption.F0 in charts) {
            canvas.drawLine(legendX, legendY - 3f, legendX + 14f, legendY - 3f, paints.frequencyLine)
            val label = context.getString(R.string.analysis_export_group_pitch)
            val width = paints.legend.measureText(label)
            canvas.drawText(label, legendX + 19f, legendY, paints.legend)
            legendX += 19f + width + 26f
        }
        if (ExportChartOption.VFP in charts) {
            canvas.drawLine(legendX, legendY - 3f, legendX + 14f, legendY - 3f, paints.vfpLine)
            val label = context.getString(R.string.analysis_export_metric_standard_score)
            val width = paints.legend.measureText(label)
            canvas.drawText(label, legendX + 19f, legendY, paints.legend)
            legendX += 19f + width + 26f
        }
        if (ExportChartOption.NATURALNESS in charts) {
            canvas.drawLine(legendX, legendY - 3f, legendX + 14f, legendY - 3f, paints.naturalnessLine)
            canvas.drawText(
                context.getString(R.string.analysis_export_chart_naturalness),
                legendX + 19f,
                legendY,
                paints.legend,
            )
        }

        val plot = RectF(
            PAGE_MARGIN + 34f,
            top + 78f,
            PAGE_WIDTH - PAGE_MARGIN - 38f,
            top + 190f,
        )
        canvas.drawRect(plot, paints.plotBackground)
        paints.chartGrid.strokeWidth = 1f
        repeat(3) { index ->
            val lineY = plot.top + plot.height() * index / 2f
            canvas.drawLine(plot.left, lineY, plot.right, lineY, paints.chartGrid)
        }

        val duration = max(result.audio.analyzedSeconds, 0.001)
        if (ExportChartOption.F0 in charts) {
            val pitchRange = (statistics.pitchAxisMaxHz - statistics.pitchAxisMinHz).coerceAtLeast(1.0)
            drawLineSeries(
                canvas,
                normalizeSeries(
                    result.f0.windows.map { window ->
                        TimedPlotValue(
                            timeSeconds = ((window.startSeconds + window.endSeconds) / 2.0).toFloat(),
                            value = window.f0Hz?.let {
                                ((it - statistics.pitchAxisMinHz) / pitchRange).toFloat()
                            },
                        )
                    },
                ),
                plot,
                paints.frequencyLine,
            )
        }
        if (ExportChartOption.VFP in charts) {
            drawLineSeries(
                canvas,
                normalizeSeries(
                    result.vfp.windows.map { window ->
                        TimedPlotValue(
                            timeSeconds = ((window.startSeconds + window.endSeconds) / 2.0).toFloat(),
                            value = (window.standardScore / 100.0).toFloat(),
                        )
                    },
                ),
                plot,
                paints.vfpLine,
            )
        }
        if (ExportChartOption.NATURALNESS in charts) {
            drawLineSeries(
                canvas,
                normalizeSeries(
                    result.naturalness.windows.map { window ->
                        TimedPlotValue(
                            timeSeconds = ((window.startSeconds + window.endSeconds) / 2.0).toFloat(),
                            value = (window.score / 100.0).toFloat(),
                        )
                    },
                ),
                plot,
                paints.naturalnessLine,
            )
        }

        if (ExportChartOption.VFP in charts || ExportChartOption.NATURALNESS in charts) {
            val axisPaint = Paint(paints.axisLabel).apply { textAlign = Paint.Align.RIGHT }
            canvas.drawText("100", plot.left - 6f, plot.top + 3f, axisPaint)
            canvas.drawText("0", plot.left - 6f, plot.bottom, axisPaint)
        }
        if (ExportChartOption.F0 in charts) {
            val pitchPaint = Paint(paints.axisLabel).apply {
                color = FREQUENCY_COLOR
                textAlign = Paint.Align.LEFT
            }
            canvas.drawText(
                "%.0f Hz".format(statistics.pitchAxisMaxHz),
                plot.right + 4f,
                plot.top + 3f,
                pitchPaint,
            )
            canvas.drawText(
                "%.0f Hz".format(statistics.pitchAxisMinHz),
                plot.right + 4f,
                plot.bottom,
                pitchPaint,
            )
        }

        val xAxisPaint = Paint(paints.axisLabel).apply { textAlign = Paint.Align.LEFT }
        canvas.drawText("0", plot.left, plot.bottom + 16f, xAxisPaint)
        val durationLabel = "%.1f".format(duration)
        val endAxisPaint = Paint(paints.axisLabel).apply { textAlign = Paint.Align.RIGHT }
        canvas.drawText(durationLabel, plot.right, plot.bottom + 16f, endAxisPaint)
        val timeAxis = context.getString(R.string.analysis_export_time_axis)
        canvas.drawText(
            timeAxis,
            plot.centerX() - paints.axisLabel.measureText(timeAxis) / 2f,
            plot.bottom + 32f,
            paints.axisLabel,
        )

    }

    private fun drawResultChart(
        context: Context,
        canvas: Canvas,
        paints: ReportPaints,
        result: PitcheeResult,
        statistics: ExportStatistics,
        top: Float,
    ) {
        val card = RectF(PAGE_MARGIN, top, PAGE_WIDTH - PAGE_MARGIN, top + RESULT_CHART_HEIGHT - 8f)
        canvas.drawRoundRect(card, 10f, 10f, paints.card)
        canvas.drawLine(
            card.centerX(),
            card.top + 20f,
            card.centerX(),
            card.bottom - 18f,
            paints.chartGrid,
        )

        val pieRadius = 62f
        val pieCenterY = card.top + 100f
        val compositeCenterX = card.left + 136f
        val f0CenterX = card.right - 136f

        canvas.drawText(
            context.getString(R.string.analysis_export_metric_final_score),
            compositeCenterX - paints.metricLabel.measureText(
                context.getString(R.string.analysis_export_metric_final_score),
            ) / 2f,
            card.top + 26f,
            paints.metricLabel,
        )
        val pitchLabel = context.getString(R.string.analysis_export_group_pitch)
        canvas.drawText(
            pitchLabel,
            f0CenterX - paints.metricLabel.measureText(pitchLabel) / 2f,
            card.top + 26f,
            paints.metricLabel,
        )

        drawCompositeScorePie(
            context = context,
            canvas = canvas,
            paints = paints,
            result = result,
            centerX = compositeCenterX,
            centerY = pieCenterY,
            radius = pieRadius,
        )
        drawF0DistributionPie(
            context = context,
            canvas = canvas,
            paints = paints,
            result = result,
            fractionAbove165 = statistics.f0Above165Fraction,
            centerX = f0CenterX,
            centerY = pieCenterY,
            radius = pieRadius,
        )
    }

    private fun drawCompositeScorePie(
        context: Context,
        canvas: Canvas,
        paints: ReportPaints,
        result: PitcheeResult,
        centerX: Float,
        centerY: Float,
        radius: Float,
    ) {
        val score = result.composite.finalScore.coerceIn(0.0, 100.0)
        val masculineTarget = result.scoreProfile == PitcheeScoreProfile.MASCULINIZATION
        val scoreColor = if (masculineTarget) MASCULINE_COLOR else FEMININE_COLOR
        val remainderColor = if (masculineTarget) FEMININE_COLOR else MASCULINE_COLOR
        val rect = RectF(centerX - radius, centerY - radius, centerX + radius, centerY + radius)
        paints.pieRemainder.color = remainderColor
        canvas.drawCircle(centerX, centerY, radius, paints.pieRemainder)
        paints.piePrimary.color = scoreColor
        canvas.drawArc(rect, -90f, (360f * score / 100.0).toFloat(), true, paints.piePrimary)

        drawCenteredText(canvas, "%.1f%%".format(score), centerX, centerY - 10f, paints.pieValue)
        val scoreLabel = if (masculineTarget) {
            context.getString(R.string.analysis_export_composite_masculine_score)
        } else {
            context.getString(R.string.analysis_export_composite_feminine_score)
        }
        drawCenteredText(canvas, scoreLabel, centerX, centerY + 22f, paints.pieCaption)
    }

    private fun drawF0DistributionPie(
        context: Context,
        canvas: Canvas,
        paints: ReportPaints,
        result: PitcheeResult,
        fractionAbove165: Double?,
        centerX: Float,
        centerY: Float,
        radius: Float,
    ) {
        if (fractionAbove165 == null) {
            paints.pieRemainder.color = PIE_REMAINDER_COLOR
            canvas.drawCircle(centerX, centerY, radius, paints.pieRemainder)
            drawCenteredText(
                canvas,
                context.getString(R.string.analysis_export_no_value),
                centerX,
                centerY,
                paints.pieValueDark,
            )
            return
        }

        val masculineTarget = result.scoreProfile == PitcheeScoreProfile.MASCULINIZATION
        val above = fractionAbove165.coerceIn(0.0, 1.0)
        val below = 1.0 - above
        val targetFraction = if (masculineTarget) below else above
        val targetColor = if (masculineTarget) MASCULINE_COLOR else FEMININE_COLOR
        val remainderColor = if (masculineTarget) FEMININE_COLOR else MASCULINE_COLOR
        val rect = RectF(centerX - radius, centerY - radius, centerX + radius, centerY + radius)

        paints.pieRemainder.color = remainderColor
        canvas.drawCircle(centerX, centerY, radius, paints.pieRemainder)
        paints.piePrimary.color = targetColor
        canvas.drawArc(rect, -90f, (360.0 * targetFraction).toFloat(), true, paints.piePrimary)

        val percentagePaint = if (masculineTarget) paints.pieValueLight else paints.pieValue
        drawCenteredText(canvas, "%.0f%%".format(targetFraction * 100.0), centerX, centerY - 10f, percentagePaint)
        val targetLabel = if (masculineTarget) {
            context.getString(R.string.analysis_export_f0_masculine_label)
        } else {
            context.getString(R.string.analysis_export_f0_feminine_label)
        }
        val labelPaint = Paint(paints.pieCaption).apply {
            textSize = 8.0f
        }
        val labelParts = targetLabel.split('(', limit = 2)
        drawCenteredText(canvas, labelParts.first(), centerX, centerY + 20f, labelPaint)
        if (labelParts.size > 1) {
            val rangePaint = Paint(labelPaint).apply { textSize = 7.6f }
            drawCenteredText(canvas, "(${labelParts[1]}", centerX, centerY + 32f, rangePaint)
        }
    }

    private fun drawMetrics(
        context: Context,
        canvas: Canvas,
        paints: ReportPaints,
        metrics: List<ExportMetricOption>,
        result: PitcheeResult,
        statistics: ExportStatistics,
        startY: Float,
    ): Float {
        var y = startY
        canvas.drawText(context.getString(R.string.analysis_export_data), PAGE_MARGIN, y + 12f, paints.section)
        y += SECTION_HEIGHT

        ExportMetricGroup.entries.forEach { group ->
            val groupMetrics = metrics.filter { it.group == group }
            if (groupMetrics.isEmpty()) return@forEach
            canvas.drawText(context.getString(group.titleRes), PAGE_MARGIN, y + 10f, paints.groupTitle)
            y += METRIC_GROUP_HEADING_HEIGHT

            groupMetrics.chunked(2).forEach { rowMetrics ->
                rowMetrics.forEachIndexed { column, metric ->
                    val rowLeft = PAGE_MARGIN + column * (METRIC_COLUMN_WIDTH + 16f)
                    drawMetricValue(
                        context = context,
                        canvas = canvas,
                        paints = paints,
                        metric = metric,
                        result = result,
                        statistics = statistics,
                        left = rowLeft,
                        baseline = y + 9f,
                    )
                }
                y += METRIC_ROW_HEIGHT
            }
            y += 8f
        }
        return y
    }

    private fun drawMetricValue(
        context: Context,
        canvas: Canvas,
        paints: ReportPaints,
        metric: ExportMetricOption,
        result: PitcheeResult,
        statistics: ExportStatistics,
        left: Float,
        baseline: Float,
    ) {
        val value = metric.value(context, result, statistics)
        canvas.drawText(context.getString(metric.titleRes), left, baseline, paints.metricLabel)
        val titleWidth = paints.metricLabel.measureText(context.getString(metric.titleRes))
        var valueLeft = left + titleWidth + 6f
        val unitWidth = if (value.unit.isBlank()) 0f else paints.metricUnit.measureText(value.unit)
        val available = METRIC_COLUMN_WIDTH - (valueLeft - left) - unitWidth - 3f
        val valuePaint = Paint(paints.metricValue)
        val measured = valuePaint.measureText(value.value)
        if (measured > available && measured > 0f) {
            valuePaint.textScaleX = (available / measured).coerceIn(0.66f, 1f)
        }
        canvas.drawText(value.value, valueLeft, baseline, valuePaint)
        valueLeft += valuePaint.measureText(value.value) + 3f
        if (value.unit.isNotBlank()) {
            canvas.drawText(value.unit, valueLeft, baseline, paints.metricUnit)
        }
    }

    private fun drawEmptyState(
        context: Context,
        canvas: Canvas,
        paints: ReportPaints,
        top: Float,
    ) {
        canvas.drawText(
            context.getString(R.string.analysis_export_report_empty_title),
            PAGE_MARGIN,
            top + 40f,
            paints.emptyTitle,
        )
        drawWrappedText(
            canvas,
            context.getString(R.string.analysis_export_report_empty_message),
            PAGE_MARGIN,
            top + 64f,
            PAGE_WIDTH - PAGE_MARGIN * 2,
            paints.note,
            3,
        )
    }

    private fun drawFooter(
        context: Context,
        canvas: Canvas,
        paints: ReportPaints,
        page: ExportReportPage,
    ) {
        val baseline = PAGE_HEIGHT - PAGE_MARGIN
        canvas.drawText(
            context.getString(R.string.analysis_export_report_footer),
            PAGE_MARGIN,
            baseline,
            paints.footer,
        )
        val pageText = context.getString(
            R.string.analysis_export_page_indicator,
            page.pageNumber,
            page.pageCount,
        )
        canvas.drawText(
            pageText,
            PAGE_WIDTH - PAGE_MARGIN - paints.footer.measureText(pageText),
            baseline,
            paints.footer,
        )
    }

    private fun normalizeSeries(values: List<TimedPlotValue>): List<ExportPlotPoint> {
        if (values.isEmpty()) return emptyList()
        val firstTime = values.first().timeSeconds
        val lastTime = values.last().timeSeconds
        val span = (lastTime - firstTime).takeIf { it > 0f } ?: 1f
        return values.map { value ->
            ExportPlotPoint(
                x = ((value.timeSeconds - firstTime) / span).coerceIn(0f, 1f),
                y = value.value,
            )
        }
    }

    private fun drawLineSeries(
        canvas: Canvas,
        points: List<ExportPlotPoint>,
        plot: RectF,
        paint: Paint,
    ) {
        val path = Path()
        var previousValid = false
        points.forEach { point ->
            val normalizedX = point.x.coerceIn(0f, 1f)
            val normalizedY = point.y?.coerceIn(0f, 1f)
            val x = plot.left + plot.width() * normalizedX
            if (normalizedY == null) {
                previousValid = false
                return@forEach
            }
            val y = plot.bottom - plot.height() * normalizedY
            if (previousValid) path.lineTo(x, y) else path.moveTo(x, y)
            previousValid = true
        }
        canvas.drawPath(path, paint)
    }

    private fun drawWrappedText(
        canvas: Canvas,
        text: String,
        left: Float,
        baseline: Float,
        maxWidth: Float,
        paint: Paint,
        maxLines: Int,
    ) {
        var remaining = text.trim()
        var y = baseline
        var lineIndex = 0
        while (remaining.isNotEmpty() && lineIndex < maxLines) {
            val measuredCount = paint.breakText(remaining, true, maxWidth, null)
            if (measuredCount <= 0) break

            var end = measuredCount.coerceAtMost(remaining.length)
            if (end < remaining.length) {
                val lastSpace = remaining.lastIndexOf(' ', end - 1)
                if (lastSpace > 0) {
                    end = lastSpace
                }
            }

            var line = remaining.substring(0, end).trimEnd()
            remaining = remaining.substring(end).trimStart()
            if (lineIndex == maxLines - 1 && remaining.isNotEmpty()) {
                line = ellipsizeToWidth(line, maxWidth, paint)
            }
            canvas.drawText(line, left, y, paint)
            y += paint.textSize + 3f
            lineIndex += 1
        }
    }

    private fun ellipsizeToWidth(text: String, maxWidth: Float, paint: Paint): String {
        val suffix = "…"
        if (paint.measureText(text + suffix) <= maxWidth) return text + suffix
        val available = (maxWidth - paint.measureText(suffix)).coerceAtLeast(0f)
        val count = paint.breakText(text, true, available, null)
        return text.take(count).trimEnd() + suffix
    }

    private fun drawCenteredText(
        canvas: Canvas,
        text: String,
        centerX: Float,
        centerY: Float,
        paint: Paint,
    ) {
        val baseline = centerY - (paint.ascent() + paint.descent()) / 2f
        canvas.drawText(text, centerX - paint.measureText(text) / 2f, baseline, paint)
    }

    private fun ExportMetricOption.value(
        context: Context,
        result: PitcheeResult,
        statistics: ExportStatistics,
    ): ExportMetricValue = when (this) {
        ExportMetricOption.INPUT_DURATION -> ExportMetricValue(
            number(result.audio.inputSeconds),
            context.getString(R.string.analysis_export_unit_seconds),
        )
        ExportMetricOption.ANALYZED_DURATION -> ExportMetricValue(
            number(result.audio.analyzedSeconds),
            context.getString(R.string.analysis_export_unit_seconds),
        )
        ExportMetricOption.SPEECH_DURATION -> ExportMetricValue(
            number(result.vad.speechSeconds),
            context.getString(R.string.analysis_export_unit_seconds),
        )
        ExportMetricOption.SAMPLE_RATE -> ExportMetricValue(
            result.audio.sourceSampleRate.toString(),
            context.getString(R.string.analysis_export_unit_hertz),
        )
        ExportMetricOption.CHANNELS -> ExportMetricValue(
            context.getString(R.string.analysis_export_channels_value, result.audio.sourceChannels),
            "",
        )
        ExportMetricOption.MEAN_PITCH -> ExportMetricValue(
            number(statistics.meanHz),
            context.getString(R.string.analysis_export_unit_hertz),
        )
        ExportMetricOption.MEDIAN_PITCH -> ExportMetricValue(
            number(statistics.medianHz),
            context.getString(R.string.analysis_export_unit_hertz),
        )
        ExportMetricOption.PITCH_STANDARD_DEVIATION -> ExportMetricValue(
            number(result.f0.standardDeviationHz),
            context.getString(R.string.analysis_export_unit_hertz),
        )
        ExportMetricOption.PITCH_RANGE -> ExportMetricValue(
            range(context, statistics.pitchLow5Hz, statistics.pitchHigh95Hz),
            context.getString(R.string.analysis_export_unit_hertz_range),
        )
        ExportMetricOption.VOICED_WINDOWS -> ExportMetricValue(
            context.getString(R.string.analysis_export_voiced_windows_value, result.f0.voicedWindowCount),
            "",
        )
        ExportMetricOption.AVERAGE_LOUDNESS -> ExportMetricValue(
            number(statistics.averageLoudness),
            context.getString(R.string.analysis_export_unit_dbfs),
        )
        ExportMetricOption.MEDIAN_LOUDNESS -> ExportMetricValue(
            number(statistics.medianLoudness),
            context.getString(R.string.analysis_export_unit_dbfs),
        )
        ExportMetricOption.LOUDNESS_RANGE -> ExportMetricValue(
            range(context, statistics.loudnessLow5, statistics.loudnessHigh95),
            context.getString(R.string.analysis_export_unit_dbfs_range),
        )
        ExportMetricOption.FINAL_SCORE -> ExportMetricValue(
            result.composite.finalScore.roundToInt().toString(),
            context.getString(R.string.analysis_export_unit_points),
        )
        ExportMetricOption.STANDARD_SCORE -> ExportMetricValue(
            result.vfp.standardScore.roundToInt().toString(),
            context.getString(R.string.analysis_export_unit_points),
        )
        ExportMetricOption.NATURALNESS_SCORE -> ExportMetricValue(
            result.naturalness.score.roundToInt().toString(),
            context.getString(R.string.analysis_export_unit_points),
        )
    }

    private fun number(value: Double?): String =
        value?.takeIf { it.isFinite() }?.let { "%.1f".format(it) }
            ?: "—"

    private fun range(context: Context, low: Double?, high: Double?): String =
        if (low != null && high != null && low.isFinite() && high.isFinite()) {
            context.getString(R.string.analysis_export_value_range, number(low), number(high))
        } else {
            context.getString(R.string.analysis_export_no_value)
        }

    private data class ExportReportPage(
        val charts: List<ExportChartOption>,
        val metrics: List<ExportMetricOption>,
        val includeResultChart: Boolean,
        val pageNumber: Int = 1,
        val pageCount: Int = 1,
    )

    private data class ExportMetricValue(
        val value: String,
        val unit: String,
    )

    private data class TimedPlotValue(
        val timeSeconds: Float,
        val value: Float?,
    )

    private data class ExportPlotPoint(
        val x: Float,
        val y: Float?,
    )

    private data class LoudnessPoint(
        val timeSeconds: Float,
        val dbFS: Double?,
    )

    private data class ExportStatistics(
        val meanHz: Double?,
        val medianHz: Double?,
        val pitchLow5Hz: Double?,
        val pitchHigh95Hz: Double?,
        val pitchAxisMinHz: Double,
        val pitchAxisMaxHz: Double,
        val f0Above165Fraction: Double?,
        val isPass: Boolean,
        val averageLoudness: Double?,
        val medianLoudness: Double?,
        val loudnessLow5: Double?,
        val loudnessHigh95: Double?,
        val loudnessPoints: List<LoudnessPoint>,
    ) {
        companion object {
            fun from(result: PitcheeResult, audio: RecordedAudio?): ExportStatistics {
                val pitchValues = result.f0.windows.mapNotNull { it.f0Hz }
                    .filter { it.isFinite() && it > 0.0 }
                    .sorted()
                val pitchAxisRange = adaptivePitchRange(pitchValues)
                val loudness = loudnessFrom(audio, result)
                return ExportStatistics(
                    meanHz = result.f0.meanHz ?: pitchValues.averageOrNull(),
                    medianHz = percentile(0.50, pitchValues),
                    pitchLow5Hz = percentile(0.05, pitchValues),
                    pitchHigh95Hz = percentile(0.95, pitchValues),
                    pitchAxisMinHz = pitchAxisRange.first,
                    pitchAxisMaxHz = pitchAxisRange.second,
                    f0Above165Fraction = pitchValues
                        .takeIf { it.isNotEmpty() }
                        ?.let { values -> values.count { it >= 165.0 }.toDouble() / values.size },
                    isPass = result.composite.finalScore > 60.0 && !ScoreInsight.from(result).hasBottleneck,
                    averageLoudness = loudness.average,
                    medianLoudness = loudness.median,
                    loudnessLow5 = loudness.low5,
                    loudnessHigh95 = loudness.high95,
                    loudnessPoints = loudness.points,
                )
            }

            private fun loudnessFrom(
                audio: RecordedAudio?,
                result: PitcheeResult,
            ): LoudnessStatistics {
                if (audio == null || audio.waveform.isEmpty() || audio.durationSeconds <= 0.0) {
                    return LoudnessStatistics(null, null, null, null, emptyList())
                }
                val values = mutableListOf<Double>()
                val points = audio.waveform.mapIndexed { index, peak ->
                    val timeSeconds = ((index + 0.5) / audio.waveform.size * audio.durationSeconds).toFloat()
                    val db = 20.0 * log10(max(peak.toDouble(), 0.000_001)).coerceIn(-120.0, 0.0)
                    val inSpeech = result.vad.segments.any { segment ->
                        timeSeconds >= segment.startSeconds && timeSeconds <= segment.endSeconds
                    }
                    if (inSpeech) values += db
                    LoudnessPoint(timeSeconds, db.takeIf { inSpeech })
                }
                val sorted = values.sorted()
                return LoudnessStatistics(
                    average = values.averageOrNull(),
                    median = percentile(0.50, sorted),
                    low5 = percentile(0.05, sorted),
                    high95 = percentile(0.95, sorted),
                    points = points,
                )
            }

            private fun adaptivePitchRange(values: List<Double>): Pair<Double, Double> {
                if (values.isEmpty()) return 80.0 to 300.0
                val minimum = values.first()
                val maximum = values.last()
                val rawSpan = (maximum - minimum).coerceAtLeast(20.0)
                val padding = rawSpan * 0.10
                var axisMin = (minimum - padding).coerceAtLeast(0.0)
                var axisMax = maximum + padding
                if (axisMax - axisMin < 40.0) {
                    val center = (axisMax + axisMin) / 2.0
                    axisMin = (center - 20.0).coerceAtLeast(0.0)
                    axisMax = center + 20.0
                }
                return axisMin to axisMax
            }

            private fun percentile(fraction: Double, sortedValues: List<Double>): Double? {
                if (sortedValues.isEmpty()) return null
                if (sortedValues.size == 1) return sortedValues[0]
                val position = fraction * (sortedValues.size - 1)
                val lower = position.toInt()
                val upper = min(lower + 1, sortedValues.lastIndex)
                val fractionPart = position - lower
                return sortedValues[lower] + (sortedValues[upper] - sortedValues[lower]) * fractionPart
            }

            private fun List<Double>.averageOrNull(): Double? =
                if (isEmpty()) null else average()
        }
    }

    private data class LoudnessStatistics(
        val average: Double?,
        val median: Double?,
        val low5: Double?,
        val high95: Double?,
        val points: List<LoudnessPoint>,
    )

    private class ReportPaints {
        val reportTitle = paint(21f, Color.BLACK, Typeface.BOLD)
        val reportSubtitle = paint(9.5f, Color.argb(140, 0, 0, 0))
        val brand = paint(12f, Color.BLACK, Typeface.BOLD)
        val brandIcon = paint(11f, Color.WHITE, Typeface.BOLD)
        val section = paint(13f, Color.BLACK, Typeface.BOLD)
        val groupTitle = paint(10f, Color.argb(150, 0, 0, 0), Typeface.BOLD)
        val metricLabel = paint(9.2f, Color.BLACK, Typeface.BOLD)
        val metricValue = paint(9.2f, Color.argb(190, 0, 0, 0))
        val metricUnit = paint(8.2f, Color.argb(135, 0, 0, 0))
        val axisLabel = paint(7.5f, Color.argb(130, 0, 0, 0))
        val legend = paint(8f, Color.argb(170, 0, 0, 0))
        val note = paint(8f, Color.argb(145, 0, 0, 0))
        val footer = paint(9f, Color.argb(110, 0, 0, 0))
        val emptyTitle = paint(14f, Color.BLACK, Typeface.BOLD)
        val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = FEMININE_COLOR }
        val card = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(248, 244, 243) }
        val plotBackground = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(252, 250, 250) }
        val chartGrid = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(55, 0, 0, 0) }
        val divider = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(45, 0, 0, 0)
            strokeWidth = 1f
        }
        val frequencyLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = FREQUENCY_COLOR
            strokeWidth = 1.4f
            style = Paint.Style.STROKE
        }
        val vfpLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = VFP_COLOR
            strokeWidth = 1.4f
            style = Paint.Style.STROKE
        }
        val naturalnessLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = NATURALNESS_COLOR
            strokeWidth = 1.4f
            style = Paint.Style.STROKE
        }
        val pieRemainder = Paint(Paint.ANTI_ALIAS_FLAG)
        val piePrimary = Paint(Paint.ANTI_ALIAS_FLAG)
        val pieAbove = Paint(Paint.ANTI_ALIAS_FLAG)
        val pieBelow = Paint(Paint.ANTI_ALIAS_FLAG)
        val pieValue = paint(20f, Color.rgb(58, 31, 42), Typeface.BOLD)
        val pieValueLight = paint(20f, Color.WHITE, Typeface.BOLD)
        val pieValueDark = paint(18f, Color.rgb(58, 31, 42), Typeface.BOLD)
        val pieCaption = paint(8.5f, Color.rgb(58, 31, 42), Typeface.BOLD)
        val pieSliceDarkText = paint(9.2f, Color.rgb(58, 31, 42), Typeface.BOLD)
        val pieSliceLightText = paint(9.2f, Color.WHITE, Typeface.BOLD)
        val passBackground = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PASS_BACKGROUND_COLOR }
        val passIcon = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PASS_GREEN_COLOR }
        val passCheck = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 2f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val passText = paint(10.5f, PASS_GREEN_COLOR, Typeface.BOLD)
    }

    private fun paint(
        textSize: Float,
        color: Int,
        style: Int = Typeface.NORMAL,
    ): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.textSize = textSize
        this.color = color
        typeface = Typeface.create(Typeface.DEFAULT, style)
    }

    private const val PITCHEE_DEV_URL = "https://pitchee.dev"
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val PAGE_MARGIN = 42f
    private const val HEADER_HEIGHT = 88f
    private const val SECTION_HEIGHT = 30f
    private const val CURVES_CHART_HEIGHT = 240f
    private const val RESULT_CHART_HEIGHT = 208f
    private const val METRIC_GROUP_HEADING_HEIGHT = 26f
    private const val METRIC_ROW_HEIGHT = 26f
    private const val METRIC_COLUMN_WIDTH = (PAGE_WIDTH - PAGE_MARGIN * 2f - 16f) / 2f
    private const val PASS_BANNER_HEIGHT = 38f
    private const val AVAILABLE_BODY_HEIGHT = PAGE_HEIGHT - PAGE_MARGIN * 2 - HEADER_HEIGHT - 34f
    private const val FEMININE_COLOR = 0xFFFFB6C1.toInt()
    private const val MASCULINE_COLOR = 0xFF6495ED.toInt()
    private const val FREQUENCY_COLOR = 0xFF215CD1.toInt()
    private const val VFP_COLOR = 0xFF25A6A0.toInt()
    private const val NATURALNESS_COLOR = 0xFF2F8F5B.toInt()
    private const val PIE_REMAINDER_COLOR = 0xFFE8E2E1.toInt()
    private const val PASS_GREEN_COLOR = 0xFF1F6F43.toInt()
    private const val PASS_BACKGROUND_COLOR = 0xFFD9EBDD.toInt()
}
