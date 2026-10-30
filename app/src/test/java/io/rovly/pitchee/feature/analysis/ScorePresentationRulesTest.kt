package io.rovly.pitchee.feature.analysis

import io.rovly.pitchee.R
import org.junit.Assert.assertEquals
import org.junit.Test
import space.pitchee.core.PitcheeScoreProfile

class ScorePresentationRulesTest {
    @Test
    fun femininePitchScoreUsesRequestedFormula() {
        assertEquals(
            50.0,
            f0ProgressScore(160.0, PitcheeScoreProfile.FEMINIZATION),
            0.001,
        )
        assertEquals(
            100.0,
            f0ProgressScore(220.0, PitcheeScoreProfile.FEMINIZATION),
            0.001,
        )
        assertEquals(
            0.0,
            f0ProgressScore(100.0, PitcheeScoreProfile.FEMINIZATION),
            0.001,
        )
    }

    @Test
    fun valuesBelowFiftyAlwaysShowNotPassed() {
        assertEquals(
            R.string.score_tier_fail,
            scoreTier(score = 49.9, passed = false).resourceId,
        )
        assertEquals(
            R.string.score_tier_fail,
            scoreTier(score = 49.9, passed = true).resourceId,
        )
    }

    @Test
    fun valuesBelowFiftyAreNeverConsideredPassed() {
        assertEquals(false, metricPassed(score = 49.9, limited = false))
        assertEquals(false, metricPassed(score = 49.9, limited = true))
        assertEquals(true, metricPassed(score = 50.0, limited = false))
        assertEquals(false, metricPassed(score = 50.0, limited = true))
    }

    @Test
    fun scoresAtFiftyStillUseLimiterAndTierLabels() {
        assertEquals(
            R.string.score_tier_limiter,
            scoreTier(score = 50.0, passed = false).resourceId,
        )
        assertEquals(
            R.string.score_tier_fair,
            scoreTier(score = 50.0, passed = true).resourceId,
        )
    }
}
