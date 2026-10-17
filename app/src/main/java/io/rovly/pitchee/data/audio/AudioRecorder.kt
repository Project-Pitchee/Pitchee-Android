package io.rovly.pitchee.data.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import io.rovly.pitchee.R
import java.io.File
import kotlin.math.sqrt

class AudioRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    fun start() {
        check(recorder == null) { context.getString(R.string.error_audio_recorder_running) }
        val target = File.createTempFile("pitchee-", ".m4a", context.cacheDir)
        val mediaRecorder = createMediaRecorder()
        try {
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mediaRecorder.setAudioSamplingRate(44_100)
            mediaRecorder.setAudioEncodingBitRate(128_000)
            mediaRecorder.setAudioChannels(1)
            mediaRecorder.setOutputFile(target.absolutePath)
            mediaRecorder.prepare()
            mediaRecorder.start()
        } catch (error: Throwable) {
            mediaRecorder.release()
            target.delete()
            throw error
        }
        recorder = mediaRecorder
        outputFile = target
    }

    fun stop(): File {
        val mediaRecorder = checkNotNull(recorder) { context.getString(R.string.error_audio_recorder_not_running) }
        val target = checkNotNull(outputFile) { context.getString(R.string.error_recording_output_missing) }
        recorder = null
        outputFile = null
        try {
            mediaRecorder.stop()
        } catch (error: RuntimeException) {
            target.delete()
            throw IllegalStateException(context.getString(R.string.error_recording_short), error)
        } finally {
            mediaRecorder.release()
        }
        return target
    }

    fun currentAmplitude(): Float = runCatching {
        val raw = recorder?.maxAmplitude ?: 0
        sqrt((raw / 32_767f).coerceIn(0f, 1f))
    }.getOrDefault(0f)

    fun cancel() {
        val mediaRecorder = recorder
        recorder = null
        if (mediaRecorder != null) {
            runCatching { mediaRecorder.stop() }
            mediaRecorder.release()
        }
        outputFile?.delete()
        outputFile = null
    }

    @Suppress("DEPRECATION")
    private fun createMediaRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }
}
