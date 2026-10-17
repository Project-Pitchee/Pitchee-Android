package io.rovly.pitchee.data.repository

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.rovly.pitchee.data.model.FeminineTimeline
import io.rovly.pitchee.data.model.scoreSpeechSegments
import io.rovly.pitchee.data.repository.PitcheeRepository
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import space.pitchee.core.PitcheeProgressStage

@RunWith(AndroidJUnit4::class)
class PitcheeRepositoryInstrumentedTest {
    @Test
    fun emitsRealtimeF0FramesForTone() = runBlocking {
        val repository = PitcheeRepository(
            InstrumentationRegistry.getInstrumentation().targetContext
        )
        val stream = repository.createRealtimeF0()
        try {
            val samples = FloatArray(16_000) { index ->
                sin(2.0 * PI * 180.0 * index / 16_000.0).toFloat() * 0.35f
            }
            var frameCount = 0
            var voicedCount = 0
            samples.asList().chunked(256).forEach { chunk ->
                stream.process(chunk.toFloatArray()) { _, _, _, voiced ->
                    frameCount++
                    if (voiced) voicedCount++
                }
            }

            assertTrue("Realtime stream returned no frames", frameCount > 0)
            assertTrue("Expected voiced F0 frames", voicedCount > 0)
        } finally {
            stream.close()
            repository.close()
        }
    }

    @Test
    fun runsStandaloneVadAndVoiceHealth() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val repository = PitcheeRepository(context)
        val speech = File(context.cacheDir, "pitchee-voice-health.m4a")
        try {
            instrumentation.context.assets.open("pitchee-speech.m4a").use { input ->
                speech.outputStream().use(input::copyTo)
            }
            val pcm = repository.decode(Uri.fromFile(speech))
            val segments = repository.detectVad(
                samples = pcm.samples,
                sampleRate = pcm.sampleRate,
                channels = pcm.channels,
            )
            assertTrue("VAD returned no speech segments", segments.isNotEmpty())
            assertTrue(
                "VAD segment timestamps are invalid",
                segments.all { it.endSeconds > it.startSeconds },
            )

            val health = repository.analyzeVoiceHealth(
                samples = pcm.samples,
                sampleRate = pcm.sampleRate,
                channels = pcm.channels,
            )
            assertTrue(
                "Voice health score is outside 0..100: ${health.healthScore}",
                health.healthScore in 0.0..100.0,
            )
            assertTrue(
                "Voice health decision threshold is outside 0..100: ${health.decisionThreshold}",
                health.decisionThreshold in 0.0..100.0,
            )
        } finally {
            speech.delete()
            repository.close()
        }
    }

    @Test
    fun handlesPcmWavAndSuccessfulSpeechAnalysis() = runBlocking {
        val repository = PitcheeRepository(
            InstrumentationRegistry.getInstrumentation().targetContext
        )
        try {
            val error = runCatching {
                repository.analyzePcm(
                    samples = FloatArray(16_000),
                    sampleRate = 16_000,
                    channels = 1,
                )
            }.exceptionOrNull()

            assertNotNull("Silence should be rejected by VAD", error)
            assertTrue(
                "Unexpected native error: ${error?.message}",
                error?.message.orEmpty().contains("no speech", ignoreCase = true),
            )

            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val wav = File(context.cacheDir, "pitchee-silence.wav")
            try {
                writeSilenceWav(wav)
                val fileError = runCatching {
                    repository.analyze(Uri.fromFile(wav))
                }.exceptionOrNull()
                assertNotNull("Silent WAV should be rejected by VAD", fileError)
                assertTrue(
                    "Unexpected WAV error: ${fileError?.message}",
                    fileError?.message.orEmpty().contains("no speech", ignoreCase = true),
                )
            } finally {
                wav.delete()
            }

            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val speech = File(context.cacheDir, "pitchee-speech.m4a")
            try {
                instrumentation.context.assets.open("pitchee-speech.m4a").use { input ->
                    speech.outputStream().use(input::copyTo)
                }
                val progressUpdates = mutableListOf<space.pitchee.core.PitcheeProgress>()
                val pcm = repository.decode(uri = Uri.fromFile(speech))
                val result = repository.analyzePcm(
                    samples = pcm.samples,
                    sampleRate = pcm.sampleRate,
                    channels = pcm.channels,
                    onProgress = { progressUpdates += it },
                )
                assertTrue(
                    "Final score should be in [0, 100] but was ${result.composite.finalScore}",
                    result.composite.finalScore in 0.0..100.0,
                )
                assertTrue(
                    "Expected the synthetic speech to produce a VFP window",
                    result.vfp.windowCount > 0,
                )
                assertEquals(0.05, result.f0.windowSeconds, 0.0001)
                val segmentScores = scoreSpeechSegments(result)
                assertEquals(result.vad.segments.size, segmentScores.size)
                assertTrue(
                    "Every VAD segment should receive a score in [0, 100]",
                    segmentScores.all { it.score in 0.0..100.0 },
                )
                assertTrue(
                    "Native detailed progress was not invoked: ${progressUpdates.size}",
                    progressUpdates.any {
                        it.stage == PitcheeProgressStage.DETECTING_SPEECH ||
                            it.stage == PitcheeProgressStage.EXTRACTING_VFP_EMBEDDINGS
                    } && progressUpdates.any {
                        it.stage == PitcheeProgressStage.COMPLETED
                    },
                )
                val timeline = FeminineTimeline.from(result, pcm.durationSeconds)
                val speechStart = result.vad.segments.first().startSeconds
                assertNotNull(
                    "Expected a time-level VFP score inside detected speech",
                    timeline.scoreAt(speechStart + 0.1),
                )
            } finally {
                speech.delete()
            }
        } finally {
            repository.close()
        }
    }

    private fun writeSilenceWav(file: File) {
        val sampleRate = 16_000
        val sampleCount = sampleRate
        val dataSize = sampleCount * Short.SIZE_BYTES
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray())
            putInt(36 + dataSize)
            put("WAVE".toByteArray())
            put("fmt ".toByteArray())
            putInt(16)
            putShort(1)
            putShort(1)
            putInt(sampleRate)
            putInt(sampleRate * Short.SIZE_BYTES)
            putShort(Short.SIZE_BYTES.toShort())
            putShort(16)
            put("data".toByteArray())
            putInt(dataSize)
        }
        file.outputStream().use { output ->
            output.write(header.array())
            output.write(ByteArray(dataSize))
        }
    }
}
