package com.chenniuniu.rokidfocus.data

import com.chenniuniu.rokidfocus.clock.ChimeKind
import com.chenniuniu.rokidfocus.glasses.GlassesStatus
import com.chenniuniu.rokidfocus.listen.ConvoTurn

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
    val opportunities: List<Opportunity> = emptyList(),
    val slogan: String = "怪奇实验室 + 外交官",
    val listenBind: String = "",
    val listenLive: Boolean = false,
    val talkStyle: String = "怪奇实验室 + 外交官",
    val nativeLang: String = "zh",
    val convoTurns: List<ConvoTurn> = emptyList(),
    val convoLiveWho: String = "",
    val convoLiveText: String = "",
    val convoLiveTrans: String = "",
    val replyKeySet: Boolean = false,
    val llmLine: String = "",
    val voiceEnrolled: Boolean = false,
    val enrollLine: String = "",
)
