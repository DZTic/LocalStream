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

        assertEquals("tmdb_key", repo.getTmdbApiKey())
        assertEquals("os_key", repo.getOpenSubtitlesApiKey())
        assertEquals("user", repo.getOpenSubtitlesUsername())
        assertEquals("pass", repo.getOpenSubtitlesPassword())
    }
}

/**
 * Adapter du [SettingsRepository] pour les tests sans Android :
 * utilise [InMemoryPreferencesDataSource] comme backend.
 */
private class SettingsRepository(private val prefs: InMemoryPreferencesDataSource) {
    fun getTmdbApiKey(): String = prefs.getTmdbApiKey()
    fun saveTmdbApiKey(key: String) = prefs.saveTmdbApiKey(key)
    fun getOpenSubtitlesApiKey(): String = prefs.getOpenSubtitlesApiKey()
    fun saveOpenSubtitlesApiKey(key: String) = prefs.saveOpenSubtitlesApiKey(key)
    fun getOpenSubtitlesUsername(): String = prefs.getOpenSubtitlesUsername()
    fun saveOpenSubtitlesUsername(u: String) = prefs.saveOpenSubtitlesUsername(u)
    fun getOpenSubtitlesPassword(): String = prefs.getOpenSubtitlesPassword()
    fun saveOpenSubtitlesPassword(p: String) = prefs.saveOpenSubtitlesPassword(p)
}
