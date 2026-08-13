package com.localstream.app.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localstream.app.ui.theme.Red600
import com.localstream.app.ui.theme.White
import com.localstream.app.ui.theme.Zinc800
import com.localstream.app.ui.theme.Zinc900

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TracksSelectionSheet(
    audioTracks: List<AudioTrackUiState>,
    subtitleTracks: List<SubtitleTrackUiState>,
    onSelectAudio: (String) -> Unit,
    onSelectSubtitle: (String?) -> Unit,
    onPickSubtitleFile: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Zinc900,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
        ) {
            Text("Pistes Audio", color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            if (audioTracks.isEmpty()) {
                Text("Aucune piste audio détectée", color = Color.Gray, fontSize = 14.sp)
            } else {
                audioTracks.forEach { track ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectAudio(track.id) }
                            .padding(vertical = 6.dp),
                    ) {
                        RadioButton(
                            selected = track.isSelected,
                            onClick = { onSelectAudio(track.id) },
                            colors = RadioButtonDefaults.colors(selectedColor = Red600),
                        )
                        Text(track.label, color = White, fontSize = 14.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            Text("Sous-titres", color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectSubtitle(null) }
                    .padding(vertical = 6.dp),
            ) {
                RadioButton(
                    selected = subtitleTracks.none { it.isSelected },
                    onClick = { onSelectSubtitle(null) },
                    colors = RadioButtonDefaults.colors(selectedColor = Red600),
                )
                Text("Désactivés", color = White, fontSize = 14.sp)
            }

            subtitleTracks.forEach { track ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectSubtitle(track.id) }
                        .padding(vertical = 6.dp),
                ) {
                    RadioButton(
                        selected = track.isSelected,
                        onClick = { onSelectSubtitle(track.id) },
                        colors = RadioButtonDefaults.colors(selectedColor = Red600),
                    )
                    Text(
                        text = if (track.isExternal) "${track.label} (Fichier externe)" else track.label,
                        color = White,
                        fontSize = 14.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onPickSubtitleFile,
                colors = ButtonDefaults.buttonColors(containerColor = Zinc800),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = White)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Importer un fichier .srt", color = White)
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
