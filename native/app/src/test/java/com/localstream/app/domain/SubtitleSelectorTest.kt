package com.localstream.app.domain

import com.localstream.app.domain.model.SubtitleEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubtitleSelectorTest {

    private val auto = SubtitleEntry(name = "film.srt", folder = "/movies", uri = "content://auto/film.srt")

    @Test
    fun manualSubtitleWinsOverAutoDetected() {
        val selected = SubtitleSelector.select("content://manual/picked.srt", auto)

        assertEquals("content://manual/picked.srt", selected)
    }

    @Test
    fun fallsBackToAutoDetected() {
        val selected = SubtitleSelector.select(null, auto)

        assertEquals("content://auto/film.srt", selected)
    }

    @Test
    fun returnsNullWhenNothingAvailable() {
        assertNull(SubtitleSelector.select(null, null))
    }
}
