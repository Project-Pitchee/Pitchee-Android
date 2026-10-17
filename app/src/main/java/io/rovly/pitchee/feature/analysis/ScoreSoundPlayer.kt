package io.rovly.pitchee.feature.analysis

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import io.rovly.pitchee.R

internal class ScoreSoundPlayer(context: Context) {
    private val soundPool = SoundPool.Builder()
        .setMaxStreams(8)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    @Volatile
    private var loaded = false

    @Volatile
    private var released = false

    @Volatile
    private var pendingScore: Pair<Float, Float>? = null

    private val soundId: Int

    init {
        soundPool.setOnLoadCompleteListener { _, _, status ->
            if (released) return@setOnLoadCompleteListener
            loaded = status == 0
            val score = pendingScore
            if (loaded && score != null) {
                pendingScore = null
                playLoaded(score.first, score.second)
            }
        }
        soundId = soundPool.load(context.applicationContext, R.raw.pitchee_score, 1)
    }

    fun play(score: Float, pitchScore: Float) {
        if (released) return
        val normalizedScore = score.coerceIn(0f, 100f)
        val normalizedPitch = pitchScore.coerceIn(0f, 100f)
        if (loaded) {
            playLoaded(normalizedScore, normalizedPitch)
        } else {
            pendingScore = normalizedScore to normalizedPitch
        }
    }

    fun release() {
        released = true
        pendingScore = null
        loaded = false
        soundPool.release()
    }

    private fun playLoaded(score: Float, pitchScore: Float) {
        val volume = volumeFor(score)
        soundPool.play(
            soundId,
            volume,
            volume,
            1,
            0,
            rateFor(pitchScore),
        )
    }

    private fun volumeFor(score: Float): Float {
        val progress = score / 100f
        return 0.55f + progress * 0.20f
    }

    private fun rateFor(score: Float): Float {
        val progress = score / 100f
        return 0.80f + progress * 0.75f
    }
}
