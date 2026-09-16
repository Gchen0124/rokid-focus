package com.chenniuniu.rokidfocus.glass.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.chenniuniu.rokidfocus.glass.R
import com.chenniuniu.rokidfocus.glass.clock.ChimeKind

class ChimePlayer(private val context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var tones: ToneGenerator? = null

    fun play(kind: ChimeKind) {
        raiseVolume()
        val wavOk = playWav(kind)
        if (!wavOk) {
            Log.w(TAG, "wav failed, using tones for $kind")
            playTones(kind)
        } else if (kind != ChimeKind.BUNNY) {
            playTones(kind)
        }
    }

    fun release() {
        runCatching { player?.release() }
        player = null
        runCatching { tones?.release() }
        tones = null
    }

    private fun playWav(kind: ChimeKind): Boolean {
        val resId = when (kind) {
            ChimeKind.HOUR -> R.raw.chime_hour
            ChimeKind.QUARTER -> R.raw.chime_quarter
            ChimeKind.HALF -> R.raw.chime_half
            ChimeKind.THREE_QUARTER -> R.raw.chime_three_quarter
            ChimeKind.FIVE -> R.raw.chime_five
            ChimeKind.TEN -> R.raw.chime_ten
            ChimeKind.BUNNY -> R.raw.alert_bunny
        }
        return runCatching {
            runCatching { player?.release() }
            val mp = MediaPlayer()
            player = mp
            val afd = context.resources.openRawResourceFd(resId)
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            afd.close()
            mp.setVolume(1f, 1f)
            mp.setOnCompletionListener { it.release() }
            mp.prepare()
            mp.start()
            Log.i(TAG, "wav started $kind")
            true
        }.getOrElse {
            Log.e(TAG, "wav error $kind: ${it.message}")
            false
        }
    }

    private fun playTones(kind: ChimeKind) {
        val gen = tones ?: ToneGenerator(AudioManager.STREAM_MUSIC, 100).also { tones = it }
        val sequence = when (kind) {
            ChimeKind.HOUR -> listOf(
                ToneGenerator.TONE_DTMF_1 to 180,
                ToneGenerator.TONE_DTMF_4 to 180,
                ToneGenerator.TONE_DTMF_8 to 280,
            )
            ChimeKind.QUARTER -> listOf(
                ToneGenerator.TONE_DTMF_3 to 140,
                ToneGenerator.TONE_DTMF_6 to 140,
                ToneGenerator.TONE_DTMF_9 to 220,
            )
            ChimeKind.HALF -> listOf(
                ToneGenerator.TONE_DTMF_0 to 160,
                ToneGenerator.TONE_DTMF_5 to 260,
            )
            ChimeKind.THREE_QUARTER -> listOf(
                ToneGenerator.TONE_DTMF_9 to 140,
                ToneGenerator.TONE_DTMF_6 to 140,
                ToneGenerator.TONE_DTMF_3 to 220,
            )
            ChimeKind.FIVE -> listOf(
                ToneGenerator.TONE_PROP_BEEP to 90,
                ToneGenerator.TONE_PROP_BEEP2 to 90,
            )
            ChimeKind.TEN -> listOf(
                ToneGenerator.TONE_DTMF_7 to 110,
                ToneGenerator.TONE_DTMF_9 to 110,
            )
            ChimeKind.BUNNY -> listOf(
                ToneGenerator.TONE_PROP_BEEP2 to 160,
                ToneGenerator.TONE_PROP_BEEP to 220,
            )
        }
        var delay = 0L
        sequence.forEach { (tone, ms) ->
            handler.postDelayed({
                runCatching { gen.startTone(tone, ms) }
            }, delay)
            delay += ms + 40L
        }
    }

    private fun raiseVolume() {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val target = (max * 0.8).toInt().coerceAtLeast(1)
        if (am.getStreamVolume(AudioManager.STREAM_MUSIC) < target) {
            am.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
        }
        runCatching { am.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT) }
    }

    companion object {
        private const val TAG = "ChimePlayer"
    }
}
