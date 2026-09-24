package com.chenniuniu.rokidfocus

import android.app.Activity
import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chenniuniu.rokidfocus.agent.AgentClient
import com.chenniuniu.rokidfocus.agent.AgentMessage
import com.chenniuniu.rokidfocus.agent.HermesDirectClient
import com.chenniuniu.rokidfocus.agent.MockAgentClient
import com.chenniuniu.rokidfocus.clock.ChimeKind
import com.chenniuniu.rokidfocus.clock.WallClock
import com.chenniuniu.rokidfocus.data.FocusState
import com.chenniuniu.rokidfocus.listen.Lang
import com.chenniuniu.rokidfocus.sound.ChimePlayer
import com.chenniuniu.rokidfocus.speak.TtsSpeaker
import com.chenniuniu.rokidfocus.speak.XfyunTts
import com.chenniuniu.rokidfocus.data.TaskSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FocusViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as FocusApplication
    private val previewPlayer = ChimePlayer(application)
    private var lastLocalEditAt = 0L

    val state: StateFlow<FocusState> = app.store.state

    init {
        app.speak = { speak(it) }
        app.askAgentNow = { askAgentWithConvo() }
        viewModelScope.launch {
            while (isActive) {
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
                delay(1000)
            }
        }
        viewModelScope.launch {
            while (isActive) {
                pullRemote()
                delay(2500)
            }
        }
    }

    fun setSyncUrl(url: String) {
        app.store.setSyncUrl(url)
    }

    fun addTask(title: String, value: Int) {
        app.store.addTask(title, value)
        app.pushGlasses()
        lastLocalEditAt = System.currentTimeMillis()
        pushRemote()
    }

    fun setPriority(value: String) {
        app.store.setPriority(value)
        app.pushGlasses()
    }

    fun setNowDoing(value: String) {
        app.store.setNowDoing(value)
        app.pushGlasses()
    }

    fun removeTask(id: String) {
        app.store.removeTask(id)
        app.pushGlasses()
        lastLocalEditAt = System.currentTimeMillis()
        pushRemote()
    }

    fun setTaskValue(id: String, value: Int) {
        app.store.setTaskValue(id, value)
        app.pushGlasses()
        lastLocalEditAt = System.currentTimeMillis()
        pushRemote()
    }

    private fun pushRemote() {
        viewModelScope.launch {
            val url = app.store.snapshot().syncUrl
            if (url.isBlank()) return@launch
            runCatching {
                withContext(Dispatchers.IO) { TaskSync.push(url, app.store.snapshot().tasks) }
                app.store.setSyncLine("Pushed to laptop")
            }.onFailure {
                app.store.setSyncLine("Push failed: ${it.message}")
            }
        }
    }

    private suspend fun pullRemote() {
        if (System.currentTimeMillis() - lastLocalEditAt < 3000) return
        val url = app.store.snapshot().syncUrl
        if (url.isBlank()) return
        runCatching {
            val remote = withContext(Dispatchers.IO) { TaskSync.pull(url) }
            app.store.replaceTasks(remote.tasks)
            app.store.setOpportunities(remote.opportunities, remote.slogan)
            app.store.setSyncLine("Synced ${remote.tasks.size} tasks · ${remote.opportunities.size} opps")
        }.onFailure {
            app.store.setSyncLine("Waiting for laptop: ${it.message}")
        }
    }

    fun startReminders() {
        FocusService.start(getApplication())
        app.store.update { it.copy(remindersOn = true, statusLine = "Reminders armed on the 5-minute clock.") }
    }

    fun stopReminders() {
        FocusService.stop(getApplication())
        app.store.update { it.copy(remindersOn = false, checkInActive = false, statusLine = "Reminders stopped.") }
    }

    fun stillOnThis() {
        app.store.update { it.copy(checkInActive = false, statusLine = "Still on it.") }
        app.pushGlasses()
    }

    fun connectGlasses(activity: Activity) {
        app.glasses.requestAuth(activity)
    }

    fun toggleListen() {
        app.glasses.toggleListen()
    }

    fun beginVoiceEnroll() {
        app.glasses.beginVoiceEnroll()
    }

    fun setTalkStyle(value: String) {
        app.store.setTalkStyle(value)
    }

    fun setNativeLang(code: String) {
        app.store.setNativeLang(code)
    }

    fun setReplyKey(value: String) {
        app.store.setReplyKey(value)
    }

    fun clearConvo() {
        app.convo.clear()
        app.store.clearConvo()
    }

    fun onAuthResult(resultCode: Int, data: Intent?) {
        app.glasses.parseAuth(resultCode, data)
    }

    fun preview(kind: ChimeKind) {
        previewPlayer.play(kind)
    }

    // ---- Agent tab -------------------------------------------------------

    private var agentClient: AgentClient? = null

    fun askAgent(prompt: String) {
        val clean = prompt.trim()
        if (clean.isBlank() || app.store.snapshot().agentBusy) return
        val snap = app.store.snapshot()
        val history = snap.agentMessages
        app.store.setAgentMessages(history + AgentMessage(role = "user", text = clean))
        app.store.setAgentLive("")
        app.store.setAgentLine("")
        app.store.setAgentBusy(true)
        app.glasses.agentDelta("…")

        val client = buildClient(snap)
        agentClient = client
        val acc = StringBuilder()
        val images = mutableListOf<String>()
        client.ask(
            history = history,
            prompt = clean,
            images = emptyList(),
            onDelta = { delta ->
                acc.append(delta)
                app.store.setAgentLive(acc.toString())
                app.glasses.agentDelta(acc.toString())
            },
            onImage = { url ->
                if (images.none { it == url }) images.add(url)
                app.glasses.agentImage()
            },
            onDone = {
                app.glasses.agentDone()
                val text = acc.toString().trim()
                if (text.isNotBlank() || images.isNotEmpty()) {
                    app.store.setAgentMessages(
                        app.store.snapshot().agentMessages +
                            AgentMessage(role = "agent", text = text, images = images.toList()),
                    )
                }
                app.store.setAgentLive("")
                app.store.setAgentBusy(false)
            },
            onError = { err ->
                app.store.setAgentMessages(
                    app.store.snapshot().agentMessages + AgentMessage(role = "agent", text = "⚠ $err"),
                )
                app.store.setAgentLive("")
                app.store.setAgentBusy(false)
                app.store.setAgentLine(err)
            },
        )
    }

    /** Ask the agent using the live conversation as context (glasses gesture / ring). */
    fun askAgentWithConvo() {
        val convo = app.convo.prompt().trim()
        val prompt = if (convo.isBlank()) {
            "We are in a live conversation. Give me one short, spoken line I could say next."
        } else {
            "【live convo】\n$convo\n\nGive me one short, spoken line I should say next."
        }
        askAgent(prompt)
    }

    fun cancelAgent() {
        agentClient?.cancel()
        app.store.setAgentBusy(false)
        app.store.setAgentLive("")
    }

    fun clearAgent() {
        agentClient?.cancel()
        app.store.setAgentMessages(emptyList())
        app.store.setAgentLive("")
        app.store.setAgentBusy(false)
        app.store.setAgentLine("")
    }

    fun setAgentBackend(value: String) = app.store.setAgentBackend(value)
    fun setAgentUrl(value: String) = app.store.setAgentUrl(value)
    fun setAgentKey(value: String) = app.store.setAgentKey(value)
    fun setAgentModel(value: String) = app.store.setAgentModel(value)

    /** Hits `GET /v1/models` with the saved key so the URL/key can be checked first. */
    fun testAgent() {
        val snap = app.store.snapshot()
        val url = snap.agentUrl.trim().trimEnd('/')
        if (url.isBlank()) {
            app.store.setAgentLine("set the gateway URL first")
            return
        }
        val key = app.store.agentKey()
        app.store.setAgentLine("testing $url …")
        viewModelScope.launch {
            val line = withContext(Dispatchers.IO) {
                runCatching {
                    val conn = java.net.URL("$url/v1/models").openConnection() as java.net.HttpURLConnection
                    conn.setRequestProperty("Authorization", "Bearer $key")
                    conn.connectTimeout = 6000
                    conn.readTimeout = 8000
                    val code = conn.responseCode
                    val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                        ?.bufferedReader()?.readText().orEmpty()
                    if (code in 200..299) "ok · ${body.take(200)}" else "http $code · ${body.take(120)}"
                }.getOrElse { "fail: ${it.message}" }
            }
            app.store.setAgentLine(line)
        }
    }

    // ---- Speak for me (嘴替) ---------------------------------------------

    private val speaker by lazy { TtsSpeaker(app) }
    private var xfyunTts: XfyunTts? = null

    private fun cloudTts(): XfyunTts = xfyunTts ?: XfyunTts(
        appId = BuildConfig.XFYUN_APP_ID,
        apiKey = BuildConfig.XFYUN_API_KEY,
        apiSecret = BuildConfig.XFYUN_API_SECRET,
        onLine = { line -> app.store.setSpeakLine(line) },
    ).also { xfyunTts = it }

    /** Speaks [text] aloud through the phone / Bluetooth media route. */
    fun speak(text: String) {
        val t = text.trim()
        if (t.isBlank() || t.equals("skip", true)) return
        val lang = Lang.detect(t)
        if (app.store.snapshot().ttsBackend == "xfyun") {
            cloudTts().speak(t, app.store.snapshot().ttsVoice, lang)
            app.store.setSpeakLine("xfyun tts · $lang")
        } else {
            speaker.speak(t, lang)
            app.store.setSpeakLine("speaking · $lang")
        }
    }

    fun stopSpeak() {
        speaker.stop()
        xfyunTts?.stop()
        app.store.setSpeakLine("")
    }

    fun setTtsBackend(value: String) = app.store.setTtsBackend(value)

    fun setTtsVoice(value: String) = app.store.setTtsVoice(value)

    private fun buildClient(snap: FocusState): AgentClient {
        val key = app.store.agentKey()
        return if (snap.agentBackend == "hermes" && snap.agentUrl.isNotBlank() && key.isNotBlank()) {
            HermesDirectClient(snap.agentUrl, key, snap.agentModel)
        } else {
            MockAgentClient()
        }
    }

    override fun onCleared() {
        agentClient?.cancel()
        xfyunTts?.stop()
        speaker.shutdown()
        previewPlayer.release()
        super.onCleared()
    }
}
