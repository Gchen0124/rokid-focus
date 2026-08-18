package com.chenniuniu.rokidfocus.glass.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.math.roundToInt

data class FocusTask(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val usd: Double,
    val minutes: Int,
    val done: Boolean = false,
    val doneAt: String = "",
    val archived: Boolean = false,
    val assignee: String = "me",
    val handoff: String = "",
    val agentStatus: String = "idle",
    val outcome: String = "",
    val pin: Boolean = false,
) {
    val isOpen: Boolean get() = !done && !archived
    val leveragePerHour: Double
        get() = if (minutes <= 0) 0.0 else usd / (minutes / 60.0)

    fun moneyLabel(): String {
        val v = usd
        return if (v >= 1000.0) "$${v.div(1000.0).roundToInt()}k" else "$${v.roundToInt()}"
    }

    fun minsLabel(): String = formatMinutes(minutes)

    fun leverageLabel(): String {
        if (minutes <= 0) return "—"
        val perH = leveragePerHour
        return if (perH >= 1000.0) "${(perH / 1000.0).roundToInt()}k/h" else "${perH.roundToInt()}/h"
    }

    companion object {
        fun formatMinutes(raw: Int): String {
            val m = raw.coerceAtLeast(0)
            val h = m / 60
            val mm = m % 60
            return when {
                h > 0 && mm > 0 -> "${h}h%02d".format(mm)
                h > 0 -> "${h}h"
                else -> "${mm}m"
            }
        }

        fun ranked(tasks: List<FocusTask>): List<FocusTask> =
            tasks.sortedWith(
                compareByDescending<FocusTask> { it.pin }
                    .thenByDescending { it.usd }
                    .thenBy { it.title }
            )

        fun open(tasks: List<FocusTask>): List<FocusTask> =
            ranked(tasks.filter { it.isOpen && it.assignee != "agent" })

        fun finishedCount(tasks: List<FocusTask>): Int = tasks.count { it.done || it.archived }

        fun toJson(tasks: List<FocusTask>): String {
            val arr = JSONArray()
            tasks.forEach { task ->
                arr.put(
                    JSONObject()
                        .put("id", task.id)
                        .put("title", task.title)
                        .put("usd", task.usd)
                        .put("minutes", task.minutes)
                        .put("done", task.done)
                        .put("doneAt", task.doneAt)
                        .put("archived", task.archived)
                        .put("assignee", task.assignee)
                        .put("handoff", task.handoff)
                        .put("agentStatus", task.agentStatus)
                        .put("outcome", task.outcome)
                        .put("pin", task.pin)
                )
            }
            return arr.toString()
        }

        fun fromJson(raw: String?): List<FocusTask> {
            if (raw.isNullOrBlank()) return emptyList()
            return runCatching {
                val trimmed = raw.trim()
                val arr = if (trimmed.startsWith("{")) {
                    JSONObject(trimmed).optJSONArray("tasks") ?: JSONArray()
                } else {
                    JSONArray(trimmed)
                }
                buildList {
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        val usd = when {
                            o.has("usd") -> o.optDouble("usd", 0.0)
                            o.has("value") -> o.optDouble("value", 0.0)
                            else -> 0.0
                        }
                        add(
                            FocusTask(
                                id = o.optString("id", UUID.randomUUID().toString()),
                                title = o.optString("title"),
                                usd = if (usd.isNaN()) 0.0 else usd,
                                minutes = o.optInt("minutes", 30).coerceAtLeast(0),
                                done = o.optBoolean("done", false),
                                doneAt = o.optString("doneAt", ""),
                                archived = o.optBoolean("archived", false),
                                assignee = o.optString("assignee", "me").ifBlank { "me" },
                                handoff = o.optString("handoff", ""),
                                agentStatus = o.optString("agentStatus", "idle").ifBlank { "idle" },
                                outcome = o.optString("outcome", ""),
                                pin = o.optBoolean("pin", false),
                            )
                        )
                    }
                }.filter { it.title.isNotBlank() }
            }.getOrDefault(emptyList())
        }
    }
}
