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
}

private class FakeMediaScanner(private val videos: List<VideoItem>) : MediaScanner {
    override fun scanVideoFiles() = videos
    override fun scanSubtitleFiles() = emptyList<SubtitleEntry>()
    override fun scanAndGroup(
        whitelistedVideos: Set<String>,
        movieCollections: Map<String, MovieCollection>,
        releaseDates: Map<String, String>,
    ): List<VideoItem> {
        // Regroupement minimal pour les tests (utilise le VideoGrouper r\u00e9el)
        return com.localstream.app.domain.VideoGrouper.groupVideos(videos, movieCollections, releaseDates, whitelistedVideos)
    }
}
