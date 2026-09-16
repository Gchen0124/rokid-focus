package com.chenniuniu.rokidfocus.glass

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.chenniuniu.rokidfocus.glass.clock.ChimeKind
import com.chenniuniu.rokidfocus.glass.clock.WallClock
import com.chenniuniu.rokidfocus.glass.sound.ChimePlayer

/**
 * Lives on the Application so chimes still fire when the activity is paused
 * (glasses display sleep).
 */
class GlassRuntime(private val app: GlassApplication) {
    private val handler = Handler(Looper.getMainLooper())
    private val player = ChimePlayer(app)
    private var lastFiredMinuteKey: String = ""
    private var started = false

    private val tick = object : Runnable {
        override fun run() {
            refreshClock()
            fireIfDue()
            handler.postDelayed(this, 1000)
        }
    }

    fun start() {
        if (started) return
        started = true
        handler.post(tick)
        DisplayWake.scheduleNextChime(app)
    }

    fun preview(kind: ChimeKind = ChimeKind.FIVE) {
        player.play(kind)
    }

    fun onAlarm() {
        DisplayWake.wakeAndShowApp(app)
        fireIfDue(force = true)
        DisplayWake.scheduleNextChime(app)
    }

    private fun refreshClock() {
        val now = WallClock.now()
        val next = WallClock.nextFiveMinuteMark(now)
        val totalSec = (WallClock.millisUntil(next, now) / 1000).toInt()
        val kind = ChimeKind.forMinute(next.minute)
        app.store.update {
            it.copy(
                clockLabel = WallClock.formatHms(now),
                nextMarkLabel = kind.label,
                countdownLabel = "%d:%02d".format(totalSec / 60, totalSec % 60),
            )
        }
    }

    private fun fireIfDue(force: Boolean = false) {
        val now = WallClock.now()
        if (!force && now.minute % 5 != 0) return
        val markMinute = now.minute - (now.minute % 5)
        val key = WallClock.formatHm(now.withMinute(markMinute).withSecond(0).withNano(0))
        if (key == lastFiredMinuteKey) return
        lastFiredMinuteKey = key
        val kind = ChimeKind.forMinute(markMinute)
        Log.i(TAG, "chime $kind at $key")
        DisplayWake.wakeAndShowApp(app)
        player.play(kind)
        DisplayWake.scheduleNextChime(app)
        app.store.update {
            it.copy(
                checkInActive = true,
                lastChimeKind = kind,
                lastChimeLabel = "${kind.label}  $key",
                statusLine = "CHECK IN",
            )
        }
        app.bridge.sendCheckIn(kind, key)
        handler.postDelayed({
            app.store.update { state ->
                if (state.checkInActive) state.copy(checkInActive = false, statusLine = "Focus")
                else state
            }
        }, 20_000)
    }

    companion object {
        private const val TAG = "GlassRuntime"
    }
}
