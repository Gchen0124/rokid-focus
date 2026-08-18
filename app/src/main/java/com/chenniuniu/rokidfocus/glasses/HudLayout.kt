package com.chenniuniu.rokidfocus.glasses

import com.chenniuniu.rokidfocus.data.FocusState
import com.chenniuniu.rokidfocus.data.FocusTask

object HudLayout {

    fun openJson(state: FocusState): String {
        val rows = taskLines(state)
        val children = buildString {
            append(textNode("timeView", header(state), "18sp"))
            rows.forEachIndexed { i, line ->
                append(",")
                append(textNode("task$i", line, "15sp"))
            }
        }
        return """{"type":"LinearLayout","props":{"id":"root","layout_width":"match_parent","layout_height":"match_parent","orientation":"vertical","gravity":"top","marginTop":"8dp","marginBottom":"8dp","paddingStart":"10dp","paddingEnd":"10dp","backgroundColor":"#FF000000"},"children":[$children]}"""
    }

    fun updateJson(state: FocusState): String {
        val rows = taskLines(state)
        val updates = buildString {
            append("""{"action":"update","id":"timeView","props":{"text":"${esc(header(state))}"}}""")
            rows.forEachIndexed { i, line ->
                append(",")
                append("""{"action":"update","id":"task$i","props":{"text":"${esc(line)}"}}""")
            }
        }
        return "[$updates]"
    }

    private fun header(state: FocusState): String {
        val clock = state.clockLabel.take(5).ifBlank { "--:--" }
        return if (state.checkInActive) {
            "$clock  CHECK ${state.lastChimeKind?.label.orEmpty()}"
        } else {
            "$clock  ${state.nextMarkLabel.ifBlank { "5" }} ${state.countdownLabel.ifBlank { "--" }}"
        }
    }

    private fun taskLines(state: FocusState): List<String> {
        val ranked = FocusTask.ranked(state.tasks).take(10)
        val lines = ranked.mapIndexed { i, task ->
            "${i + 1} ${task.clampedValue} ${task.title.take(26)}"
        }
        return (lines + List(10) { " " }).take(10)
    }

    private fun textNode(id: String, text: String, size: String): String {
        return """{"type":"TextView","props":{"id":"$id","layout_width":"match_parent","layout_height":"wrap_content","text":"${esc(text)}","textColor":"#00FF00","textSize":"$size","gravity":"start","paddingTop":"2dp","paddingBottom":"2dp"}}"""
    }

    private fun esc(raw: String): String {
        return raw
            .replace("\\", "\\\\")
            .replace("\"", "'")
            .replace("\n", " ")
            .replace("\r", " ")
    }
}
