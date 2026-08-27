package com.localstream.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localstream.app.ui.theme.AppIcons
import com.localstream.app.ui.theme.Red600
import com.localstream.app.ui.theme.White
import com.localstream.app.ui.theme.Zinc800
import com.localstream.app.ui.theme.Zinc900

@Suppress("LongParameterList")
@Composable
fun TopPlayerBar(
    title: String,
    aspectRatioMode: AspectRatioMode,
    playbackSpeed: Float,
    hasEpisodes: Boolean = false,
    sleepTimerActive: Boolean = false,
    isQuickSpeedActive: Boolean = false,
    onBack: () -> Unit,
    onOpenTracks: () -> Unit,
    onOpenEpisodes: () -> Unit = {},
    onOpenSleepTimer: () -> Unit = {},
    onCycleAspect: () -> Unit,
    onCycleSpeed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour", tint = White)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                color = White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (isQuickSpeedActive) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Zinc900.copy(alpha = 0.9f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "2.0x >>",
                    color = Red600,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (hasEpisodes) {
                IconButton(onClick = onOpenEpisodes) {
                    Icon(AppIcons.Tv, contentDescription = "Liste des épisodes", tint = White)
                }
            }
            IconButton(onClick = onOpenSleepTimer) {
                Icon(
                    imageVector = AppIcons.AccessTime,
                    contentDescription = "Minuteur de veille",
                    tint = if (sleepTimerActive) Red600 else White,
                )
            }
            IconButton(onClick = onOpenTracks) {
                Icon(AppIcons.Subtitles, contentDescription = "Sous-titres & Audio", tint = White)
            }
            IconButton(onClick = onCycleAspect) {
                Icon(AppIcons.AspectRatio, contentDescription = "Format : ${aspectRatioMode.label}", tint = White)
            }
            Button(
                onClick = onCycleSpeed,
                colors = ButtonDefaults.buttonColors(containerColor = Zinc800),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Icon(AppIcons.Speed, contentDescription = null, tint = White, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("${playbackSpeed}x", color = White, fontSize = 12.sp)
            }
        }
    }
}

