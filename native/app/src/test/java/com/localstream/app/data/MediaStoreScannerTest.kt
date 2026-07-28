package com.localstream.app.data

import com.localstream.app.data.scanner.MediaStoreScanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class MediaStoreScannerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun scanVideoFiles_scansVideosFromFileSystemFallback() {
        val moviesDir = tempFolder.newFolder("Movies")
        val videoFile = File(moviesDir, "Inception.2010.1080p.mkv")
        videoFile.writeText("fake video content")

        val scanner = MediaStoreScanner(customDirectories = listOf(moviesDir))
        val videos = scanner.scanVideoFiles()

        assertEquals(1, videos.size)
        assertEquals("Inception.2010.1080p.mkv", videos[0].name)
        assertTrue(videos[0].url.startsWith("file://"))
    }

    @Test
    fun scanSubtitleFiles_scansSubtitlesFromFileSystemFallback() {
        val moviesDir = tempFolder.newFolder("Movies")
        val subFile = File(moviesDir, "Inception.2010.1080p.fr.srt")
        subFile.writeText("1\n00:00:00,000 --> 00:00:02,000\nHello")

        val scanner = MediaStoreScanner(customDirectories = listOf(moviesDir))
        val subs = scanner.scanSubtitleFiles()

        assertEquals(1, subs.size)
        assertEquals("Inception.2010.1080p.fr.srt", subs[0].name)
    }

    @Test
    fun scanAndGroup_matchesSubtitlesAndGroupsSeries() {
        val moviesDir = tempFolder.newFolder("Movies")
        val ep1 = File(moviesDir, "Show.S01E01.mkv").apply { writeText("vid1") }
        val ep1Sub = File(moviesDir, "Show.S01E01.fr.srt").apply { writeText("sub1") }
        File(moviesDir, "Show.S01E02.mkv").apply { writeText("vid2") }

        val scanner = MediaStoreScanner(customDirectories = listOf(moviesDir))
        val grouped = scanner.scanAndGroup()

        assertEquals(1, grouped.size)
        assertTrue(grouped[0].isSeriesGroup)
        assertEquals("Show", grouped[0].seriesName)
        assertEquals(2, grouped[0].episodes?.size)

        val matchedEp1 = grouped[0].episodes?.find { it.name == ep1.name }
        assertEquals("file://${ep1Sub.absolutePath}", matchedEp1?.subtitleNativePath)
    }
}
