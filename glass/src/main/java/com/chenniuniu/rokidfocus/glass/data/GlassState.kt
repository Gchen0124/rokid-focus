package com.chenniuniu.rokidfocus.glass.data

import com.chenniuniu.rokidfocus.glass.clock.ChimeKind

data class GlassState(
    val priority: String = "",
    val nowDoing: String = "",
    val tasks: List<FocusTask> = emptyList(),
    val dimLevel: Int = 1,
    val clockLabel: String = "--:--:--",
    val nextMarkLabel: String = "",
    val countdownLabel: String = "",
    val checkInActive: Boolean = false,
    val lastChimeLabel: String = "",
    val lastChimeKind: ChimeKind? = null,
    val phoneLinked: Boolean = false,
    val statusLine: String = "Focus",
)
