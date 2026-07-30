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
        val heroCandidates = HeroSelector.getHeroCandidates(grouped, watched, progress)
            .ifEmpty { listOfNotNull(filteredSorted.firstOrNull() ?: grouped.firstOrNull()) }

        val unwatchedOnly = { items: List<VideoItem> ->
            items.filter { !VideoUiSelectors.isWatched(it, watched) }
        }

        return HomeRows(
            heroCandidates = heroCandidates,
            continueWatching = filteredSorted.filter { v ->
                val p = progress[v.name] ?: 0.0
                p > 0.0 && p < CONTINUE_MAX_PROGRESS
            },
            recentAdditions = unwatchedOnly(grouped.asReversed().take(ROW_LIMIT)),
            recommendations = unwatchedOnly(grouped.take(ROW_LIMIT)),
            series = unwatchedOnly(grouped.filter { it.isSeriesGroup }),
            movies = unwatchedOnly(grouped.filter { !it.isSeriesGroup }),
            alphabetical = unwatchedOnly(filteredSorted.sortedWith(
                compareBy<VideoItem, String>(String.CASE_INSENSITIVE_ORDER) { it.name }
                    .thenBy { it.name },
            )),
        )
    }
}
