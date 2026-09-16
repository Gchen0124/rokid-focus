package com.chenniuniu.rokidfocus.glass

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

enum class GlassKey(val action: String) {
    CLICK("com.android.action.ACTION_SPRITE_BUTTON_CLICK"),
    DOUBLE_CLICK("com.android.action.ACTION_SPRITE_BUTTON_DOUBLE_CLICK"),
    LONG_PRESS("com.android.action.ACTION_SPRITE_BUTTON_LONG_PRESS"),
    TAP("com.android.action.ACTION_TWO_FINGER_SINGLE_TAP"),
    DOUBLE_TAP("com.android.action.ACTION_TWO_FINGER_DOUBLE_TAP"),
    SWIPE_FORWARD("com.android.action.ACTION_TWO_FINGER_SWIPE_FORWARD"),
    SWIPE_BACK("com.android.action.ACTION_TWO_FINGER_SWIPE_BACK"),
}

class KeyReceiver(
    private val onExit: () -> Unit,
    private val onShowResults: () -> Unit,
    private val onShowTasks: () -> Unit,
    private val onToggleListen: () -> Unit,
    private val onCycleDraft: (Int) -> Unit,
) : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        when (intent?.action) {
            // Temple tap = listen on/off. Do not steal camera click/long-press.
            GlassKey.TAP.action -> {
                onToggleListen()
                abortBroadcast()
            }
            GlassKey.SWIPE_FORWARD.action -> {
                onCycleDraft(1)
                onShowResults()
                abortBroadcast()
            }
            GlassKey.SWIPE_BACK.action -> {
                onCycleDraft(-1)
                onShowTasks()
                abortBroadcast()
            }
            // Temple double-tap = back/exit. Stop mic first.
            GlassKey.DOUBLE_TAP.action -> {
                onExit()
                abortBroadcast()
            }
            // Camera button: let the system take photo / record.
            GlassKey.CLICK.action,
            GlassKey.DOUBLE_CLICK.action,
            GlassKey.LONG_PRESS.action -> {
                return
            }
        }
    }
}
