package com.chenniuniu.rokidfocus.glass

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

enum class GlassKey(val action: String) {
    CLICK("com.android.action.ACTION_SPRITE_BUTTON_CLICK"),
    DOUBLE_CLICK("com.android.action.ACTION_SPRITE_BUTTON_DOUBLE_CLICK"),
    LONG_PRESS("com.android.action.ACTION_SPRITE_BUTTON_LONG_PRESS"),
    TAP("com.android.action.ACTION_TWO_FINGER_SINGLE_TAP"),
    SWIPE_FORWARD("com.android.action.ACTION_TWO_FINGER_SWIPE_FORWARD"),
    SWIPE_BACK("com.android.action.ACTION_TWO_FINGER_SWIPE_BACK"),
}

class KeyReceiver(
    private val onAck: () -> Unit,
    private val onExit: () -> Unit,
    private val onShowResults: () -> Unit,
    private val onShowTasks: () -> Unit,
) : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        when (intent?.action) {
            GlassKey.CLICK.action,
            GlassKey.TAP.action -> {
                onAck()
                abortBroadcast()
            }
            GlassKey.SWIPE_FORWARD.action -> {
                onShowResults()
                abortBroadcast()
            }
            GlassKey.SWIPE_BACK.action -> {
                onShowTasks()
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
