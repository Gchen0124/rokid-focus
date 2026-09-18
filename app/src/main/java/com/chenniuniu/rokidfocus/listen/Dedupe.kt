package com.chenniuniu.rokidfocus.listen

/** Cross-lane echo dedupe window. Two mics catching the same sentence land within this. */
internal const val ECHO_DEDUPE_WINDOW_MS = 10_000L

/**
 * True when two ASR lines are the same speech captured by two mics.
 * The glasses mic is authoritative for the wearer, so a `them` line that
 * echoes a recent `you` line is dropped.
 */
internal fun isEcho(a: String, b: String): Boolean {
    val x = fold(a)
    val y = fold(b)
    if (x.length < 4 || y.length < 4) return false
    if (x == y) return true
    val short = if (x.length <= y.length) x else y
    val long = if (x.length <= y.length) y else x
    return long.contains(short) && short.length.toDouble() / long.length >= 0.5
}

private fun fold(s: String): String = s.lowercase().filter { it.isLetterOrDigit() }
