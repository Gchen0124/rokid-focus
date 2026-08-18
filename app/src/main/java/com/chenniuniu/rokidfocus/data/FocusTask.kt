package com.chenniuniu.rokidfocus.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class FocusTask(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val value: Int,
) {
    val clampedValue: Int get() = value.coerceIn(1, 10)

    companion object {
        fun ranked(tasks: List<FocusTask>): List<FocusTask> =
            tasks.sortedWith(compareByDescending<FocusTask> { it.clampedValue }.thenBy { it.title })

        fun toJson(tasks: List<FocusTask>): String {
            val arr = JSONArray()
            tasks.forEach { task ->
                arr.put(
                    JSONObject()
                        .put("id", task.id)
                        .put("title", task.title)
                        .put("value", task.clampedValue)
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
                        add(
                            FocusTask(
                                id = o.optString("id", UUID.randomUUID().toString()),
                                title = o.optString("title"),
                                value = o.optInt("value", 5),
                            )
                        )
                    }
                }.filter { it.title.isNotBlank() }
            }.getOrDefault(emptyList())
        }
    }
}
