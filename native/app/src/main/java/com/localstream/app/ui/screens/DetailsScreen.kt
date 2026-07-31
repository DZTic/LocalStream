package com.localstream.app.ui.screens

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.localstream.app.LocalStreamApplication
import com.localstream.app.domain.Formatters
import com.localstream.app.domain.TitleCleaner
import com.localstream.app.ui.components.PlaylistBottomSheet
import com.localstream.app.ui.details.DetailsViewModel
import com.localstream.app.ui.details.EpisodeUiState
import com.localstream.app.ui.subtitles.SubtitlePickerSheet
import com.localstream.app.ui.subtitles.SubtitlePickerViewModel
import com.localstream.app.ui.theme.Black
import com.localstream.app.ui.theme.Red600
import com.localstream.app.ui.theme.White
import com.localstream.app.ui.theme.Zinc300
import com.localstream.app.ui.theme.Zinc500
import com.localstream.app.ui.theme.Zinc800
import com.localstream.app.ui.theme.Zinc900

private val WatchedGreen = Color(0xFF16A34A)

@OptIn(ExperimentalLayoutApi::class)
@Suppress("FunctionNaming", "LongMethod", "CyclomaticComplexMethod")
@Composable
fun DetailsScreen(
    id: String,
    onBack: () -> Unit = {},
    onPlayVideo: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val container = (context.applicationContext as LocalStreamApplication).container

    val decodedId = remember(id) { Uri.decode(id) }
    val detailsViewModel: DetailsViewModel = viewModel(
        key = decodedId,
        factory = DetailsViewModel.factory(decodedId, container),
    )
    val subtitlePickerViewModel: SubtitlePickerViewModel = viewModel(
        factory = SubtitlePickerViewModel.factory(container),
    )

    val uiState by detailsViewModel.uiState.collectAsStateWithLifecycle()
    val subtitleUiState by subtitlePickerViewModel.uiState.collectAsStateWithLifecycle()

    var showPlaylistSheet by remember { mutableStateOf(false) }
    var showSubtitleSheet by remember { mutableStateOf(false) }
    var isSynopsisExpanded by remember { mutableStateOf(false) }

    val videoGroup = uiState.videoGroup
    val meta = uiState.metadata
    val cleanTitle = remember(videoGroup?.name) {
        TitleCleaner.getCleanTitle(videoGroup?.seriesName ?: videoGroup?.name ?: id)
    }

    if (showPlaylistSheet && videoGroup != null) {
        PlaylistBottomSheet(
            userPlaylists = uiState.userPlaylists,
            currentVideoName = videoGroup.name,
            onTogglePlaylist = detailsViewModel::togglePlaylistMembership,
            onCreatePlaylist = detailsViewModel::createPlaylist,
            onDismiss = { showPlaylistSheet = false },
        )
    }

    if (showSubtitleSheet) {
        SubtitlePickerSheet(
            uiState = subtitleUiState,
            initialQuery = cleanTitle,
            onQueryChange = subtitlePickerViewModel::onQueryChange,
            onSearch = subtitlePickerViewModel::searchOpenSubtitles,
            onDownload = subtitlePickerViewModel::downloadSubtitle,
            onPickLocal = {},
            onSubtitleSelected = { showSubtitleSheet = false },
            onDismiss = { showSubtitleSheet = false },
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Black),
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                // Header section with backdrop image & gradients
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                ) {
                    val imageUrl = meta?.backdropUrl() ?: meta?.posterUrl()
                    if (imageUrl != null) {
                        AsyncImage(
                            model = imageUrl,
                            contentDescription = cleanTitle,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize().background(Zinc800))
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Black),
                                ),
                            ),
                    )

                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .statusBarsPadding()
                            .padding(12.dp)
                            .align(Alignment.TopStart),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour", tint = White)
                    }
                }
            }

            item {
                // Title and badges
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val badgeText = when {
                            videoGroup?.isTvSeries == true -> "SÉRIE ORIGINALE"
                            videoGroup?.isSeriesGroup == true -> "SAGA / COLLECTION"
                            else -> "FILM"
                        }
                        Text(
                            text = badgeText,
                            color = Red600,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        meta?.releaseDate?.take(4)?.let { year ->
                            Text(text = "• $year", color = Zinc300, style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = cleanTitle,
                        color = White,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Black,
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Action buttons
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        val playLabel = when {
                            uiState.watchPositionMs > 0L -> {
                                val labelPart = uiState.activeEpisodeLabel?.let { "$it " } ?: ""
                                "REPRENDRE ${labelPart}À ${Formatters.formatDuration(uiState.watchPositionMs / 1000L)}"
                            }
                            !uiState.activeEpisodeLabel.isNullOrEmpty() -> {
                                "LECTURE ${uiState.activeEpisodeLabel}"
                            }
                            else -> "LECTURE"
                        }
                        Button(
                            onClick = { onPlayVideo(uiState.activeEpisodeName ?: videoGroup?.name ?: id) },
                            colors = ButtonDefaults.buttonColors(containerColor = White, contentColor = Color.Black),
                            shape = RoundedCornerShape(4.dp),
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Text(text = playLabel, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp))
                        }

                        Button(
                            onClick = detailsViewModel::toggleGroupWatched,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (uiState.isWatched) Red600 else Zinc800,
                                contentColor = White,
                            ),
                            shape = RoundedCornerShape(4.dp),
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = null)
                            Text(
                                text = if (uiState.isWatched) "VU" else "MARQUER VU",
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 4.dp),
                            )
                        }

                        Button(
                            onClick = { showPlaylistSheet = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Zinc800, contentColor = White),
                            shape = RoundedCornerShape(4.dp),
                        ) {
                            Icon(Icons.Filled.PlaylistAdd, contentDescription = null)
                            Text(text = "MA LISTE", modifier = Modifier.padding(start = 4.dp))
                        }

                        IconButton(onClick = detailsViewModel::refreshTmdbMetadata) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Recharger TMDB", tint = White)
                        }

                        IconButton(onClick = { showSubtitleSheet = true }) {
                            Icon(Icons.Filled.ClosedCaption, contentDescription = "Sous-titres", tint = White)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Synopsis
                    meta?.overview?.takeIf { it.isNotBlank() }?.let { overview ->
                        Column(modifier = Modifier.clickable { isSynopsisExpanded = !isSynopsisExpanded }) {
                            Text(
                                text = overview,
                                color = Zinc300,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = if (isSynopsisExpanded) Int.MAX_VALUE else 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = if (isSynopsisExpanded) "RÉDUIRE" else "LIRE LA SUITE",
                                color = Red600,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Tech metadata chips
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        videoGroup?.let { item ->
                            val res = Formatters.getResolution(item.name)
                            if (res.isNotBlank()) {
                                Text(
                                    text = res,
                                    color = White,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier
                                        .background(Red600, RoundedCornerShape(2.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                            val ext = item.name.substringAfterLast('.', "").uppercase()
                            if (ext.isNotBlank()) {
                                Text(
                                    text = ext,
                                    color = White,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier
                                        .background(Zinc800, RoundedCornerShape(2.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                            Text(
                                text = Formatters.formatSize(item.size),
                                color = Zinc300,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier
                                    .background(Zinc800, RoundedCornerShape(2.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            // Episodes Section if Series
            if (uiState.episodes.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Text(
                            text = "ÉPISODES",
                            color = White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // Seasons bar if multiple
                        if (uiState.availableSeasons.size > 1) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(bottom = 12.dp),
                            ) {
                                items(uiState.availableSeasons) { seasonNum ->
                                    FilterChip(
                                        selected = uiState.selectedSeason == seasonNum,
                                        onClick = { detailsViewModel.selectSeason(seasonNum) },
                                        label = { Text("Saison $seasonNum") },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Red600,
                                            selectedLabelColor = White,
                                            containerColor = Zinc800,
                                            labelColor = White,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }

                itemsIndexed(uiState.episodes, key = { _, ep -> ep.video.name }) { index, ep ->
                    EpisodeItemRow(
                        ep = ep,
                        seasonNum = uiState.selectedSeason,
                        epIndex = index,
                        onPlay = { onPlayVideo(ep.video.name) },
                        onToggleWatched = { detailsViewModel.toggleEpisodeWatched(ep.video.name) },
                        onToggleExpanded = { detailsViewModel.toggleEpisodeExpanded(ep.video.name) },
                        onResetProgress = { detailsViewModel.resetProgress(ep.video.name) },
                    )
                }
            }
        }
    }
}

@Suppress("FunctionNaming", "LongMethod", "LongParameterList", "CyclomaticComplexMethod")
@Composable
private fun EpisodeItemRow(
    ep: EpisodeUiState,
    seasonNum: Int,
    epIndex: Int,
    onPlay: () -> Unit,
    onToggleWatched: () -> Unit,
    onToggleExpanded: () -> Unit,
    onResetProgress: () -> Unit,
) {
    val epNum = ep.video.episode ?: ep.tmdbEpisode?.episodeNumber ?: (epIndex + 1)
    val epSeason = ep.video.season ?: seasonNum
    val epLabel = "S$epSeason:E$epNum"
    val epTitle = ep.tmdbEpisode?.name ?: TitleCleaner.getCleanTitle(ep.video.name)

    Card(
        colors = CardDefaults.cardColors(containerColor = Zinc900),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onToggleExpanded),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                // Episode thumbnail
                Box(
                    modifier = Modifier
                        .size(width = 100.dp, height = 56.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Zinc800)
                        .clickable(onClick = onPlay),
                ) {
                    val image = ep.tmdbEpisode?.stillUrl() ?: ep.fallbackImageUrl
                    if (image != null) {
                        AsyncImage(
                            model = image,
                            contentDescription = ep.video.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = "Lecture",
                        tint = White.copy(alpha = 0.9f),
                        modifier = Modifier
                            .size(24.dp)
                            .align(Alignment.Center)
                            .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .padding(2.dp),
                    )
                    if (ep.progressPercent > 0f) {
                        LinearProgressIndicator(
                            progress = { ep.progressPercent },
                            color = Red600,
                            trackColor = Color.Transparent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .align(Alignment.BottomCenter),
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "$epLabel • $epTitle",
                        color = White,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        when {
                            ep.isWatched -> {
                                Text(
                                    text = "Vu",
                                    color = WatchedGreen,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            ep.progressPercent > 0f -> {
                                Text(
                                    text = "En cours (${(ep.progressPercent * 100).toInt()}%)",
                                    color = Red600,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            else -> {
                                Text(
                                    text = "Non commencé",
                                    color = Zinc500,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        if (ep.durationMs > 0L) {
                            Text(
                                text = "• ${Formatters.formatDuration(ep.durationMs / 1000L)}",
                                color = Zinc300,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }

                IconButton(onClick = onToggleWatched) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = if (ep.isWatched) "Marquer non vu" else "Marquer vu",
                        tint = if (ep.isWatched) WatchedGreen else Zinc300.copy(alpha = 0.4f),
                    )
                }

                IconButton(onClick = onToggleExpanded) {
                    Icon(
                        if (ep.isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = "Détails de l'épisode",
                        tint = White,
                    )
                }
            }

            AnimatedVisibility(visible = ep.isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                ) {
                    ep.tmdbEpisode?.overview?.takeIf { it.isNotBlank() }?.let { overview ->
                        Text(text = overview, color = Zinc300, style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Button(
                        onClick = onPlay,
                        colors = ButtonDefaults.buttonColors(containerColor = Red600),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.padding(bottom = 6.dp),
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Lecture", tint = White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Lecture", color = White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text = "Fichier : ${ep.video.name}",
                        color = Zinc300.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Text(
                        text = "Taille : ${Formatters.formatSize(ep.video.size)}",
                        color = Zinc300.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelSmall,
                    )
                    if (ep.progressPercent > 0f) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Button(
                            onClick = onResetProgress,
                            colors = ButtonDefaults.buttonColors(containerColor = Zinc800),
                            shape = RoundedCornerShape(4.dp),
                        ) {
                            Text("Réinitialiser la progression", color = Red600, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}
