package com.chenniuniu.rokidfocus.glass

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.SystemClock
import com.chenniuniu.rokidfocus.glass.clock.ChimeKind
import com.chenniuniu.rokidfocus.glass.clock.WallClock
import com.chenniuniu.rokidfocus.glass.sound.ChimePlayer
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class GlassFocusService : Service() {

    private val executor = Executors.newSingleThreadScheduledExecutor()
    private var tick: ScheduledFuture<*>? = null
    private lateinit var player: ChimePlayer
    private var lastFiredMinuteKey: String = ""

    override fun onCreate() {
        super.onCreate()
        player = ChimePlayer(this)
        ensureChannel()
        runCatching { startForeground(NOTIF_ID, notification("Focus running")) }
        tick = executor.scheduleAtFixedRate({
            refreshCountdown()
            fireIfDue()
        }, 0, 1, TimeUnit.SECONDS)
        scheduleNextAlarm()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CHIME) fireIfDue(force = true)
        scheduleNextAlarm()
        return START_STICKY
    }

    override fun onDestroy() {
        tick?.cancel(true)
        executor.shutdownNow()
        player.release()
        getSystemService(AlarmManager::class.java).cancel(alarmIntent())
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun refreshCountdown() {
        val now = WallClock.now()
        val next = WallClock.nextFiveMinuteMark(now)
        val totalSec = (WallClock.millisUntil(next, now) / 1000).toInt()
        val kind = ChimeKind.forMinute(next.minute)
        app().store.update {
            it.copy(
                clockLabel = WallClock.formatHms(now),
                nextMarkLabel = kind.label,
                countdownLabel = "%d:%02d".format(totalSec / 60, totalSec % 60),
            )
        }
    }

    private fun fireIfDue(force: Boolean = false) {
        val now = WallClock.now()
        val onMark = now.minute % 5 == 0 && now.second < 2
        if (!force && !onMark) return
        val key = WallClock.formatHm(now)
        if (!force && key == lastFiredMinuteKey) return
        lastFiredMinuteKey = key
        val kind = ChimeKind.forMinute(now.minute - (now.minute % 5))
        player.play(kind)
        app().store.update {
            it.copy(
                checkInActive = true,
                lastChimeKind = kind,
                lastChimeLabel = "${kind.label}  $key",
                statusLine = "CHECK IN",
            )
        }
        app().bridge.sendCheckIn(kind, key)
        startForeground(NOTIF_ID, notification("CHECK IN · ${kind.label}"))
        executor.schedule({
            app().store.update { state ->
                if (state.checkInActive) state.copy(checkInActive = false, statusLine = "Focus")
                else state
            }
        }, 12, TimeUnit.SECONDS)
    }

    private fun scheduleNextAlarm() {
        val next = WallClock.nextFiveMinuteMark()
        val triggerAt = SystemClock.elapsedRealtime() + WallClock.millisUntil(next)
        val am = getSystemService(AlarmManager::class.java)
        val pi = alarmIntent()
        runCatching {
            am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
        }.onFailure {
            am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
        }
    }

    private fun alarmIntent(): PendingIntent {
        return PendingIntent.getBroadcast(
            this,
            8,
            Intent(this, GlassAlarmReceiver::class.java).setAction(ACTION_CHIME),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun ensureChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Focus", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun notification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Focus")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    private fun app(): GlassApplication = application as GlassApplication

    companion object {
        const val ACTION_CHIME = "com.chenniuniu.rokidfocus.glass.CHIME"
        private const val CHANNEL_ID = "glass_focus"
        private const val NOTIF_ID = 42

        fun start(context: Context) {
            runCatching {
                context.startForegroundService(Intent(context, GlassFocusService::class.java))
            }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, GlassFocusService::class.java)) }
        }
    }
}

class GlassAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        DisplayWake.wakeAndShowApp(context)
        val app = context.applicationContext as? GlassApplication
        app?.runtime?.onAlarm()
        runCatching {
            context.startForegroundService(
                Intent(context, GlassFocusService::class.java).setAction(GlassFocusService.ACTION_CHIME)
            )
        }
        DisplayWake.scheduleNextChime(context)
    }
}
