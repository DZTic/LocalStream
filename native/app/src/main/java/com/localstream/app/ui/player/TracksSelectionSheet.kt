package com.localstream.app.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localstream.app.ui.theme.AppIcons
import com.localstream.app.ui.theme.Red600
import com.localstream.app.ui.theme.White
import com.localstream.app.ui.theme.Zinc800
import com.localstream.app.ui.theme.Zinc900

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongMethod", "LongParameterList")
@Composable
fun TracksSelectionSheet(
    audioTracks: List<AudioTrackUiState>,
    subtitleTracks: List<SubtitleTrackUiState>,
    subtitleOffsetMs: Long = 0L,
    isAudioBoostEnabled: Boolean = false,
    onSelectAudio: (String) -> Unit,
    onSelectSubtitle: (String?) -> Unit,
    onAdjustSubtitleOffset: (Long) -> Unit = {},
    onResetSubtitleOffset: () -> Unit = {},
    onToggleAudioBoost: (Boolean) -> Unit = {},
    onPickSubtitleFile: () -> Unit,
    onOpenOnlineSubtitles: () -> Unit = {},
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
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text("Pistes Audio & Volume", color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Boost de volume / Clarté", color = White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("Amplifie les dialogues faibles", color = Color.Gray, fontSize = 12.sp)
                }
                Switch(
                    checked = isAudioBoostEnabled,
                    onCheckedChange = onToggleAudioBoost,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = White,
                        checkedTrackColor = Red600,
                        uncheckedThumbColor = Color.Gray,
                        uncheckedTrackColor = Zinc800,
                    ),
                )
            }

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

            Text("Synchronisation sous-titres", color = White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedButton(
                    onClick = { onAdjustSubtitleOffset(-500L) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = White, containerColor = Zinc800),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("-0.5s", fontSize = 11.sp)
                }
                OutlinedButton(
                    onClick = { onAdjustSubtitleOffset(-100L) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = White, containerColor = Zinc800),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("-0.1s", fontSize = 11.sp)
                }
                Text(
                    text = "${if (subtitleOffsetMs >= 0) "+$subtitleOffsetMs" else "$subtitleOffsetMs"}ms",
                    color = Red600,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable(enabled = subtitleOffsetMs != 0L, onClick = onResetSubtitleOffset)
                        .padding(horizontal = 4.dp),
                )
                OutlinedButton(
                    onClick = { onAdjustSubtitleOffset(100L) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = White, containerColor = Zinc800),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("+0.1s", fontSize = 11.sp)
                }
                OutlinedButton(
                    onClick = { onAdjustSubtitleOffset(500L) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = White, containerColor = Zinc800),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("+0.5s", fontSize = 11.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Button(
                    onClick = onPickSubtitleFile,
                    colors = ButtonDefaults.buttonColors(containerColor = Zinc800),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Fichier local", color = White, fontSize = 12.sp)
                }

                Button(
                    onClick = onOpenOnlineSubtitles,
                    colors = ButtonDefaults.buttonColors(containerColor = Red600),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(AppIcons.CloudDownload, contentDescription = null, tint = White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("OpenSubtitles", color = White, fontSize = 12.sp)
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

