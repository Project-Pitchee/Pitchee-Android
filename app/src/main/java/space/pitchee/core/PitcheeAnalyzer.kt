package space.pitchee.core

import java.io.Closeable

/**
 * Coarse app-level stage. Core only reports [PitcheeProgressStage]; this is the
 * three-phase summary the UI shows while the model warms up and infers.
 */
enum class PitcheePhase(val nativeValue: Int) {
    PREPARING_MODELS(0),
    LOADING_AUDIO(1),
    ANALYZING(2),
    COMPLETED(3),
}

enum class PitcheeScoreProfile(
    val nativeValue: Int,
    val jsonValue: String,
) {
    FEMINIZATION(0, "feminization"),
    MASCULINIZATION(1, "masculinization"),
    ;

    companion object {
        internal fun fromJsonValue(value: String?): PitcheeScoreProfile =
            entries.firstOrNull { it.jsonValue == value } ?: FEMINIZATION
    }
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

    fun toPhase(): PitcheePhase = when (this) {
        LOADING_AUDIO,
        RESAMPLING_AUDIO,
        -> PitcheePhase.LOADING_AUDIO
        COMPLETED -> PitcheePhase.COMPLETED
        else -> PitcheePhase.ANALYZING
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

data class PitcheeVadSegment(
    val startSeconds: Double,
    val endSeconds: Double,
)

fun interface PitcheeVadCallback {
    fun onSegment(startSeconds: Double, endSeconds: Double)
}

enum class PitcheeVoiceHealthStage(val nativeValue: Int) {
    LOADING_AUDIO(0),
    RESAMPLING_AUDIO(1),
    ANALYZING_F0(2),
    DETECTING_SPEECH(3),
    PREPARING_VOICE_HEALTH(13),
    EXTRACTING_VOICE_HEALTH_EMBEDDINGS(14),
    SCORING_VOICE_HEALTH(15),
    COMPLETED(12),
    ;

    internal companion object {
        fun fromNative(value: Int): PitcheeVoiceHealthStage =
            entries.firstOrNull { it.nativeValue == value } ?: LOADING_AUDIO
    }
}

data class PitcheeVoiceHealthProgress(
    val stage: PitcheeVoiceHealthStage,
    val completed: Long,
    val total: Long,
    val stageFraction: Float,
)

data class PitcheeVoiceHealthFeatures(
    val hnrDb: Double,
    val cppsDb: Double,
    val jitterLocal: Double,
    val jitterRap: Double,
    val jitterPpq5: Double,
    val shimmerLocal: Double,
    val shimmerApq3: Double,
    val shimmerApq5: Double,
    val f0Median: Double,
    val f0Iqr: Double,
    val f0Cv: Double,
    val voicedFraction: Double,
    val intensityMedian: Double,
    val intensityIqr: Double,
)

data class PitcheeVoiceHealthResult(
    val healthScore: Double,
    val disorderProbability: Double,
    val decisionThreshold: Double,
    val isDisordered: Boolean,
    val features: PitcheeVoiceHealthFeatures,
)

/**
 * SwiftF0 / VAD / scoring thresholds handed to Core at analyzer creation.
 * Zero values fall back to the Core defaults (2 threads, 75-600 Hz, 0.9).
 */
data class PitcheeThresholds(
    val intraOpThreads: Int = DEFAULT_THREADS,
    val useCoreMl: Boolean = false,
    val minF0Hz: Float = DEFAULT_MIN_F0_HZ,
    val maxF0Hz: Float = DEFAULT_MAX_F0_HZ,
    val minConfidence: Float = DEFAULT_MIN_CONFIDENCE,
) {
    companion object {
        /** Three quarters of the available cores, so one quarter is left for the UI. */
        val DEFAULT_THREADS: Int =
            (Runtime.getRuntime().availableProcessors() * 3 / 4).coerceAtLeast(1)

        const val DEFAULT_MIN_F0_HZ = 75f
        const val DEFAULT_MAX_F0_HZ = 450f
        const val DEFAULT_MIN_CONFIDENCE = 0.9f
    }
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
        scoreProfile: PitcheeScoreProfile,
        onPhase: ((PitcheePhase) -> Unit)? = null,
        onProgress: ((PitcheeProgress) -> Unit)? = null,
    ): String {
        check(nativeHandle != 0L)
        require(samples.isNotEmpty())
        require(sampleRate > 0)
        require(channels > 0 && samples.size % channels == 0)
        val progressCallback = if (onProgress != null || onPhase != null) {
            PitcheeProgressCallback { stage, completed, total, fraction ->
                val parsed = PitcheeProgressStage.fromNative(stage)
                onProgress?.invoke(
                    PitcheeProgress(
                        stage = parsed,
                        completed = completed,
                        total = total,
                        stageFraction = fraction,
                    )
                )
                onPhase?.invoke(parsed.toPhase())
            }
        } else {
            null
        }
        val result = nativeAnalyze(
            nativeHandle,
            samples,
            sampleRate,
            channels,
            scoreProfile.nativeValue,
            progressCallback,
        )
        return checkNotNull(result)
    }

    @Synchronized
    fun detectVad(
        samples: FloatArray,
        sampleRate: Int,
        channels: Int,
    ): List<PitcheeVadSegment> {
        check(nativeHandle != 0L)
        require(samples.isNotEmpty())
        require(sampleRate > 0)
        require(channels > 0 && samples.size % channels == 0)
        val segments = mutableListOf<PitcheeVadSegment>()
        nativeDetectVad(
            nativeHandle,
            samples,
            sampleRate,
            channels,
            PitcheeVadCallback { startSeconds, endSeconds ->
                segments += PitcheeVadSegment(startSeconds, endSeconds)
            },
        )
        return segments
    }

    @Synchronized
    fun runVoiceHealth(
        samples: FloatArray,
        sampleRate: Int,
        channels: Int,
        onProgress: ((PitcheeVoiceHealthProgress) -> Unit)? = null,
    ): PitcheeVoiceHealthResult {
        check(nativeHandle != 0L)
        require(samples.isNotEmpty())
        require(sampleRate > 0)
        require(channels > 0 && samples.size % channels == 0)
        val progressCallback = onProgress?.let { listener ->
            PitcheeProgressCallback { stage, completed, total, fraction ->
                listener(
                    PitcheeVoiceHealthProgress(
                        stage = PitcheeVoiceHealthStage.fromNative(stage),
                        completed = completed,
                        total = total,
                        stageFraction = fraction,
                    )
                )
            }
        }
        val values = checkNotNull(
            nativeRunVoiceHealth(
                nativeHandle,
                samples,
                sampleRate,
                channels,
                progressCallback,
            )
        )
        return PitcheeVoiceHealthResult(
            healthScore = values[0],
            disorderProbability = values[1],
            decisionThreshold = values[2],
            isDisordered = values[3] != 0.0,
            features = PitcheeVoiceHealthFeatures(
                hnrDb = values[4],
                cppsDb = values[5],
                jitterLocal = values[6],
                jitterRap = values[7],
                jitterPpq5 = values[8],
                shimmerLocal = values[9],
                shimmerApq3 = values[10],
                shimmerApq5 = values[11],
                f0Median = values[12],
                f0Iqr = values[13],
                f0Cv = values[14],
                voicedFraction = values[15],
                intensityMedian = values[16],
                intensityIqr = values[17],
            ),
        )
    }

    /**
     * Pass 0 for the window values to let Core apply its defaults
     * (5120 context samples, 256 inference-hop samples). A non-positive
     * [outputRateHz] selects SwiftF0's native output rate.
     *
     * [onClosed] runs once the stream is destroyed so callers can tell when the
     * analyzer can safely be rebuilt.
     */
    @Synchronized
    fun createRealtimeF0(
        contextSamples: Int = 0,
        hopSamples: Int = 0,
        outputRateHz: Double = 0.0,
        startTimestampSeconds: Double = 0.0,
        onClosed: (() -> Unit)? = null,
    ): PitcheeRealtimeF0 {
        check(nativeHandle != 0L)
        require(contextSamples >= 0)
        require(hopSamples >= 0)
        require(outputRateHz.isFinite() && outputRateHz >= 0.0)
        require(startTimestampSeconds.isFinite())
        return PitcheeRealtimeF0(
            nativeRealtimeF0Create(
                nativeHandle,
                contextSamples,
                hopSamples,
                outputRateHz,
                startTimestampSeconds,
            ),
            onClosed,
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
        scoreProfile: Int,
        callback: PitcheeProgressCallback?,
    ): String?

    private external fun nativeDetectVad(
        handle: Long,
        samples: FloatArray,
        sampleRate: Int,
        channels: Int,
        callback: PitcheeVadCallback,
    )

    private external fun nativeRunVoiceHealth(
        handle: Long,
        samples: FloatArray,
        sampleRate: Int,
        channels: Int,
        callback: PitcheeProgressCallback?,
    ): DoubleArray?

    private external fun nativeRealtimeF0Create(
        handle: Long,
        contextSamples: Int,
        hopSamples: Int,
        outputRateHz: Double,
        startTimestampSeconds: Double,
    ): Long

    companion object {
        init {
            System.loadLibrary("pitchee_core_jni")
        }

        fun version(): String = nativeVersion()

        fun create(
            modelDirectory: String,
            thresholds: PitcheeThresholds = PitcheeThresholds(),
        ): PitcheeAnalyzer {
            require(modelDirectory.isNotBlank())
            require(thresholds.intraOpThreads > 0)
            require(thresholds.minF0Hz > 0f && thresholds.maxF0Hz > thresholds.minF0Hz)
            require(thresholds.minConfidence in 0f..1f)
            return PitcheeAnalyzer(
                nativeCreate(
                    modelDirectory = modelDirectory,
                    threads = thresholds.intraOpThreads,
                    useCoreMl = if (thresholds.useCoreMl) 1 else 0,
                    minF0Hz = thresholds.minF0Hz,
                    maxF0Hz = thresholds.maxF0Hz,
                    minConfidence = thresholds.minConfidence,
                ),
            )
        }

        fun scoreValue(
            scoreProfile: PitcheeScoreProfile,
            vfpStandardScore: Double,
            naturalnessScore: Double,
            f0Hz: Double?,
        ): Double = nativeScore(
            scoreProfile.nativeValue,
            vfpStandardScore,
            naturalnessScore,
            f0Hz ?: Double.NaN,
        )

        @JvmStatic
        private external fun nativeVersion(): String

        @JvmStatic
        private external fun nativeCreate(
            modelDirectory: String,
            threads: Int,
            useCoreMl: Int,
            minF0Hz: Float,
            maxF0Hz: Float,
            minConfidence: Float,
        ): Long

        @JvmStatic
        private external fun nativeScore(
            scoreProfile: Int,
            vfpStandardScore: Double,
            naturalnessScore: Double,
            f0Hz: Double,
        ): Double
    }
}

data class PitcheeF0Metadata(
    val sampleRate: Int,
    val frameHopSamples: Int,
    val frameIntervalSeconds: Double,
    val minContextSamples: Int,
    val modelOutputRateHz: Double,
    val effectiveOutputRateHz: Double,
)

data class PitcheeF0Stats(
    val processCalls: Long,
    val inferenceCalls: Long,
    val emittedFrames: Long,
    val totalMs: Double,
    val lastTotalMs: Double,
    val averageTotalMs: Double,
    val lastPreprocessMs: Double,
    val averagePreprocessMs: Double,
    val lastModelMs: Double,
    val averageModelMs: Double,
    val lastPostprocessMs: Double,
    val averagePostprocessMs: Double,
    val measuredOutputHz: Double,
)

class PitcheeRealtimeF0 internal constructor(
    private var nativeHandle: Long,
    private val onClosed: (() -> Unit)? = null,
) : Closeable {
    @Synchronized
    fun process(
        samples: FloatArray,
        onFrame: PitcheeF0FrameCallback,
    ) {
        check(nativeHandle != 0L)
        require(samples.isNotEmpty())
        nativeProcess(nativeHandle, samples, onFrame)
    }

    @Synchronized
    fun reset() {
        if (nativeHandle != 0L) nativeReset(nativeHandle)
    }

    @Synchronized
    fun warmup() {
        check(nativeHandle != 0L)
        nativeWarmup(nativeHandle)
    }

    @Synchronized
    fun metadata(): PitcheeF0Metadata {
        check(nativeHandle != 0L)
        val values = nativeMetadata(nativeHandle)
        return PitcheeF0Metadata(
            sampleRate = values[0].toInt(),
            frameHopSamples = values[1].toInt(),
            frameIntervalSeconds = values[2],
            minContextSamples = values[3].toInt(),
            modelOutputRateHz = values[4],
            effectiveOutputRateHz = values[5],
        )
    }

    @Synchronized
    fun stats(): PitcheeF0Stats {
        check(nativeHandle != 0L)
        val values = nativeStats(nativeHandle)
        return PitcheeF0Stats(
            processCalls = values[0].toLong(),
            inferenceCalls = values[1].toLong(),
            emittedFrames = values[2].toLong(),
            totalMs = values[3],
            lastTotalMs = values[4],
            averageTotalMs = values[5],
            lastPreprocessMs = values[6],
            averagePreprocessMs = values[7],
            lastModelMs = values[8],
            averageModelMs = values[9],
            lastPostprocessMs = values[10],
            averagePostprocessMs = values[11],
            measuredOutputHz = values[12],
        )
    }

    @Synchronized
    override fun close() {
        if (nativeHandle != 0L) {
            nativeDestroy(nativeHandle)
            nativeHandle = 0L
            onClosed?.invoke()
        }
    }

    private external fun nativeProcess(
        handle: Long,
        samples: FloatArray,
        callback: PitcheeF0FrameCallback,
    )

    private external fun nativeReset(handle: Long)

    private external fun nativeWarmup(handle: Long)

    private external fun nativeMetadata(handle: Long): DoubleArray

    private external fun nativeStats(handle: Long): DoubleArray

    private external fun nativeDestroy(handle: Long)
}
