package com.chenniuniu.rokidfocus.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chenniuniu.rokidfocus.FocusViewModel

private data class Voice(val vcn: String, val label: String)

private val Voices = listOf(
    Voice("xiaoyan", "小燕 · 女 · 中英"),
    Voice("xiaofeng", "小峰 · 男 · 中英"),
    Voice("x4_yezi", "叶子 · 女"),
    Voice("catherine", "Catherine · EN"),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SayScreen(
    viewModel: FocusViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    var text by rememberSaveable { mutableStateOf("") }
    val replies = state.convoTurns.lastOrNull { it.replies.isNotEmpty() }?.replies ?: emptyList()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Say for me (嘴替)", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Rotate the phone so its mic faces the other person, then tap a line to make the phone speak it.",
            style = MaterialTheme.typography.bodySmall,
        )

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
                selected = state.ttsBackend == "system",
                onClick = { viewModel.setTtsBackend("system") },
                label = { Text("Phone TTS") },
            )
            FilterChip(
                selected = state.ttsBackend == "xfyun",
                onClick = { viewModel.setTtsBackend("xfyun") },
                label = { Text("讯飞 TTS") },
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { viewModel.stopSpeak() }) { Text("Stop") }
        }

        if (state.ttsBackend == "xfyun") {
            Text("Voice", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Voices.forEach { v ->
                    FilterChip(
                        selected = state.ttsVoice == v.vcn,
                        onClick = { viewModel.setTtsVoice(v.vcn) },
                        label = { Text(v.label) },
                    )
                }
            }
            OutlinedTextField(
                value = state.ttsVoice,
                onValueChange = { viewModel.setTtsVoice(it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("vcn") },
                singleLine = true,
            )
        }

        if (replies.isNotEmpty()) {
            Text("Suggested", style = MaterialTheme.typography.titleSmall)
            replies.filter { !it.equals("skip", true) }.forEach { line ->
                OutlinedButton(
                    onClick = { viewModel.speak(line) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(line, maxLines = 3) }
            }
        }

        Text("Type anything", style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("What to say") },
            minLines = 2,
            maxLines = 5,
        )
        Button(
            onClick = { viewModel.speak(text) },
            modifier = Modifier.fillMaxWidth(),
            enabled = text.isNotBlank(),
        ) { Text("Speak") }

        if (state.speakLine.isNotBlank()) {
            Text(state.speakLine, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(24.dp))
    }
}
