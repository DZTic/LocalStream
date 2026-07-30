package com.localstream.app.data

import com.localstream.app.data.scanner.MediaScanner
import com.localstream.app.data.scanner.MediaStoreScanner
import com.localstream.app.domain.model.MovieCollection
import com.localstream.app.domain.model.SubtitleEntry
import com.localstream.app.domain.model.VideoItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Test unitaire du scanner sur le syst\u00e8me de fichiers (sans Android Context).
 * [MediaStoreScanner] sans context repli sur [customDirectories].
 */
class MediaStoreScannerTest {

    private lateinit var tempDir: File

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("localstream_test").toFile()
    }

    @Test
    fun scanVideoFiles_findsVideosByExtension() {
        File(tempDir, "Film.mkv").createNewFile()
        File(tempDir, "Serie.S01E01.mp4").createNewFile()
        File(tempDir, "notes.txt").createNewFile()

        val scanner = MediaStoreScanner(context = null, customDirectories = listOf(tempDir))
        val result = scanner.scanVideoFiles()

        assertEquals(2, result.size)
        assertTrue(result.any { it.name == "Film.mkv" })
        assertTrue(result.any { it.name == "Serie.S01E01.mp4" })
    }

    @Test
    fun scanVideoFiles_returnsEmptyWhenNoVideos() {
        File(tempDir, "doc.pdf").createNewFile()
        val scanner = MediaStoreScanner(context = null, customDirectories = listOf(tempDir))
        assertTrue(scanner.scanVideoFiles().isEmpty())
    }

    @Test
    fun scanSubtitleFiles_findsSrtAndVtt() {
        File(tempDir, "Film.srt").createNewFile()
        File(tempDir, "Film.vtt").createNewFile()
        File(tempDir, "Film.mkv").createNewFile()

        val scanner = MediaStoreScanner(context = null, customDirectories = listOf(tempDir))
        val subs = scanner.scanSubtitleFiles()

        assertEquals(2, subs.size)
        assertTrue(subs.any { it.name == "Film.srt" })
        assertTrue(subs.any { it.name == "Film.vtt" })
    }

    @Test
    fun scanAndGroup_groupsSeriesEpisodes() {
        File(tempDir, "Dark.S01E01.mkv").createNewFile()
        File(tempDir, "Dark.S01E02.mkv").createNewFile()
        File(tempDir, "Inception.mkv").createNewFile()

        val scanner = MediaStoreScanner(context = null, customDirectories = listOf(tempDir))
        val result = scanner.scanAndGroup()

        // Dark regroup\u00e9 (1 groupe) + Inception (1 film) = 2 \u00e9l\u00e9ments
        assertEquals(2, result.size)
        val dark = result.firstOrNull { it.isSeriesGroup }
        assertTrue(dark != null)
        assertEquals(2, dark?.episodes?.size)
    }
}
