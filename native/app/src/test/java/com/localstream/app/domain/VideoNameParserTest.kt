package com.localstream.app.domain

import com.localstream.app.domain.model.SeriesInfo
import com.localstream.app.domain.model.SubtitleEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoNameParserTest {

    @Test
    fun parseSeriesInfo_detectsSxxExx() {
        assertEquals(
            SeriesInfo(seriesName = "Breaking Bad", season = 1, episode = 5),
            VideoNameParser.parseSeriesInfo("Breaking.Bad.S01E05.1080p.mkv")
        )
    }

    @Test
    fun parseSeriesInfo_detectsNxM() {
        assertEquals(
            SeriesInfo(seriesName = "The Office", season = 3, episode = 12),
            VideoNameParser.parseSeriesInfo("The Office 3x12.mp4")
        )
    }

    @Test
    fun parseSeriesInfo_cleansNameSeparators() {
        val info = VideoNameParser.parseSeriesInfo("[Team]_Dark-S02E01.mkv")
        assertEquals("Team  Dark", info.seriesName)
        assertEquals(2, info.season)
        assertEquals(1, info.episode)
    }

    @Test
    fun parseSeriesInfo_fallsBackToUnknownSeriesIfNameEmpty() {
        assertEquals("Série Inconnue", VideoNameParser.parseSeriesInfo("S01E01.mkv").seriesName)
    }

    @Test
    fun parseSeriesInfo_detectsSeriesFromFolderStructure() {
        assertEquals(
            SeriesInfo(seriesName = "Dark", season = 2, episode = 1),
            VideoNameParser.parseSeriesInfo("01.mp4", "Series/Dark/Saison 2/01.mp4")
        )
        assertEquals(
            SeriesInfo(seriesName = "Lost", season = 1, episode = null),
            VideoNameParser.parseSeriesInfo("pilot.mp4", "Shows/Lost/Season 1/pilot.mp4")
        )
    }

    @Test
    fun parseSeriesInfo_returnsEmptyForSimpleMovie() {
        assertEquals(SeriesInfo(), VideoNameParser.parseSeriesInfo("Inception.2010.1080p.mkv", "Movies/Inception.2010.1080p.mkv"))
    }

    @Test
    fun baseNameFunctions_stripExtensionsAndLanguageSuffixes() {
        assertEquals("film.test", VideoNameParser.videoBaseName("Film.Test.MKV"))
        assertEquals("film.test", VideoNameParser.subtitleBaseName("Film.Test.fr.srt"))
        assertEquals("film.test", VideoNameParser.subtitleBaseName("Film.Test.srt"))
    }

    @Test
    fun parentFolder_extractsParent() {
        assertEquals("Movies/Sub", VideoNameParser.parentFolder("Movies/Sub/film.mp4"))
        assertEquals("", VideoNameParser.parentFolder("film.mp4"))
        assertEquals("Movies", VideoNameParser.parentFolder("Movies\\film.mp4"))
    }

    @Test
    fun matchSubtitle_indexesAndMatchesSubtitles() {
        val subs = listOf(
            SubtitleEntry(name = "Film.fr.srt", folder = "Movies", uri = "u1"),
            SubtitleEntry(name = "Autre.vtt", folder = "Movies", uri = "u2"),
            SubtitleEntry(name = "notes.txt", folder = "Movies", uri = "u3")
        )
        val index = VideoNameParser.buildSubtitleIndex(subs)
        assertEquals("u1", VideoNameParser.matchSubtitle(index, "Film.mkv", "Movies")?.uri)
        assertEquals("u2", VideoNameParser.matchSubtitle(index, "Autre.mp4", "Movies")?.uri)
        assertNull(VideoNameParser.matchSubtitle(index, "notes.mp4", "Movies"))
        assertNull(VideoNameParser.matchSubtitle(index, "Film.mkv", "Download"))
    }

    @Test
    fun regexes_matchSupportedExtensions() {
        for (f in listOf("a.mp4", "b.MKV", "c.webm", "d.avi", "e.mov")) {
            assertTrue(VideoNameParser.VIDEO_EXT_REGEX.containsMatchIn(f))
        }
        assertFalse(VideoNameParser.VIDEO_EXT_REGEX.containsMatchIn("a.txt"))

        for (f in listOf("a.srt", "b.VTT", "c.ass", "d.ssa")) {
            assertTrue(VideoNameParser.SUBTITLE_EXT_REGEX.containsMatchIn(f))
        }
        assertFalse(VideoNameParser.SUBTITLE_EXT_REGEX.containsMatchIn("a.sub"))
    }
}

