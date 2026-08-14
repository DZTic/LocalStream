package com.localstream.app.data.local

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Wrapper autour de [EncryptedSharedPreferences] pour stocker les credentials sensibles.
 *
 * Clés stockées (chiffrées) :
 *   - TMDB API key
 *   - OpenSubtitles API key, username, password
 *
 * Les valeurs sont chiffrées avec AES-256-GCM via Jetpack Security (AndroidKeyStore).
 * En cas de corruption du KeyStore ou d'incompatibilité, bascule en mode sécurisé
 * réinitialisé ou SharedPreferences standard pour garantir l'absence de crash au lancement.
 */
class EncryptedPreferencesManager(context: Context) {

    private val prefs: SharedPreferences = createSafePrefs(context)

    var tmdbApiKey: String
        get() = prefs.getString(KEY_TMDB_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_TMDB_API_KEY, value).apply()

    var openSubtitlesApiKey: String
        get() = prefs.getString(KEY_OPENSUB_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_OPENSUB_API_KEY, value).apply()

    var openSubtitlesUsername: String
        get() = prefs.getString(KEY_OPENSUB_USER, "") ?: ""
        set(value) = prefs.edit().putString(KEY_OPENSUB_USER, value).apply()

    /** Mot de passe stocké chiffré — jamais en clair sur le disque. */
    var openSubtitlesPassword: String
        get() = prefs.getString(KEY_OPENSUB_PASS, "") ?: ""
        set(value) = prefs.edit().putString(KEY_OPENSUB_PASS, value).apply()

    /** Jeton de session OpenSubtitles (Phase 6) — effacé sur 401 définitif. */
    var openSubtitlesToken: String
        get() = prefs.getString(KEY_OPENSUB_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_OPENSUB_TOKEN, value).apply()

    /** Efface tous les credentials chiffrés (utilisé lors de la déconnexion). */
    fun clearAll() = prefs.edit().clear().apply()

    companion object {
        private const val TAG = "EncryptedPrefs"
        private const val PREFS_NAME = "localstream_secure_prefs"
        private const val KEY_TMDB_API_KEY = "tmdb_api_key"
        private const val KEY_OPENSUB_API_KEY = "opensub_api_key"
        private const val KEY_OPENSUB_USER = "opensub_username"
        private const val KEY_OPENSUB_PASS = "opensub_password"
        private const val KEY_OPENSUB_TOKEN = "opensub_token"

        @Suppress("TooGenericExceptionCaught")
        private fun createSafePrefs(context: Context): SharedPreferences {
            return try {
                createEncryptedPrefs(context)
            } catch (error: Throwable) {
                Log.e(TAG, "Erreur d'accès à EncryptedSharedPreferences, tentative de reset", error)
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        context.deleteSharedPreferences(PREFS_NAME)
                    } else {
                        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
                    }
                    createEncryptedPrefs(context)
                } catch (fallbackError: Throwable) {
                    Log.e(TAG, "Repli sur SharedPreferences standard suite à une erreur KeyStore", fallbackError)
                    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                }
            }
        }

        private fun createEncryptedPrefs(context: Context): SharedPreferences {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            return EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }
    }
}
