package com.localstream.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.localstream.app.domain.model.PlaylistInfo
import com.localstream.app.ui.theme.Red600
import com.localstream.app.ui.theme.White
import com.localstream.app.ui.theme.Zinc900

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("FunctionNaming", "LongMethod")
@Composable
fun PlaylistBottomSheet(
    userPlaylists: List<PlaylistInfo>,
    currentVideoName: String,
    onTogglePlaylist: (String) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var newPlaylistName by remember { mutableStateOf("") }
    var isCreating by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Zinc900,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Text(
                text = "Ajouter à une liste",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = White,
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (userPlaylists.isEmpty()) {
                Text(
                    text = "Aucune liste créée.",
                    color = White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.height((userPlaylists.size * 56).coerceAtMost(240).dp),
                ) {
                    items(userPlaylists, key = { it.id }) { playlist ->
                        val inPlaylist = playlist.videoNames.contains(currentVideoName)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onTogglePlaylist(playlist.id) }
                                .padding(vertical = 4.dp),
                        ) {
                            Checkbox(
                                checked = inPlaylist,
                                onCheckedChange = { onTogglePlaylist(playlist.id) },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = Red600,
                                    uncheckedColor = White.copy(alpha = 0.5f),
                                ),
                            )
                            Text(
                                text = playlist.name,
                                color = White,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isCreating) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
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
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = {
                            if (newPlaylistName.isNotBlank()) {
                                onCreatePlaylist(newPlaylistName)
                                newPlaylistName = ""
                                isCreating = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Red600),
                        shape = RoundedCornerShape(4.dp),
                    ) {
                        Text("Créer", color = White)
                    }
                }
            } else {
                Button(
                    onClick = { isCreating = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Red600.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = Red600)
                    Text("Nouvelle playlist", color = Red600, modifier = Modifier.padding(start = 8.dp))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
