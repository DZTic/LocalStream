package com.localstream.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.localstream.app.domain.VideoUiSelectors
import com.localstream.app.domain.model.TmdbMetadata
import com.localstream.app.domain.model.VideoItem
import com.localstream.app.ui.theme.White

/**
 * Carrousel horizontal d'affiches (équivalent Compose de `VideoRow.tsx`).
 * Clés stables pour éviter les recompositions inutiles au fil des mises à jour.
 */
@Composable
fun VideoRow(
    title: String,
    items: List<VideoItem>,
    metadata: Map<String, TmdbMetadata>,
    watched: Map<String, Boolean>,
    progress: Map<String, Double>,
    showResetProgress: Boolean,
    onOpenDetails: (VideoItem) -> Unit,
    onResetProgress: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return

    Column(modifier = modifier.padding(bottom = 24.dp)) {
        Text(
            text = title,
            color = White,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(
                items = items,
                key = { it.nativeUri?.takeIf(String::isNotEmpty) ?: it.path.ifEmpty { it.name } },
            ) { video ->
                val meta = metadata[VideoUiSelectors.metadataKey(video)]
                VideoCard(
                    video = video,
                    posterUrl = meta?.posterUrl(),
                    isWatched = VideoUiSelectors.isWatched(video, watched),
                    progress = VideoUiSelectors.progressOf(video, progress),
                    showResetProgress = showResetProgress,
                    onClick = { onOpenDetails(video) },
                    onResetProgress = { onResetProgress(video.name) },
                    modifier = Modifier.width(112.dp),
                )
            }
        }
    }
}
