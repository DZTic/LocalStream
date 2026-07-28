package com.localstream.app.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Wrapper autour de [EncryptedSharedPreferences] pour stocker les credentials sensibles.
 *
 * Cl\u00e9s stock\u00e9es (chiffr\u00e9es) :
 *   - TMDB API key
 *   - OpenSubtitles API key, username, password
 *
 * Les valeurs sont chiffr\u00e9es avec AES-256-GCM via Jetpack Security (AndroidKeyStore).
 * V\u00e9rification : `adb shell run-as com.localstream.app.native cat shared_prefs/localstream_secure_prefs.xml`
 * — le contenu doit \u00eatre illisible (valeurs chiffr\u00e9es).
 */
class EncryptedPreferencesManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    var tmdbApiKey: String
        get() = prefs.getString(KEY_TMDB_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_TMDB_API_KEY, value).apply()

    var openSubtitlesApiKey: String
        get() = prefs.getString(KEY_OPENSUB_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_OPENSUB_API_KEY, value).apply()

    var openSubtitlesUsername: String
        get() = prefs.getString(KEY_OPENSUB_USER, "") ?: ""
        set(value) = prefs.edit().putString(KEY_OPENSUB_USER, value).apply()

    /** Mot de passe stock\u00e9 chiffr\u00e9 — jamais en clair sur le disque. */
    var openSubtitlesPassword: String
        get() = prefs.getString(KEY_OPENSUB_PASS, "") ?: ""
        set(value) = prefs.edit().putString(KEY_OPENSUB_PASS, value).apply()

    /** Efface tous les credentials chiffr\u00e9s (utilis\u00e9 lors de la d\u00e9connexion). */
    fun clearAll() = prefs.edit().clear().apply()

    companion object {
        private const val PREFS_NAME = "localstream_secure_prefs"
        private const val KEY_TMDB_API_KEY = "tmdb_api_key"
        private const val KEY_OPENSUB_API_KEY = "opensub_api_key"
        private const val KEY_OPENSUB_USER = "opensub_username"
        private const val KEY_OPENSUB_PASS = "opensub_password"
    }
}
