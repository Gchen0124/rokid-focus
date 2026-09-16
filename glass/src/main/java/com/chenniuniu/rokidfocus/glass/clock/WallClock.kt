package com.chenniuniu.rokidfocus.glass.clock

import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

object WallClock {
    private val timeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val timeSecFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

    fun now(zone: ZoneId = ZoneId.systemDefault()): ZonedDateTime = ZonedDateTime.now(zone)

    fun formatHm(time: ZonedDateTime): String = time.format(timeFmt)

    fun formatHms(time: ZonedDateTime): String = time.format(timeSecFmt)

    fun nextFiveMinuteMark(from: ZonedDateTime = now()): ZonedDateTime {
        val truncated = from.truncatedTo(ChronoUnit.MINUTES)
        val remainder = truncated.minute % 5
        val add = if (remainder == 0 && from.second == 0 && from.nano == 0) 5L else (5 - remainder).toLong()
        return truncated.plusMinutes(add).withSecond(0).withNano(0)
    }

    fun millisUntil(target: ZonedDateTime, from: ZonedDateTime = now()): Long {
        return Duration.between(from, target).toMillis().coerceAtLeast(0L)
    }
}
