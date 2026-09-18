package com.chenniuniu.rokidfocus.data

import android.content.Context
import com.chenniuniu.rokidfocus.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class FocusStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(
        FocusState(
            priority = prefs.getString(KEY_PRIORITY, "") ?: "",
            nowDoing = prefs.getString(KEY_NOW, "") ?: "",
            tasks = loadTasks(),
            syncUrl = prefs.getString(KEY_SYNC, DEFAULT_SYNC).orEmpty(),
            opportunities = Opportunity.fromJson(prefs.getString(KEY_OPPS, null)),
            slogan = prefs.getString(KEY_SLOGAN, DEFAULT_SLOGAN) ?: DEFAULT_SLOGAN,
            talkStyle = prefs.getString(KEY_TALK, DEFAULT_TALK) ?: DEFAULT_TALK,
            nativeLang = prefs.getString(KEY_NATIVE, "zh") ?: "zh",
            replyKeySet = replyKeyFromPrefs().isNotBlank(),
            llmLine = if (replyKeyFromPrefs().isNotBlank()) "DeepSeek ready" else "Paste DeepSeek key for replies",
            convoTurns = com.chenniuniu.rokidfocus.listen.ConvoTurn.fromJson(prefs.getString(KEY_CONVO, null)),
            voiceEnrolled = prefs.getString(KEY_VP, "").orEmpty().isNotBlank(),
        )
    )
    val state: StateFlow<FocusState> = _state.asStateFlow()

    fun snapshot(): FocusState = _state.value

    fun setPriority(value: String) {
        prefs.edit().putString(KEY_PRIORITY, value).apply()
        _state.update { it.copy(priority = value) }
    }

    fun setNowDoing(value: String) {
        prefs.edit().putString(KEY_NOW, value).apply()
        _state.update { it.copy(nowDoing = value) }
    }

    fun addTask(title: String, value: Int) {
        val clean = title.trim()
        if (clean.isEmpty()) return
        val next = FocusTask.ranked(_state.value.tasks + FocusTask(title = clean, value = value))
        saveTasks(next)
    }

    fun removeTask(id: String) {
        saveTasks(_state.value.tasks.filterNot { it.id == id })
    }

    fun setTaskValue(id: String, value: Int) {
        saveTasks(
            FocusTask.ranked(
                _state.value.tasks.map { if (it.id == id) it.copy(value = value) else it }
            )
        )
    }

    private fun loadTasks(): List<FocusTask> {
        val stored = FocusTask.fromJson(prefs.getString(KEY_TASKS, null))
        if (stored.isNotEmpty()) return FocusTask.ranked(stored)
        val migrated = buildList {
            prefs.getString(KEY_PRIORITY, "")?.trim()?.takeIf { it.isNotEmpty() }?.let {
                add(FocusTask(title = it, value = 8))
            }
            prefs.getString(KEY_NOW, "")?.trim()?.takeIf { it.isNotEmpty() }?.let {
                add(FocusTask(title = it, value = 5))
            }
        }
        if (migrated.isNotEmpty()) saveTasks(migrated)
        return FocusTask.ranked(migrated)
    }

    fun replaceTasks(tasks: List<FocusTask>) {
        saveTasks(tasks)
    }

    fun setOpportunities(items: List<Opportunity>, slogan: String? = null) {
        prefs.edit().putString(KEY_OPPS, org.json.JSONArray().apply {
            items.forEach { o ->
                put(
                    org.json.JSONObject()
                        .put("id", o.id)
                        .put("date", o.date)
                        .put("endDate", o.endDate)
                        .put("name", o.name)
                        .put("kind", o.kind)
                        .put("url", o.url)
                        .put("x", o.x)
                        .put("note", o.note)
                        .put("source", o.source)
                )
            }
        }.toString()).apply()
        if (slogan != null && slogan.isNotBlank()) {
            prefs.edit().putString(KEY_SLOGAN, slogan).apply()
            _state.update { it.copy(opportunities = items, slogan = slogan) }
        } else {
            _state.update { it.copy(opportunities = items) }
        }
    }

    fun setSyncUrl(url: String) {
        val clean = url.trim().trimEnd('/')
        prefs.edit().putString(KEY_SYNC, clean).apply()
        _state.update { it.copy(syncUrl = clean) }
    }

    fun setSyncLine(line: String) {
        _state.update { it.copy(syncLine = line) }
    }

    fun setTalkStyle(value: String) {
        val clean = value.trim().ifBlank { DEFAULT_TALK }
        prefs.edit().putString(KEY_TALK, clean).apply()
        _state.update { it.copy(talkStyle = clean) }
    }

    fun setNativeLang(code: String) {
        val clean = if (code == "en") "en" else "zh"
        prefs.edit().putString(KEY_NATIVE, clean).apply()
        _state.update { it.copy(nativeLang = clean) }
    }

    fun setConvoTurns(turns: List<com.chenniuniu.rokidfocus.listen.ConvoTurn>) {
        saveConvo(turns)
    }

    fun appendConvo(who: String, text: String, trans: String = "") {
        val t = text.trim()
        if (t.isBlank()) return
        val cur = _state.value.convoTurns.toMutableList()
        val last = cur.lastOrNull()
        if (last != null && last.who == who) {
            val merged = if (t.startsWith(last.text)) t else (last.text + " " + t).trim()
            cur[cur.lastIndex] = last.copy(
                text = merged.take(2000),
                trans = trans.ifBlank { last.trans },
                replies = emptyList(),
                at = System.currentTimeMillis(),
            )
        } else {
            cur.add(com.chenniuniu.rokidfocus.listen.ConvoTurn(who = who, text = t.take(2000), trans = trans))
        }
        saveConvo(cur.takeLast(200))
    }

    fun setLastTrans(trans: String) {
        if (trans.isBlank()) return
        val cur = _state.value.convoTurns.toMutableList()
        if (cur.isEmpty()) return
        cur[cur.lastIndex] = cur.last().copy(trans = trans)
        saveConvo(cur)
    }

    fun setLastReplies(replies: List<String>) {
        val clean = replies.filter { it.isNotBlank() && it != "…" }
        if (clean.isEmpty()) return
        val cur = _state.value.convoTurns.toMutableList()
        if (cur.isEmpty()) return
        cur[cur.lastIndex] = cur.last().copy(replies = clean)
        saveConvo(cur)
    }

    fun clearConvo() {
        saveConvo(emptyList())
        _state.update { it.copy(convoLiveWho = "", convoLiveText = "", convoLiveTrans = "") }
    }

    fun setConvoLive(who: String, text: String, trans: String = "") {
        _state.update { it.copy(convoLiveWho = who, convoLiveText = text, convoLiveTrans = trans) }
    }

    fun markListen(on: Boolean) {
        appendConvo("sys", if (on) "listen on" else "listen off")
        if (!on) _state.update { it.copy(convoLiveWho = "", convoLiveText = "", convoLiveTrans = "") }
    }

    private fun saveConvo(turns: List<com.chenniuniu.rokidfocus.listen.ConvoTurn>) {
        prefs.edit().putString(KEY_CONVO, com.chenniuniu.rokidfocus.listen.ConvoTurn.toJson(turns)).apply()
        _state.update { it.copy(convoTurns = turns) }
    }

    fun replyKey(): String = replyKeyFromPrefs()

    fun setReplyKey(value: String) {
        prefs.edit().putString(KEY_REPLY, value.trim()).apply()
        val ok = replyKeyFromPrefs().isNotBlank()
        _state.update {
            it.copy(
                replyKeySet = ok,
                llmLine = if (ok) "DeepSeek ready" else "Paste DeepSeek key for replies",
            )
        }
    }

    fun setLlmLine(line: String) {
        _state.update { it.copy(llmLine = line) }
    }

    fun voiceId(): String = prefs.getString(KEY_VP, "").orEmpty()

    fun setVoiceId(id: String) {
        prefs.edit().putString(KEY_VP, id.trim()).apply()
        _state.update { it.copy(voiceEnrolled = id.isNotBlank(), enrollLine = if (id.isNotBlank()) "voice print saved" else "") }
    }

    fun setEnrollLine(line: String) {
        _state.update { it.copy(enrollLine = line) }
    }

    private fun replyKeyFromPrefs(): String {
        val stored = prefs.getString(KEY_REPLY, "")?.trim().orEmpty()
        if (stored.isNotBlank()) return stored
        return BuildConfig.DEEPSEEK_API_KEY.trim()
    }

    private fun saveTasks(tasks: List<FocusTask>) {
        val ranked = FocusTask.ranked(tasks)
        prefs.edit().putString(KEY_TASKS, FocusTask.toJson(ranked)).apply()
        _state.update { it.copy(tasks = ranked) }
    }

    fun update(transform: (FocusState) -> FocusState) {
        _state.update(transform)
    }

    companion object {
        private const val PREFS = "rokid_focus"
        private const val KEY_PRIORITY = "priority"
        private const val KEY_NOW = "now_doing"
        private const val KEY_TASKS = "tasks_json"
        private const val KEY_SYNC = "sync_url"
        private const val KEY_OPPS = "opportunities_json"
        private const val KEY_SLOGAN = "slogan"
        private const val KEY_TALK = "talk_style"
        private const val KEY_NATIVE = "native_lang"
        private const val KEY_REPLY = "deepseek_reply_key"
        private const val KEY_CONVO = "convo_history_json"
        private const val KEY_VP = "xfyun_feature_id"
        const val DEFAULT_SYNC = "http://192.168.1.24:8787"
        const val DEFAULT_SLOGAN = "怪奇实验室 + 外交官"
        const val DEFAULT_TALK = "怪奇实验室 + 外交官"
    }
}
