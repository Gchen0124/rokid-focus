package com.chenniuniu.rokidfocus.glass

import android.content.Context
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.chenniuniu.rokidfocus.glass.ui.GlassHud

class MainActivity : ComponentActivity() {

    private val keyReceiver = KeyReceiver(
        onAck = { dimDisplay() },
        onExit = { exitApp() },
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DisplayWake.stayAwake(this)
        runCatching { GlassFocusService.start(this) }
        DisplayWake.scheduleNextChime(this)
        val filter = IntentFilter().apply {
            GlassKey.entries.forEach { addAction(it.action) }
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(keyReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(keyReceiver, filter)
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                exitApp()
            }
        })
        val store = app().store
        setContent {
            val state by store.state.collectAsState()
            LaunchedEffect(state.dimLevel, state.checkInActive) {
                DisplayWake.applyDim(this@MainActivity, state.dimLevel, state.checkInActive)
            }
            GlassHud(state, onDim = { dimDisplay() })
        }
        DisplayWake.applyDim(this, store.snapshot().dimLevel, store.snapshot().checkInActive)
    }

    override fun onResume() {
        super.onResume()
        app().store.importUsbList()
        DisplayWake.stayAwake(this)
        DisplayWake.scheduleNextChime(this)
        val snap = app().store.snapshot()
        DisplayWake.applyDim(this, snap.dimLevel, snap.checkInActive)
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(keyReceiver) }
        super.onDestroy()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        Log.i(TAG, "onKeyDown keyCode=$keyCode")
        return when (keyCode) {
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_ESCAPE -> {
                exitApp()
                true
            }
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_SPACE,
            KeyEvent.KEYCODE_NOTIFICATION -> {
                dimDisplay()
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    private fun dimDisplay() {
        val level = app().store.cycleDim()
        DisplayWake.applyDim(this, level, app().store.snapshot().checkInActive)
    }

    private fun exitApp() {
        DisplayWake.cancelChime(this)
        DisplayWake.restoreTimeout(this)
        GlassFocusService.stop(this)
        finishAndRemoveTask()
    }

    private fun app(): GlassApplication = application as GlassApplication

    companion object {
        private const val TAG = "FocusGlass"
    }
}
