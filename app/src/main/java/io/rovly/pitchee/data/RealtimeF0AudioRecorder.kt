package io.rovly.pitchee.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import io.rovly.pitchee.R

class RealtimeF0AudioRecorder(private val context: Context) {
    private var audioRecord: AudioRecord? = null

    @SuppressLint("MissingPermission")
    fun start() {
        check(audioRecord == null) { context.getString(R.string.error_realtime_recorder_running) }
        check(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        ) {
            context.getString(R.string.error_permission_missing)
        }
        val minimumBytes = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
        )
        check(minimumBytes > 0) { context.getString(R.string.error_realtime_recorder_unsupported) }
        val bufferBytes = maxOf(minimumBytes, SAMPLE_RATE * Float.SIZE_BYTES / 2)
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
            bufferBytes,
        )
        check(recorder.state == AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            context.getString(R.string.error_realtime_recorder_init)
        }
        recorder.startRecording()
        audioRecord = recorder
    }

    fun read(buffer: FloatArray): Int {
        val recorder = checkNotNull(audioRecord) { context.getString(R.string.error_realtime_recorder_not_running) }
        val count = recorder.read(
            buffer,
            0,
            buffer.size,
            AudioRecord.READ_BLOCKING,
        )
        check(count >= 0) { context.getString(R.string.error_realtime_recorder_read, count) }
        return count
    }

    fun pause() {
        val recorder = audioRecord ?: return
        if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
            runCatching { recorder.stop() }
        }
    }

    fun resume() {
        val recorder = audioRecord
        if (recorder == null) {
            start()
            return
        }
        check(recorder.state == AudioRecord.STATE_INITIALIZED) {
            stop()
            context.getString(R.string.error_realtime_recorder_invalid)
        }
        if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            recorder.startRecording()
        }
    }

    fun stop() {
        val recorder = audioRecord ?: return
        audioRecord = null
        runCatching { recorder.stop() }
        recorder.release()
    }

    companion object {
        const val SAMPLE_RATE = 16_000
    }
}
