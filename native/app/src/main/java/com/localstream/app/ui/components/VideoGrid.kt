package com.localstream.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.localstream.app.domain.VideoUiSelectors
import com.localstream.app.domain.model.TmdbMetadata
import com.localstream.app.domain.model.VideoItem

/**
 * Grille adaptative d'affiches (réf. grilles de `SearchScreen.tsx` /
 * `LibraryScreen.tsx` : 2 à 6 colonnes selon la largeur, minSize ~110 dp).
 * Clés stables pour limiter les recompositions.
 */
@Composable
fun VideoGrid(
    videos: List<VideoItem>,
    metadata: Map<String, TmdbMetadata>,
    watched: Map<String, Boolean>,
    progress: Map<String, Double>,
    onOpenDetails: (VideoItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 110.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        items(
            items = videos,
            key = { it.nativeUri?.takeIf(String::isNotEmpty) ?: it.path.ifEmpty { it.name } },
        ) { video ->
            val meta = metadata[VideoUiSelectors.metadataKey(video)]
            VideoCard(
                video = video,
                posterUrl = meta?.posterUrl(),
                isWatched = VideoUiSelectors.isWatched(video, watched),
                progress = VideoUiSelectors.progressOf(video, progress),
                showResetProgress = false,
                onClick = { onOpenDetails(video) },
                onResetProgress = {},
            )
        }
    }
}
