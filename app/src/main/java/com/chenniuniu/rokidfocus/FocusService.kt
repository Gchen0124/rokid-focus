package com.chenniuniu.rokidfocus

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
import com.chenniuniu.rokidfocus.clock.ChimeKind
import com.chenniuniu.rokidfocus.clock.WallClock
import com.chenniuniu.rokidfocus.sound.ChimePlayer
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class FocusService : Service() {

    private val executor = Executors.newSingleThreadScheduledExecutor()
    private var tick: ScheduledFuture<*>? = null
    private lateinit var player: ChimePlayer
    private var lastFiredMinuteKey: String = ""

    override fun onCreate() {
        super.onCreate()
        player = ChimePlayer(this)
        ensureChannel()
        startForeground(NOTIF_ID, buildNotification("Reminders armed"))
        startTicker()
        scheduleNextAlarm()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_CHIME -> fireIfDue(force = true)
            else -> fireIfDue(force = false)
        }
        scheduleNextAlarm()
        return START_STICKY
    }

    override fun onDestroy() {
        tick?.cancel(true)
        executor.shutdownNow()
        player.release()
        cancelAlarm()
        app().store.update { it.copy(remindersOn = false, checkInActive = false) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startTicker() {
        tick = executor.scheduleAtFixedRate({
            refreshCountdown()
            fireIfDue(force = false)
        }, 0, 1, TimeUnit.SECONDS)
    }

    private fun refreshCountdown() {
        val now = WallClock.now()
        val next = WallClock.nextFiveMinuteMark(now)
        val ms = WallClock.millisUntil(next, now)
        val totalSec = (ms / 1000).toInt()
        val mm = totalSec / 60
        val ss = totalSec % 60
        val kind = ChimeKind.forMinute(next.minute)
        app().store.update {
            it.copy(
                remindersOn = true,
                clockLabel = WallClock.formatHms(now),
                nextMarkLabel = kind.label,
                countdownLabel = "%d:%02d".format(mm, ss),
            )
        }
        app().pushGlasses()
    }

    private fun fireIfDue(force: Boolean) {
        val now = WallClock.now()
        val onMark = now.minute % 5 == 0 && now.second < 2
        if (!force && !onMark) return
        val key = WallClock.formatHm(now)
        if (!force && key == lastFiredMinuteKey) return
        lastFiredMinuteKey = key
        val markMinute = now.minute - (now.minute % 5)
        val kind = ChimeKind.forMinute(markMinute)
        player.play(kind)
        app().store.update {
            it.copy(
                checkInActive = true,
                lastChimeKind = kind,
                lastChimeLabel = "${kind.label}  $key",
                statusLine = "Check in: still on your priority?",
            )
        }
        app().pushGlasses()
        startForeground(NOTIF_ID, buildNotification("Check in · ${kind.label} · $key"))
        executor.schedule({
            app().store.update { state ->
                if (state.checkInActive) {
                    state.copy(checkInActive = false, statusLine = "Back to work.")
                } else state
            }
            app().pushGlasses()
        }, CHECK_IN_SECONDS, TimeUnit.SECONDS)
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

    private fun cancelAlarm() {
        getSystemService(AlarmManager::class.java).cancel(alarmIntent())
    }

    private fun alarmIntent(): PendingIntent {
        val intent = Intent(this, FocusAlarmReceiver::class.java).setAction(ACTION_CHIME)
        return PendingIntent.getBroadcast(
            this,
            7,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Focus reminders", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Rokid Focus")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    private fun app(): FocusApplication = application as FocusApplication

    companion object {
        const val ACTION_CHIME = "com.chenniuniu.rokidfocus.CHIME"
        const val ACTION_STOP = "com.chenniuniu.rokidfocus.STOP"
        private const val CHANNEL_ID = "focus_reminders"
        private const val NOTIF_ID = 41
        private const val CHECK_IN_SECONDS = 12L

        fun start(context: Context) {
            context.startForegroundService(Intent(context, FocusService::class.java))
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, FocusService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}

class FocusAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        context.startForegroundService(
            Intent(context, FocusService::class.java).setAction(FocusService.ACTION_CHIME)
        )
    }
}
