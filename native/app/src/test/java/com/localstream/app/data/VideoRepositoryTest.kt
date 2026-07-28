package com.localstream.app.data

import com.localstream.app.data.local.InMemoryPreferencesDataSource
import com.localstream.app.data.repository.VideoRepository
import com.localstream.app.data.scanner.MediaStoreScanner
import com.localstream.app.domain.model.FilterSortOptions
import com.localstream.app.domain.model.SortBy
import com.localstream.app.domain.model.VideoItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class VideoRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var prefs: InMemoryPreferencesDataSource
    private lateinit var moviesDir: File
    private lateinit var repo: VideoRepository

    @Before
    fun setUp() {
        prefs = InMemoryPreferencesDataSource()
        moviesDir = tempFolder.newFolder("Movies")
        File(moviesDir, "MovieA.1080p.mkv").writeText("a")
        File(moviesDir, "MovieB.720p.mkv").writeText("b")

        val scanner = MediaStoreScanner(customDirectories = listOf(moviesDir))
        repo = VideoRepository(scanner, prefs)
        repo.scanAndLoad()
    }

    @Test
    fun scanAndLoad_loadsGroupedVideos() {
        assertEquals(2, repo.getGroupedVideos().size)
    }

    @Test
    fun toggleWatched_updatesWatchedStateAndPersists() {
        assertFalse(repo.getWatchedVideos()["MovieA.1080p.mkv"] ?: false)
        val isNowWatched = repo.toggleWatched("MovieA.1080p.mkv")
        assertTrue(isNowWatched)
        assertTrue(repo.getWatchedVideos()["MovieA.1080p.mkv"] ?: false)
        assertTrue(prefs.getWatchedVideos()["MovieA.1080p.mkv"] ?: false)
    }

    @Test
    fun watchProgress_updatesAndClearsProgress() {
        repo.updateWatchProgress("MovieA.1080p.mkv", 42.0)
        assertEquals(42.0, repo.getWatchProgress()["MovieA.1080p.mkv"]!!, 0.01)
        assertEquals(42.0, prefs.getWatchProgress()["MovieA.1080p.mkv"]!!, 0.01)

        repo.clearWatchProgress("MovieA.1080p.mkv")
        assertFalse(repo.getWatchProgress().containsKey("MovieA.1080p.mkv"))
    }

    @Test
    fun playlistManagement_createsAddsRemovesAndDeletesPlaylists() {
        val playlist = repo.createPlaylist("Ma Liste")
        assertEquals(1, repo.getPlaylists().size)
        assertEquals("Ma Liste", playlist.name)

        repo.addToPlaylist(playlist.id, "MovieA.1080p.mkv")
        val updated = repo.getPlaylists().first { it.id == playlist.id }
        assertEquals(listOf("MovieA.1080p.mkv"), updated.videoNames)

        repo.removeFromPlaylist(playlist.id, "MovieA.1080p.mkv")
        val afterRemove = repo.getPlaylists().first { it.id == playlist.id }
        assertTrue(afterRemove.videoNames.isEmpty())

        repo.deletePlaylist(playlist.id)
        assertTrue(repo.getPlaylists().isEmpty())
    }

    @Test
    fun getFilteredAndSortedVideos_appliesFilters() {
        val sorted = repo.getFilteredAndSortedVideos(FilterSortOptions(sortBy = SortBy.ALPHA))
        assertEquals(listOf("MovieA.1080p.mkv", "MovieB.720p.mkv"), sorted.map { it.name })
    }

    @Test
    fun getHeroCandidates_returnsCandidates() {
        val candidates = repo.getHeroCandidates()
        assertEquals(2, candidates.size)
    }
}
