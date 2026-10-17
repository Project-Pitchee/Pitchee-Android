package io.rovly.pitchee.core

import org.junit.Assert.assertEquals
import org.junit.Test
import space.pitchee.core.PitcheeProgress
import space.pitchee.core.PitcheeProgressStage

class PitcheeProgressTest {
    @Test
    fun embeddingWeightsMatchMeasuredRuntime() {
        val vfpStart = PitcheeProgress(
            stage = PitcheeProgressStage.EXTRACTING_VFP_EMBEDDINGS,
            completed = 0,
            total = 1,
            stageFraction = 0f,
        ).overallFraction
        val vfpEnd = PitcheeProgress(
            stage = PitcheeProgressStage.EXTRACTING_VFP_EMBEDDINGS,
            completed = 1,
            total = 1,
            stageFraction = 1f,
        ).overallFraction
        val naturalnessStart = PitcheeProgress(
            stage = PitcheeProgressStage.EXTRACTING_NATURALNESS_EMBEDDINGS,
            completed = 0,
            total = 1,
            stageFraction = 0f,
        ).overallFraction
        val naturalnessEnd = PitcheeProgress(
            stage = PitcheeProgressStage.EXTRACTING_NATURALNESS_EMBEDDINGS,
            completed = 1,
            total = 1,
            stageFraction = 1f,
        ).overallFraction

        assertEquals(0.595f, vfpEnd - vfpStart, 0.0001f)
        assertEquals(0.364f, naturalnessEnd - naturalnessStart, 0.0001f)
    }

    @Test
    fun completedStageReachesFullProgress() {
        val progress = PitcheeProgress(
            stage = PitcheeProgressStage.COMPLETED,
            completed = 1,
            total = 1,
            stageFraction = 1f,
        )

        assertEquals(1f, progress.overallFraction, 0.0001f)
    }
}
