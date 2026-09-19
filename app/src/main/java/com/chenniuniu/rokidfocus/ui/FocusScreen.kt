package com.chenniuniu.rokidfocus.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chenniuniu.rokidfocus.FocusViewModel
import com.chenniuniu.rokidfocus.clock.ChimeKind
import com.chenniuniu.rokidfocus.data.FocusTask
import com.chenniuniu.rokidfocus.listen.ConvoTurn

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FocusScreen(
    viewModel: FocusViewModel,
    onConnectGlasses: () -> Unit,
    onListen: () -> Unit = { viewModel.toggleListen() },
) {
    val state by viewModel.state.collectAsState()
    var draft by rememberSaveable { mutableStateOf("") }
    var draftValue by rememberSaveable { mutableIntStateOf(5) }
    var tab by rememberSaveable { mutableStateOf("convo") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(selected = tab == "swipe", onClick = { tab = "swipe" }, label = { Text("Swipe") })
            FilterChip(selected = tab == "desk", onClick = { tab = "desk" }, label = { Text("Desk") })
            FilterChip(selected = tab == "convo", onClick = { tab = "convo" }, label = { Text("Convo") })
            FilterChip(selected = tab == "agent", onClick = { tab = "agent" }, label = { Text("Agent") })
            Spacer(Modifier.weight(1f))
            Text(state.syncLine, style = MaterialTheme.typography.bodySmall)
        }
        if (tab == "swipe") {
            OpportunityFeed(
                items = state.opportunities,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
        } else if (tab == "agent") {
            AgentScreen(
                viewModel = viewModel,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
        } else if (tab == "convo") {
            ConvoHistory(
                turns = state.convoTurns,
                liveWho = state.convoLiveWho,
                liveText = state.convoLiveText,
                liveTrans = state.convoLiveTrans,
                llmLine = state.llmLine,
                listenLive = state.listenLive,
                onClear = { viewModel.clearConvo() },
                onSpeak = { viewModel.speak(it) },
                speakLine = state.speakLine,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
        } else {
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Rokid Focus", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Ranked list by value. Glasses show the same list + a small clock. Swipe is the opportunity calendar.",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            if (state.listenLive) {
                "Listening — iFlytek on this phone (Wi‑Fi or 5G)."
            } else {
                "Listen rides the glasses pairing link. Phone Wi‑Fi or 5G is the internet. Connect glasses, then tap the temple (or Listen below)."
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        if (state.listenBind.isNotBlank()) {
            Text(
                "LAN fallback ${state.listenBind} if pairing audio is down.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(state.clockLabel, style = MaterialTheme.typography.titleLarge)
                Text("Next ${state.nextMarkLabel.ifBlank { "5-min" }} in ${state.countdownLabel.ifBlank { "—" }}")
                Text(state.glasses.label, style = MaterialTheme.typography.bodySmall)
                Text(state.statusLine, style = MaterialTheme.typography.bodySmall)
            }
        }

        OutlinedTextField(
            value = state.syncUrl,
            onValueChange = { viewModel.setSyncUrl(it) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Laptop sync URL") },
            placeholder = { Text("http://192.168.1.24:8787") },
            singleLine = true
        )
        Text(state.syncLine, style = MaterialTheme.typography.bodySmall)

        Text("Priorities", style = MaterialTheme.typography.titleMedium)
        if (state.tasks.isEmpty()) {
            Text("Nothing ranked yet. Add one below.", style = MaterialTheme.typography.bodySmall)
        }
        FocusTask.ranked(state.tasks).forEach { task ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${task.clampedValue}  ${task.title}",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { viewModel.removeTask(task.id) }) {
                            Text("Remove")
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (1..10).forEach { n ->
                            FilterChip(
                                selected = task.clampedValue == n,
                                onClick = { viewModel.setTaskValue(task.id, n) },
                                label = { Text("$n") }
                            )
                        }
                    }
                }
            }
        }

        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("New task") },
            placeholder = { Text("What is worth doing") },
            singleLine = true
        )
        Text("Value 1–10", style = MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (1..10).forEach { n ->
                FilterChip(
                    selected = draftValue == n,
                    onClick = { draftValue = n },
                    label = { Text("$n") }
                )
            }
        }
        Button(
            onClick = {
                viewModel.addTask(draft, draftValue)
                draft = ""
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = draft.isNotBlank()
        ) {
            Text("Add to list")
        }

        if (state.remindersOn) {
            Button(onClick = { viewModel.stopReminders() }, modifier = Modifier.fillMaxWidth()) {
                Text("Stop reminders")
            }
        } else {
            Button(onClick = { viewModel.startReminders() }, modifier = Modifier.fillMaxWidth()) {
                Text("Start reminders")
            }
        }

        OutlinedButton(onClick = onConnectGlasses, modifier = Modifier.fillMaxWidth()) {
            Text("Connect glasses")
        }
        Button(
            onClick = onListen,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state.listenLive) "Stop listen" else "Listen")
        }
        OutlinedButton(onClick = { viewModel.beginVoiceEnroll() }, modifier = Modifier.fillMaxWidth()) {
            Text(if (state.voiceEnrolled) "Re-register my voice (12s)" else "Register my voice (12s)")
        }
        if (state.enrollLine.isNotBlank()) {
            Text(state.enrollLine, style = MaterialTheme.typography.bodySmall)
        }

        Text("Mother tongue", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
                selected = state.nativeLang == "zh",
                onClick = { viewModel.setNativeLang("zh") },
                label = { Text("中文") },
            )
            FilterChip(
                selected = state.nativeLang == "en",
                onClick = { viewModel.setNativeLang("en") },
                label = { Text("English") },
            )
        }
        Text("How you talk", style = MaterialTheme.typography.titleMedium)
        Text(
            "Reactions are written in this voice, using the whole convo, not only the last line.",
            style = MaterialTheme.typography.bodySmall,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                "怪奇实验室 + 外交官",
                "短、直接、不客套",
                "温和留余地",
                "好奇追问",
            ).forEach { style ->
                FilterChip(
                    selected = state.talkStyle == style,
                    onClick = { viewModel.setTalkStyle(style) },
                    label = { Text(style) },
                )
            }
        }
        OutlinedTextField(
            value = state.talkStyle,
            onValueChange = { viewModel.setTalkStyle(it) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Talk style") },
            placeholder = { Text("怪奇实验室 + 外交官") },
        )
        var replyKey by rememberSaveable { mutableStateOf("") }
        OutlinedTextField(
            value = replyKey,
            onValueChange = {
                replyKey = it
                viewModel.setReplyKey(it)
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("DeepSeek key (replies)") },
            placeholder = {
                Text(if (state.replyKeySet) "saved — paste to replace" else "sk-… DeepSeek replies, not ASR")
            },
            singleLine = true,
        )
        if (state.llmLine.isNotBlank()) {
            Text(state.llmLine, style = MaterialTheme.typography.bodySmall)
        }

        if (state.convoTurns.isNotEmpty()) {
            Text(
                "${state.convoTurns.size} turns in Convo tab",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Text("Preview chimes", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChimeKind.entries.forEach { kind ->
                OutlinedButton(onClick = { viewModel.preview(kind) }) {
                    Text(kind.label)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
        }
    }
}

@Composable
private fun ConvoHistory(
    turns: List<ConvoTurn>,
    liveWho: String,
    liveText: String,
    liveTrans: String,
    llmLine: String,
    listenLive: Boolean,
    onClear: () -> Unit,
    onSpeak: (String) -> Unit,
    speakLine: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Convo history", style = MaterialTheme.typography.headlineSmall)
            if (turns.isNotEmpty()) TextButton(onClick = onClear) { Text("Clear") }
        }
        Text(
            when {
                listenLive && liveText.isNotBlank() -> "Live · $llmLine".trimEnd(' ', '·')
                listenLive -> "Listening. Lines land here as they finish."
                llmLine.isNotBlank() -> llmLine
                else -> "Saved on this phone. ASR lines + DeepSeek say? options."
            },
            style = MaterialTheme.typography.bodySmall,
        )
        if (speakLine.isNotBlank()) {
            Text(speakLine, style = MaterialTheme.typography.bodySmall)
        }
        if (turns.isEmpty() && liveText.isBlank()) {
            Text("Nothing yet. Connect glasses, listen, talk.", style = MaterialTheme.typography.bodyMedium)
        }
        if (liveText.isNotBlank()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "LIVE  ${when {
                            liveWho == "you" -> "YOU"
                            liveWho.startsWith("them") && liveWho.length > 4 -> "S${liveWho.removePrefix("them")}"
                            else -> "THEY"
                        }}",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(liveText, style = MaterialTheme.typography.bodyLarge)
                    if (liveTrans.isNotBlank()) {
                        Text(liveTrans, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        turns.asReversed().forEach { turn ->
            if (turn.who == "sys") {
                Text(
                    "— ${turn.text}  ${turn.timeLabel()} —",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            } else {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "${turn.timeLabel()}  ${turn.label}",
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Text(turn.text, style = MaterialTheme.typography.bodyLarge)
                        if (turn.trans.isNotBlank()) {
                            Text(turn.trans, style = MaterialTheme.typography.bodyMedium)
                        }
                        if (turn.replies.isNotEmpty()) {
                            ReplyRack(turn.replies, onSpeak)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

private val SlotKey = listOf("A", "B", "C")
private val SlotTag = listOf("LEAN", "TURN", "SKIP")
private val SlotBg = Color(0xFF071F12)
private val SlotGreen = Color(0xFF00FF66)
private val SlotGold = Color(0xFFFFE08A)
private val SlotDim = Color(0xFF3D7A58)

@Composable
private fun ReplyRack(replies: List<String>, onSpeak: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .background(SlotBg)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("tap to speak", color = SlotDim, fontSize = 11.sp)
        replies.take(3).forEachIndexed { i, line ->
            val key = SlotKey.getOrElse(i) { "${i + 1}" }
            val tag = if (line.equals("skip", true)) "SKIP" else SlotTag.getOrElse(i) { "SAY" }
            Text(
                "[$key] $tag  $line",
                color = if (tag == "SKIP") SlotDim else SlotGreen,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = tag != "SKIP") { onSpeak(line) }
                    .padding(vertical = 2.dp),
            )
        }
    }
}
