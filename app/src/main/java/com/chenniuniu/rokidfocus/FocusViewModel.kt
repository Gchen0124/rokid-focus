package com.chenniuniu.rokidfocus

import android.app.Activity
import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chenniuniu.rokidfocus.clock.ChimeKind
import com.chenniuniu.rokidfocus.clock.WallClock
import com.chenniuniu.rokidfocus.data.FocusState
import com.chenniuniu.rokidfocus.sound.ChimePlayer
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
            app.store.replaceTasks(remote)
            app.store.setSyncLine("Synced ${remote.size} tasks")
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

    fun onAuthResult(resultCode: Int, data: Intent?) {
        app.glasses.parseAuth(resultCode, data)
    }

    fun preview(kind: ChimeKind) {
        previewPlayer.play(kind)
    }

    override fun onCleared() {
        previewPlayer.release()
        super.onCleared()
    }
}
