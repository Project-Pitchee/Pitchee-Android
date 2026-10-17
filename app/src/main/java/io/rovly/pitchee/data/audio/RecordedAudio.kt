package io.rovly.pitchee.data.audio

import java.io.File
import kotlin.math.abs
import kotlin.math.max

class RecordedAudio private constructor(
    val file: File,
    val waveform: FloatArray,
    val durationSeconds: Double,
) {
    companion object {
        private const val DEFAULT_BUCKET_COUNT = 2_048

        fun from(
            file: File,
            pcm: PcmAudio,
            bucketCount: Int = DEFAULT_BUCKET_COUNT,
        ): RecordedAudio {
            require(pcm.frameCount > 0)
            require(bucketCount > 0)

            val buckets = minOf(bucketCount, pcm.frameCount)
            val waveform = FloatArray(buckets)
            for (bucket in 0 until buckets) {
                val startFrame = bucket * pcm.frameCount / buckets
                val endFrame = max(
                    startFrame + 1,
                    (bucket + 1) * pcm.frameCount / buckets,
                )
                var peak = 0f
                for (frame in startFrame until endFrame) {
                    for (channel in 0 until pcm.channels) {
                        peak = max(
                            peak,
                            abs(pcm.samples[frame * pcm.channels + channel]),
                        )
                    }
                }
                waveform[bucket] = peak.coerceIn(0f, 1f)
            }

            return RecordedAudio(
                file = file,
                waveform = waveform,
                durationSeconds = pcm.durationSeconds,
            )
        }
    }
}
