package com.localstream.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.localstream.app.LocalStreamApplication
import com.localstream.app.domain.model.PlaylistInfo
import com.localstream.app.ui.components.TopBar
import com.localstream.app.ui.components.VideoCard
import com.localstream.app.ui.playlists.PlaylistsViewModel
import com.localstream.app.ui.theme.Black
import com.localstream.app.ui.theme.Red600
import com.localstream.app.ui.theme.White
import com.localstream.app.ui.theme.Zinc800
import com.localstream.app.ui.theme.Zinc900

@Suppress("FunctionNaming", "LongMethod")
@Composable
fun PlaylistsScreen(
    onLogoClick: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onOpenDetails: (String) -> Unit = {},
    onYouTubeClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val container = (context.applicationContext as LocalStreamApplication).container
    val viewModel: PlaylistsViewModel = viewModel(factory = PlaylistsViewModel.factory(container))
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var showCreateDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            containerColor = Zinc900,
            title = { Text("Nouvelle Playlist", color = White, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    placeholder = { Text("Nom de la playlist", color = White.copy(alpha = 0.5f)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = White,
                        unfocusedTextColor = White,
                        focusedBorderColor = Red600,
                        unfocusedBorderColor = White.copy(alpha = 0.3f),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPlaylistName.isNotBlank()) {
                            viewModel.createPlaylist(newPlaylistName)
                            newPlaylistName = ""
                            showCreateDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Red600),
                ) {
                    Text("Créer", color = White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Annuler", color = White)
                }
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Black),
    ) {
        TopBar(
            solid = true,
            showSearch = true,
            isFetchingMetadata = false,
            onLogoClick = onLogoClick,
            onSearchClick = onSearchClick,
            onSettingsClick = onSettingsClick,
            onYouTubeClick = onYouTubeClick,
        )

        val selected = uiState.selectedPlaylist
        if (selected != null) {
            // Detailed playlist view
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                IconButton(onClick = { viewModel.selectPlaylist(null) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour", tint = White)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = selected.name,
                        color = White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "${selected.videoNames.size} vidéo(s)",
                        color = White.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Button(
                    onClick = {
                        uiState.selectedPlaylistVideos.firstOrNull()?.let { onOpenDetails(it.name) }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = White, contentColor = Black),
                    shape = RoundedCornerShape(4.dp),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Text("Tout lire", fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp))
                }
                IconButton(onClick = { viewModel.deletePlaylist(selected.id) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Supprimer", tint = Red600)
                }
            }

            if (uiState.selectedPlaylistVideos.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Cette playlist est vide.", color = White.copy(alpha = 0.6f))
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 110.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(16.dp),
                ) {
                    items(uiState.selectedPlaylistVideos, key = { it.name }) { video ->
                        val meta = uiState.metadata[video.name]
                        VideoCard(
                            video = video,
                            posterUrl = meta?.posterUrl(),
                            isWatched = false,
                            progress = 0.0,
                            showResetProgress = false,
                            onClick = { onOpenDetails(video.name) },
                            onResetProgress = {},
                        )
                    }
                }
            }
        } else {
            // List of playlists view
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    text = "Vos Listes",
                    color = White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Button(
                    onClick = { showCreateDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Red600),
                    shape = RoundedCornerShape(4.dp),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text("Nouvelle liste", modifier = Modifier.padding(start = 4.dp))
                }
            }

            if (uiState.playlists.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Aucune liste créée pour le moment.", color = White.copy(alpha = 0.6f))
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 150.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(16.dp),
                ) {
                    items(uiState.playlists, key = { it.id }) { playlist ->
                        PlaylistCard(
                            playlist = playlist,
                            onClick = { viewModel.selectPlaylist(playlist.id) },
                        )
                    }
                }
            }
        }
    }
}

@Suppress("FunctionNaming", "LongMethod")
@Composable
private fun PlaylistCard(
    playlist: PlaylistInfo,
    onClick: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Zinc900),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .background(Zinc800, RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.PlaylistPlay,
                    contentDescription = null,
                    tint = Red600,
                    modifier = Modifier.height(40.dp),
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = playlist.name,
                color = White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${playlist.videoNames.size} vidéo(s)",
                color = White.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
