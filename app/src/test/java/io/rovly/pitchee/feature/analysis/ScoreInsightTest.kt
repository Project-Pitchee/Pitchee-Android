package io.rovly.pitchee.feature.analysis

import io.rovly.pitchee.R
import io.rovly.pitchee.data.model.AudioMetrics
import io.rovly.pitchee.data.model.CompositeScore
import io.rovly.pitchee.data.model.F0Metrics
import io.rovly.pitchee.data.model.NaturalnessMetrics
import io.rovly.pitchee.data.model.NaturalnessWindow
import io.rovly.pitchee.data.model.PitcheeResult
import io.rovly.pitchee.data.model.VfpMetrics
import io.rovly.pitchee.data.model.VfpWindow
import io.rovly.pitchee.data.model.VoiceActivity
import io.rovly.pitchee.feature.analysis.ScoreInsight
import io.rovly.pitchee.feature.analysis.ScoreRuleState
import io.rovly.pitchee.ui.common.ScoreMetric
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoreInsightTest {
    @Test
    fun producesF0BottleneckInsight() {
        val result = result(
            standardScore = 80.0,
            naturalnessScore = 90.0,
            meanF0 = 150.0,
            composite = CompositeScore(
                baseScore = 70.0,
                finalScore = 59.0,
                cap = 59.0,
                rule = "low_f0_natural_cap",
                limited = true,
                boosted = false,
            ),
        )

        val insight = ScoreInsight.from(result)

        assertEquals(ScoreMetric.F0, insight.bottleneckMetric)
        assertEquals(R.string.insight_desc_bottleneck_f0, insight.bottleneckDescription?.resourceId)
    }

    @Test
    fun passBoostHasNoMainBottleneck() {
        val result = result(
            standardScore = 80.0,
            naturalnessScore = 80.0,
            meanF0 = 180.0,
            composite = CompositeScore(
                baseScore = 60.0,
                finalScore = 75.0,
                cap = null,
                rule = "pass_boost",
                limited = false,
                boosted = true,
            ),
        )

        val insight = ScoreInsight.from(result)

        assertFalse(insight.hasBottleneck)
    }

    @Test
    fun explainsLowF0CapAndIdentifiesF0AsBottleneck() {
        val result = result(
            standardScore = 80.0,
            naturalnessScore = 90.0,
            meanF0 = 150.0,
            composite = CompositeScore(
                baseScore = 70.0,
                finalScore = 59.0,
                cap = 59.0,
                rule = "low_f0_natural_cap",
                limited = true,
                boosted = false,
            ),
        )

        val insight = ScoreInsight.from(result)

        assertEquals(ScoreRuleState.CAPPED, insight.ruleState)
        assertEquals(ScoreMetric.F0, insight.bottleneckMetric)
        assertEquals(R.string.insight_impact_cap_reduced_format, insight.ruleImpact.resourceId)
    }

    @Test
    fun identifiesNaturalnessAsContinuousScoreBottleneck() {
        val result = result(
            standardScore = 80.0,
            naturalnessScore = 20.0,
            meanF0 = 180.0,
            composite = CompositeScore(
                baseScore = 45.0,
                finalScore = 45.0,
                cap = null,
                rule = "continuous",
                limited = false,
                boosted = false,
            ),
        )

        val insight = ScoreInsight.from(result)

        assertEquals(ScoreRuleState.CONTINUOUS, insight.ruleState)
        assertEquals(ScoreMetric.NATURALNESS, insight.bottleneckMetric)
        assertEquals(R.string.insight_impact_no_cap_boost, insight.ruleImpact.resourceId)
    }

    private fun result(
        standardScore: Double,
        naturalnessScore: Double,
        meanF0: Double?,
        composite: CompositeScore,
    ) = PitcheeResult(
        schemaVersion = 2,
        modelVersion = "test",
        audio = AudioMetrics(16_000, 1, 2.0, 2.0),
        vad = VoiceActivity(1, 2.0, 1, 0, 0, emptyList()),
        f0 = F0Metrics(
            windowSeconds = 0.1,
            meanHz = meanF0,
            standardDeviationHz = 4.0,
            voicedFrameCount = 100,
            voicedWindowCount = 10,
            windows = emptyList(),
        ),
        vfp = VfpMetrics(
            standardScore = standardScore,
            windowCount = 1,
            windowDurationSeconds = 1.0,
            windows = listOf(VfpWindow(0.0, 1.0, standardScore)),
        ),
        naturalness = NaturalnessMetrics(
            score = naturalnessScore,
            windowCount = 1,
            windowDurationSeconds = 1.0,
            windows = listOf(NaturalnessWindow(0.0, 1.0, naturalnessScore)),
        ),
        composite = composite,
        rawJson = "{}",
    )
}
