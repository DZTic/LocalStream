package com.localstream.app.domain

import com.localstream.app.domain.model.VideoItem
import java.io.File

/** Une row "Dossier : X" de la page d'accueil. */
data class FolderRow(
    val title: String,
    val items: List<VideoItem>,
)

/** Ensemble des rows de la page d'accueil, dans l'ordre d'affichage exact. */
data class HomeRows(
    val heroCandidates: List<VideoItem>,
    val continueWatching: List<VideoItem>,
    val recentAdditions: List<VideoItem>,
    val recommendations: List<VideoItem>,
    val series: List<VideoItem>,
    val movies: List<VideoItem>,
    val folderRows: List<FolderRow>,
    val alphabetical: List<VideoItem>,
)

/**
 * Dérivation pure des rows de l'accueil (réf. `HomeScreen.tsx` + `App.tsx`).
 *
 * Ordre exact : "Continuer la lecture" (si non vide), "Nouveautés" (15),
 * "Recommandations" (15), "Séries", "Films", une row par dossier non-système,
 * "De A à Z".
 */
object HomeRowsDeriver {

    const val ROW_LIMIT = 15
    const val CONTINUE_MAX_PROGRESS = 95.0

    /**
     * Dossiers considérés "système" (caméra, réseaux sociaux, dossiers internes
     * Android) : exclus des rows par dossier, comme le filtrage des vidéos
     * personnelles côté web.
     */
    private val SYSTEM_FOLDERS = setOf(
        "dcim", "camera", "pictures", "screenshots", "recordings",
        "whatsapp", "telegram", "snapchat", "instagram", "tiktok",
        "messenger", "signal", "viber", "android", ".thumbnails", "lost.dir",
    )

    fun derive(
        grouped: List<VideoItem>,
        filteredSorted: List<VideoItem>,
        watched: Map<String, Boolean>,
        progress: Map<String, Double>,
    ): HomeRows {
        val heroCandidates = HeroSelector.getHeroCandidates(grouped, watched, progress)
            .ifEmpty { listOfNotNull(filteredSorted.firstOrNull() ?: grouped.firstOrNull()) }

        return HomeRows(
            heroCandidates = heroCandidates,
            continueWatching = filteredSorted.filter { v ->
                val p = progress[v.name] ?: 0.0
                p > 0.0 && p < CONTINUE_MAX_PROGRESS
            },
            // Ordre du scan (MediaStore : plus récent d'abord), inversé comme le web.
            recentAdditions = grouped.asReversed().take(ROW_LIMIT),
            recommendations = grouped.take(ROW_LIMIT),
            series = grouped.filter { it.isSeriesGroup },
            movies = grouped.filter { !it.isSeriesGroup },
            folderRows = deriveFolderRows(grouped),
            alphabetical = filteredSorted.sortedWith(
                compareBy<VideoItem, String>(String.CASE_INSENSITIVE_ORDER) { it.name }
                    .thenBy { it.name },
            ),
        )
    }

    /**
     * Regroupe par dossier parent (basename du chemin), exclut les dossiers
     * système et la racine, tri alphabétique, items dans l'ordre du scan.
     */
    private fun deriveFolderRows(grouped: List<VideoItem>): List<FolderRow> {
        val byFolder = linkedMapOf<String, MutableList<VideoItem>>()
        grouped.forEach { video ->
            val folder = folderNameOf(video) ?: return@forEach
            byFolder.getOrPut(folder) { mutableListOf() }.add(video)
        }
        return byFolder.entries
            .sortedBy { it.key.lowercase() }
            .map { (folder, items) -> FolderRow(title = "Dossier : $folder", items = items.toList()) }
    }

    private fun folderNameOf(video: VideoItem): String? {
        val path = if (video.isSeriesGroup) {
            video.episodes?.firstOrNull()?.path ?: video.path
        } else {
            video.path
        }
        var dir = path.takeIf { it.isNotBlank() }?.let { File(it).parent }
        if (dir != null && video.isSeriesGroup && video.isTvSeries) {
            // Les épisodes vivent dans un dossier dédié à la série
            // (".../Series/Show/S01E01.mkv") : on remonte d'un niveau pour
            // retrouver le dossier "bibliothèque" ("Series") et éviter une
            // row par série.
            dir = File(dir).parent
        }
        return dir
            ?.let { File(it).name.trim() }
            ?.takeIf { it.isNotEmpty() && it.lowercase() !in SYSTEM_FOLDERS }
    }
}
