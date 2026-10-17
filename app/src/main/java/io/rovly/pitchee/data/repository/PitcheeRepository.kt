package io.rovly.pitchee.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import io.rovly.pitchee.R
import io.rovly.pitchee.data.audio.AudioFileDecoder
import io.rovly.pitchee.data.audio.PcmAudio
import io.rovly.pitchee.data.model.PitcheeResult
import io.rovly.pitchee.data.preferences.CorePreferences
import java.io.Closeable
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import space.pitchee.core.PitcheeAnalyzer
import space.pitchee.core.PitcheePhase
import space.pitchee.core.PitcheeProgress
import space.pitchee.core.PitcheeRealtimeF0
import space.pitchee.core.PitcheeScoreProfile
import space.pitchee.core.PitcheeThresholds
import space.pitchee.core.PitcheeVadSegment
import space.pitchee.core.PitcheeVoiceHealthProgress
import space.pitchee.core.PitcheeVoiceHealthResult

/**
 * App-facing PitcheeCore API. Call [analyze] for an audio Uri or [analyzePcm]
 * when the caller already has interleaved Float32 PCM in the range [-1, 1].
 */
class PitcheeRepository(
    context: Context,
    private val corePreferences: CorePreferences = CorePreferences(context),
) : Closeable {
    private val appContext = context.applicationContext
    private val lock = Any()
    private var analyzer: PitcheeAnalyzer? = null
    private var analyzerThresholds: PitcheeThresholds? = null
    private var liveRealtimeStreams = 0

    /**
     * Core reads the F0 thresholds once, at analyzer creation. Rebuild when the
     * user changed them in settings, but never while a realtime stream still
     * holds a pointer into the current analyzer.
     */
    private fun needsNewAnalyzer(): Boolean {
        val wanted = corePreferences.thresholds()
        return analyzer == null ||
            (wanted != analyzerThresholds && liveRealtimeStreams == 0)
    }

    private fun analyzer(): PitcheeAnalyzer {
        val existing = analyzer
        if (!needsNewAnalyzer() && existing != null) return existing
        existing?.close()
        val wanted = corePreferences.thresholds()
        return PitcheeAnalyzer.create(
            modelDirectory = prepareModels().absolutePath,
            thresholds = wanted,
        ).also {
            analyzer = it
            analyzerThresholds = wanted
        }
    }

    suspend fun analyze(
        uri: Uri,
        maxSeconds: Int? = null,
        scoreProfile: PitcheeScoreProfile = PitcheeScoreProfile.FEMINIZATION,
        onPhase: ((PitcheePhase) -> Unit)? = null,
        onProgress: ((PitcheeProgress) -> Unit)? = null,
    ): PitcheeResult {
        val audio = decode(uri, maxSeconds, onPhase)
        return analyzePcm(
            samples = audio.samples,
            sampleRate = audio.sampleRate,
            channels = audio.channels,
            scoreProfile = scoreProfile,
            onPhase = onPhase,
            onProgress = onProgress,
        )
    }

    suspend fun decode(
        uri: Uri,
        maxSeconds: Int? = null,
        onPhase: ((PitcheePhase) -> Unit)? = null,
    ): PcmAudio {
        onPhase?.invoke(PitcheePhase.LOADING_AUDIO)
        return AudioFileDecoder.decode(appContext, uri, maxSeconds)
    }

    suspend fun detectVad(
        samples: FloatArray,
        sampleRate: Int,
        channels: Int,
    ): List<PitcheeVadSegment> = withContext(Dispatchers.Default) {
        require(samples.isNotEmpty()) { appContext.getString(R.string.error_pcm_empty) }
        require(sampleRate > 0) { appContext.getString(R.string.error_sample_rate_invalid) }
        require(channels > 0 && samples.size % channels == 0) {
            appContext.getString(R.string.error_pcm_channels_invalid)
        }
        synchronized(lock) { analyzer().detectVad(samples, sampleRate, channels) }
    }

    suspend fun analyzeVoiceHealth(
        samples: FloatArray,
        sampleRate: Int,
        channels: Int,
        onProgress: ((PitcheeVoiceHealthProgress) -> Unit)? = null,
    ): PitcheeVoiceHealthResult = withContext(Dispatchers.Default) {
        require(samples.isNotEmpty()) { appContext.getString(R.string.error_pcm_empty) }
        require(sampleRate > 0) { appContext.getString(R.string.error_sample_rate_invalid) }
        require(channels > 0 && samples.size % channels == 0) {
            appContext.getString(R.string.error_pcm_channels_invalid)
        }
        synchronized(lock) {
            analyzer().runVoiceHealth(
                samples = samples,
                sampleRate = sampleRate,
                channels = channels,
                onProgress = onProgress,
            )
        }
    }

    suspend fun analyzeVoiceHealth(
        uri: Uri,
        onProgress: ((PitcheeVoiceHealthProgress) -> Unit)? = null,
    ): PitcheeVoiceHealthResult {
        val audio = decode(uri)
        return analyzeVoiceHealth(
            samples = audio.samples,
            sampleRate = audio.sampleRate,
            channels = audio.channels,
            onProgress = onProgress,
        )
    }

    suspend fun analyzePcm(
        samples: FloatArray,
        sampleRate: Int,
        channels: Int,
        scoreProfile: PitcheeScoreProfile = PitcheeScoreProfile.FEMINIZATION,
        onPhase: ((PitcheePhase) -> Unit)? = null,
        onProgress: ((PitcheeProgress) -> Unit)? = null,
    ): PitcheeResult = withContext(Dispatchers.Default) {
        require(samples.isNotEmpty()) { appContext.getString(R.string.error_pcm_empty) }
        require(sampleRate > 0) { appContext.getString(R.string.error_sample_rate_invalid) }
        require(channels > 0 && samples.size % channels == 0) {
            appContext.getString(R.string.error_pcm_channels_invalid)
        }

        synchronized(lock) {
            if (needsNewAnalyzer()) {
                onPhase?.invoke(PitcheePhase.PREPARING_MODELS)
            }
            val readyEngine = analyzer()

            val json = readyEngine.analyze(
                samples = samples,
                sampleRate = sampleRate,
                channels = channels,
                scoreProfile = scoreProfile,
                onPhase = onPhase,
                onProgress = onProgress,
            )
            val result = PitcheeResult.fromJson(json)
            Log.i(
                TAG,
                "Analysis completed: score=${result.composite.finalScore}, " +
                    "speech=${result.vad.speechSeconds}s, " +
                    "windows=${result.vfp.windowCount}",
            )
            result
        }
    }

    /**
     * Core defaults the realtime stream to a 320 ms analysis window per
     * inference, which on slower phones costs more than the 16 ms hop it runs at.
     * The chart only needs a window long enough to cover the new frames, so the
     * realtime stream uses a shorter one; file analysis still feeds Core the whole
     * recording, so scores are unaffected.
     */
    suspend fun createRealtimeF0(
        contextSamples: Int = REALTIME_CONTEXT_SAMPLES,
        hopSamples: Int = 0,
        outputRateHz: Double = 0.0,
        startTimestampSeconds: Double = 0.0,
    ): PitcheeRealtimeF0 = withContext(Dispatchers.Default) {
        synchronized(lock) {
            val readyEngine = analyzer()
            liveRealtimeStreams++
            try {
                readyEngine.createRealtimeF0(
                    contextSamples = contextSamples,
                    hopSamples = hopSamples,
                    outputRateHz = outputRateHz,
                    startTimestampSeconds = startTimestampSeconds,
                    onClosed = {
                        synchronized(lock) {
                            liveRealtimeStreams = (liveRealtimeStreams - 1).coerceAtLeast(0)
                        }
                    },
                )
            } catch (error: Throwable) {
                liveRealtimeStreams = (liveRealtimeStreams - 1).coerceAtLeast(0)
                throw error
            }
        }
    }

    override fun close() {
        synchronized(lock) {
            analyzer?.close()
            analyzer = null
        }
    }

    private fun prepareModels(): File {
        val target = File(appContext.noBackupFilesDir, MODELS_DIRECTORY)
        if (File(target, READY_MARKER).readTextOrNull() == MODEL_CACHE_VERSION) return target

        val staging = File(appContext.cacheDir, "$MODELS_DIRECTORY-staging")
        staging.deleteRecursively()
        check(staging.mkdirs()) { appContext.getString(R.string.error_model_cache_create) }
        try {
            MODEL_FILES.forEach { name ->
                appContext.assets.open(name).use { input ->
                    File(staging, name).outputStream().use(input::copyTo)
                }
            }
            File(staging, READY_MARKER).writeText(MODEL_CACHE_VERSION)
            target.deleteRecursively()
            check(staging.renameTo(target)) { appContext.getString(R.string.error_model_cache_commit) }
        } catch (error: Throwable) {
            staging.deleteRecursively()
            throw error
        }
        return target
    }

    private fun File.readTextOrNull(): String? =
        runCatching { if (isFile) readText() else null }.getOrNull()

    private companion object {
        const val TAG = "PitcheeRepository"
        const val MODELS_DIRECTORY = "pitchee-models"
        const val READY_MARKER = ".ready"
        // Changes when packaged model bytes change, even if Core's model version does not.
        const val MODEL_CACHE_VERSION = "2026-10-ncnn-fp16"

        /** 160 ms at 16 kHz: enough history for the frames emitted between hops. */
        const val REALTIME_CONTEXT_SAMPLES = 2560

        // Android runs the ncnn backend, so only the .ncnn.param/bin pairs are
        // packaged; the ONNX originals are not shipped.
        val MODEL_FILES = listOf(
            "SileroVAD.ncnn.param",
            "SileroVAD.ncnn.bin",
            "ECAPAFrontend.ncnn.param",
            "ECAPAFrontend.ncnn.bin",
            "ECAPA.ncnn.param",
            "ECAPA.ncnn.bin",
            "VFPHead.ncnn.param",
            "VFPHead.ncnn.bin",
            "SwiftF0.ncnn.param",
            "SwiftF0.ncnn.bin",
            "Naturalness.ncnn.param",
            "Naturalness.ncnn.bin",
            "manifest.json",
        )
    }
}
