package io.rovly.pitchee.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat

class RealtimeF0AudioRecorder(private val context: Context) {
    private var audioRecord: AudioRecord? = null

    @SuppressLint("MissingPermission")
    fun start() {
        check(audioRecord == null) { "Realtime recorder is already running" }
        check(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        ) {
            "缺少麦克风权限"
        }
        val minimumBytes = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
        )
        check(minimumBytes > 0) { "当前设备不支持 16 kHz 单声道实时录音" }
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
            "无法初始化实时录音"
        }
        recorder.startRecording()
        audioRecord = recorder
    }

    fun read(buffer: FloatArray): Int {
        val recorder = checkNotNull(audioRecord) { "Realtime recorder is not running" }
        val count = recorder.read(
            buffer,
            0,
            buffer.size,
            AudioRecord.READ_BLOCKING,
        )
        check(count >= 0) { "实时录音读取失败：$count" }
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
            "实时录音设备已失效"
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
