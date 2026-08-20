package com.localstream.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.localstream.app.domain.VideoUiSelectors
import com.localstream.app.domain.model.TmdbMetadata
import com.localstream.app.domain.model.VideoDisplayData
import com.localstream.app.domain.model.VideoItem
import com.localstream.app.ui.theme.White

/**
 * Carrousel horizontal d'affiches (équivalent Compose de `VideoRow.tsx`).
 * Clés stables pour éviter les recompositions inutiles au fil des mises à jour,
 * et utilisation de [VideoDisplayData] (@Immutable) pour restaurer le skipping Compose.
 */
@Suppress("LongParameterList", "FunctionNaming")
@Composable
fun VideoRow(
    title: String,
    items: List<VideoItem>,
    displayData: VideoDisplayData,
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
        val rowState = rememberLazyListState()
        LazyRow(
            state = rowState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(
                items = items,
                key = { it.nativeUri?.takeIf(String::isNotEmpty) ?: it.path.ifEmpty { it.name } },
                contentType = { "video_card" },
            ) { video ->
                val onItemClick = remember(video, onOpenDetails) { { onOpenDetails(video) } }
                val onResetClick = remember(video.name, onResetProgress) { { onResetProgress(video.name) } }
                VideoCard(
                    video = video,
                    posterUrl = VideoUiSelectors.posterUrl(video, displayData),
                    isWatched = VideoUiSelectors.isWatched(video, displayData),
                    progress = VideoUiSelectors.progressOf(video, displayData),
                    episodeLabel = VideoUiSelectors.activeEpisodeLabel(video, displayData),
                    showResetProgress = showResetProgress,
                    onClick = onItemClick,
                    onResetProgress = onResetClick,
                    modifier = Modifier.width(112.dp),
                )
            }
        }
    }
}

/**
 * Surcharge de compatibilité pour [VideoRow].
 */
@Suppress("LongParameterList", "FunctionNaming")
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
    VideoRow(
        title = title,
        items = items,
        displayData = VideoDisplayData(metadata = metadata, watched = watched, progress = progress),
        showResetProgress = showResetProgress,
        onOpenDetails = onOpenDetails,
        onResetProgress = onResetProgress,
        modifier = modifier,
    )
}
