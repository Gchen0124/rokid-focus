package com.chenniuniu.rokidfocus.clock

/**
 * Wall-clock 5-minute grid.
 *
 * :00 hour (unique)
 * :15 / :30 / :45 three different quarter voices
 * remaining :05-family vs :10-family — two tick voices
 */
enum class ChimeKind {
    HOUR,
    QUARTER,
    HALF,
    THREE_QUARTER,
    FIVE,
    TEN,
    ;

    val label: String
        get() = when (this) {
            HOUR -> "Hour"
            QUARTER -> "15"
            HALF -> "30"
            THREE_QUARTER -> "45"
            FIVE -> "5"
            TEN -> "10"
        }

    companion object {
        fun forMinute(minute: Int): ChimeKind {
            val m = ((minute % 60) + 60) % 60
            require(m % 5 == 0) { "not a 5-minute mark: $minute" }
            return when (m) {
                0 -> HOUR
                15 -> QUARTER
                30 -> HALF
                45 -> THREE_QUARTER
                in listOf(5, 20, 35, 50) -> FIVE
                in listOf(10, 25, 40, 55) -> TEN
                else -> error("unreachable minute $m")
            }
        }
    }
}
