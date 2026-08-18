package com.chenniuniu.rokidfocus.data

import com.chenniuniu.rokidfocus.clock.ChimeKind
import com.chenniuniu.rokidfocus.glasses.GlassesStatus

data class FocusState(
    val priority: String = "",
    val nowDoing: String = "",
    val tasks: List<FocusTask> = emptyList(),
    val remindersOn: Boolean = false,
    val checkInActive: Boolean = false,
    val lastChimeKind: ChimeKind? = null,
    val lastChimeLabel: String = "",
    val nextMarkLabel: String = "",
    val countdownLabel: String = "",
    val clockLabel: String = "--:--:--",
    val glasses: GlassesStatus = GlassesStatus.Idle,
    val statusLine: String = "Set priority and what you are doing.",
    val syncUrl: String = "",
    val syncLine: String = "Not synced",
)
