package com.chenniuniu.rokidfocus.data

import android.content.Context
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

    fun setSyncUrl(url: String) {
        val clean = url.trim().trimEnd('/')
        prefs.edit().putString(KEY_SYNC, clean).apply()
        _state.update { it.copy(syncUrl = clean) }
    }

    fun setSyncLine(line: String) {
        _state.update { it.copy(syncLine = line) }
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
        const val DEFAULT_SYNC = "http://192.168.1.24:8787"
    }
}
