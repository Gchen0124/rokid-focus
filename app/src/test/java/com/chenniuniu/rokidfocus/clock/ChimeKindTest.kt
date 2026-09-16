package com.chenniuniu.rokidfocus.clock

import org.junit.Assert.assertEquals
import org.junit.Test

class ChimeKindTest {
    @Test
    fun mapsFiveMinuteGrid() {
        assertEquals(ChimeKind.HOUR, ChimeKind.forMinute(0))
        assertEquals(ChimeKind.FIVE, ChimeKind.forMinute(5))
        assertEquals(ChimeKind.TEN, ChimeKind.forMinute(10))
        assertEquals(ChimeKind.QUARTER, ChimeKind.forMinute(15))
        assertEquals(ChimeKind.FIVE, ChimeKind.forMinute(20))
        assertEquals(ChimeKind.TEN, ChimeKind.forMinute(25))
        assertEquals(ChimeKind.HALF, ChimeKind.forMinute(30))
        assertEquals(ChimeKind.FIVE, ChimeKind.forMinute(35))
        assertEquals(ChimeKind.TEN, ChimeKind.forMinute(40))
        assertEquals(ChimeKind.THREE_QUARTER, ChimeKind.forMinute(45))
        assertEquals(ChimeKind.FIVE, ChimeKind.forMinute(50))
        assertEquals(ChimeKind.TEN, ChimeKind.forMinute(55))
    }
}
