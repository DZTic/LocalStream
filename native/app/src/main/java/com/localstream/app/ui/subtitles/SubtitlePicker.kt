package com.localstream.app.ui.subtitles

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Sélection locale d'un fichier de sous-titres via SAF (port Kotlin de
 * `VideoLauncherPlugin.pickSubtitle`, Phase 6 étape 4).
 *
 * La permission de lecture persistée est prise pour pouvoir rejouer le fichier
 * après redémarrage de l'app.
 */
class SubtitlePicker(
    private val activity: ComponentActivity,
    private val onSubtitlePicked: (uri: Uri, displayName: String) -> Unit,
) {

    private val launcher = activity.registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            activity.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (_: SecurityException) {
            // Provider sans permission persistable : l'uri reste utilisable pour la session.
        }
        onSubtitlePicked(uri, queryDisplayName(activity, uri))
    }

    /** Ouvre le sélecteur de documents filtré sur les formats de sous-titres. */
    fun launch() = launcher.launch(MIME_TYPES)

    private fun queryDisplayName(context: Context, uri: Uri): String {
        val fallback = uri.lastPathSegment ?: DEFAULT_NAME
        return runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else fallback
            } ?: fallback
        }.getOrDefault(fallback)
    }

    companion object {
        // srt/vtt/ass sont souvent exposés comme text/plain ou octet-stream.
        val MIME_TYPES = arrayOf(
            "application/x-subrip",
            "text/vtt",
            "text/plain",
            "application/octet-stream",
        )
        const val DEFAULT_NAME = "subtitle.srt"
    }
}
