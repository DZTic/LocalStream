package com.localstream.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Extension property — cr\u00e9e un DataStore par [Context], singleton. */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "user_preferences"
)

/**
 * DataStore pour les pr\u00e9f\u00e9rences l\u00e9g\u00e8res non-sensibles :
 *   - whitelist des vid\u00e9os personnelles incluses
 *   - forceAvailableVideos (historique dispo forc\u00e9e)
 *   - mode lecteur (internal / external)
 *   - package lecteur externe s\u00e9lectionn\u00e9
 *
 * Toutes les lectures exposent un [Flow] pour l'observation r\u00e9active en Compose.
 */
class UserPreferencesDataStore(private val context: Context) {

    private val ds: DataStore<Preferences> get() = context.dataStore

    // -------- Whitelist --------

    val whitelistedVideos: Flow<Set<String>> = ds.data.map { prefs ->
        prefs[Keys.WHITELISTED_VIDEOS] ?: emptySet()
    }

    suspend fun saveWhitelistedVideos(whitelist: Set<String>) {
        ds.edit { it[Keys.WHITELISTED_VIDEOS] = whitelist }
    }

    // -------- Force-available --------

    /** Stock\u00e9 en JSON compl\u00e9mentaire; la map est petite donc un String suffit. */
    val forceAvailableJson: Flow<String> = ds.data.map { prefs ->
        prefs[Keys.FORCE_AVAILABLE_JSON] ?: ""
    }

    suspend fun saveForceAvailableJson(json: String) {
        ds.edit { it[Keys.FORCE_AVAILABLE_JSON] = json }
    }

    // -------- Pr\u00e9f\u00e9rences lecteur --------

    val videoPlayerMode: Flow<String> = ds.data.map { prefs ->
        prefs[Keys.VIDEO_PLAYER_MODE] ?: "internal"
    }

    suspend fun saveVideoPlayerMode(mode: String) {
        ds.edit { it[Keys.VIDEO_PLAYER_MODE] = mode }
    }

    val externalPlayerPackage: Flow<String> = ds.data.map { prefs ->
        prefs[Keys.EXTERNAL_PLAYER_PACKAGE] ?: ""
    }

    suspend fun saveExternalPlayerPackage(pkg: String) {
        ds.edit { it[Keys.EXTERNAL_PLAYER_PACKAGE] = pkg }
    }

    // -------- Marqueur import legacy --------

    val legacyImportDone: Flow<Boolean> = ds.data.map { prefs ->
        prefs[Keys.LEGACY_IMPORT_DONE] ?: false
    }

    suspend fun markLegacyImportDone() {
        ds.edit { it[Keys.LEGACY_IMPORT_DONE] = true }
    }

    // -------- Bannière onboarding TMDB --------

    /** true si l'utilisateur a masqué la bannière "Configurer TMDB" de l'accueil. */
    val tmdbBannerDismissed: Flow<Boolean> = ds.data.map { prefs ->
        prefs[Keys.TMDB_BANNER_DISMISSED] ?: false
    }

    suspend fun dismissTmdbBanner() {
        ds.edit { it[Keys.TMDB_BANNER_DISMISSED] = true }
    }

    private object Keys {
        val WHITELISTED_VIDEOS = stringSetPreferencesKey("whitelisted_videos")
        val FORCE_AVAILABLE_JSON = stringPreferencesKey("force_available_json")
        val VIDEO_PLAYER_MODE = stringPreferencesKey("video_player_mode")
        val EXTERNAL_PLAYER_PACKAGE = stringPreferencesKey("external_player_package")
        val LEGACY_IMPORT_DONE = booleanPreferencesKey("legacy_import_done")
        val TMDB_BANNER_DISMISSED = booleanPreferencesKey("tmdb_banner_dismissed")
    }
}
