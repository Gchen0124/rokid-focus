package com.chenniuniu.rokidfocus.glass.clock

enum class ChimeKind {
    HOUR,
    QUARTER,
    HALF,
    THREE_QUARTER,
    FIVE,
    TEN,
    BUNNY,
    ;

    val label: String
        get() = when (this) {
            HOUR -> "Hour"
            QUARTER -> "15"
            HALF -> "30"
            THREE_QUARTER -> "45"
            FIVE -> "5"
            TEN -> "10"
            BUNNY -> "bunny"
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
                25, 55 -> BUNNY
                in listOf(5, 20, 35, 50) -> FIVE
                in listOf(10, 40) -> TEN
                else -> error("unreachable minute $m")
            }
        }
    }
}
