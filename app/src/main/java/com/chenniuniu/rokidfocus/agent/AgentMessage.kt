package com.chenniuniu.rokidfocus.agent

import org.json.JSONArray
import org.json.JSONObject

/** One bubble in the Agent tab. `images` are http(s) URLs or local file paths. */
data class AgentMessage(
    val role: String,
    val text: String,
    val images: List<String> = emptyList(),
    val at: Long = System.currentTimeMillis(),
    val done: Boolean = true,
) {
    val fromUser: Boolean get() = role == "user"

    companion object {
        fun toJson(items: List<AgentMessage>): String {
            val arr = JSONArray()
            items.forEach { m ->
                arr.put(
                    JSONObject()
                        .put("role", m.role)
                        .put("text", m.text)
                        .put("images", JSONArray(m.images))
                        .put("at", m.at)
                        .put("done", m.done),
                )
            }
            return arr.toString()
        }

        fun fromJson(raw: String?): List<AgentMessage> {
            if (raw.isNullOrBlank()) return emptyList()
            return runCatching {
                val arr = JSONArray(raw)
                buildList {
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        val images = mutableListOf<String>()
                        o.optJSONArray("images")?.let { a ->
                            for (j in 0 until a.length()) a.optString(j).takeIf { it.isNotBlank() }?.let(images::add)
                        }
                        add(
                            AgentMessage(
                                role = o.optString("role", "agent"),
                                text = o.optString("text", ""),
                                images = images,
                                at = o.optLong("at", 0L),
                                done = o.optBoolean("done", true),
                            ),
                        )
                    }
                }
            }.getOrDefault(emptyList())
        }
    }
}
