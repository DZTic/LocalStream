package com.localstream.app.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow

@Composable
fun PlayerControlsOverlay(
    isVisible: Boolean,
    isLocked: Boolean,
    title: String,
    aspectRatioMode: AspectRatioMode,
    playbackSpeed: Float,
    isPlaying: Boolean,
    hasNextVideo: Boolean,
    durationMs: Long,
    onBack: () -> Unit,
    onOpenTracks: () -> Unit,
    onCycleAspect: () -> Unit,
    onCycleSpeed: () -> Unit,
    onTogglePlay: () -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit,
    onNextVideo: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleLock: () -> Unit,
    onEnterPip: () -> Unit,
    modifier: Modifier = Modifier,
    positionMs: Long = 0L,
    positionMsFlow: StateFlow<Long>? = null,
) {
    val currentPosition = if (positionMsFlow != null) {
        val pos by positionMsFlow.collectAsStateWithLifecycle()
        pos
    } else {
        positionMs
    }

    AnimatedVisibility(
        visible = isVisible && !isLocked,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f)),
        ) {
            TopPlayerBar(
                title = title,
                aspectRatioMode = aspectRatioMode,
                playbackSpeed = playbackSpeed,
                onBack = onBack,
                onOpenTracks = onOpenTracks,
                onCycleAspect = onCycleAspect,
                onCycleSpeed = onCycleSpeed,
                modifier = Modifier.align(Alignment.TopCenter),
            )

            CenterPlayerControls(
                isPlaying = isPlaying,
                hasNextVideo = hasNextVideo,
                onTogglePlay = onTogglePlay,
                onRewind = onRewind,
                onForward = onForward,
                onNextVideo = onNextVideo,
                modifier = Modifier.align(Alignment.Center),
            )

            BottomPlayerBar(
                positionMs = currentPosition,
                durationMs = durationMs,
                isLocked = isLocked,
                onSeek = onSeek,
                onToggleLock = onToggleLock,
                onEnterPip = onEnterPip,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}
