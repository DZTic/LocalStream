package com.localstream.app.data

import com.localstream.app.data.repository.VideoRepository
import com.localstream.app.data.scanner.MediaScanner
import com.localstream.app.domain.model.MovieCollection
import com.localstream.app.domain.model.SubtitleEntry
import com.localstream.app.domain.model.VideoItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Test du [VideoRepository] avec un scanner factice (Phase 4 — VideoRepository all\u00e9g\u00e9).
 */
class VideoRepositoryTest {

    private lateinit var repo: VideoRepository

    @Before
    fun setUp() {
        val scanner = FakeMediaScanner(
            listOf(
                VideoItem(url = "file://a.mkv", name = "Alpha.mkv"),
                VideoItem(url = "file://b.mkv", name = "Beta.S01E01.mkv"),
                VideoItem(url = "file://c.mkv", name = "Beta.S01E02.mkv"),
            )
        )
        repo = VideoRepository(scanner)
    }

    @Test
    fun scanAndLoad_returnsGroupedVideos() {
        val result = repo.scanAndLoad()
        // Alpha (1 film) + Beta group\u00e9 (1 s\u00e9rie) = 2 \u00e9l\u00e9ments
        assertEquals(2, result.size)
    }

    @Test
    fun getRawVideos_returnsAllFlatVideos() {
        repo.scanAndLoad()
        assertEquals(3, repo.getRawVideos().size)
    }

    @Test
    fun getGroupedVideos_returnsGroupedList() {
        repo.scanAndLoad()
        assertTrue(repo.getGroupedVideos().any { it.isSeriesGroup })
    }

    @Test
    fun scanAndLoad_emptyScanner_returnsEmpty() {
        val emptyRepo = VideoRepository(FakeMediaScanner(emptyList()))
        assertEquals(0, emptyRepo.scanAndLoad().size)
    }

    @Test
    fun scanAndLoad_callsScanVideoFilesOnlyOnce() {
        var scanCount = 0
        val countingScanner = object : MediaScanner {
            override fun scanVideoFiles(): List<VideoItem> {
                scanCount++
                return listOf(VideoItem(url = "file://test.mp4", name = "Test.mp4"))
            }
            override fun scanSubtitleFiles() = emptyList<SubtitleEntry>()
            override fun scanAndGroup(
                whitelistedVideos: Set<String>,
                movieCollections: Map<String, MovieCollection>,
                releaseDates: Map<String, String>,
                rawVideos: List<VideoItem>?,
            ): List<VideoItem> {
                val list = rawVideos ?: scanVideoFiles()
                return list
            }
        }
        val repo = VideoRepository(countingScanner)
        repo.scanAndLoad()
        assertEquals(1, scanCount)
    }

    @Test
    fun scanAndLoad_previewsFirstVisibleBatchThenCommitsCompleteGroupsAndSubtitles() {
        val hidden = VideoItem(url = "hidden", name = "VID_20240315_143022.mp4")
        val allowed = hidden.copy(url = "allowed", name = "VID_20240316_143022.mp4")
        val first = VideoItem(url = "ep1", name = "Beta.S01E01.mkv")
        val second = VideoItem(url = "ep2", name = "Beta.S01E02.mkv")
        val files = listOf(hidden, allowed, first, second)
        val whitelist = setOf(allowed.name)
        var previewCount = 0
        val scanner = object : MediaScanner by FakeMediaScanner(files) {
            override fun scanVideoFiles(onBatchScanned: (List<VideoItem>) -> Unit): List<VideoItem> {
                onBatchScanned(listOf(hidden))
                assertEquals(0, previewCount)
                onBatchScanned(listOf(allowed, first))
                assertEquals(1, previewCount) // Before the final page or subtitle scan.
                onBatchScanned(listOf(second))
                assertEquals(1, previewCount)
                return files
            }

            override fun scanAndGroup(
                whitelistedVideos: Set<String>,
                movieCollections: Map<String, MovieCollection>,
                releaseDates: Map<String, String>,
                rawVideos: List<VideoItem>?,
            ): List<VideoItem> = com.localstream.app.domain.VideoGrouper.groupVideos(
                rawVideos.orEmpty().map { it.copy(subtitleNativePath = "subtitle") },
                movieCollections, releaseDates, whitelistedVideos,
            )
        }
        val repository = VideoRepository(scanner)
        val result = repository.scanAndLoad(whitelistedVideos = whitelist, onInitialContent = { preview, batch ->
            previewCount++
            assertEquals(listOf(allowed, first), batch)
            assertEquals(setOf(allowed.name, "Beta"), preview.map { it.name }.toSet())
            assertEquals(1, preview.single { it.isSeriesGroup }.episodes.orEmpty().size)
            assertTrue(repository.getRawVideos().isEmpty())
            assertTrue(repository.getGroupedVideos().isEmpty())
        })
        assertEquals(files, repository.getRawVideos())
        assertEquals(2, result.size)
        val episodes = result.single { it.isSeriesGroup }.episodes.orEmpty()
        assertEquals(listOf(first.name, second.name), episodes.map { it.name })
        assertTrue(episodes.all { it.subtitleNativePath == "subtitle" })
    }

    @Test
    fun scanAndLoad_refreshKeepsCatalogueUntilCompletionAndRemovesDeletedFiles() {
        var files = listOf(VideoItem(url = "old", name = "Old.mkv"))
        var subtitleCacheCleared = false
        val scanner = object : MediaScanner by FakeMediaScanner(emptyList()) {
            override fun scanVideoFiles() = files
            override fun scanVideoFiles(onBatchScanned: (List<VideoItem>) -> Unit): List<VideoItem> =
                files.also { if (it.isNotEmpty()) onBatchScanned(it) }
            override fun clearSubtitleCache() { subtitleCacheCleared = true }
        }
        val repository = VideoRepository(scanner)
        val original = repository.scanAndLoad()
        files = listOf(VideoItem(url = "new", name = "New.mkv"))
        assertEquals(original, repository.scanAndLoad())
        val refreshed = repository.scanAndLoad(forceRefresh = true, onInitialContent = { _, _ ->
            error("An existing catalogue must not be replaced by a partial scan")
        })
        assertTrue(subtitleCacheCleared)
        assertEquals(listOf("New.mkv"), refreshed.map { it.name })
        files = emptyList()
        assertTrue(repository.scanAndLoad(forceRefresh = true).isEmpty())
        assertTrue(repository.getRawVideos().isEmpty())
    }
}

private class FakeMediaScanner(private val videos: List<VideoItem>) : MediaScanner {
    override fun scanVideoFiles() = videos
    override fun scanSubtitleFiles() = emptyList<SubtitleEntry>()
    override fun scanAndGroup(
        whitelistedVideos: Set<String>,
        movieCollections: Map<String, MovieCollection>,
        releaseDates: Map<String, String>,
        rawVideos: List<VideoItem>?,
    ): List<VideoItem> {
        // Regroupement minimal pour les tests (utilise le VideoGrouper réel)
        return com.localstream.app.domain.VideoGrouper.groupVideos(rawVideos ?: videos, movieCollections, releaseDates, whitelistedVideos)
    }
}
