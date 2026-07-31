package com.localstream.app.domain

import com.localstream.app.domain.model.VideoItem

/** Ensemble des rows de la page d'accueil, dans l'ordre d'affichage exact. */
data class HomeRows(
    val heroCandidates: List<VideoItem>,
    val continueWatching: List<VideoItem>,
    val recentAdditions: List<VideoItem>,
    val recommendations: List<VideoItem>,
    val series: List<VideoItem>,
    val movies: List<VideoItem>,
    val alphabetical: List<VideoItem>,
)

/**
 * Dérivation pure des rows de l'accueil (réf. `HomeScreen.tsx` + `App.tsx`).
 *
 * Ordre exact : "Continuer la lecture" (si non vide), "Nouveautés" (15),
 * "Recommandations" (15), "Séries", "Films", "De A à Z".
 */
object HomeRowsDeriver {

    const val ROW_LIMIT = 15
    const val CONTINUE_MAX_PROGRESS = 95.0

    fun derive(
        grouped: List<VideoItem>,
        filteredSorted: List<VideoItem>,
        watched: Map<String, Boolean>,
        progress: Map<String, Double>,
    ): HomeRows {
        val unwatchedGrouped = grouped.filter { !VideoUiSelectors.isWatched(it, watched) }
        val unwatchedFilteredSorted = filteredSorted.filter { !VideoUiSelectors.isWatched(it, watched) }

        val heroCandidates = HeroSelector.getHeroCandidates(grouped, watched, progress)
            .ifEmpty { listOfNotNull(unwatchedFilteredSorted.firstOrNull() ?: unwatchedGrouped.firstOrNull() ?: grouped.firstOrNull()) }

        return HomeRows(
            heroCandidates = heroCandidates,
            continueWatching = unwatchedFilteredSorted.filter { v ->
                val p = VideoUiSelectors.progressOf(v, progress)
                p > 0.0 && p < CONTINUE_MAX_PROGRESS
            },
            recentAdditions = unwatchedGrouped.asReversed().take(ROW_LIMIT),
            recommendations = unwatchedGrouped.take(ROW_LIMIT),
            series = unwatchedGrouped.filter { it.isSeriesGroup },
            movies = unwatchedGrouped.filter { !it.isSeriesGroup },
            alphabetical = unwatchedFilteredSorted.sortedWith(
                compareBy<VideoItem, String>(String.CASE_INSENSITIVE_ORDER) { it.name }
                    .thenBy { it.name },
            ),
        )
    }
}
