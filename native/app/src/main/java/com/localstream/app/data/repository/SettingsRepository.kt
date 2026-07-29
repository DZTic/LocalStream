package com.localstream.app.data.repository

import com.localstream.app.data.local.EncryptedPreferencesManager
import com.localstream.app.data.local.UserPreferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

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

    // -------- Credentials chiffrés --------

    open fun getTmdbApiKey(): String = encryptedPrefs?.tmdbApiKey ?: ""
    open fun saveTmdbApiKey(key: String) { encryptedPrefs?.tmdbApiKey = key }

    open fun getOpenSubtitlesApiKey(): String = encryptedPrefs?.openSubtitlesApiKey ?: ""
    open fun saveOpenSubtitlesApiKey(key: String) { encryptedPrefs?.openSubtitlesApiKey = key }

    open fun getOpenSubtitlesUsername(): String = encryptedPrefs?.openSubtitlesUsername ?: ""
    open fun saveOpenSubtitlesUsername(username: String) { encryptedPrefs?.openSubtitlesUsername = username }

    open fun getOpenSubtitlesPassword(): String = encryptedPrefs?.openSubtitlesPassword ?: ""
    open fun saveOpenSubtitlesPassword(password: String) { encryptedPrefs?.openSubtitlesPassword = password }

    // -------- Préférences DataStore --------

    open val videoPlayerMode: Flow<String> get() = dataStore?.videoPlayerMode ?: emptyFlow()
    open suspend fun saveVideoPlayerMode(mode: String) { dataStore?.saveVideoPlayerMode(mode) }

    open val externalPlayerPackage: Flow<String> get() = dataStore?.externalPlayerPackage ?: emptyFlow()
    open suspend fun saveExternalPlayerPackage(pkg: String) { dataStore?.saveExternalPlayerPackage(pkg) }

    open val whitelistedVideos: Flow<Set<String>> get() = dataStore?.whitelistedVideos ?: emptyFlow()
    open suspend fun saveWhitelistedVideos(whitelist: Set<String>) {
        dataStore?.saveWhitelistedVideos(whitelist)
    }

    open val forceAvailableJson: Flow<String> get() = dataStore?.forceAvailableJson ?: emptyFlow()
    open suspend fun saveForceAvailableJson(json: String) { dataStore?.saveForceAvailableJson(json) }

    open val legacyImportDone: Flow<Boolean> get() = dataStore?.legacyImportDone ?: emptyFlow()
    open suspend fun markLegacyImportDone() { dataStore?.markLegacyImportDone() }
}
