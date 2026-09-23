package com.localstream.app.data.legacy

import android.content.Context
import java.io.File

/**
 * Supprime les fichiers laissés par l'ancienne fonctionnalité YouTube (retirée dans 1e0c883) :
 * le runtime Python extrait par youtubedl-android (~45 Mo dans `no_backup/`) et ses préférences.
 * Sans effet quand le dossier n'existe pas ; à appeler hors du thread principal.
 */
object LegacyYoutubeCleaner {
    internal const val RUNTIME_DIR = "youtubedl-android"
    internal const val PREFS_NAME = "youtubedl-android"

    fun cleanup(context: Context): Boolean =
        cleanup(context.noBackupFilesDir) { context.deleteSharedPreferences(PREFS_NAME) }

    /** @return true si des fichiers obsolètes ont été supprimés. */
    internal fun cleanup(noBackupDir: File, deletePreferences: () -> Unit): Boolean {
        val runtimeDir = File(noBackupDir, RUNTIME_DIR)
        if (!runtimeDir.exists()) return false
        // Les liens symboliques du runtime pointent vers des fichiers du même dossier.
        runtimeDir.deleteRecursively()
        deletePreferences()
        return true
    }
}
