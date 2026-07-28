package com.localstream.app.data.local

import com.localstream.app.domain.model.PlaylistInfo

/**
 * Interface d'accès à la persistance locale des préférences et états utilisateur.
 */
interface PreferencesDataSource {
    fun getWatchedVideos(): Map<String, Boolean>
    fun saveWatchedVideos(watched: Map<String, Boolean>)

    fun getWatchProgress(): Map<String, Double>
    fun saveWatchProgress(progress: Map<String, Double>)

    fun getWhitelistedVideos(): Set<String>
    fun saveWhitelistedVideos(whitelist: Set<String>)

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

    fun getExternalPlayerPackage(): String
    fun saveExternalPlayerPackage(packageName: String)

    fun getTmdbCacheMap(key: String): Map<String, String>
    fun saveTmdbCacheMap(key: String, map: Map<String, String>)

    fun clearTmdbCache()
}
