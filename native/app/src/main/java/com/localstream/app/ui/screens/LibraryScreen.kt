package com.localstream.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.localstream.app.domain.model.ResolutionFilter
import com.localstream.app.domain.model.SortBy
import com.localstream.app.domain.model.VideoItem
import com.localstream.app.ui.components.LibraryFilterBar
import com.localstream.app.ui.components.TopBar
import com.localstream.app.ui.components.VideoGrid
import com.localstream.app.ui.library.LibraryUiState
import com.localstream.app.ui.theme.White
import com.localstream.app.ui.theme.Zinc500

/**
 * Écran Bibliothèque (Phase 7, réf. `LibraryScreen.tsx` + `FilterBar.tsx`) :
 * barre de tri (A-Z, date, taille, durée) et filtres (genre TMDB, qualité) en
 * chips/menus M3, grille adaptative identique à la recherche, état vide dédié.
 * Le filtrage/tri est assuré par [com.localstream.app.domain.VideoFilterSorter].
 */
@Composable
fun LibraryScreen(
    uiState: LibraryUiState,
    onSortBy: (SortBy) -> Unit,
    onFilterGenre: (Int?) -> Unit,
    onFilterResolution: (ResolutionFilter) -> Unit,
    onOpenDetails: (VideoItem) -> Unit,
    onLogoClick: () -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onYouTubeClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        TopBar(
            solid = true,
            showSearch = uiState.videos.isNotEmpty(),
            isFetchingMetadata = uiState.isFetchingMetadata,
            onLogoClick = onLogoClick,
            onSearchClick = onSearchClick,
            onSettingsClick = onSettingsClick,
            onYouTubeClick = onYouTubeClick,
        )

        LibraryFilterBar(
            sortBy = uiState.sortBy,
            filterGenre = uiState.filterGenre,
            filterResolution = uiState.filterResolution,
            onSortBy = onSortBy,
            onFilterGenre = onFilterGenre,
            onFilterResolution = onFilterResolution,
        )

        Text(
            text = "Bibliothèque",
            color = White,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        if (uiState.filteredSorted.isEmpty()) {
            Text(
                text = if (uiState.videos.isEmpty()) {
                    "Aucune vidéo dans la bibliothèque."
                } else {
                    "Aucun résultat correspondant à ces filtres."
                },
                color = Zinc500,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .padding(32.dp)
                    .align(Alignment.CenterHorizontally),
            )
        } else {
            VideoGrid(
                videos = uiState.filteredSorted,
                metadata = uiState.metadata,
                watched = uiState.watched,
                progress = uiState.progress,
                onOpenDetails = onOpenDetails,
            )
        }
    }
}
