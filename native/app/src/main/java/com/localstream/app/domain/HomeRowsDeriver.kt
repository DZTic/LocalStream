package com.localstream.app.domain

import androidx.compose.runtime.Immutable
import com.localstream.app.domain.model.VideoItem

/** Ensemble des rows de la page d'accueil, dans l'ordre d'affichage exact. */
@Immutable
data class HomeRows(
    val heroCandidates: List<VideoItem>,
    val continueWatching: List<VideoItem>,
    val recentAdditions: List<VideoItem>,
    val recommendations: List<VideoItem>,
    val series: List<VideoItem>,
    val movies: List<VideoItem>,
    val alphabetical: List<VideoItem>,
)

/** Lignes insensibles à la progression pendant la lecture. */
@Immutable
data class StaticHomeRows(
    val recentAdditions: List<VideoItem>,
    val recommendations: List<VideoItem>,
    val series: List<VideoItem>,
    val movies: List<VideoItem>,
    val alphabetical: List<VideoItem>,
    val unwatchedFilteredSorted: List<VideoItem>,
    val fallbackHero: List<VideoItem>,
)

/**
 * Dérivation pure des rows de l'accueil (réf. `HomeScreen.tsx` + `App.tsx`).
 *
 * Ordre exact : "Continuer la lecture" (si non vide), "Nouveautés" (15),
 * "Recommandations" (15), "Séries" (50), "Films" (50), "De A à Z" (50).
 */
object HomeRowsDeriver {

    const val ROW_LIMIT = 15
    const val CATEGORY_ROW_LIMIT = 50
    const val CONTINUE_MAX_PROGRESS = 95.0

    @Volatile
    private var lastFilteredSortedForAlpha: List<VideoItem>? = null
    @Volatile
    private var cachedAlphabeticalSorted: List<VideoItem>? = null

    private fun getAlphabeticalSorted(filteredSorted: List<VideoItem>): List<VideoItem> {
        val cached = cachedAlphabeticalSorted
        if (lastFilteredSortedForAlpha === filteredSorted && cached != null) {
            return cached
        }
        val sorted = filteredSorted.sortedWith(
            compareBy<VideoItem, String>(String.CASE_INSENSITIVE_ORDER) { it.name }
                .thenBy { it.name },
        )
        lastFilteredSortedForAlpha = filteredSorted
        cachedAlphabeticalSorted = sorted
        return sorted
    }

    fun deriveStatic(
        grouped: List<VideoItem>,
        filteredSorted: List<VideoItem>,
        watched: Map<String, Boolean>,
    ): StaticHomeRows {
        val unwatchedOnly = { items: List<VideoItem> ->
            if (watched.isEmpty()) items else items.filter { !VideoUiSelectors.isWatched(it, watched) }
        }

        val unwatchedGrouped = unwatchedOnly(grouped)
        val unwatchedFilteredSorted = unwatchedOnly(filteredSorted)
        val fallbackHero = listOfNotNull(
            unwatchedFilteredSorted.firstOrNull() ?: unwatchedGrouped.firstOrNull() ?: grouped.firstOrNull(),
        )
        val alphabeticalSorted = getAlphabeticalSorted(filteredSorted)

        return StaticHomeRows(
            recentAdditions = unwatchedOnly(grouped.asReversed().take(ROW_LIMIT)),
            recommendations = unwatchedOnly(grouped.take(ROW_LIMIT)),
            series = unwatchedOnly(grouped.filter { it.isSeriesGroup }).take(CATEGORY_ROW_LIMIT),
            movies = unwatchedOnly(grouped.filter { !it.isSeriesGroup }).take(CATEGORY_ROW_LIMIT),
            alphabetical = unwatchedOnly(alphabeticalSorted).take(CATEGORY_ROW_LIMIT),
            unwatchedFilteredSorted = unwatchedFilteredSorted,
            fallbackHero = fallbackHero,
        )
    }

    fun deriveWithStatic(
        grouped: List<VideoItem>,
        watched: Map<String, Boolean>,
        progress: Map<String, Double>,
        staticRows: StaticHomeRows,
    ): HomeRows {
        val heroCandidates = HeroSelector.getHeroCandidates(grouped, watched, progress)
            .ifEmpty { staticRows.fallbackHero }

        val continueWatching = staticRows.unwatchedFilteredSorted.filter { v ->
            val p = VideoUiSelectors.progressOf(v, progress)
            p > 0.0 && p < CONTINUE_MAX_PROGRESS
        }

        return HomeRows(
            heroCandidates = heroCandidates,
            continueWatching = continueWatching,
            recentAdditions = staticRows.recentAdditions,
            recommendations = staticRows.recommendations,
            series = staticRows.series,
            movies = staticRows.movies,
            alphabetical = staticRows.alphabetical,
        )
    }

    fun derive(
        grouped: List<VideoItem>,
        filteredSorted: List<VideoItem>,
        watched: Map<String, Boolean>,
        progress: Map<String, Double>,
    ): HomeRows {
        val staticRows = deriveStatic(grouped, filteredSorted, watched)
        return deriveWithStatic(grouped, watched, progress, staticRows)
    }
}
