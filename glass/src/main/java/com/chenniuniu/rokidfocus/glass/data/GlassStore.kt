package com.chenniuniu.rokidfocus.glass.data

import android.content.Context
import java.io.File
import org.json.JSONObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class GlassStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    @Volatile private var lastUsbStamp: Long = -1L
    private val _state = MutableStateFlow(
        GlassState(
            priority = prefs.getString(KEY_PRIORITY, "") ?: "",
            nowDoing = prefs.getString(KEY_NOW, "") ?: "",
            tasks = loadTasks(),
            dimLevel = prefs.getInt(KEY_DIM, 1).coerceIn(0, 2),
            slogan = prefs.getString(KEY_SLOGAN, DEFAULT_SLOGAN) ?: DEFAULT_SLOGAN,
            opportunities = Opportunity.fromJson(prefs.getString(KEY_OPPS, null)),
        )
    )
    val state: StateFlow<GlassState> = _state.asStateFlow()

    fun snapshot(): GlassState = _state.value

    fun setPriority(value: String) {
        prefs.edit().putString(KEY_PRIORITY, value).apply()
        _state.update { it.copy(priority = value) }
    }

    fun setNowDoing(value: String) {
        prefs.edit().putString(KEY_NOW, value).apply()
        _state.update { it.copy(nowDoing = value) }
    }

    fun setTasks(tasks: List<FocusTask>) {
        saveTasks(tasks)
    }

    /** Reloads list dropped by the Mac over USB (`tasks.json` in app files). */
    fun importUsbList(): Boolean {
        val file = usbFile() ?: return false
        val text = file.readText()
        val parsed = FocusTask.fromJson(text)
        var slogan = DEFAULT_SLOGAN
        var opps = emptyList<Opportunity>()
        runCatching {
            val o = JSONObject(text)
            slogan = o.optString("slogan", DEFAULT_SLOGAN).ifBlank { DEFAULT_SLOGAN }
            opps = Opportunity.fromArray(o.optJSONArray("opportunities"))
        }
        lastUsbStamp = usbStamp(file)
        val ranked = FocusTask.ranked(parsed)
        prefs.edit()
            .putString(KEY_TASKS, FocusTask.toJson(ranked))
            .putString(KEY_SLOGAN, slogan)
            .putString(KEY_OPPS, Opportunity.toJson(opps))
            .apply()
        _state.update { it.copy(tasks = ranked, slogan = slogan, opportunities = opps) }
        return true
    }

    /** Called every tick so a USB copy shows up without restarting Focus. */
    fun reloadIfUsbChanged(): Boolean {
        val file = usbFile() ?: return false
        val stamp = usbStamp(file)
        if (stamp == lastUsbStamp) return false
        return importUsbList()
    }

    private fun usbFile(): File? {
        val candidates = listOfNotNull(
            appContext.filesDir.resolve(USB_FILE),
            appContext.getExternalFilesDir(null)?.resolve(USB_FILE),
            File("/sdcard/Download/focus_tasks.json"),
        )
        return candidates.firstOrNull { it.exists() && it.length() > 0L }
    }

    private fun usbStamp(file: File): Long = file.lastModified() xor file.length()

    fun addTask(title: String, usd: Double, minutes: Int = 30) {
        val clean = title.trim()
        if (clean.isEmpty()) return
        saveTasks(_state.value.tasks + FocusTask(title = clean, usd = usd, minutes = minutes.toDouble()))
    }

    fun cycleDim(): Int {
        val next = (_state.value.dimLevel + 1) % 3
        prefs.edit().putInt(KEY_DIM, next).apply()
        _state.update { it.copy(dimLevel = next) }
        return next
    }

    fun stillOnThis() {
        _state.update { it.copy(checkInActive = false, statusLine = "Focus") }
    }

    fun update(transform: (GlassState) -> GlassState) {
        _state.update(transform)
    }

    private fun loadTasks(): List<FocusTask> {
        val stored = FocusTask.fromJson(prefs.getString(KEY_TASKS, null))
        if (stored.isNotEmpty()) return FocusTask.ranked(stored)
        val migrated = buildList {
            prefs.getString(KEY_PRIORITY, "")?.trim()?.takeIf { it.isNotEmpty() }?.let {
                add(FocusTask(title = it, usd = 8.0, minutes = 30.0))
            }
            prefs.getString(KEY_NOW, "")?.trim()?.takeIf { it.isNotEmpty() }?.let {
                add(FocusTask(title = it, usd = 5.0, minutes = 30.0))
            }
        }
        if (migrated.isNotEmpty()) saveTasks(migrated)
        return FocusTask.ranked(migrated)
    }

    private fun saveTasks(tasks: List<FocusTask>) {
        val ranked = FocusTask.ranked(tasks)
        prefs.edit().putString(KEY_TASKS, FocusTask.toJson(ranked)).apply()
        _state.update { it.copy(tasks = ranked) }
    }

    companion object {
        private const val PREFS = "rokid_focus_glass"
        private const val KEY_PRIORITY = "priority"
        private const val KEY_NOW = "now_doing"
        private const val KEY_TASKS = "tasks_json"
        private const val KEY_DIM = "dim_level"
        private const val KEY_SLOGAN = "slogan"
        private const val KEY_OPPS = "opportunities_json"
        const val USB_FILE = "tasks.json"
        const val DEFAULT_SLOGAN = "怪奇实验室 + 外交官"
    }
}
