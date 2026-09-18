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
    val showResults: Boolean = false,
    val slogan: String = "怪奇实验室 + 外交官",
    val opportunities: List<Opportunity> = emptyList(),
    val convoActive: Boolean = false,
    val convoLine: String = "",
    val convoTrans: String = "",
    val convoPartial: Boolean = false,
    val convoWho: String = "", // "you" | "them"
    val convoDrafts: List<String> = emptyList(),
    val convoPick: Int = 0,
    val convoHist: List<String> = emptyList(),
    val convoScroll: Int = 0,
    val listenLine: String = "off",
    val listenHost: String = "127.0.0.1",
    val listenPort: Int = 8791,
)
