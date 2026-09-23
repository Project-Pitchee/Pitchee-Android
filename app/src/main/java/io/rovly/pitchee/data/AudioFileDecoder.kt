package io.rovly.pitchee.data

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import io.rovly.pitchee.R
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

internal object AudioFileDecoder {
    suspend fun decode(
        context: Context,
        uri: Uri,
        maxSeconds: Int? = null,
    ): PcmAudio = withContext(Dispatchers.IO) {
        withTimeout(DECODE_TIMEOUT_MS) {
            require(maxSeconds == null || maxSeconds > 0) {
                "maxSeconds must be positive"
            }

            val extractor = MediaExtractor()
            try {
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
                    extractor.setDataSource(
                        descriptor.fileDescriptor,
                        descriptor.startOffset,
                        descriptor.length,
                    )
                } ?: error(context.getString(R.string.error_audio_open))

                val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
                    extractor.getTrackFormat(index)
                        .getString(MediaFormat.KEY_MIME)
                        ?.startsWith("audio/") == true
                } ?: error(context.getString(R.string.error_audio_no_track))

                extractor.selectTrack(trackIndex)
                val inputFormat = extractor.getTrackFormat(trackIndex)
                val mime = inputFormat.getString(MediaFormat.KEY_MIME)
                    ?: error(context.getString(R.string.error_audio_mime_missing))
                val sourceSampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                val sourceChannels = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                val outputEncoding = inputFormat.integerOrNull(MediaFormat.KEY_PCM_ENCODING)
                    ?: AudioFormat.ENCODING_PCM_16BIT

                if (mime == MediaFormat.MIMETYPE_AUDIO_RAW) {
                    return@withTimeout extractRawPcm(
                        context = context,
                        extractor = extractor,
                        format = inputFormat,
                        encoding = outputEncoding,
                        sampleRate = sourceSampleRate,
                        channels = sourceChannels,
                        maximumSamples = maximumSamples(
                            maxSeconds,
                            sourceSampleRate,
                            sourceChannels,
                        ),
                    )
                }

                decodeWithCodec(
                    context = context,
                    extractor = extractor,
                    inputFormat = inputFormat,
                    mime = mime,
                    sourceSampleRate = sourceSampleRate,
                    sourceChannels = sourceChannels,
                    initialEncoding = outputEncoding,
                    maxSeconds = maxSeconds,
                )
            } finally {
                extractor.release()
            }
        }
    }

    private suspend fun decodeWithCodec(
        context: Context,
        extractor: MediaExtractor,
        inputFormat: MediaFormat,
        mime: String,
        sourceSampleRate: Int,
        sourceChannels: Int,
        initialEncoding: Int,
        maxSeconds: Int?,
    ): PcmAudio {
        val codec = MediaCodec.createDecoderByType(mime)
        try {
            codec.configure(inputFormat, null, null, 0)
            codec.start()

            val info = MediaCodec.BufferInfo()
            val samples = ArrayList<Float>(
                (sourceSampleRate.toLong() * sourceChannels * 5L)
                    .coerceAtMost(Int.MAX_VALUE.toLong())
                    .toInt()
            )
            var outputEncoding = initialEncoding
            var inputEnded = false
            var outputEnded = false
            var outputSampleRate = sourceSampleRate
            var outputChannels = sourceChannels
            var maximumSamples = maximumSamples(maxSeconds, outputSampleRate, outputChannels)

            while (!outputEnded && samples.size < maximumSamples) {
                coroutineContext.ensureActive()
                if (!inputEnded) {
                    val inputIndex = codec.dequeueInputBuffer(CODEC_TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val inputBuffer = checkNotNull(codec.getInputBuffer(inputIndex))
                        inputBuffer.clear()
                        val size = extractor.readSampleData(inputBuffer, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(
                                inputIndex,
                                0,
                                0,
                                0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            inputEnded = true
                        } else {
                            codec.queueInputBuffer(
                                inputIndex,
                                0,
                                size,
                                extractor.sampleTime,
                                0,
                            )
                            extractor.advance()
                        }
                    }
                }

                when (val outputIndex = codec.dequeueOutputBuffer(info, CODEC_TIMEOUT_US)) {
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val outputFormat = codec.outputFormat
                        outputEncoding = outputFormat
                            .integerOrNull(MediaFormat.KEY_PCM_ENCODING)
                            ?: outputEncoding
                        outputSampleRate = outputFormat
                            .integerOrNull(MediaFormat.KEY_SAMPLE_RATE)
                            ?: outputSampleRate
                        outputChannels = outputFormat
                            .integerOrNull(MediaFormat.KEY_CHANNEL_COUNT)
                            ?: outputChannels
                        maximumSamples =
                            maximumSamples(maxSeconds, outputSampleRate, outputChannels)
                    }
                    else -> if (outputIndex >= 0) {
                        if (info.size > 0) {
                            val outputBuffer = checkNotNull(codec.getOutputBuffer(outputIndex))
                            outputBuffer.position(info.offset)
                            outputBuffer.limit(info.offset + info.size)
                            outputBuffer.order(ByteOrder.nativeOrder())
                            appendSamples(
                                context,
                                outputBuffer,
                                outputEncoding,
                                samples,
                                maximumSamples,
                            )
                        }
                        outputEnded =
                            info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        codec.releaseOutputBuffer(outputIndex, false)
                    }
                }
            }
            codec.stop()

            check(samples.isNotEmpty()) { context.getString(R.string.error_audio_empty) }
            return PcmAudio(
                samples = samples.toFloatArray(),
                sampleRate = outputSampleRate,
                channels = outputChannels,
            )
        } finally {
            codec.release()
        }
    }

    private suspend fun extractRawPcm(
        context: Context,
        extractor: MediaExtractor,
        format: MediaFormat,
        encoding: Int,
        sampleRate: Int,
        channels: Int,
        maximumSamples: Int,
    ): PcmAudio {
        val bufferSize = format.integerOrNull(MediaFormat.KEY_MAX_INPUT_SIZE)
            ?.coerceAtLeast(4_096)
            ?: 65_536
        val buffer = java.nio.ByteBuffer.allocateDirect(bufferSize)
            .order(ByteOrder.nativeOrder())
        val samples = ArrayList<Float>(maximumSamples.coerceAtMost(sampleRate * channels * 5))

        while (samples.size < maximumSamples) {
            coroutineContext.ensureActive()
            buffer.clear()
            val size = extractor.readSampleData(buffer, 0)
            if (size < 0) break
            buffer.position(0)
            buffer.limit(size)
            appendSamples(context, buffer, encoding, samples, maximumSamples)
            extractor.advance()
        }

        check(samples.isNotEmpty()) { context.getString(R.string.error_audio_empty) }
        return PcmAudio(samples.toFloatArray(), sampleRate, channels)
    }

    private fun appendSamples(
        context: Context,
        buffer: java.nio.ByteBuffer,
        encoding: Int,
        target: MutableList<Float>,
        maximumSamples: Int,
    ) {
        when (encoding) {
            AudioFormat.ENCODING_PCM_FLOAT -> {
                val source = buffer.asFloatBuffer()
                repeat(source.remaining()) {
                    if (target.size < maximumSamples) target += source.get()
                }
            }
            AudioFormat.ENCODING_PCM_16BIT -> {
                val source = buffer.asShortBuffer()
                repeat(source.remaining()) {
                    if (target.size < maximumSamples) {
                        target += source.get() / 32768f
                    }
                }
            }
            else -> error(context.getString(R.string.error_pcm_encoding_unsupported, encoding))
        }
    }

    private fun maximumSamples(
        maxSeconds: Int?,
        sampleRate: Int,
        channels: Int,
    ): Int = if (maxSeconds == null) {
        Int.MAX_VALUE
    } else {
        (maxSeconds.toLong() * sampleRate * channels)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
    }

    private fun MediaFormat.integerOrNull(name: String): Int? = try {
        getInteger(name)
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: NullPointerException) {
        null
    }

    private const val CODEC_TIMEOUT_US = 10_000L
    private const val DECODE_TIMEOUT_MS = 60_000L
}
