package io.rovly.pitchee.data.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import io.rovly.pitchee.R
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max

class AudioRecorder(private val context: Context) {
    private var audioRecord: AudioRecord? = null
    private var outputFile: File? = null
    private var output: RandomAccessFile? = null
    private var dataBytes = 0L

    @SuppressLint("MissingPermission")
    fun start() {
        check(audioRecord == null) { context.getString(R.string.error_audio_recorder_running) }
        check(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        ) {
            context.getString(R.string.error_permission_missing)
        }

        val minimumBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        check(minimumBuffer > 0) {
            context.getString(R.string.error_realtime_recorder_unsupported)
        }
        val target = File.createTempFile("pitchee-", ".wav", context.cacheDir)
        val file = RandomAccessFile(target, "rw")
        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            max(minimumBuffer, CAPTURE_BUFFER_SAMPLES * Short.SIZE_BYTES * 2),
        )
        try {
            check(record.state == AudioRecord.STATE_INITIALIZED) {
                context.getString(R.string.error_realtime_recorder_init)
            }
            file.setLength(0)
            file.write(ByteArray(WAV_HEADER_SIZE))
            record.startRecording()
            check(record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                context.getString(R.string.error_recording_start)
            }
        } catch (error: Throwable) {
            runCatching { record.release() }
            runCatching { file.close() }
            target.delete()
            throw error
        }

        audioRecord = record
        outputFile = target
        output = file
        dataBytes = 0L
    }

    /**
     * Reads PCM16 from the microphone and appends it to the temporary WAV file.
     * Calls are serialized by the recording capture loop.
     */
    fun readAndWrite(buffer: ShortArray): Int {
        val record = checkNotNull(audioRecord) {
            context.getString(R.string.error_audio_recorder_not_running)
        }
        val count = record.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
        if (count > 0) writePcm16(buffer, count)
        return count
    }

    /** Unblocks [readAndWrite] without closing or finalizing the output file. */
    fun stopCapture() {
        val record = audioRecord ?: return
        if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
            runCatching { record.stop() }
        }
    }

    fun finish(): File {
        val record = checkNotNull(audioRecord) {
            context.getString(R.string.error_audio_recorder_not_running)
        }
        val target = checkNotNull(outputFile) {
            context.getString(R.string.error_recording_output_missing)
        }
        val file = checkNotNull(output) {
            context.getString(R.string.error_recording_output_missing)
        }
        var completed = false
        try {
            stopCapture()
            if (dataBytes <= 0L) {
                throw IllegalStateException(context.getString(R.string.error_recording_short))
            }
            writeWavHeader(file, dataBytes)
            completed = true
        } finally {
            runCatching { record.release() }
            runCatching { file.close() }
            audioRecord = null
            outputFile = null
            output = null
            dataBytes = 0L
            if (!completed) target.delete()
        }
        check(target.length() > WAV_HEADER_SIZE) {
            target.delete()
            context.getString(R.string.error_recording_short)
        }
        return target
    }

    fun cancel() {
        stopCapture()
        runCatching { audioRecord?.release() }
        runCatching { output?.close() }
        outputFile?.delete()
        audioRecord = null
        outputFile = null
        output = null
        dataBytes = 0L
    }

    private fun writePcm16(samples: ShortArray, count: Int) {
        val file = checkNotNull(output)
        val bytes = ByteArray(count * Short.SIZE_BYTES)
        for (index in 0 until count) {
            val value = samples[index].toInt()
            val offset = index * Short.SIZE_BYTES
            bytes[offset] = (value and 0xFF).toByte()
            bytes[offset + 1] = (value shr 8 and 0xFF).toByte()
        }
        file.write(bytes)
        dataBytes += bytes.size
    }

    private fun writeWavHeader(file: RandomAccessFile, sampleBytes: Long) {
        val dataSize = sampleBytes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val header = ByteBuffer.allocate(WAV_HEADER_SIZE)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply {
                put("RIFF".toByteArray(Charsets.US_ASCII))
                putInt(36 + dataSize)
                put("WAVE".toByteArray(Charsets.US_ASCII))
                put("fmt ".toByteArray(Charsets.US_ASCII))
                putInt(16)
                putShort(1)
                putShort(1)
                putInt(SAMPLE_RATE)
                putInt(SAMPLE_RATE * Short.SIZE_BYTES)
                putShort(Short.SIZE_BYTES.toShort())
                putShort(16)
                put("data".toByteArray(Charsets.US_ASCII))
                putInt(dataSize)
            }
        file.seek(0)
        file.write(header.array())
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        const val CAPTURE_BUFFER_SAMPLES = 400
        private const val WAV_HEADER_SIZE = 44
    }
}
