package com.chenniuniu.rokidfocus.glasses

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import com.chenniuniu.rokidfocus.data.FocusState
import com.chenniuniu.rokidfocus.data.FocusTask
import com.chenniuniu.rokidfocus.FocusApplication
import com.chenniuniu.rokidfocus.listen.PhoneListen
import com.rokid.cxr.Caps
import com.rokid.cxr.link.CXRLink
import com.rokid.cxr.link.callbacks.IAudioStreamCbk
import com.rokid.cxr.link.callbacks.ICXRLinkCbk
import com.rokid.cxr.link.callbacks.ICustomCmdCbk
import com.rokid.cxr.link.callbacks.ICustomViewCbk
import com.rokid.cxr.link.callbacks.IGlassAppCbk
import com.rokid.cxr.link.utils.CxrDefs
import com.rokid.cxr.link.utils.GlassInfo
import com.rokid.sprite.aiapp.externalapp.auth.AuthResult
import com.rokid.sprite.aiapp.externalapp.auth.AuthorizationHelper
import com.rokid.sprite.aiapp.externalapp.auth.GlassPermission

/**
 * CXR-L CustomApp. Glasses mic over pairing; iFlytek ASR on the phone.
 */
class CxrHudController(
    private val appContext: Context,
    private val onStatus: (GlassesStatus, String) -> Unit,
    private val onListen: (Boolean) -> Unit = {},
    private val onReactPick: (Int) -> Unit = {},
) {
    private var link: CXRLink? = null
    private var token: String = ""
    private var cxrOk = false
    private var btOk = false
    private var viewOpen = false
    private var appStarted = false
    private var lastJson: String = ""
    private var lastTasks: String = ""
    private var lastPriority: String = ""
    private var lastNow: String = ""

    private val app get() = appContext as FocusApplication

    private val listen = PhoneListen(
        context = appContext,
        memory = app.convo,
        seed = { app.store.snapshot().convoTurns },
        style = { app.store.snapshot().talkStyle },
        nativeLang = { app.store.snapshot().nativeLang },
        llmKey = { app.store.replyKey() },
        voiceId = { app.store.voiceId() },
        onEnroll = { ok, msg ->
            if (ok) {
                app.store.setVoiceId(msg)
                app.store.setEnrollLine("voice print saved — toggle listen to apply")
            } else {
                app.store.setEnrollLine(msg)
            }
        },
        onLog = { },
        onTurn = { who, text -> app.store.appendConvo(who, text) },
        onLive = { who, text, trans -> app.store.setConvoLive(who, text, trans) },
        onReplies = { replies -> app.store.setLastReplies(replies) },
        onTrans = { trans -> app.store.setLastTrans(trans) },
        onLlm = { status -> app.store.setLlmLine(status) },
        sendAsr = { text, who, trans ->
            if (trans.isBlank()) send("asr", text, "0", who)
            else send("asr", text, "0", who, trans)
        },
        sendReact = { drafts ->
            if (drafts.isNotEmpty()) send("react", *drafts.toTypedArray())
        },
        sendState = { state, msg -> send("listen_state", state, msg) },
        onPhoneMic = { line -> app.store.setLlmLine(line) },
    )

    val requestCode: Int = REQUEST_AUTH
    val isListening: Boolean get() = listen.isOn

    fun isCompanionInstalled(): Boolean {
        val pm = appContext.packageManager
        return COMPANION_PACKAGES.any { pkg ->
            runCatching {
                pm.getPackageInfo(pkg, 0)
                true
            }.getOrDefault(false)
        }
    }

    fun requestAuth(activity: Activity) {
        if (!isCompanionInstalled()) {
            onStatus(GlassesStatus.MissingCompanion, "Install Rokid AI or Hi Rokid, then retry.")
            return
        }
        onStatus(GlassesStatus.Authorizing, "Waiting for companion authorization…")
        val cached = AuthorizationHelper.requestAuthorization(
            activity,
            arrayOf(GlassPermission.MEDIA, GlassPermission.MICROPHONE),
            REQUEST_AUTH,
        )
        if (cached != null) {
            parseAuth(cached.first, cached.second)
        }
    }

    fun parseAuth(resultCode: Int, data: Intent?) {
        when (val result = AuthorizationHelper.parseAuthorizationResult(resultCode, data)) {
            is AuthResult.AuthSuccess -> {
                token = result.token
                if (token.isBlank()) {
                    onStatus(GlassesStatus.Error, "Auth returned an empty token.")
                } else {
                    onStatus(GlassesStatus.Connecting, "Token ok. Connecting…")
                    connect()
                }
            }
            is AuthResult.AuthFail -> onStatus(GlassesStatus.Error, "Authorization failed.")
            is AuthResult.AuthCancel -> onStatus(GlassesStatus.Idle, "Authorization cancelled.")
        }
    }

    fun connect() {
        if (token.isBlank()) {
            onStatus(GlassesStatus.Error, "No token. Tap Connect glasses first.")
            return
        }
        disconnect()
        cxrOk = false
        btOk = false
        viewOpen = false
        appStarted = false
        lastTasks = ""
        val created = CXRLink(appContext).apply {
            configCXRSession(
                CxrDefs.CXRSession(CxrDefs.CXRSessionType.CUSTOMAPP, GLASS_PKG)
            )
            setCXRLinkCbk(linkCallback)
            setCXRCustomViewCbk(viewCallback)
            setCXRCustomCmdCbk(cmdCallback)
            setCXRAudioCbk(audioCallback)
        }
        link = created
        created.connect(token)
        onStatus(GlassesStatus.Connecting, "Waiting for CXR + Bluetooth…")
    }

    fun push(state: FocusState) {
        val current = link ?: return
        if (!cxrOk || !btOk) return
        val tasks = FocusTask.toJson(state.tasks)
        if (tasks != lastTasks) {
            lastTasks = tasks
            send("set_tasks", tasks)
        }
        if (state.priority != lastPriority && state.priority.isNotBlank()) {
            lastPriority = state.priority
            send("set_priority", state.priority)
        }
        if (state.nowDoing != lastNow && state.nowDoing.isNotBlank()) {
            lastNow = state.nowDoing
            send("set_now", state.nowDoing)
        }
        if (state.checkInActive.not()) {
            // still keep CustomView overlay if the companion allows it
            runCatching {
                val json = HudLayout.updateJson(state)
                if (json != lastJson && viewOpen) {
                    lastJson = json
                    current.customViewUpdate(json)
                }
            }
        }
    }

    fun startListen(fromGlass: Boolean = false) {
        if (link == null || !cxrOk || !btOk) {
            onStatus(GlassesStatus.Error, "Connect glasses first, then listen.")
            onListen(false)
            return
        }
        if (!fromGlass) send("listen_start")
        listen.start()
        app.store.markListen(true)
        onListen(true)
        runCatching { link?.stopAudioStream() }
        onStatus(GlassesStatus.Ready, "Listening — iFlytek ASR on this phone.")
    }

    fun stopListen(fromGlass: Boolean = false) {
        if (!fromGlass) send("listen_stop")
        runCatching { link?.stopAudioStream() }
        listen.stop()
        app.store.markListen(false)
        onListen(false)
    }

    fun toggleListen() {
        if (listen.isOn) stopListen() else startListen()
    }

    fun agentDelta(text: String) {
        if (text.isBlank()) return
        send("agent_stream", "agent", text.takeLast(400))
    }

    fun agentImage() {
        send("agent_img", "1")
    }

    fun agentDone() {
        send("agent_done")
    }

    fun beginVoiceEnroll() {
        if (!listen.isOn) startListen()
        app.store.setEnrollLine("speak 12s into the glasses")
        listen.beginEnroll()
    }

    fun disconnect() {
        stopListen()
        val current = link
        if (current != null) {
            runCatching {
                if (viewOpen || current.customViewIsOpen()) current.customViewClose()
            }
            runCatching { current.disconnect() }
        }
        link = null
        viewOpen = false
        cxrOk = false
        btOk = false
        appStarted = false
    }

    private fun send(vararg parts: String) {
        val current = link ?: return
        runCatching {
            val caps = Caps().apply { parts.forEach { write(it) } }
            val code = current.sendCustomCmd(CLIENT_KEY, caps)
            if (code != null && code < 0) {
                Log.w(TAG, "sendCustomCmd $code ${parts.firstOrNull()}")
            }
        }.onFailure { Log.e(TAG, "send failed: ${it.message}") }
    }

    private fun maybeReady() {
        if (!(cxrOk && btOk)) return
        if (!appStarted) {
            appStarted = true
            runCatching { link?.appStart(GLASS_PKG, appCallback) }
        }
        onStatus(
            if (viewOpen) GlassesStatus.ViewOpen else GlassesStatus.Ready,
            if (listen.isOn) "Listening on phone network."
            else "Link ready. Temple tap listens via this phone’s Wi‑Fi or 5G.",
        )
    }

    private val linkCallback = object : ICXRLinkCbk {
        override fun onCXRLConnected(connected: Boolean) {
            Log.i(TAG, "onCXRLConnected $connected")
            cxrOk = connected
            if (!connected) {
                viewOpen = false
                stopListen()
                onStatus(GlassesStatus.Connecting, "CXR dropped.")
            } else {
                maybeReady()
            }
        }

        override fun onGlassBtConnected(connected: Boolean) {
            Log.i(TAG, "onGlassBtConnected $connected")
            btOk = connected
            if (!connected) {
                viewOpen = false
                stopListen()
                onStatus(GlassesStatus.Connecting, "Glasses Bluetooth dropped.")
            } else {
                maybeReady()
            }
        }

        override fun onGlassAiAssistStart() {}
        override fun onGlassAiAssistStop() {}
        override fun onGlassAiInterrupt(interruptWake: Boolean) {}
        override fun onGlassDeviceInfo(deviceInfo: GlassInfo) {}
        override fun onGlassWearingStatus(wearing: Boolean) {}
    }

    private val viewCallback = object : ICustomViewCbk {
        override fun onCustomViewOpened() {
            viewOpen = true
            onStatus(GlassesStatus.ViewOpen, "HUD on glasses.")
        }

        override fun onCustomViewUpdated() {}

        override fun onCustomViewClosed() {
            viewOpen = false
            onStatus(GlassesStatus.Ready, "HUD closed.")
        }

        override fun onCustomViewIconsSent() {}

        override fun onCustomViewError(code: Int, message: String?) {
            viewOpen = false
            Log.w(TAG, "CustomView error $code ${message.orEmpty()}")
        }
    }

    private val cmdCallback = object : ICustomCmdCbk {
        override fun onCustomCmdResult(cmd: String?, bytes: ByteArray?) {
            if (bytes == null || bytes.isEmpty()) return
            val caps = runCatching { Caps.fromBytes(bytes) }.getOrNull() ?: return
            if (feedPcm(caps)) return
            val fields = readStrings(caps)
            Log.i(TAG, "cmd=$cmd fields=$fields")
            when (fields.firstOrNull()) {
                "listen_on" -> startListen(fromGlass = true)
                "listen_off" -> stopListen(fromGlass = true)
                "still_on_this" -> { }
                "react_pick" -> onReactPick(fields.getOrNull(1)?.toIntOrNull() ?: 0)
            }
        }
    }

    private fun feedPcm(caps: Caps): Boolean {
        if (caps.size() < 2) return false
        val head = caps.at(0)
        if (head.type() != Caps.Value.TYPE_STRING) return false
        if (head.string != "pcm") return false
        val bin = caps.at(1)
        if (bin.type() != Caps.Value.TYPE_BINARY) return false
        val b = bin.binary ?: return false
        val data = b.data ?: return false
        val start = b.offset.coerceAtLeast(0)
        val end = (start + b.length).coerceAtMost(data.size)
        if (end <= start) return false
        if (!listen.isOn) startListen()
        listen.onPcm(data.copyOfRange(start, end), 16000, 1)
        return true
    }

    private val audioCallback = object : IAudioStreamCbk {
        override fun onAudioReceived(data: ByteArray?, sampleRate: Int, channels: Int) {
            if (data == null || data.isEmpty()) return
            listen.onPcm(data, sampleRate, channels)
        }

        override fun onAudioError(code: Int, msg: String?) {
            Log.e(TAG, "audio $code $msg")
            send("listen_state", "err", msg.orEmpty().take(40))
        }

        override fun onAudioStreamStateChanged(streaming: Boolean) {
            Log.i(TAG, "audio streaming=$streaming")
            if (streaming && listen.isOn) send("listen_state", "live", "phone")
        }
    }

    private val appCallback = object : IGlassAppCbk {
        override fun onInstallAppResult(ok: Boolean) {}
        override fun onUnInstallAppResult(ok: Boolean) {}
        override fun onOpenAppResult(ok: Boolean) {
            Log.i(TAG, "appStart $ok")
            if (ok) onStatus(GlassesStatus.Ready, "Focus app on glasses.")
        }
        override fun onStopAppResult(ok: Boolean) {}
        override fun onGlassAppResume(ok: Boolean) {}
        override fun onQueryAppResult(ok: Boolean) {}
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
        private const val TAG = "CxrHud"
        const val REQUEST_AUTH = 1001
        const val GLASS_PKG = "com.chenniuniu.rokidfocus.glass"
        const val CLIENT_KEY = "rk_custom_client"
        private val COMPANION_PACKAGES = listOf(
            "com.rokid.sprite.aiapp",
            "com.rokid.sprite.global.aiapp",
        )
    }
}
