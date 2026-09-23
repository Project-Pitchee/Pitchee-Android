package io.rovly.pitchee.data

import android.content.Context
import android.net.Uri
import android.util.Log
import io.rovly.pitchee.R
import java.io.Closeable
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import space.pitchee.core.PitcheeAnalyzer
import space.pitchee.core.PitcheePhase
import space.pitchee.core.PitcheeProgress
import space.pitchee.core.PitcheeRealtimeF0

/**
 * App-facing PitcheeCore API. Call [analyze] for an audio Uri or [analyzePcm]
 * when the caller already has interleaved Float32 PCM in the range [-1, 1].
 */
class PitcheeRepository(
    context: Context,
    private val threads: Int = DEFAULT_THREADS,
) : Closeable {
    private val appContext = context.applicationContext
    private val lock = Any()
    private var analyzer: PitcheeAnalyzer? = null

    suspend fun analyze(
        uri: Uri,
        maxSeconds: Int? = null,
        onPhase: ((PitcheePhase) -> Unit)? = null,
        onProgress: ((PitcheeProgress) -> Unit)? = null,
    ): PitcheeResult {
        val audio = decode(uri, maxSeconds, onPhase)
        return analyzePcm(
            samples = audio.samples,
            sampleRate = audio.sampleRate,
            channels = audio.channels,
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

    suspend fun analyzePcm(
        samples: FloatArray,
        sampleRate: Int,
        channels: Int,
        onPhase: ((PitcheePhase) -> Unit)? = null,
        onProgress: ((PitcheeProgress) -> Unit)? = null,
    ): PitcheeResult = withContext(Dispatchers.Default) {
        require(samples.isNotEmpty()) { appContext.getString(R.string.error_pcm_empty) }
        require(sampleRate > 0) { appContext.getString(R.string.error_sample_rate_invalid) }
        require(channels > 0 && samples.size % channels == 0) {
            appContext.getString(R.string.error_pcm_channels_invalid)
        }

        synchronized(lock) {
            val engine = analyzer
            if (engine == null) {
                onPhase?.invoke(PitcheePhase.PREPARING_MODELS)
            }
            val readyEngine = engine ?: PitcheeAnalyzer.create(
                modelDirectory = prepareModels().absolutePath,
                threads = threads,
            ).also { analyzer = it }

            val json = readyEngine.analyze(
                samples = samples,
                sampleRate = sampleRate,
                channels = channels,
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

    suspend fun createRealtimeF0(): PitcheeRealtimeF0 = withContext(Dispatchers.Default) {
        synchronized(lock) {
            val readyEngine = analyzer ?: PitcheeAnalyzer.create(
                modelDirectory = prepareModels().absolutePath,
                threads = threads,
            ).also { analyzer = it }
            readyEngine.createRealtimeF0()
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
        const val DEFAULT_THREADS = 2
        const val TAG = "PitcheeRepository"
        const val MODELS_DIRECTORY = "pitchee-models"
        const val READY_MARKER = ".ready"
        // Changes when packaged model bytes change, even if Core's model version does not.
        const val MODEL_CACHE_VERSION = "2026-09-native-windows"

        val MODEL_FILES = listOf(
            "SileroVAD.onnx",
            "ECAPAFrontend.onnx",
            "ECAPA.onnx",
            "VFPHead.onnx",
            "SwiftF0.onnx",
            "Naturalness.onnx",
            "manifest.json",
        )
    }
}
