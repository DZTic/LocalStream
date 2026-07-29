package com.localstream.app.domain

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
     * Clé de recherche des métadonnées TMDB (affiche, backdrop, synopsis) :
     * le nom de série pour les groupes, sinon le nom du fichier
     * (équivalent du `posterKey` web).
     */
    fun metadataKey(video: VideoItem): String = video.seriesName ?: video.name

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
