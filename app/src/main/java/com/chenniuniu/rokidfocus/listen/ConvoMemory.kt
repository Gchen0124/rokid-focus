package com.chenniuniu.rokidfocus.listen

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ConvoTurn(
    val who: String,
    val text: String,
    val replies: List<String> = emptyList(),
    val at: Long = System.currentTimeMillis(),
) {
    val label: String
        get() = when {
            who == "you" -> "YOU"
            who == "them" -> "THEY"
            who.startsWith("them") -> "S${who.removePrefix("them").ifBlank { "2" }}"
            else -> who.uppercase()
        }

    fun timeLabel(): String = TIME.format(Date(at))

    companion object {
        private val TIME = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault())

        fun toJson(turns: List<ConvoTurn>): String {
            val arr = JSONArray()
            turns.forEach { t ->
                arr.put(
                    JSONObject()
                        .put("who", t.who)
                        .put("text", t.text)
                        .put("at", t.at)
                        .put("replies", JSONArray(t.replies)),
                )
            }
            return arr.toString()
        }

        fun fromJson(raw: String?): List<ConvoTurn> {
            if (raw.isNullOrBlank()) return emptyList()
            return runCatching {
                val arr = JSONArray(raw)
                val out = mutableListOf<ConvoTurn>()
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val replies = mutableListOf<String>()
                    val r = o.optJSONArray("replies")
                    if (r != null) for (j in 0 until r.length()) replies.add(r.optString(j))
                    out.add(
                        ConvoTurn(
                            who = o.optString("who"),
                            text = o.optString("text"),
                            replies = replies.filter { it.isNotBlank() && it != "…" },
                            at = o.optLong("at", 0L),
                        ),
                    )
                }
                out
            }.getOrDefault(emptyList())
        }
    }
}

class ConvoMemory {
    private val lock = Any()
    private val turns = ArrayDeque<ConvoTurn>()

    fun clear() = synchronized(lock) { turns.clear() }

    fun replace(items: List<ConvoTurn>) = synchronized(lock) {
        turns.clear()
        items.filter { it.who == "you" || it.who.startsWith("them") }.takeLast(24).forEach { turns.addLast(it) }
    }

    fun add(who: String, text: String) = synchronized(lock) {
        val t = text.trim()
        if (t.isBlank()) return
        val last = turns.lastOrNull()
        if (last != null && last.who == who) {
            val merged = if (t.startsWith(last.text)) t else (last.text + " " + t).trim()
            turns.removeLast()
            turns.addLast(last.copy(text = merged.take(2000), replies = emptyList(), at = System.currentTimeMillis()))
        } else {
            turns.addLast(ConvoTurn(who, t.take(2000)))
        }
        while (turns.size > 24) turns.removeFirst()
    }

    fun snapshot(): List<ConvoTurn> = synchronized(lock) { turns.toList() }

    fun prompt(): String = snapshot()
        .filter { it.who == "you" || it.who.startsWith("them") }
        .joinToString("\n") { "${it.label}: ${it.text}" }

    fun hudLines(n: Int = 6): List<String> =
        snapshot().filter { it.who != "sys" }.takeLast(n).map { "${it.label}  ${it.text.take(80)}" }
}
