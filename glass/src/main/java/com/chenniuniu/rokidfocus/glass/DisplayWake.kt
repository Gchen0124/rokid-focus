package com.chenniuniu.rokidfocus.glass

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import com.chenniuniu.rokidfocus.glass.clock.WallClock

/**
 * Rokid Glasses (YodaOS) default [Settings.System.SCREEN_OFF_TIMEOUT] is 5000 ms.
 * FLAG_KEEP_SCREEN_ON only works while our window is focused.
 * True 24/7 always-on is a system policy; we keep the display on while Focus
 * is running, and force-wake + reopen the app at each 5-minute mark.
 */
object DisplayWake {
    private const val TAG = "DisplayWake"
    private const val CHANNEL_ID = "focus_alerts"
    private const val NOTIF_ID = 43
    private const val ALARM_REQ = 9
    const val ACTION_CHIME = "com.chenniuniu.rokidfocus.glass.CHIME"
    private const val FOCUS_TIMEOUT_MS = 30 * 60 * 1000
    private const val STOCK_TIMEOUT_MS = 5_000

    fun stayAwake(activity: MainActivity) {
        activity.setShowWhenLocked(true)
        activity.setTurnScreenOn(true)
        activity.window.addFlags(
            android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                android.view.WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )
        activity.window.decorView.keepScreenOn = true
        acquireScreenLock(activity, 60_000)
        lengthenTimeout(activity)
    }

    fun wakeAndShowApp(context: Context) {
        acquireScreenLock(context, 20_000)
        val launch = Intent(context, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            )
            putExtra(EXTRA_FROM_CHIME, true)
        }
        runCatching { context.startActivity(launch) }
            .onFailure { Log.e(TAG, "startActivity failed: ${it.message}") }
        showHeadsUp(context)
    }

    fun scheduleNextChime(context: Context) {
        val next = WallClock.nextFiveMinuteMark()
        val at = next.toInstant().toEpochMilli()
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = chimePending(context)
        runCatching {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            Log.i(TAG, "next chime alarm at $next")
        }.onFailure {
            Log.e(TAG, "exact alarm failed: ${it.message}")
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun cancelChime(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(chimePending(context))
    }

    fun restoreTimeout(context: Context) {
        writeTimeout(context, STOCK_TIMEOUT_MS)
    }

    fun applyDim(activity: MainActivity, dimLevel: Int, checkIn: Boolean) {
        val brightness = when {
            checkIn -> 0.72f
            dimLevel <= 0 -> 0.10f
            dimLevel == 1 -> 0.22f
            else -> 0.45f
        }
        val params = activity.window.attributes
        params.screenBrightness = brightness
        activity.window.attributes = params
    }

    @Suppress("DEPRECATION")
    private fun acquireScreenLock(context: Context, ms: Long) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val flags = PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
            PowerManager.ACQUIRE_CAUSES_WAKEUP or
            PowerManager.ON_AFTER_RELEASE
        runCatching {
            pm.newWakeLock(flags, "rokidfocus:wake").apply {
                setReferenceCounted(false)
                acquire(ms)
            }
        }.onFailure { Log.e(TAG, "wake lock: ${it.message}") }
    }

    private fun lengthenTimeout(context: Context) {
        writeTimeout(context, FOCUS_TIMEOUT_MS)
    }

    private fun writeTimeout(context: Context, value: Int) {
        runCatching {
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_OFF_TIMEOUT,
                value
            )
            Log.i(TAG, "screen_off_timeout=$value")
        }.onFailure { Log.w(TAG, "cannot write timeout: ${it.message}") }
    }

    private fun showHeadsUp(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Focus check-in", NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
                setSound(null, null)
            }
        )
        val open = PendingIntent.getActivity(
            context,
            1,
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(EXTRA_FROM_CHIME, true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = Notification.Builder(context, CHANNEL_ID)
            .setContentTitle("CHECK IN")
            .setContentText("Look at your priority")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentIntent(open)
            .setFullScreenIntent(open, true)
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(NOTIF_ID, notif) }
    }

    private fun chimePending(context: Context): PendingIntent {
        return PendingIntent.getBroadcast(
            context,
            ALARM_REQ,
            Intent(context, GlassAlarmReceiver::class.java).setAction(ACTION_CHIME),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    const val EXTRA_FROM_CHIME = "from_chime"
}
