package com.chenniuniu.rokidfocus.glass

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import com.chenniuniu.rokidfocus.glass.ui.GlassHud

class MainActivity : ComponentActivity() {

    private var convo: ConvoListen? = null
    private var pendingFromPhone = false

    private val askMic = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) startConvo(fromPhone = pendingFromPhone)
        else app().store.update { it.copy(listenLine = "mic denied") }
    }

    private val keyReceiver = KeyReceiver(
        onExit = { exitApp() },
        onShowResults = {
            if (!app().store.snapshot().convoActive) {
                app().store.update { it.copy(showResults = true) }
            }
        },
        onShowTasks = {
            if (!app().store.snapshot().convoActive) {
                app().store.update { it.copy(showResults = false) }
            }
        },
        onToggleListen = { toggleListen() },
        onCycleDraft = { delta ->
            val s = app().store.snapshot()
            if (s.convoActive) {
                app().store.update {
                    it.copy(convoScroll = (it.convoScroll - delta).coerceIn(0, 80))
                }
            }
        },
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
            // CXR-S is another process; must be exported or two-finger swipe never arrives.
            registerReceiver(keyReceiver, filter, Context.RECEIVER_EXPORTED)
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
            GlassHud(
                state,
                onToggleListen = { toggleListen() },
                onShowResults = { show -> app().store.update { it.copy(showResults = show) } },
            )
        }
        DisplayWake.applyDim(this, store.snapshot().dimLevel, store.snapshot().checkInActive)
        store.update { it.copy(listenLine = "off") }
        app().bridge.onPhoneListen = { on ->
            runOnUiThread {
                if (on) {
                    if (convo?.isOn == true) return@runOnUiThread
                    pendingFromPhone = true
                    val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                    if (granted) startConvo(fromPhone = true) else askMic.launch(Manifest.permission.RECORD_AUDIO)
                } else {
                    convo?.stop()
                    convo = null
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        app().store.importUsbList()
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
        convo?.stop()
        convo = null
        runCatching { unregisterReceiver(keyReceiver) }
        super.onDestroy()
    }

    private var lastToggleAt = 0L

    private fun toggleListen() {
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastToggleAt < 450) return
        lastToggleAt = now
        if (convo?.isOn == true) {
            convo?.stop()
            convo = null
            return
        }
        pendingFromPhone = false
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (granted) startConvo() else askMic.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun startConvo(fromPhone: Boolean = false) {
        convo?.stop()
        convo = null
        convo = ConvoListen(this, app().store, app().bridge).also { it.start(notifyPhone = !fromPhone) }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        Log.i(TAG, "onKeyDown keyCode=$keyCode")
        return when (keyCode) {
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_ESCAPE -> {
                exitApp()
                true
            }
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_SPACE,
            KeyEvent.KEYCODE_NOTIFICATION -> {
                toggleListen()
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
        convo?.stop()
        convo = null
        GlassFocusService.stop(this)
        finishAndRemoveTask()
    }

    private fun app(): GlassApplication = application as GlassApplication

    companion object {
        private const val TAG = "FocusGlass"
    }
}
