package com.localstream.app.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localstream.app.ui.theme.AppIcons
import com.localstream.app.ui.theme.Red600
import com.localstream.app.ui.theme.White
import com.localstream.app.ui.theme.Zinc800
import java.util.Locale

internal fun formatTimeMs(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

@Suppress("LongParameterList")
@Composable
fun BottomPlayerBar(
    positionMs: Long,
    durationMs: Long,
    isLocked: Boolean,
    onSeek: (Long) -> Unit,
    onToggleLock: () -> Unit,
    onEnterPip: () -> Unit,
    onSkipIntro: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var isSeeking by remember { mutableStateOf(false) }
    var seekPositionMs by remember { mutableLongStateOf(0L) }
    var showRemainingTime by remember { mutableStateOf(false) }

    val displayPos = if (isSeeking) seekPositionMs else positionMs

    val timeLabel by remember(displayPos, durationMs, showRemainingTime) {
        derivedStateOf {
            if (showRemainingTime && durationMs > 0L) {
                val remaining = (durationMs - displayPos).coerceAtLeast(0L)
                "${formatTimeMs(displayPos)} (-${formatTimeMs(remaining)})"
            } else {
                "${formatTimeMs(displayPos)} / ${formatTimeMs(durationMs)}"
            }
        }
    }

    val sliderValue by remember(displayPos, durationMs) {
        derivedStateOf {
            if (durationMs > 0L) {
                displayPos.coerceIn(0L, durationMs).toFloat()
            } else {
                0f
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = timeLabel,
                color = White,
                fontSize = 12.sp,
                modifier = Modifier
                    .padding(4.dp)
                    .clickable { showRemainingTime = !showRemainingTime },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onSkipIntro != null) {
                    TextButton(
                        onClick = onSkipIntro,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text("+85s Intro", color = Red600, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                IconButton(onClick = onEnterPip) {
                    Icon(AppIcons.PictureInPicture, contentDescription = "PiP", tint = White)
                }
                IconButton(onClick = onToggleLock) {
                    Icon(
                        imageVector = if (isLocked) Icons.Filled.Lock else AppIcons.LockOpen,
                        contentDescription = "Verrouiller",
                        tint = White,
                    )
                }
            }
        }

        Slider(
            value = sliderValue,
            onValueChange = {
                if (durationMs > 0L) {
                    isSeeking = true
                    seekPositionMs = it.toLong()
                }
            },
            onValueChangeFinished = {
                if (durationMs > 0L) {
                    onSeek(seekPositionMs)
                    isSeeking = false
                }
            },
            valueRange = 0f..durationMs.coerceAtLeast(1L).toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = Red600,
                activeTrackColor = Red600,
                inactiveTrackColor = Zinc800,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

