package com.localstream.app.data

import com.localstream.app.data.local.InMemoryPreferencesDataSource
import com.localstream.app.domain.model.PlaylistInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PreferencesRepositoryTest {

    private lateinit var prefs: InMemoryPreferencesDataSource

    @Before
    fun setUp() {
        prefs = InMemoryPreferencesDataSource()
    }

    @Test
    fun watchedVideos_persistsAndRetrievesMap() {
        assertTrue(prefs.getWatchedVideos().isEmpty())
        prefs.saveWatchedVideos(mapOf("Film1.mp4" to true, "Film2.mkv" to false))
        val res = prefs.getWatchedVideos()
        assertEquals(2, res.size)
        assertEquals(true, res["Film1.mp4"])
        assertEquals(false, res["Film2.mkv"])
    }

    @Test
    fun watchProgress_persistsAndRetrievesProgress() {
        prefs.saveWatchProgress(mapOf("Film1.mp4" to 45.5, "Film2.mkv" to 90.0))
        val res = prefs.getWatchProgress()
        assertEquals(45.5, res["Film1.mp4"]!!, 0.01)
        assertEquals(90.0, res["Film2.mkv"]!!, 0.01)
    }

    @Test
    fun whitelistedVideos_persistsAndRetrievesSet() {
        prefs.saveWhitelistedVideos(setOf("VID_1.mp4", "VID_2.mp4"))
        val res = prefs.getWhitelistedVideos()
        assertEquals(2, res.size)
        assertTrue(res.contains("VID_1.mp4"))
    }

    @Test
    fun playlists_persistsAndRetrievesPlaylists() {
        val playlist = PlaylistInfo(id = "p1", name = "Favoris", videoNames = listOf("Film1.mp4"))
        prefs.savePlaylists(listOf(playlist))
        val res = prefs.getPlaylists()
        assertEquals(1, res.size)
        assertEquals("Favoris", res[0].name)
        assertEquals(listOf("Film1.mp4"), res[0].videoNames)
    }

    @Test
    fun apiSettings_persistsAndRetrievesCredentials() {
        prefs.saveTmdbApiKey("tmdb_key_123")
        prefs.saveOpenSubtitlesApiKey("os_key_456")
        prefs.saveOpenSubtitlesUsername("user1")
        prefs.saveOpenSubtitlesPassword("pass1")
        prefs.saveExternalPlayerPackage("org.videolan.vlc")

        assertEquals("tmdb_key_123", prefs.getTmdbApiKey())
        assertEquals("os_key_456", prefs.getOpenSubtitlesApiKey())
        assertEquals("user1", prefs.getOpenSubtitlesUsername())
        assertEquals("pass1", prefs.getOpenSubtitlesPassword())
        assertEquals("org.videolan.vlc", prefs.getExternalPlayerPackage())
    }

    @Test
    fun tmdbCache_savesAndClearsCacheMap() {
        prefs.saveTmdbCacheMap("moviePosters", mapOf("Inception" to "/poster.jpg"))
        assertEquals(mapOf("Inception" to "/poster.jpg"), prefs.getTmdbCacheMap("moviePosters"))
        prefs.clearTmdbCache()
        assertTrue(prefs.getTmdbCacheMap("moviePosters").isEmpty())
    }
}
