package com.chenniuniu.rokidfocus.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.chenniuniu.rokidfocus.R
import com.chenniuniu.rokidfocus.clock.ChimeKind

class ChimePlayer(private val context: Context) {

    private var player: MediaPlayer? = null

    fun play(kind: ChimeKind) {
        val resId = when (kind) {
            ChimeKind.HOUR -> R.raw.chime_hour
            ChimeKind.QUARTER -> R.raw.chime_quarter
            ChimeKind.HALF -> R.raw.chime_half
            ChimeKind.THREE_QUARTER -> R.raw.chime_three_quarter
            ChimeKind.FIVE -> R.raw.chime_five
            ChimeKind.TEN -> R.raw.chime_ten
        }
        runCatching { player?.release() }
        player = MediaPlayer.create(context.applicationContext, resId)?.apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            setOnCompletionListener { it.release() }
            start()
        }
    }

    fun release() {
        runCatching { player?.release() }
        player = null
    }
}
