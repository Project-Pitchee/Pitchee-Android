package space.pitchee.core

import java.io.Closeable

enum class PitcheePhase(val nativeValue: Int) {
    PREPARING_MODELS(0),
    LOADING_AUDIO(1),
    ANALYZING(2),
    COMPLETED(3),
    ;

    internal companion object {
        fun fromNative(value: Int): PitcheePhase =
            entries.firstOrNull { it.nativeValue == value } ?: ANALYZING
    }
}

fun interface PitcheePhaseCallback {
    fun onPhase(nativeValue: Int)
}

enum class PitcheeProgressStage(val nativeValue: Int) {
    LOADING_AUDIO(0),
    RESAMPLING_AUDIO(1),
    ANALYZING_F0(2),
    DETECTING_SPEECH(3),
    PREPARING_VFP_WINDOWS(4),
    EXTRACTING_VFP_EMBEDDINGS(5),
    CLASSIFYING_VFP_WINDOWS(6),
    PREPARING_NATURALNESS_WINDOWS(7),
    EXTRACTING_NATURALNESS_EMBEDDINGS(8),
    SCORING_NATURALNESS_WINDOWS(9),
    CALCULATING_SCORES(10),
    SERIALIZING_RESULT(11),
    COMPLETED(12),
    ;

    internal companion object {
        fun fromNative(value: Int): PitcheeProgressStage =
            entries.firstOrNull { it.nativeValue == value } ?: LOADING_AUDIO
    }
}

data class PitcheeProgress(
    val stage: PitcheeProgressStage,
    val completed: Long,
    val total: Long,
    val stageFraction: Float,
) {
    val overallFraction: Float
        get() = (
            stageStarts[stage.ordinal] +
                stageWeights[stage.ordinal] * stageFraction.coerceIn(0f, 1f)
            ).coerceIn(0f, 1f)
}

// Profiled on PHT110 with a 6.8-second speech sample. VFP and naturalness
// embedding extraction account for most of the inference time.
private val stageWeights = floatArrayOf(
    0.000f, // loading audio
    0.003f, // resampling
    0.022f, // analyzing F0
    0.012f, // detecting speech
    0.001f, // preparing VFP windows
    0.595f, // extracting VFP embeddings
    0.002f, // classifying VFP windows
    0.001f, // preparing naturalness windows
    0.364f, // extracting naturalness embeddings
    0.000f, // scoring naturalness windows
    0.000f, // calculating scores
    0.000f, // serializing result
    0.001f, // completed
)

private val stageStarts = FloatArray(stageWeights.size).also { starts ->
    var accumulated = 0f
    stageWeights.forEachIndexed { index, weight ->
        starts[index] = accumulated
        accumulated += weight
    }
}

fun interface PitcheeProgressCallback {
    fun onProgress(
        stage: Int,
        completed: Long,
        total: Long,
        fraction: Float,
    )
}

fun interface PitcheeF0FrameCallback {
    fun onFrame(
        timestampSeconds: Double,
        f0Hz: Float,
        confidence: Float,
        voiced: Boolean,
    )
}

/**
 * Thin JNI binding to the PitcheeCore C ABI.
 *
 * Calls on one instance are serialized because one native analyzer is not safe
 * for concurrent inference.
 */
class PitcheeAnalyzer private constructor(private var nativeHandle: Long) : Closeable {
    @Synchronized
    fun analyze(
        samples: FloatArray,
        sampleRate: Int,
        channels: Int,
        onPhase: ((PitcheePhase) -> Unit)? = null,
        onProgress: ((PitcheeProgress) -> Unit)? = null,
    ): String {
        check(nativeHandle != 0L)
        require(samples.isNotEmpty())
        require(sampleRate > 0)
        require(channels > 0 && samples.size % channels == 0)
        val phaseCallback = onPhase?.let { listener ->
            PitcheePhaseCallback { value ->
                listener(PitcheePhase.fromNative(value))
            }
        }
        val progressCallback = onProgress?.let { listener ->
            PitcheeProgressCallback { stage, completed, total, fraction ->
                listener(
                    PitcheeProgress(
                        stage = PitcheeProgressStage.fromNative(stage),
                        completed = completed,
                        total = total,
                        stageFraction = fraction,
                    )
                )
            }
        }
        val result = if (progressCallback != null) {
            nativeAnalyzeWithProgress(
                nativeHandle,
                samples,
                sampleRate,
                channels,
                progressCallback,
            )
        } else {
            nativeAnalyze(nativeHandle, samples, sampleRate, channels, phaseCallback)
        }
        return checkNotNull(result)
    }

    @Synchronized
    fun createRealtimeF0(
        contextSamples: Int = DEFAULT_F0_CONTEXT_SAMPLES,
        hopSamples: Int = DEFAULT_F0_HOP_SAMPLES,
    ): PitcheeRealtimeF0 {
        check(nativeHandle != 0L)
        require(contextSamples > 0)
        require(hopSamples > 0)
        return PitcheeRealtimeF0(
            nativeRealtimeF0Create(nativeHandle, contextSamples, hopSamples),
        )
    }

    @Synchronized
    override fun close() {
        if (nativeHandle != 0L) {
            nativeDestroy(nativeHandle)
            nativeHandle = 0L
        }
    }

    private external fun nativeDestroy(handle: Long)

    private external fun nativeAnalyze(
        handle: Long,
        samples: FloatArray,
        sampleRate: Int,
        channels: Int,
        callback: PitcheePhaseCallback?,
    ): String?

    private external fun nativeAnalyzeWithProgress(
        handle: Long,
        samples: FloatArray,
        sampleRate: Int,
        channels: Int,
        callback: PitcheeProgressCallback?,
    ): String?

    private external fun nativeRealtimeF0Create(
        handle: Long,
        contextSamples: Int,
        hopSamples: Int,
    ): Long

    companion object {
        init {
            System.loadLibrary("pitchee_core_jni")
        }

        fun create(modelDirectory: String, threads: Int = 2): PitcheeAnalyzer {
            require(modelDirectory.isNotBlank())
            require(threads > 0)
            return PitcheeAnalyzer(nativeCreate(modelDirectory, threads))
        }

        fun compositeScoreValue(
            vfpStandardScore: Double,
            naturalnessScore: Double,
            f0Hz: Double?,
        ): Double = nativeCompositeScoreValue(
            vfpStandardScore,
            naturalnessScore,
            f0Hz ?: Double.NaN,
        )

        @JvmStatic
        private external fun nativeCreate(modelDirectory: String, threads: Int): Long

        @JvmStatic
        private external fun nativeCompositeScoreValue(
            vfpStandardScore: Double,
            naturalnessScore: Double,
            f0Hz: Double,
        ): Double

        private const val DEFAULT_F0_CONTEXT_SAMPLES = 5120
        private const val DEFAULT_F0_HOP_SAMPLES = 256
    }
}

class PitcheeRealtimeF0 internal constructor(
    private var nativeHandle: Long,
) : Closeable {
    @Synchronized
    fun process(
        samples: FloatArray,
        onFrame: PitcheeF0FrameCallback,
    ): Long {
        check(nativeHandle != 0L)
        require(samples.isNotEmpty())
        return nativeProcess(nativeHandle, samples, onFrame)
    }

    @Synchronized
    fun reset() {
        if (nativeHandle != 0L) nativeReset(nativeHandle)
    }

    @Synchronized
    override fun close() {
        if (nativeHandle != 0L) {
            nativeDestroy(nativeHandle)
            nativeHandle = 0L
        }
    }

    private external fun nativeProcess(
        handle: Long,
        samples: FloatArray,
        callback: PitcheeF0FrameCallback,
    ): Long

    private external fun nativeReset(handle: Long)

    private external fun nativeDestroy(handle: Long)
}
