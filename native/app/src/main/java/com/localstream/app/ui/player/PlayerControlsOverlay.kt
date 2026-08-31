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

@Suppress("LongParameterList")
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
    hasEpisodes: Boolean = false,
    sleepTimerActive: Boolean = false,
    isQuickSpeedActive: Boolean = false,
    onBack: () -> Unit,
    onOpenTracks: () -> Unit,
    onOpenEpisodes: () -> Unit = {},
    onOpenSleepTimer: () -> Unit = {},
    onSkipIntro: () -> Unit = {},
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
    AnimatedVisibility(
        visible = isVisible && !isLocked,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier.fillMaxSize(),
    ) {
        // Collectée ici, dans le contenu visible : hors écran, l'overlay
        // ne se recompose plus à chaque tick de position (4x/seconde).
        val currentPosition = if (positionMsFlow != null) {
            val pos by positionMsFlow.collectAsStateWithLifecycle()
            pos
        } else {
            positionMs
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f)),
        ) {
            TopPlayerBar(
                title = title,
                aspectRatioMode = aspectRatioMode,
                playbackSpeed = playbackSpeed,
                hasEpisodes = hasEpisodes,
                sleepTimerActive = sleepTimerActive,
                isQuickSpeedActive = isQuickSpeedActive,
                onBack = onBack,
                onOpenTracks = onOpenTracks,
                onOpenEpisodes = onOpenEpisodes,
                onOpenSleepTimer = onOpenSleepTimer,
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
                onSkipIntro = onSkipIntro,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}
