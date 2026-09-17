package com.localstream.app.data.repository

import com.localstream.app.data.local.EncryptedPreferencesManager
import com.localstream.app.data.local.UserPreferencesDataStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
    encryptedPrefsFactory: (() -> EncryptedPreferencesManager?)? = null,
    private val dataStore: UserPreferencesDataStore? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val encryptedPrefs by lazy { encryptedPrefsFactory?.invoke() }
    private val credentialsMutex = Mutex()

    // The factory and every encrypted read/write run on IO, including the first access.
    private suspend fun <T> accessCredentials(block: () -> T): T =
        credentialsMutex.withLock {
            if (hasPersistentCredentials) withContext(ioDispatcher) { block() } else block()
        }

    private val hasPersistentCredentials = encryptedPrefsFactory != null
    private var inMemoryTmdbApiKey: String = ""
    private var inMemoryOsApiKey: String = ""
    private var inMemoryOsUser: String = ""
    private var inMemoryOsPass: String = ""
    private var inMemoryOsToken: String = ""

    // -------- Credentials chiffrés --------

    open suspend fun getTmdbApiKey(): String = accessCredentials { encryptedPrefs?.tmdbApiKey ?: inMemoryTmdbApiKey }
    open suspend fun saveTmdbApiKey(key: String) = accessCredentials {
        val prefs = encryptedPrefs
        if (prefs != null) prefs.tmdbApiKey = key else inMemoryTmdbApiKey = key
    }

    open suspend fun getOpenSubtitlesApiKey(): String = accessCredentials { encryptedPrefs?.openSubtitlesApiKey ?: inMemoryOsApiKey }
    open suspend fun saveOpenSubtitlesApiKey(key: String) = accessCredentials {
        val prefs = encryptedPrefs
        if (prefs != null) prefs.openSubtitlesApiKey = key else inMemoryOsApiKey = key
    }

    open suspend fun getOpenSubtitlesUsername(): String = accessCredentials { encryptedPrefs?.openSubtitlesUsername ?: inMemoryOsUser }
    open suspend fun saveOpenSubtitlesUsername(username: String) = accessCredentials {
        val prefs = encryptedPrefs
        if (prefs != null) prefs.openSubtitlesUsername = username else inMemoryOsUser = username
    }

    open suspend fun getOpenSubtitlesPassword(): String = accessCredentials { encryptedPrefs?.openSubtitlesPassword ?: inMemoryOsPass }
    open suspend fun saveOpenSubtitlesPassword(password: String) = accessCredentials {
        val prefs = encryptedPrefs
        if (prefs != null) prefs.openSubtitlesPassword = password else inMemoryOsPass = password
    }

    open suspend fun getOpenSubtitlesToken(): String = accessCredentials { encryptedPrefs?.openSubtitlesToken ?: inMemoryOsToken }
    open suspend fun saveOpenSubtitlesToken(token: String) = accessCredentials {
        val prefs = encryptedPrefs
        if (prefs != null) prefs.openSubtitlesToken = token else inMemoryOsToken = token
    }

    // -------- Préférences DataStore --------

    open val videoPlayerMode: Flow<String> get() = dataStore?.videoPlayerMode ?: flowOf("internal")
    open val observePlayerMode: Flow<String> get() = videoPlayerMode
    open suspend fun saveVideoPlayerMode(mode: String) { dataStore?.saveVideoPlayerMode(mode) }
    open suspend fun setPlayerMode(mode: String) { saveVideoPlayerMode(mode) }

    open val externalPlayerPackage: Flow<String> get() = dataStore?.externalPlayerPackage ?: flowOf("")
    open val observeExternalPlayer: Flow<String> get() = externalPlayerPackage
    open suspend fun saveExternalPlayerPackage(pkg: String) { dataStore?.saveExternalPlayerPackage(pkg) }
    open suspend fun setSelectedExternalPlayer(pkg: String) { saveExternalPlayerPackage(pkg) }

    open val whitelistedVideos: Flow<Set<String>> get() = dataStore?.whitelistedVideos ?: flowOf(emptySet())
    open suspend fun saveWhitelistedVideos(whitelist: Set<String>) {
        dataStore?.saveWhitelistedVideos(whitelist)
    }

    open val forceAvailableJson: Flow<String> get() = dataStore?.forceAvailableJson ?: flowOf("[]")
    open val observeForceAvailable: Flow<Set<String>> get() = dataStore?.forceAvailableJson?.map { jsonStr ->
        runCatching { Json.decodeFromString<List<String>>(jsonStr).toSet() }.getOrDefault(emptySet())
    } ?: flowOf(emptySet())

    open suspend fun saveForceAvailableJson(json: String) { dataStore?.saveForceAvailableJson(json) }
    open suspend fun toggleForceAvailable(videoName: String) {
        dataStore?.let { ds ->
            val currentJson = ds.forceAvailableJson.firstOrNull() ?: "[]"
            val currentSet = runCatching { Json.decodeFromString<List<String>>(currentJson).toSet() }.getOrDefault(emptySet())
            val nextSet = if (currentSet.contains(videoName)) currentSet - videoName else currentSet + videoName
            ds.saveForceAvailableJson(Json.encodeToString(nextSet.toList()))
        }
    }

    open val legacyImportDone: Flow<Boolean> get() = dataStore?.legacyImportDone ?: flowOf(false)
    open suspend fun markLegacyImportDone() { dataStore?.markLegacyImportDone() }

    open val tmdbBannerDismissed: Flow<Boolean> get() = dataStore?.tmdbBannerDismissed ?: flowOf(false)
    open suspend fun dismissTmdbBanner() { dataStore?.dismissTmdbBanner() }
}
