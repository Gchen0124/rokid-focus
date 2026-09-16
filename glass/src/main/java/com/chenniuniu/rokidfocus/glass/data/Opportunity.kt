package com.chenniuniu.rokidfocus.glass.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

data class Opportunity(
    val date: String,
    val name: String,
    val kind: String = "",
    val url: String = "",
    val x: String = "",
    val note: String = "",
    val endDate: String = "",
    val id: String = "",
) {
    fun dateLabel(): String {
        val d = parseDate() ?: return date
        return "${d.monthValue}/${d.dayOfMonth}"
    }

    fun parseDate(): LocalDate? =
        runCatching { LocalDate.parse(date.take(10)) }.getOrNull()

    companion object {
        fun upcoming(items: List<Opportunity>, today: LocalDate = LocalDate.now(), limit: Int = 8): List<Opportunity> =
            items
                .mapNotNull { it.parseDate()?.let { d -> it to d } }
                .filter { it.second >= today }
                .sortedBy { it.second }
                .take(limit)
                .map { it.first }

        fun toJson(items: List<Opportunity>): String {
            val arr = JSONArray()
            items.forEach { o ->
                arr.put(
                    JSONObject()
                        .put("id", o.id)
                        .put("date", o.date)
                        .put("endDate", o.endDate)
                        .put("name", o.name)
                        .put("kind", o.kind)
                        .put("url", o.url)
                        .put("x", o.x)
                        .put("note", o.note)
                )
            }
            return arr.toString()
        }

        fun fromJson(raw: String?): List<Opportunity> {
            if (raw.isNullOrBlank()) return emptyList()
            return runCatching {
                val arr = if (raw.trim().startsWith("[")) JSONArray(raw) else JSONObject(raw).optJSONArray("opportunities") ?: JSONArray()
                fromArray(arr)
            }.getOrDefault(emptyList())
        }

        fun fromArray(arr: JSONArray?): List<Opportunity> {
            if (arr == null) return emptyList()
            return buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val name = o.optString("name").trim()
                    val date = o.optString("date").trim()
                    if (name.isEmpty() || date.isEmpty()) continue
                    add(
                        Opportunity(
                            date = date.take(10),
                            name = name,
                            kind = o.optString("kind").trim(),
                            url = o.optString("url").trim(),
                            x = o.optString("x").trim(),
                            note = o.optString("note").trim(),
                            endDate = o.optString("endDate").trim().take(10),
                            id = o.optString("id").trim(),
                        )
                    )
                }
            }
        }
    }
}
