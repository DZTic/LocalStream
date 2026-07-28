package com.localstream.app.data.local

import com.localstream.app.domain.model.PlaylistInfo

/**
 * Implémentation en mémoire de [PreferencesDataSource] pour les tests unitaires JVM.
 */
class InMemoryPreferencesDataSource : PreferencesDataSource {
    private var watchedVideosMap = mutableMapOf<String, Boolean>()
    private var watchProgressMap = mutableMapOf<String, Double>()
    private var whitelistedVideosSet = mutableSetOf<String>()
    private var playlistsList = mutableListOf<PlaylistInfo>()

    private var tmdbApiKeyStr: String = ""
    private var openSubtitlesApiKeyStr: String = ""
    private var openSubtitlesUsernameStr: String = ""
    private var openSubtitlesPasswordStr: String = ""
    private var externalPlayerPackageStr: String = ""

    private val tmdbCache = mutableMapOf<String, MutableMap<String, String>>()

    override fun getWatchedVideos(): Map<String, Boolean> = watchedVideosMap.toMap()
    override fun saveWatchedVideos(watched: Map<String, Boolean>) {
        watchedVideosMap = watched.toMutableMap()
    }

    override fun getWatchProgress(): Map<String, Double> = watchProgressMap.toMap()
    override fun saveWatchProgress(progress: Map<String, Double>) {
        watchProgressMap = progress.toMutableMap()
    }

    override fun getWhitelistedVideos(): Set<String> = whitelistedVideosSet.toSet()
    override fun saveWhitelistedVideos(whitelist: Set<String>) {
        whitelistedVideosSet = whitelist.toMutableSet()
    }

    override fun getPlaylists(): List<PlaylistInfo> = playlistsList.toList()
    override fun savePlaylists(playlists: List<PlaylistInfo>) {
        playlistsList = playlists.toMutableList()
    }

    override fun getTmdbApiKey(): String = tmdbApiKeyStr
    override fun saveTmdbApiKey(key: String) {
        tmdbApiKeyStr = key
    }

    override fun getOpenSubtitlesApiKey(): String = openSubtitlesApiKeyStr
    override fun saveOpenSubtitlesApiKey(key: String) {
        openSubtitlesApiKeyStr = key
    }

    override fun getOpenSubtitlesUsername(): String = openSubtitlesUsernameStr
    override fun saveOpenSubtitlesUsername(username: String) {
        openSubtitlesUsernameStr = username
    }

    override fun getOpenSubtitlesPassword(): String = openSubtitlesPasswordStr
    override fun saveOpenSubtitlesPassword(password: String) {
        openSubtitlesPasswordStr = password
    }

    override fun getExternalPlayerPackage(): String = externalPlayerPackageStr
    override fun saveExternalPlayerPackage(packageName: String) {
        externalPlayerPackageStr = packageName
    }

    override fun getTmdbCacheMap(key: String): Map<String, String> =
        tmdbCache[key]?.toMap() ?: emptyMap()

    override fun saveTmdbCacheMap(key: String, map: Map<String, String>) {
        tmdbCache[key] = map.toMutableMap()
    }

    override fun clearTmdbCache() {
        tmdbCache.clear()
    }
}
