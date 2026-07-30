package com.localstream.app.ui.subtitles

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.localstream.app.domain.model.SubtitleInfo
import com.localstream.app.ui.theme.Red600
import com.localstream.app.ui.theme.White
import com.localstream.app.ui.theme.Zinc800
import com.localstream.app.ui.theme.Zinc900
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("FunctionNaming", "LongMethod", "LongParameterList")
@Composable
fun SubtitlePickerSheet(
    uiState: SubtitlePickerUiState,
    initialQuery: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onDownload: (String, (File) -> Unit) -> Unit,
    onPickLocal: () -> Unit,
    onSubtitleSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Zinc900,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Text(
                text = "Sous-titres",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = White,
            )
            Spacer(modifier = Modifier.height(12.dp))

            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Zinc900,
                contentColor = White,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = Red600,
                    )
                },
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("OpenSubtitles", color = if (selectedTab == 0) Red600 else White) },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Local / SAF", color = if (selectedTab == 1) Red600 else White) },
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (selectedTab == 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedTextField(
                        value = uiState.searchQuery.ifBlank { initialQuery },
                        onValueChange = onQueryChange,
                        placeholder = { Text("Titre de la vidéo", color = White.copy(alpha = 0.5f)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = White,
                            unfocusedTextColor = White,
                            focusedBorderColor = Red600,
                            unfocusedBorderColor = White.copy(alpha = 0.3f),
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = { onSearch(uiState.searchQuery.ifBlank { initialQuery }) },
                    ) {
                        Icon(Icons.Filled.Search, contentDescription = "Rechercher", tint = Red600)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (uiState.isSearching || uiState.isDownloading) {
                    Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Red600)
                    }
                } else if (uiState.error != null) {
                    Text(text = uiState.error, color = Red600, style = MaterialTheme.typography.bodyMedium)
                } else if (uiState.searchResults.isEmpty()) {
                    Text(
                        text = "Aucun sous-titre trouvé.",
                        color = White.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.height(260.dp),
                    ) {
                        items(uiState.searchResults, key = { it.id }) { item ->
                            SubtitleItemRow(item = item, onDownload = {
                                onDownload(item.id) { file ->
                                    onSubtitleSelected(file.absolutePath)
                                    onDismiss()
                                }
                            })
                        }
                    }
                }
            } else {
                Button(
                    onClick = onPickLocal,
                    colors = ButtonDefaults.buttonColors(containerColor = Zinc800),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                ) {
                    Icon(Icons.Filled.FolderOpen, contentDescription = null, tint = Red600)
                    Text("Sélectionner un fichier (.srt, .vtt)", color = White, modifier = Modifier.padding(start = 8.dp))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Suppress("FunctionNaming", "LongMethod")
@Composable
private fun SubtitleItemRow(
    item: SubtitleInfo,
    onDownload: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onDownload)
            .padding(vertical = 8.dp, horizontal = 4.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = item.filename, color = White, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(text = "Langue : ${item.language.uppercase()}", color = White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
        }
        IconButton(onClick = onDownload) {
            Icon(Icons.Filled.CloudDownload, contentDescription = "Télécharger", tint = Red600)
        }
    }
}
