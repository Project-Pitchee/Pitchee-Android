package io.rovly.pitchee.feature.analysis

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.rovly.pitchee.data.model.PitcheeResult
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnalysisExporterTest {
    @Test
    fun exportSamplePdf() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val result = PitcheeResult.fromJson(SAMPLE_RESULT)
        val generated = AnalysisExporter.createPdf(
            context = context,
            result = result,
            audio = null,
            timestampMillis = 1_800_000_000_000,
            selection = ExportSelection(),
        )
        val output = File(context.getExternalFilesDir(null), "pitchee-export-test.pdf")
        generated.copyTo(output, overwrite = true)
        assertTrue(output.length() > 0)
        val previews = AnalysisExporter.renderPreview(context, output)
        assertTrue(previews.isNotEmpty())
        assertTrue(AnalysisExporter.savePreviewImages(context, previews, 1_800_000_000_000) > 0)
    }

    private companion object {
        val SAMPLE_RESULT = """
        {
          "schema_version": 3,
          "model_version": "layout-test",
          "score_profile": "feminization",
          "audio": {
            "source_sample_rate": 48000,
            "source_channels": 1,
            "input_seconds": 12.0,
            "analyzed_seconds": 12.0
          },
          "vad": {
            "segment_count": 2,
            "speech_seconds": 10.5,
            "silero_segment_count": 2,
            "discarded_breath_like_count": 0,
            "trimmed_segment_count": 0,
            "segments": [
              {"start_seconds": 0.4, "end_seconds": 6.0, "speech_start_seconds": 0.5, "speech_end_seconds": 5.9},
              {"start_seconds": 6.6, "end_seconds": 11.6, "speech_start_seconds": 6.7, "speech_end_seconds": 11.5}
            ]
          },
          "f0": {
            "window_seconds": 0.05,
            "mean_hz": 202.4,
            "standard_deviation_hz": 31.2,
            "voiced_frame_count": 880,
            "voiced_window_count": 16,
            "windows": [
              {"start_seconds": 0.5, "end_seconds": 1.1, "f0_hz": 148.0},
              {"start_seconds": 1.2, "end_seconds": 1.8, "f0_hz": 154.0},
              {"start_seconds": 1.9, "end_seconds": 2.5, "f0_hz": null},
              {"start_seconds": 2.6, "end_seconds": 3.2, "f0_hz": 160.0},
              {"start_seconds": 3.3, "end_seconds": 3.9, "f0_hz": 205.0},
              {"start_seconds": 4.0, "end_seconds": 4.6, "f0_hz": 218.0},
              {"start_seconds": 4.7, "end_seconds": 5.3, "f0_hz": 236.0},
              {"start_seconds": 5.4, "end_seconds": 6.0, "f0_hz": 248.0},
              {"start_seconds": 6.7, "end_seconds": 7.3, "f0_hz": 255.0},
              {"start_seconds": 7.4, "end_seconds": 8.0, "f0_hz": 242.0},
              {"start_seconds": 8.1, "end_seconds": 8.7, "f0_hz": 229.0},
              {"start_seconds": 8.8, "end_seconds": 9.4, "f0_hz": 216.0},
              {"start_seconds": 9.5, "end_seconds": 10.1, "f0_hz": 201.0},
              {"start_seconds": 10.2, "end_seconds": 10.8, "f0_hz": 158.0},
              {"start_seconds": 10.9, "end_seconds": 11.5, "f0_hz": 151.0}
            ]
          },
          "vfp": {
            "vfp_standard_score": 84.0,
            "window_count": 6,
            "window_duration_seconds": 1.5,
            "windows": [
              {"start_seconds": 0.5, "end_seconds": 2.0, "vfp_standard_score": 72.0},
              {"start_seconds": 2.0, "end_seconds": 3.5, "vfp_standard_score": 78.0},
              {"start_seconds": 3.5, "end_seconds": 5.0, "vfp_standard_score": 82.0},
              {"start_seconds": 6.7, "end_seconds": 8.2, "vfp_standard_score": 86.0},
              {"start_seconds": 8.2, "end_seconds": 9.7, "vfp_standard_score": 88.0},
              {"start_seconds": 9.7, "end_seconds": 11.2, "vfp_standard_score": 91.0}
            ]
          },
          "naturalness": {
            "score": 79.0,
            "window_count": 6,
            "window_duration_seconds": 1.5,
            "windows": [
              {"start_seconds": 0.5, "end_seconds": 2.0, "score": 68.0},
              {"start_seconds": 2.0, "end_seconds": 3.5, "score": 73.0},
              {"start_seconds": 3.5, "end_seconds": 5.0, "score": 76.0},
              {"start_seconds": 6.7, "end_seconds": 8.2, "score": 80.0},
              {"start_seconds": 8.2, "end_seconds": 9.7, "score": 84.0},
              {"start_seconds": 9.7, "end_seconds": 11.2, "score": 87.0}
            ]
          },
          "composite": {
            "base_score": 84.0,
            "final_score": 82.4,
            "cap": 100.0,
            "rule": "continuous",
            "limited": false,
            "boosted": false
          }
        }
        """.trimIndent()
    }
}
