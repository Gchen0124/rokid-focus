package com.chenniuniu.rokidfocus.glass.cxr

import android.util.Log
import com.chenniuniu.rokidfocus.glass.clock.ChimeKind
import com.chenniuniu.rokidfocus.glass.data.GlassStore
import com.rokid.cxr.CXRServiceBridge
import com.rokid.cxr.Caps

/**
 * CXR-S link to the phone app.
 *
 * Phone → glasses channel: [CLIENT_KEY] (`rk_custom_client`)
 * Glasses → phone channel: [CMD_KEY] (`rk_custom_key`)
 *
 * Caps commands:
 *   set_priority / <text>
 *   set_now / <text>
 *   still_on_this
 */
class FocusBridge(private val store: GlassStore) {

    private val bridge = CXRServiceBridge()

    private val statusListener = object : CXRServiceBridge.StatusListener {
        override fun onConnected(p0: String?, p1: String?, p2: Int) {
            Log.i(TAG, "onConnected")
            store.update { it.copy(phoneLinked = true, statusLine = "Phone linked") }
        }

        override fun onDisconnected() {
            Log.i(TAG, "onDisconnected")
            store.update { it.copy(phoneLinked = false, statusLine = "Phone offline") }
        }

        override fun onConnecting(p0: String?, p1: String?, p2: Int) {
            store.update { it.copy(statusLine = "Linking phone…") }
        }

        override fun onARTCStatus(p0: Float, p1: Boolean) {}
        override fun onRokidAccountChanged(p0: String?) {}
        override fun onAudioNoise(p0: Float) {}
    }

    private val msgCallback = object : CXRServiceBridge.MsgCallback {
        override fun onReceive(name: String?, args: Caps?, bytes: ByteArray?) {
            val fields = args?.let { readStrings(it) }.orEmpty()
            Log.i(TAG, "onReceive name=$name fields=$fields")
            when (fields.firstOrNull()) {
                "set_priority" -> store.setPriority(fields.getOrNull(1).orEmpty())
                "set_now" -> store.setNowDoing(fields.getOrNull(1).orEmpty())
                "set_tasks" -> store.setTasks(
                    com.chenniuniu.rokidfocus.glass.data.FocusTask.fromJson(fields.getOrNull(1).orEmpty())
                )
                "add_task" -> store.addTask(
                    fields.getOrNull(1).orEmpty(),
                    fields.getOrNull(2)?.toDoubleOrNull() ?: 0.0,
                    fields.getOrNull(3)?.toIntOrNull() ?: 30,
                )
                "still_on_this" -> store.stillOnThis()
            }
        }
    }

    fun start() {
        bridge.setStatusListener(statusListener)
        bridge.subscribe(CLIENT_KEY, msgCallback)
    }

    fun sendCheckIn(kind: ChimeKind, timeLabel: String) {
        send("check_in", kind.label, timeLabel)
    }

    fun sendStillOnThis() {
        send("still_on_this")
    }

    private fun send(vararg parts: String) {
        runCatching {
            bridge.sendMessage(
                CMD_KEY,
                Caps().apply { parts.forEach { write(it) } }
            )
        }.onFailure { Log.e(TAG, "send failed: ${it.message}") }
    }

    private fun readStrings(caps: Caps): List<String> {
        val out = mutableListOf<String>()
        for (i in 0 until caps.size()) {
            val value = caps.at(i)
            if (value.type() == Caps.Value.TYPE_STRING) {
                out.add(value.string.orEmpty())
            }
        }
        return out
    }

    companion object {
        private const val TAG = "FocusBridge"
        const val CLIENT_KEY = "rk_custom_client"
        const val CMD_KEY = "rk_custom_key"
    }
}
