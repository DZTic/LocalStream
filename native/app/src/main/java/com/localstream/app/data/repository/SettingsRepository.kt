package com.localstream.app.data.repository

import com.localstream.app.data.local.EncryptedPreferencesManager
import com.localstream.app.data.local.UserPreferencesDataStore
import kotlinx.coroutines.flow.Flow

/**
 * Repository des param\u00e8tres applicatifs (Phase 4).
 *
 * - Credentials sensibles  \u2192 [EncryptedPreferencesManager] (chiffrement AES-256-GCM)
 * - Pr\u00e9f\u00e9rences l\u00e9g\u00e8res     \u2192 [UserPreferencesDataStore] (DataStore/Preferences)
 */
@Suppress("TooManyFunctions")
class SettingsRepository(
    private val encryptedPrefs: EncryptedPreferencesManager,
    private val dataStore: UserPreferencesDataStore,
) {

    // -------- Credentials chiffr\u00e9s --------

    fun getTmdbApiKey(): String = encryptedPrefs.tmdbApiKey
    fun saveTmdbApiKey(key: String) { encryptedPrefs.tmdbApiKey = key }

    fun getOpenSubtitlesApiKey(): String = encryptedPrefs.openSubtitlesApiKey
    fun saveOpenSubtitlesApiKey(key: String) { encryptedPrefs.openSubtitlesApiKey = key }

    fun getOpenSubtitlesUsername(): String = encryptedPrefs.openSubtitlesUsername
    fun saveOpenSubtitlesUsername(username: String) { encryptedPrefs.openSubtitlesUsername = username }

    fun getOpenSubtitlesPassword(): String = encryptedPrefs.openSubtitlesPassword
    fun saveOpenSubtitlesPassword(password: String) { encryptedPrefs.openSubtitlesPassword = password }

    // -------- Pr\u00e9f\u00e9rences DataStore --------

    val videoPlayerMode: Flow<String> = dataStore.videoPlayerMode
    suspend fun saveVideoPlayerMode(mode: String) = dataStore.saveVideoPlayerMode(mode)

    val externalPlayerPackage: Flow<String> = dataStore.externalPlayerPackage
    suspend fun saveExternalPlayerPackage(pkg: String) = dataStore.saveExternalPlayerPackage(pkg)

    val whitelistedVideos: Flow<Set<String>> = dataStore.whitelistedVideos
    suspend fun saveWhitelistedVideos(whitelist: Set<String>) =
        dataStore.saveWhitelistedVideos(whitelist)

    val forceAvailableJson: Flow<String> = dataStore.forceAvailableJson
    suspend fun saveForceAvailableJson(json: String) = dataStore.saveForceAvailableJson(json)

    val legacyImportDone: Flow<Boolean> = dataStore.legacyImportDone
    suspend fun markLegacyImportDone() = dataStore.markLegacyImportDone()
}
