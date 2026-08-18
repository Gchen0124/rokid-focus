package com.chenniuniu.rokidfocus.glass

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

enum class GlassKey(val action: String) {
    CLICK("com.android.action.ACTION_SPRITE_BUTTON_CLICK"),
    DOUBLE_CLICK("com.android.action.ACTION_SPRITE_BUTTON_DOUBLE_CLICK"),
    LONG_PRESS("com.android.action.ACTION_SPRITE_BUTTON_LONG_PRESS"),
    TAP("com.android.action.ACTION_TWO_FINGER_SINGLE_TAP"),
}

class KeyReceiver(
    private val onAck: () -> Unit,
    private val onExit: () -> Unit,
) : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        when (intent?.action) {
            GlassKey.CLICK.action,
            GlassKey.TAP.action -> {
                onAck()
                abortBroadcast()
            }
            GlassKey.DOUBLE_CLICK.action,
            GlassKey.LONG_PRESS.action -> {
                onExit()
                abortBroadcast()
            }
        }
    }
}
