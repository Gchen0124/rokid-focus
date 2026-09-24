package com.chenniuniu.rokidfocus.glass.cxr

import android.os.Handler
import android.os.Looper
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

    var onPhoneListen: ((Boolean) -> Unit)? = null

    private val bridge = CXRServiceBridge()
    private val main = Handler(Looper.getMainLooper())
    private var hideRunnable: Runnable? = null
    private val pcmGate = java.util.concurrent.atomic.AtomicBoolean(false)

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
                "listen_start" -> main.post { onPhoneListen?.invoke(true) }
                "listen_stop" -> main.post { onPhoneListen?.invoke(false) }
                "set_listen_proxy" -> store.update {
                    it.copy(
                        listenHost = fields.getOrNull(1)?.ifBlank { it.listenHost } ?: it.listenHost,
                        listenPort = fields.getOrNull(2)?.toIntOrNull() ?: it.listenPort,
                    )
                }
                "listen_state" -> {
                    val st = fields.getOrNull(1).orEmpty()
                    val msg = fields.getOrNull(2).orEmpty()
                    val line = when (st) {
                        "live" -> when {
                            msg.isBlank() || msg == "phone" -> "live"
                            msg == "ok" -> "live"
                            else -> msg.take(18)
                        }
                        "off" -> "off"
                        "err" -> shortErr(msg)
                        else -> st.ifBlank { "live" }
                    }
                    store.update { it.copy(listenLine = line, listenOn = st != "off") }
                    if (st == "off") clearConvo()
                }
                "asr" -> applyAsr(
                    text = fields.getOrNull(1).orEmpty(),
                    definite = fields.getOrNull(2) == "1",
                    who = fields.getOrNull(3).orEmpty(),
                    trans = fields.getOrNull(4).orEmpty(),
                )
                "react" -> {
                    val drafts = fields.drop(1).filter { it.isNotBlank() }
                    val incoming = when {
                        drafts.size == 1 && drafts[0] == "…" -> listOf("…")
                        drafts.size >= 2 -> (drafts.take(2) + "skip")
                        else -> emptyList()
                    }
                    if (incoming.isNotEmpty()) {
                        store.update {
                            it.copy(
                                convoActive = true,
                                convoDrafts = incoming,
                                convoPick = 0,
                            )
                        }
                    }
                }
                "convo_hist" -> store.update {
                    it.copy(convoHist = fields.drop(1).filter { line -> line.isNotBlank() })
                }
                "agent_stream" -> {
                    val text = fields.getOrNull(2).orEmpty()
                    store.update {
                        it.copy(agentActive = true, agentLine = text.takeLast(400), agentImg = it.agentImg)
                    }
                    scheduleAgentClear()
                }
                "agent_img" -> {
                    store.update { it.copy(agentActive = true, agentImg = true) }
                    scheduleAgentClear()
                }
                "agent_done" -> scheduleAgentClear()
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

    fun sendReactPick(index: Int) {
        send("react_pick", index.toString())
    }

    fun sendAgentAsk() {
        send("agent_ask")
    }

    fun sendListen(on: Boolean) {
        send(if (on) "listen_on" else "listen_off")
    }

    fun sendPcm(pcm: ByteArray): Boolean {
        if (pcm.isEmpty()) return false
        if (!pcmGate.compareAndSet(false, true)) return false
        return try {
            val code = bridge.sendMessage(
                CMD_KEY,
                Caps().apply {
                    write("pcm")
                    write(pcm)
                },
            )
            if (code != 0) Log.w(TAG, "pcm send $code")
            code == 0
        } catch (e: Exception) {
            Log.w(TAG, "pcm ${e.message}")
            false
        } finally {
            pcmGate.set(false)
        }
    }

    private var agentHide: Runnable? = null

    private fun scheduleAgentClear() {
        agentHide?.let { main.removeCallbacks(it) }
        val hide = Runnable {
            store.update { it.copy(agentActive = false, agentLine = "", agentImg = false) }
        }
        agentHide = hide
        main.postDelayed(hide, AGENT_HIDE_MS)
    }

    fun clearConvo() {
        hideRunnable?.let { main.removeCallbacks(it) }
        store.update {
            it.copy(
                convoActive = false,
                listenOn = false,
                convoLine = "",
                convoTrans = "",
                convoDrafts = emptyList(),
                convoWho = "",
                convoHist = emptyList(),
                listenLine = if (it.listenLine == "live") "off" else it.listenLine,
            )
        }
    }

    private fun applyAsr(text: String, definite: Boolean, who: String, trans: String = "") {
        val line = text.trim()
        if (line.isBlank()) return
        store.update {
            it.copy(
                convoActive = true,
                listenOn = true,
                convoLine = line,
                convoTrans = trans,
                convoPartial = !definite,
                convoWho = who,
                convoScroll = 0,
                listenLine = "live",
            )
        }
        hideRunnable?.let { main.removeCallbacks(it) }
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
        private const val HIDE_AFTER_MS = 8000L
        private const val AGENT_HIDE_MS = 15000L

        private fun shortErr(msg: String): String {
            val s = msg.lowercase()
            return when {
                s.contains("timeout") || s.contains("waiting next") -> "live"
                s.contains("no mic") -> "no mic"
                s.contains("no key") || s.contains("xfyun") || s.contains("no xfyun") -> "no key"
                else -> "asr"
            }
        }
    }
}
