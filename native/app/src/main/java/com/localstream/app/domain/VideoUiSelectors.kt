package com.localstream.app.domain

import com.localstream.app.domain.model.TmdbMetadata
import com.localstream.app.domain.model.VideoItem

/**
 * Sélecteurs purs partagés par les écrans (Accueil, Recherche, Bibliothèque).
 * Portage exact de la logique de `VideoRow.tsx` / `VideoCard.tsx` (Phase 0).
 */
object VideoUiSelectors {

    /**
     * "Vu" d'un item : pour un groupe (série/saga), tous les épisodes doivent
     * être marqués vus ; pour un film, l'entrée du nom dans la map.
     */
    fun isWatched(video: VideoItem, watched: Map<String, Boolean>): Boolean =
        if (video.isSeriesGroup) {
            !video.episodes.isNullOrEmpty() && video.episodes.all { watched[it.name] == true }
        } else {
            watched[video.name] == true
        }

    /**
     * Progression affichée (0-100) : pour un groupe, la progression est stockée
     * par épisode — on retient celle du premier épisode en cours (0 < p < 100).
     */
    fun progressOf(video: VideoItem, progress: Map<String, Double>): Double =
        if (video.isSeriesGroup && video.episodes != null) {
            video.episodes
                .map { progress[it.name] ?: 0.0 }
                .firstOrNull { it > 0.0 && it < 100.0 } ?: 0.0
        } else {
            progress[video.name] ?: 0.0
        }

    /**
     * Identifie l'épisode actif (en cours de lecture, ou le prochain non vu) d'une série.
     */
    @Suppress("ReturnCount")
    fun getActiveEpisode(
        video: VideoItem,
        progress: Map<String, Double>,
        watched: Map<String, Boolean>,
    ): VideoItem? {
        if (!video.isSeriesGroup || video.episodes.isNullOrEmpty()) return null
        val inProgress = video.episodes.firstOrNull { ep ->
            val p = progress[ep.name] ?: 0.0
            p > 0.0 && watched[ep.name] != true
        }
        val nextUnwatched = video.episodes.firstOrNull { ep -> watched[ep.name] != true }
        return inProgress ?: nextUnwatched ?: video.episodes.firstOrNull()
    }

    /**
     * Formate le numéro de saison et d'épisode (ex: "S1:E2").
     */
    fun formatEpisodeLabel(video: VideoItem, episode: VideoItem): String {
        val idx = video.episodes?.indexOfFirst { it.name == episode.name } ?: -1
        val epNum = episode.episode ?: (if (idx >= 0) idx + 1 else 1)
        val seasonNum = episode.season ?: 1
        return "S${seasonNum}:E${epNum}"
    }

    /**
     * Retourne le libellé de l'épisode actif pour l'affichage sur la carte.
     */
    @Suppress("ReturnCount")
    fun activeEpisodeLabel(
        video: VideoItem,
        progress: Map<String, Double>,
        watched: Map<String, Boolean>,
    ): String? {
        if (!video.isSeriesGroup || video.episodes.isNullOrEmpty()) return null
        val activeEp = getActiveEpisode(video, progress, watched) ?: return null
        val label = formatEpisodeLabel(video, activeEp)
        val p = progress[activeEp.name] ?: 0.0
        val epWatched = watched[activeEp.name] == true
        return when {
            p > 0.0 && !epWatched -> "En cours : $label"
            video.episodes.any { watched[it.name] == true } -> "Prochain : $label"
            else -> label
        }
    }

    /**
     * Clé de recherche des métadonnées TMDB (affiche, backdrop, synopsis) :
     * le nom de série pour les groupes, sinon le nom du fichier
     * (équivalent du `posterKey` web).
     */
    fun metadataKey(video: VideoItem): String = video.seriesName ?: video.name

    /**
     * URL d'affiche avec fallback automatique pour les sagas/groupes si l'affiche
     * dédiée au groupe n'est pas encore disponible.
     */
    fun posterUrl(video: VideoItem, metadata: Map<String, TmdbMetadata>): String? {
        val primary = metadata[metadataKey(video)]?.posterUrl()
        val fallback = if (primary.isNullOrBlank() && video.isSeriesGroup) {
            video.episodes?.firstNotNullOfOrNull { ep ->
                metadata[ep.name]?.posterUrl()?.takeIf { it.isNotBlank() }
            }
        } else {
            null
        }
        return primary.takeIf { !it.isNullOrBlank() } ?: fallback
    }

    /** Titre affiché sous la vignette (équivalent du `title` de VideoCard.tsx). */
    fun displayTitle(video: VideoItem): String =
        if (video.isSeriesGroup) {
            video.seriesName ?: video.name
        } else {
            video.seriesName ?: TitleCleaner.getCleanTitle(video.name)
        }

    /** Filtrage de recherche insensible à la casse (équivalent App.tsx). */
    fun filterByQuery(videos: List<VideoItem>, query: String): List<VideoItem> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        return videos.filter { it.name.contains(q, ignoreCase = true) }
    }
}
