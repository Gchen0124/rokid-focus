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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chenniuniu.rokidfocus.FocusViewModel
import com.chenniuniu.rokidfocus.clock.ChimeKind
import com.chenniuniu.rokidfocus.data.FocusTask

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FocusScreen(
    viewModel: FocusViewModel,
    onConnectGlasses: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    var draft by rememberSaveable { mutableStateOf("") }
    var draftValue by rememberSaveable { mutableIntStateOf(5) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Rokid Focus", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Ranked list by value. Glasses show the same list + a small clock.",
            style = MaterialTheme.typography.bodyMedium
        )

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
