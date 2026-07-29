package com.localstream.app.data.local

import com.localstream.app.domain.model.PlaylistInfo

/**
 * Interface d'acc\u00e8s \u00e0 la persistance locale des pr\u00e9f\u00e9rences et \u00e9tats utilisateur.
 * Impl\u00e9mentations : [InMemoryPreferencesDataSource] (tests), [SharedPreferencesDataSource] (prod Phase 3).
 * Phase 4 remplace le stockage interne par Room/DataStore/EncryptedSharedPreferences.
 */
@Suppress("TooManyFunctions")
interface PreferencesDataSource {
    fun getWatchedVideos(): Map<String, Boolean>
    fun saveWatchedVideos(watched: Map<String, Boolean>)

    fun getWatchProgress(): Map<String, Double>
    fun saveWatchProgress(progress: Map<String, Double>)

    fun getWatchPositions(): Map<String, Long>
    fun saveWatchPositions(positions: Map<String, Long>)

    fun getRecentlyWatched(): List<String>
    fun saveRecentlyWatched(recent: List<String>)

    fun getWhitelistedVideos(): Set<String>
    fun saveWhitelistedVideos(whitelist: Set<String>)

    fun getForceAvailableVideos(): Map<String, Boolean>
    fun saveForceAvailableVideos(map: Map<String, Boolean>)

    fun getPlaylists(): List<PlaylistInfo>
    fun savePlaylists(playlists: List<PlaylistInfo>)

    fun getTmdbApiKey(): String
    fun saveTmdbApiKey(key: String)

    fun getOpenSubtitlesApiKey(): String
    fun saveOpenSubtitlesApiKey(key: String)

    fun getOpenSubtitlesUsername(): String
    fun saveOpenSubtitlesUsername(username: String)

    fun getOpenSubtitlesPassword(): String
    fun saveOpenSubtitlesPassword(password: String)

    fun getVideoPlayerMode(): String
    fun saveVideoPlayerMode(mode: String)

    fun getExternalPlayerPackage(): String
    fun saveExternalPlayerPackage(packageName: String)

    fun getTmdbCacheMap(key: String): Map<String, String>
    fun saveTmdbCacheMap(key: String, map: Map<String, String>)

    fun clearTmdbCache()
}
