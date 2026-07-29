package com.localstream.app.data.repository

import com.localstream.app.data.local.EncryptedPreferencesManager
import com.localstream.app.data.local.UserPreferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Repository des paramètres applicatifs (Phase 4, Phase 5).
 *
 * - Credentials sensibles  -> [EncryptedPreferencesManager] (chiffrement AES-256-GCM)
 * - Préférences légères     -> [UserPreferencesDataStore] (DataStore/Preferences)
 */
@Suppress("TooManyFunctions")
open class SettingsRepository(
    private val encryptedPrefs: EncryptedPreferencesManager? = null,
    private val dataStore: UserPreferencesDataStore? = null,
) {
    private var inMemoryTmdbApiKey: String = ""
    private var inMemoryOsApiKey: String = ""
    private var inMemoryOsUser: String = ""
    private var inMemoryOsPass: String = ""
    private var inMemoryOsToken: String = ""

    // -------- Credentials chiffrés --------

    open fun getTmdbApiKey(): String = encryptedPrefs?.tmdbApiKey ?: inMemoryTmdbApiKey
    open fun saveTmdbApiKey(key: String) {
        if (encryptedPrefs != null) encryptedPrefs.tmdbApiKey = key else inMemoryTmdbApiKey = key
    }

    open fun getOpenSubtitlesApiKey(): String = encryptedPrefs?.openSubtitlesApiKey ?: inMemoryOsApiKey
    open fun saveOpenSubtitlesApiKey(key: String) {
        if (encryptedPrefs != null) encryptedPrefs.openSubtitlesApiKey = key else inMemoryOsApiKey = key
    }

    open fun getOpenSubtitlesUsername(): String = encryptedPrefs?.openSubtitlesUsername ?: inMemoryOsUser
    open fun saveOpenSubtitlesUsername(username: String) {
        if (encryptedPrefs != null) encryptedPrefs.openSubtitlesUsername = username else inMemoryOsUser = username
    }

    open fun getOpenSubtitlesPassword(): String = encryptedPrefs?.openSubtitlesPassword ?: inMemoryOsPass
    open fun saveOpenSubtitlesPassword(password: String) {
        if (encryptedPrefs != null) encryptedPrefs.openSubtitlesPassword = password else inMemoryOsPass = password
    }

    open fun getOpenSubtitlesToken(): String = encryptedPrefs?.openSubtitlesToken ?: inMemoryOsToken
    open fun saveOpenSubtitlesToken(token: String) {
        if (encryptedPrefs != null) encryptedPrefs.openSubtitlesToken = token else inMemoryOsToken = token
    }

    // -------- Préférences DataStore --------

    open val videoPlayerMode: Flow<String> get() = dataStore?.videoPlayerMode ?: emptyFlow()
    open val observePlayerMode: Flow<String> get() = videoPlayerMode
    open suspend fun saveVideoPlayerMode(mode: String) { dataStore?.saveVideoPlayerMode(mode) }
    open suspend fun setPlayerMode(mode: String) { saveVideoPlayerMode(mode) }

    open val externalPlayerPackage: Flow<String> get() = dataStore?.externalPlayerPackage ?: emptyFlow()
    open val observeExternalPlayer: Flow<String> get() = externalPlayerPackage
    open suspend fun saveExternalPlayerPackage(pkg: String) { dataStore?.saveExternalPlayerPackage(pkg) }
    open suspend fun setSelectedExternalPlayer(pkg: String) { saveExternalPlayerPackage(pkg) }

    open val whitelistedVideos: Flow<Set<String>> get() = dataStore?.whitelistedVideos ?: emptyFlow()
    open suspend fun saveWhitelistedVideos(whitelist: Set<String>) {
        dataStore?.saveWhitelistedVideos(whitelist)
    }

    open val forceAvailableJson: Flow<String> get() = dataStore?.forceAvailableJson ?: emptyFlow()
    open val observeForceAvailable: Flow<Set<String>> get() = dataStore?.forceAvailableJson?.map { jsonStr ->
        runCatching { Json.decodeFromString<List<String>>(jsonStr).toSet() }.getOrDefault(emptySet())
    } ?: emptyFlow()

    open suspend fun saveForceAvailableJson(json: String) { dataStore?.saveForceAvailableJson(json) }
    open suspend fun toggleForceAvailable(videoName: String) {
        dataStore?.let { ds ->
            val currentJson = ds.forceAvailableJson.firstOrNull() ?: "[]"
            val currentSet = runCatching { Json.decodeFromString<List<String>>(currentJson).toSet() }.getOrDefault(emptySet())
            val nextSet = if (currentSet.contains(videoName)) currentSet - videoName else currentSet + videoName
            ds.saveForceAvailableJson(Json.encodeToString(nextSet.toList()))
        }
    }

    open val legacyImportDone: Flow<Boolean> get() = dataStore?.legacyImportDone ?: emptyFlow()
    open suspend fun markLegacyImportDone() { dataStore?.markLegacyImportDone() }

    open val tmdbBannerDismissed: Flow<Boolean> get() = dataStore?.tmdbBannerDismissed ?: emptyFlow()
    open suspend fun dismissTmdbBanner() { dataStore?.dismissTmdbBanner() }
}
