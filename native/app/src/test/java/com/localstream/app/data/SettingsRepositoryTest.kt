package com.localstream.app.data

import com.localstream.app.data.local.InMemoryPreferencesDataSource
import com.localstream.app.data.repository.SettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class SettingsRepositoryTest {

    private lateinit var prefs: InMemoryPreferencesDataSource
    private lateinit var repo: SettingsRepository

    @Before
    fun setUp() {
        prefs = InMemoryPreferencesDataSource()
        repo = SettingsRepository(prefs)
    }

    @Test
    fun settingsRepository_readsAndWritesAllSettings() {
        repo.saveTmdbApiKey("tmdb_key")
        repo.saveOpenSubtitlesApiKey("os_key")
        repo.saveOpenSubtitlesUsername("user")
        repo.saveOpenSubtitlesPassword("pass")
        repo.saveExternalPlayerPackage("com.mxtech.videoplayer.ad")

        assertEquals("tmdb_key", repo.getTmdbApiKey())
        assertEquals("os_key", repo.getOpenSubtitlesApiKey())
        assertEquals("user", repo.getOpenSubtitlesUsername())
        assertEquals("pass", repo.getOpenSubtitlesPassword())
        assertEquals("com.mxtech.videoplayer.ad", repo.getExternalPlayerPackage())
    }
}
