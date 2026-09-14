package com.localstream.app.ui.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.localstream.app.domain.HomeRows
import com.localstream.app.domain.HomeRowsDeriver
import com.localstream.app.domain.StaticHomeRows
import com.localstream.app.domain.model.TmdbMetadata
import com.localstream.app.domain.model.VideoDisplayData
import com.localstream.app.domain.model.VideoItem
import com.localstream.app.ui.library.LibraryUiState
import com.localstream.app.ui.library.LibraryViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * État de l'écran d'accueil : les rows dans l'ordre exact du web plus les
 * données d'affichage (métadonnées, vu/progression, bannière TMDB).
 */
@Immutable
data class HomeUiState(
    val isLoading: Boolean = true,
    val isFetchingMetadata: Boolean = false,
    val hasContent: Boolean = false,
    val heroCandidates: List<VideoItem> = emptyList(),
    val continueWatching: List<VideoItem> = emptyList(),
    val recentAdditions: List<VideoItem> = emptyList(),
    val recommendations: List<VideoItem> = emptyList(),
    val series: List<VideoItem> = emptyList(),
    val movies: List<VideoItem> = emptyList(),
    val alphabetical: List<VideoItem> = emptyList(),
    val displayData: VideoDisplayData = VideoDisplayData(),
    val showTmdbBanner: Boolean = false,
) {
    val metadata: Map<String, TmdbMetadata> get() = displayData.metadata
    val watched: Map<String, Boolean> get() = displayData.watched
    val progress: Map<String, Double> get() = displayData.progress
}

/**
 * ViewModel de l'accueil (Phase 7) : dérive les rows de l'état bibliothèque
 * via [HomeRowsDeriver] (pur). Aucune E/S propre — le scan et l'enrichissement
 * restent la responsabilité du [LibraryViewModel] partagé.
 */
class HomeViewModel(
    libraryUiState: StateFlow<LibraryUiState>,
    computationDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private var cachedRows: HomeRows? = null
    private var cachedStaticRows: StaticHomeRows? = null
    private var lastStaticGrouped: List<VideoItem>? = null
    private var lastStaticFilteredSorted: List<VideoItem>? = null
    private var lastStaticWatched: Map<String, Boolean>? = null
    private var lastProgress: Map<String, Double>? = null

    private fun isStaticCacheValid(state: LibraryUiState): Boolean =
        cachedStaticRows != null &&
            lastStaticGrouped === state.videos &&
            lastStaticFilteredSorted === state.filteredSorted &&
            lastStaticWatched == state.watched

    @Suppress("ComplexCondition")
    private fun isCacheValid(state: LibraryUiState): Boolean =
        isStaticCacheValid(state) &&
            cachedRows != null &&
            lastProgress == state.progress

    private fun deriveHomeUiStateWithCache(state: LibraryUiState): HomeUiState {
        val rows = if (isCacheValid(state)) {
            cachedRows ?: computeRows(state)
        } else {
            computeRows(state)
        }
        return HomeUiState(
            isLoading = state.isScanning && state.videos.isEmpty(),
            isFetchingMetadata = state.isFetchingMetadata,
            hasContent = state.videos.isNotEmpty(),
            heroCandidates = rows.heroCandidates,
            continueWatching = rows.continueWatching,
            recentAdditions = rows.recentAdditions,
            recommendations = rows.recommendations,
            series = rows.series,
            movies = rows.movies,
            alphabetical = rows.alphabetical,
            displayData = state.displayData,
            showTmdbBanner = state.videos.isNotEmpty() &&
                !state.hasTmdbKey &&
                !state.tmdbBannerDismissed,
        )
    }

    private fun computeRows(state: LibraryUiState): HomeRows {
        val staticRows = if (isStaticCacheValid(state) && cachedStaticRows != null) {
            cachedStaticRows!!
        } else {
            HomeRowsDeriver.deriveStatic(
                grouped = state.videos,
                filteredSorted = state.filteredSorted,
                watched = state.watched,
            ).also {
                cachedStaticRows = it
                lastStaticGrouped = state.videos
                lastStaticFilteredSorted = state.filteredSorted
                lastStaticWatched = state.watched
            }
        }

        val computed = HomeRowsDeriver.deriveWithStatic(
            grouped = state.videos,
            watched = state.watched,
            progress = state.progress,
            staticRows = staticRows,
        )
        cachedRows = computed
        lastProgress = state.progress
        return computed
    }

    val uiState: StateFlow<HomeUiState> = libraryUiState
        .map { deriveHomeUiStateWithCache(it) }
        .flowOn(computationDispatcher)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = HomeUiState(),
        )

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        /** Projection pure LibraryUiState → HomeUiState (testable sans Android). */
        fun deriveHomeUiState(state: LibraryUiState): HomeUiState {
            val rows = HomeRowsDeriver.derive(
                grouped = state.videos,
                filteredSorted = state.filteredSorted,
                watched = state.watched,
                progress = state.progress,
            )
            return HomeUiState(
                isLoading = state.isScanning && state.videos.isEmpty(),
                isFetchingMetadata = state.isFetchingMetadata,
                hasContent = state.videos.isNotEmpty(),
                heroCandidates = rows.heroCandidates,
                continueWatching = rows.continueWatching,
                recentAdditions = rows.recentAdditions,
                recommendations = rows.recommendations,
                series = rows.series,
                movies = rows.movies,
                alphabetical = rows.alphabetical,
                displayData = state.displayData,
                showTmdbBanner = state.videos.isNotEmpty() &&
                    !state.hasTmdbKey &&
                    !state.tmdbBannerDismissed,
            )
        }

        fun factory(libraryViewModel: LibraryViewModel): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { HomeViewModel(libraryViewModel.uiState) }
            }
    }
}
