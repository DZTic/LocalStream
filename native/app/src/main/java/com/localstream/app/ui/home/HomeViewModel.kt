package com.localstream.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.localstream.app.domain.FolderRow
import com.localstream.app.domain.HomeRowsDeriver
import com.localstream.app.domain.model.TmdbMetadata
import com.localstream.app.domain.model.VideoItem
import com.localstream.app.ui.library.LibraryUiState
import com.localstream.app.ui.library.LibraryViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * État de l'écran d'accueil : les rows dans l'ordre exact du web plus les
 * données d'affichage (métadonnées, vu/progression, bannière TMDB).
 */
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
    val folderRows: List<FolderRow> = emptyList(),
    val alphabetical: List<VideoItem> = emptyList(),
    val metadata: Map<String, TmdbMetadata> = emptyMap(),
    val watched: Map<String, Boolean> = emptyMap(),
    val progress: Map<String, Double> = emptyMap(),
    val showTmdbBanner: Boolean = false,
)

/**
 * ViewModel de l'accueil (Phase 7) : dérive les rows de l'état bibliothèque
 * via [HomeRowsDeriver] (pur). Aucune E/S propre — le scan et l'enrichissement
 * restent la responsabilité du [LibraryViewModel] partagé.
 */
class HomeViewModel(
    libraryUiState: StateFlow<LibraryUiState>,
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = libraryUiState
        .map { deriveHomeUiState(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = HomeUiState(),
        )

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

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
                folderRows = rows.folderRows,
                alphabetical = rows.alphabetical,
                metadata = state.metadata,
                watched = state.watched,
                progress = state.progress,
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
