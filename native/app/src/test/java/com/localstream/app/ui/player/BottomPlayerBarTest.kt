package com.localstream.app.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class BottomPlayerBarTest {

    @Test
    fun formatTimeMs_formatsSecondsAndMinutes() {
        assertEquals("00:00", formatTimeMs(0L))
        assertEquals("00:05", formatTimeMs(5000L))
        assertEquals("01:30", formatTimeMs(90000L))
    }

    @Test
    fun formatTimeMs_formatsHours() {
        assertEquals("1:00:00", formatTimeMs(3600000L))
        assertEquals("2:15:42", formatTimeMs(8142000L))
    }

    @Test
    fun formatTimeMs_handlesNegative() {
        assertEquals("00:00", formatTimeMs(-1000L))
    }
}
