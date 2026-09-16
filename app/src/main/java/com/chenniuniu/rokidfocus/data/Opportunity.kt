package com.chenniuniu.rokidfocus.data

import android.content.Intent
import android.provider.CalendarContract
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class Opportunity(
    val id: String = "",
    val date: String,
    val endDate: String = "",
    val name: String,
    val kind: String = "",
    val url: String = "",
    val x: String = "",
    val note: String = "",
    val source: String = "",
) {
    fun parseDate(): LocalDate? =
        runCatching { LocalDate.parse(date.take(10)) }.getOrNull()

    fun parseEnd(): LocalDate? =
        endDate.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }

    fun isPast(today: LocalDate = LocalDate.now()): Boolean {
        val last = parseEnd() ?: parseDate() ?: return false
        return last.isBefore(today)
    }

    fun isToday(today: LocalDate = LocalDate.now()): Boolean {
        val start = parseDate() ?: return false
        val last = parseEnd() ?: start
        return !today.isBefore(start) && !today.isAfter(last)
    }

    fun dateLabel(): String {
        val start = parseDate() ?: return date
        val last = parseEnd()
        val fmt = DateTimeFormatter.ofPattern("MMM d", Locale.US)
        return if (last != null && last != start) {
            start.format(fmt) + " – " + last.format(fmt)
        } else {
            start.format(fmt)
        }
    }

    fun xHandle(): String {
        if (x.isBlank()) return ""
        val path = x.substringAfter("x.com/").substringAfter("twitter.com/").substringBefore("/").substringBefore("?")
        return if (path.isBlank()) "X" else "@" + path.removePrefix("@")
    }

    fun daysUntil(today: LocalDate = LocalDate.now()): Long? {
        val start = parseDate() ?: return null
        return java.time.temporal.ChronoUnit.DAYS.between(today, start)
    }

    fun badge(today: LocalDate = LocalDate.now()): String {
        if (isPast(today)) return "PAST"
        if (isToday(today)) return "TODAY"
        val d = daysUntil(today) ?: return kind.ifBlank { "OPEN" }
        return if (d == 1L) "D-1" else "D-$d"
    }

    fun googleCalendarUrl(): String {
        val start = parseDate() ?: LocalDate.now()
        val endExclusive = (parseEnd() ?: start).plusDays(1)
        val fmt = DateTimeFormatter.BASIC_ISO_DATE
        val details = listOf(url, x, note).filter { it.isNotBlank() }.joinToString("\n")
        return "https://calendar.google.com/calendar/render?action=TEMPLATE" +
            "&text=" + java.net.URLEncoder.encode(name, "UTF-8") +
            "&dates=" + start.format(fmt) + "/" + endExclusive.format(fmt) +
            "&details=" + java.net.URLEncoder.encode(details, "UTF-8")
    }

    fun calendarInsertIntent(): Intent {
        val start = parseDate() ?: LocalDate.now()
        val endExclusive = (parseEnd() ?: start).plusDays(1)
        val zone = ZoneId.systemDefault()
        val details = listOf(url, x, note).filter { it.isNotBlank() }.joinToString("\n")
        return Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, name)
            putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
            putExtra(
                CalendarContract.EXTRA_EVENT_BEGIN_TIME,
                start.atStartOfDay(zone).toInstant().toEpochMilli(),
            )
            putExtra(
                CalendarContract.EXTRA_EVENT_END_TIME,
                endExclusive.atStartOfDay(zone).toInstant().toEpochMilli(),
            )
            putExtra(CalendarContract.Events.DESCRIPTION, details)
        }
    }

    companion object {
        fun forSwipe(items: List<Opportunity>, today: LocalDate = LocalDate.now()): List<Opportunity> =
            items.sortedWith(compareBy<Opportunity> { it.date }.thenBy { it.name })

        fun startIndex(items: List<Opportunity>, today: LocalDate = LocalDate.now()): Int {
            val i = items.indexOfFirst { !it.isPast(today) }
            return if (i >= 0) i else (items.lastIndex).coerceAtLeast(0)
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
                            id = o.optString("id").trim(),
                            date = date.take(10),
                            endDate = o.optString("endDate").trim().take(10),
                            name = name,
                            kind = o.optString("kind").trim(),
                            url = o.optString("url").trim(),
                            x = o.optString("x").trim(),
                            note = o.optString("note").trim(),
                            source = o.optString("source").trim(),
                        )
                    )
                }
            }
        }
    }
}
