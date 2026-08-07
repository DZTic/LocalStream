package com.localstream.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
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
import com.localstream.app.ui.components.TopBar
import com.localstream.app.ui.history.HistoryItemUiState
import com.localstream.app.ui.history.HistoryViewModel
import com.localstream.app.ui.theme.Black
import com.localstream.app.ui.theme.Red600
import com.localstream.app.ui.theme.White
import com.localstream.app.ui.theme.Zinc800
import com.localstream.app.ui.theme.Zinc900

@Suppress("FunctionNaming", "LongMethod")
@Composable
fun HistoryScreen(
    onLogoClick: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onOpenDetails: (String) -> Unit = {},
    onYouTubeClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val container = (context.applicationContext as LocalStreamApplication).container
    val viewModel: HistoryViewModel = viewModel(factory = HistoryViewModel.factory(container))
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var showManualAddDialog by remember { mutableStateOf(false) }
    var manualTitle by remember { mutableStateOf("") }

    if (showManualAddDialog) {
        AlertDialog(
            onDismissRequest = { showManualAddDialog = false },
            containerColor = Zinc900,
            title = { Text("Ajouter au vu", color = White, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = manualTitle,
                    onValueChange = { manualTitle = it },
                    placeholder = { Text("Titre du film ou de la série", color = White.copy(alpha = 0.5f)) },
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
                        if (manualTitle.isNotBlank()) {
                            viewModel.addManualTitle(manualTitle)
                            manualTitle = ""
                            showManualAddDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Red600),
                ) {
                    Text("Ajouter", color = White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualAddDialog = false }) {
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

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                text = "Historique",
                color = White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { showManualAddDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Red600),
                    shape = RoundedCornerShape(4.dp),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text("Ajouter", modifier = Modifier.padding(start = 4.dp))
                }

                if (uiState.items.isNotEmpty()) {
                    IconButton(onClick = viewModel::clearHistory) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = "Vider l'historique", tint = White)
                    }
                }
            }
        }

        if (uiState.items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Aucun élément dans l'historique.", color = White.copy(alpha = 0.6f))
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 120.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(16.dp),
            ) {
                items(uiState.items, key = { it.videoName }) { item ->
                    HistoryItemCard(
                        item = item,
                        onClick = { onOpenDetails(item.videoName) },
                        onRemove = { viewModel.removeFromHistory(item.videoName) },
                        onToggleForceAvailable = { viewModel.toggleForceAvailable(item.videoName) },
                    )
                }
            }
        }
    }
}

@Suppress("FunctionNaming", "LongMethod")
@Composable
private fun HistoryItemCard(
    item: HistoryItemUiState,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onToggleForceAvailable: () -> Unit,
) {
    val alpha = if (item.isAvailableOnDisk) 1f else 0.5f

    Card(
        colors = CardDefaults.cardColors(containerColor = Zinc900),
        modifier = Modifier
            .fillMaxWidth()
            .alpha(alpha)
            .clickable(onClick = onClick),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                    .background(Zinc800),
            ) {
                val posterUrl = item.metadata?.posterUrl()
                if (posterUrl != null) {
                    AsyncImage(
                        model = posterUrl,
                        contentDescription = item.cleanTitle,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                if (!item.isAvailableOnDisk) {
                    Text(
                        text = "Non disponible",
                        color = White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier
                            .background(Red600.copy(alpha = 0.85f))
                            .align(Alignment.TopStart)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                if (item.progressPercent > 0f) {
                    LinearProgressIndicator(
                        progress = { item.progressPercent },
                        color = Red600,
                        trackColor = Color.Transparent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .align(Alignment.BottomCenter),
                    )
                }
            }

            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = item.cleanTitle,
                    color = White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                ) {
                    IconButton(onClick = onToggleForceAvailable, modifier = Modifier.size(28.dp)) {
                        Icon(
                            if (item.isForceAvailable) Icons.Filled.Cloud else Icons.Filled.CloudOff,
                            contentDescription = "Disponibilité",
                            tint = if (item.isForceAvailable) Red600 else White.copy(alpha = 0.5f),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Supprimer",
                            tint = White.copy(alpha = 0.5f),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}
