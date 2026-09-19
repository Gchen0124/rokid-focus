package com.chenniuniu.rokidfocus.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.chenniuniu.rokidfocus.FocusViewModel
import com.chenniuniu.rokidfocus.agent.AgentMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

private val AgentBg = Color(0xFF071F12)
private val AgentInk = Color(0xFFC8F5D8)
private val UserBubble = Color(0xFF145C36)
private val AgentBubble = Color(0xFF0E2C1C)

@Composable
fun AgentScreen(
    viewModel: FocusViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    var input by rememberSaveable { mutableStateOf("") }
    var zoom by remember { mutableStateOf<String?>(null) }
    val scroll = rememberScrollState()

    LaunchedEffect(state.agentMessages.size, state.agentLive) {
        runCatching { scroll.animateScrollTo(scroll.maxValue) }
    }

    Column(modifier = modifier.fillMaxSize()) {
        AgentConfig(viewModel)
        if (state.agentLine.isNotBlank()) {
            Text(state.agentLine, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scroll)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.agentMessages.isEmpty() && state.agentLive.isBlank()) {
                Text(
                    "Talk to your agent. Mock backend works with no keys; switch to Hermes and paste the gateway URL + key.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            state.agentMessages.forEach { m ->
                AgentBubbleRow(m, onZoom = { zoom = it }, onSpeak = { viewModel.speak(it) })
            }
            if (state.agentLive.isNotBlank()) {
                AgentBubbleRow(
                    AgentMessage(role = "agent", text = state.agentLive, done = false),
                    onZoom = { zoom = it },
                    onSpeak = { viewModel.speak(it) },
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                label = { Text("Message") },
                maxLines = 4,
            )
            if (state.agentBusy) {
                OutlinedButton(onClick = { viewModel.cancelAgent() }) { Text("Stop") }
            } else {
                Button(
                    onClick = {
                        viewModel.askAgent(input)
                        input = ""
                    },
                    enabled = input.isNotBlank(),
                ) { Text("Send") }
            }
        }
    }

    zoom?.let { url ->
        Dialog(onDismissRequest = { zoom = null }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black)
                    .clickable { zoom = null },
            ) {
                RemoteImage(url, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun AgentConfig(viewModel: FocusViewModel) {
    val state by viewModel.state.collectAsState()
    var key by rememberSaveable { mutableStateOf("") }
    Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Backend", style = MaterialTheme.typography.titleSmall)
                FilterChip(
                    selected = state.agentBackend == "mock",
                    onClick = { viewModel.setAgentBackend("mock") },
                    label = { Text("Mock") },
                )
                FilterChip(
                    selected = state.agentBackend == "hermes",
                    onClick = { viewModel.setAgentBackend("hermes") },
                    label = { Text("Hermes") },
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { viewModel.clearAgent() }) { Text("Clear") }
            }
            OutlinedTextField(
                value = state.agentUrl,
                onValueChange = { viewModel.setAgentUrl(it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Hermes gateway URL") },
                placeholder = { Text("http://192.168.1.10:8642") },
                singleLine = true,
            )
            OutlinedTextField(
                value = key,
                onValueChange = {
                    key = it
                    viewModel.setAgentKey(it)
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("API key") },
                placeholder = { Text(if (state.agentKeySet) "saved — paste to replace" else "HERMES_API_KEY") },
                singleLine = true,
            )
            OutlinedTextField(
                value = state.agentModel,
                onValueChange = { viewModel.setAgentModel(it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Model") },
                singleLine = true,
            )
        }
    }
}

@Composable
private fun AgentBubbleRow(m: AgentMessage, onZoom: (String) -> Unit, onSpeak: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (m.fromUser) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .clip(RoundedCornerShape(12.dp))
                .background(if (m.fromUser) UserBubble else AgentBubble)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                if (m.fromUser) "YOU" else "AGENT",
                color = if (m.fromUser) Color(0xFF9FD9B3) else Color(0xFF8CFFC4),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
            if (m.text.isNotBlank()) {
                Text(
                    m.text + if (!m.done) " ▍" else "",
                    color = AgentInk,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            m.images.forEach { url ->
                RemoteImage(url, Modifier.fillMaxWidth()) { onZoom(url) }
            }
            if (!m.done && m.text.isBlank()) {
                Text("…", color = AgentInk, style = MaterialTheme.typography.bodyLarge)
            }
            if (!m.fromUser && m.done && m.text.isNotBlank()) {
                TextButton(onClick = { onSpeak(m.text) }) { Text("Speak") }
            }
        }
    }
}

@Composable
private fun RemoteImage(url: String, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    var bitmap by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(url) { mutableStateOf(false) }

    LaunchedEffect(url) {
        val decoded = withContext(Dispatchers.IO) {
            runCatching {
                val conn = URL(url).openConnection()
                conn.connectTimeout = 8000
                conn.readTimeout = 15000
                conn.getInputStream().use { BitmapFactory.decodeStream(it) }
            }.getOrNull()
        }
        if (decoded != null) bitmap = decoded.asImageBitmap() else failed = true
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0A1A10))
            .clickable { onClick() },
    ) {
        val bmp = bitmap
        when {
            bmp != null -> Image(
                bitmap = bmp,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.FillWidth,
            )
            failed -> Text("image failed", color = Color(0xFF8CFFC4), modifier = Modifier.padding(12.dp))
            else -> Text("loading image…", color = Color(0xFF3D7A58), modifier = Modifier.padding(12.dp))
        }
    }
}
