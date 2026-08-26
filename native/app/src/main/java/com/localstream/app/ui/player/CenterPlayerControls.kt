package com.localstream.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import com.localstream.app.ui.theme.AppIcons
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.localstream.app.ui.theme.Red600
import com.localstream.app.ui.theme.White

@Composable
fun CenterPlayerControls(
    isPlaying: Boolean,
    hasNextVideo: Boolean,
    onTogglePlay: () -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit,
    onNextVideo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        IconButton(onClick = onRewind, modifier = Modifier.size(48.dp)) {
            Icon(AppIcons.Replay10, contentDescription = "Reculer 10s", tint = White, modifier = Modifier.size(36.dp))
        }

        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Red600)
                .clickable(onClick = onTogglePlay),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isPlaying) AppIcons.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Lecture",
                tint = White,
                modifier = Modifier.size(40.dp),
            )
        }

        IconButton(onClick = onForward, modifier = Modifier.size(48.dp)) {
            Icon(AppIcons.Forward10, contentDescription = "Avancer 10s", tint = White, modifier = Modifier.size(36.dp))
        }

        if (hasNextVideo) {
            IconButton(onClick = onNextVideo, modifier = Modifier.size(48.dp)) {
                Icon(AppIcons.SkipNext, contentDescription = "Épisode suivant", tint = White, modifier = Modifier.size(36.dp))
            }
        }
    }
}
