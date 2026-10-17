package space.pitchee.core

import java.io.Closeable

enum class PitcheeSpectrumValue(val nativeValue: Int) {
    AMPLITUDE(0),
    POWER(1),
    DBFS(2),
}

fun interface PitcheeSpectrumFrameCallback {
    fun onFrame(
        timestampSeconds: Double,
        magnitudes: FloatArray,
        firstBinIndex: Long,
        binHz: Float,
        peakHz: Float,
        centroidHz: Float,
        rolloffHz: Float,
        flatness: Float,
    )
}

class PitcheeSpectrum private constructor(
    private var nativeHandle: Long,
) : Closeable {
    @Synchronized
    fun process(
        samples: FloatArray,
        onFrame: PitcheeSpectrumFrameCallback,
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
    override fun close() {
        if (nativeHandle != 0L) {
            nativeDestroy(nativeHandle)
            nativeHandle = 0L
        }
    }

    private external fun nativeProcess(
        handle: Long,
        samples: FloatArray,
        callback: PitcheeSpectrumFrameCallback,
    )

    private external fun nativeReset(handle: Long)

    private external fun nativeDestroy(handle: Long)

    companion object {
        init {
            System.loadLibrary("pitchee_core_jni")
        }

        fun create(
            fftSize: Int = 2048,
            hopSamples: Int = 256,
            minHz: Int = 40,
            maxHz: Int = 8000,
            valueType: PitcheeSpectrumValue = PitcheeSpectrumValue.DBFS,
            smoothing: Float = 0.65f,
        ): PitcheeSpectrum {
            require(fftSize > 0)
            require(hopSamples > 0)
            require(minHz >= 0)
            require(maxHz > minHz)
            require(smoothing in 0f..1f)
            return PitcheeSpectrum(
                nativeCreate(
                    fftSize = fftSize,
                    hopSamples = hopSamples,
                    minHz = minHz,
                    maxHz = maxHz,
                    valueType = valueType.nativeValue,
                    smoothing = smoothing,
                ),
            )
        }

        @JvmStatic
        private external fun nativeCreate(
            fftSize: Int,
            hopSamples: Int,
            minHz: Int,
            maxHz: Int,
            valueType: Int,
            smoothing: Float,
        ): Long
    }
}
