@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.media3.common.util.UnstableApi::class,
)

package com.localstream.app.ui.screens

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.util.Rational
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.localstream.app.LocalStreamApplication
import com.localstream.app.domain.model.VideoItem
import com.localstream.app.ui.player.AspectRatioMode
import com.localstream.app.ui.player.AudioTrackUiState
import com.localstream.app.ui.player.FeedbackType
import com.localstream.app.ui.player.GestureFeedback
import com.localstream.app.ui.player.PlayerViewModel
import com.localstream.app.ui.player.SubtitleTrackUiState
import com.localstream.app.ui.theme.Black
import com.localstream.app.ui.theme.Red600
import com.localstream.app.ui.theme.White
import com.localstream.app.ui.theme.Zinc800
import com.localstream.app.ui.theme.Zinc900
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private const val CONTROLS_TIMEOUT_MS = 3500L
private const val FEEDBACK_TIMEOUT_MS = 1500L


@Suppress("LongMethod", "CyclomaticComplexMethod", "TooManyFunctions")
@Composable
fun PlayerScreen(
    videoName: String,
    onBack: () -> Unit,
    viewModel: PlayerViewModel = run {
        val container = (LocalContext.current.applicationContext as LocalStreamApplication).container
        viewModel(factory = PlayerViewModel.factory(videoName, container))
    },
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }
    val lifecycleOwner = LocalLifecycleOwner.current

    // Immersive Landscape mode
    DisposableEffect(activity) {
        val window = activity?.window
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        if (window != null) {
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Keep screen turned on while playing
    DisposableEffect(activity, uiState.isPlaying) {
        val window = activity?.window
        if (window != null && uiState.isPlaying) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    var isVolumeInitialized by remember { mutableStateOf(false) }

    // Init volume from system AudioManager
    LaunchedEffect(Unit) {
        audioManager?.let { am ->
            val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            if (maxVol > 0) {
                val curVol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                val curPct = ((curVol.toFloat() / maxVol) * 100).roundToInt().coerceIn(0, 100)
                viewModel.setInitialVolumePercent(curPct)
            }
        }
        isVolumeInitialized = true
    }

    // Sync volume to AudioManager
    LaunchedEffect(uiState.volumePercent, isVolumeInitialized) {
        if (!isVolumeInitialized) return@LaunchedEffect
        audioManager?.let { am ->
            val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            if (maxVol > 0) {
                val targetVol = ((uiState.volumePercent / 100f) * maxVol).roundToInt().coerceIn(0, maxVol)
                am.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
            }
        }
    }

    // External player launcher if selected in settings
    LaunchedEffect(uiState.playerMode, uiState.currentVideo) {
        val video = uiState.currentVideo
        if (uiState.playerMode == "external" && video != null) {
            launchExternalPlayer(context, video, uiState.selectedExternalPlayer)
            onBack()
        }
    }

    var showTracksSheet by remember { mutableStateOf(false) }

    // SAF picker launcher for external .srt subtitle files
    val subtitleFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val fileName = it.lastPathSegment?.substringAfterLast('/') ?: "Sous-titre"
            viewModel.addExternalSubtitle(fileName, it.toString())
        }
    }

    // Controls overlay auto-hide delay
    LaunchedEffect(uiState.isControlsVisible, uiState.isPlaying, uiState.isLocked) {
        if (uiState.isControlsVisible && uiState.isPlaying && !uiState.isLocked) {
            delay(CONTROLS_TIMEOUT_MS)
            viewModel.setControlsVisible(false)
        }
    }

    // Clear gesture feedback card
    LaunchedEffect(uiState.gestureFeedback) {
        if (uiState.gestureFeedback != null) {
            delay(FEEDBACK_TIMEOUT_MS)
            viewModel.clearGestureFeedback()
        }
    }

    // Window brightness control
    LaunchedEffect(uiState.brightnessPercent) {
        val brightness = uiState.brightnessPercent
        if (brightness >= 0f) {
            activity?.window?.attributes = activity?.window?.attributes?.apply {
                screenBrightness = brightness
            }
        }
    }
    DisposableEffect(activity) {
        onDispose {
            activity?.window?.attributes = activity?.window?.attributes?.apply {
                screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
        }
    }

    // ExoPlayer creation & AudioAttributes
    val exoPlayer = remember(context) {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()
        ExoPlayer.Builder(context)
            .setAudioAttributes(audioAttributes, true)
            .build()
    }

    // Pause on background lifecycle event
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                exoPlayer.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var isPlayerReady by remember { mutableStateOf(false) }

    // ExoPlayer event listeners
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_BUFFERING -> viewModel.setBuffering(true)
                    Player.STATE_READY -> {
                        viewModel.setBuffering(false)
                        viewModel.setErrorMessage(null)
                        isPlayerReady = true
                    }
                    Player.STATE_ENDED -> {
                        viewModel.setBuffering(false)
                        viewModel.onVideoEnded()
                    }
                    else -> viewModel.setBuffering(false)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                viewModel.setBuffering(false)
                viewModel.setErrorMessage(error.localizedMessage ?: "Erreur de lecture de la vidéo")
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                viewModel.onPlayingStateChanged(isPlaying)
            }

            override fun onTracksChanged(tracks: Tracks) {
                val audioList = mutableListOf<AudioTrackUiState>()
                val subList = mutableListOf<SubtitleTrackUiState>()

                for (group in tracks.groups) {
                    val trackType = group.type
                    for (i in 0 until group.length) {
                        val format = group.getTrackFormat(i)
                        val id = format.id ?: "$trackType-$i"
                        val label = format.label ?: format.language ?: "Piste ${i + 1}"
                        val isSelected = group.isTrackSelected(i)

                        if (trackType == C.TRACK_TYPE_AUDIO) {
                            audioList.add(
                                AudioTrackUiState(
                                    id = id,
                                    label = label,
                                    language = format.language,
                                    isSelected = isSelected,
                                )
                            )
                        } else if (trackType == C.TRACK_TYPE_TEXT) {
                            subList.add(
                                SubtitleTrackUiState(
                                    id = id,
                                    label = label,
                                    language = format.language,
                                    isSelected = isSelected,
                                )
                            )
                        }
                    }
                }
                viewModel.updateTracks(audioList, subList)
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            exoPlayer.release()
        }
    }

    // Apply track selections to ExoPlayer
    LaunchedEffect(uiState.selectedAudioTrackId, uiState.selectedSubtitleTrackId, exoPlayer.currentTracks) {
        val tracks = exoPlayer.currentTracks
        val builder = exoPlayer.trackSelectionParameters.buildUpon()

        // Audio Track
        val selAudioId = uiState.selectedAudioTrackId
        if (selAudioId != null) {
            for (group in tracks.groups) {
                if (group.type == C.TRACK_TYPE_AUDIO) {
                    for (i in 0 until group.length) {
                        val format = group.getTrackFormat(i)
                        val id = format.id ?: "${group.type}-$i"
                        if (id == selAudioId) {
                            builder.setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, i))
                            break
                        }
                    }
                }
            }
        }

        // Subtitle Track
        val selSubId = uiState.selectedSubtitleTrackId
        if (selSubId == null) {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        } else {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            for (group in tracks.groups) {
                if (group.type == C.TRACK_TYPE_TEXT) {
                    for (i in 0 until group.length) {
                        val format = group.getTrackFormat(i)
                        val id = format.id ?: "${group.type}-$i"
                        if (id == selSubId) {
                            builder.setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, i))
                            break
                        }
                    }
                }
            }
        }

        exoPlayer.trackSelectionParameters = builder.build()
    }

    // Handle imported external subtitle file (.srt)
    LaunchedEffect(uiState.subtitleTracks) {
        val externalTrack = uiState.subtitleTracks.firstOrNull { it.isExternal && it.isSelected && !it.uriString.isNullOrEmpty() }
        if (externalTrack != null) {
            val currentVideo = uiState.currentVideo ?: return@LaunchedEffect
            val uri = extractUri(currentVideo) ?: return@LaunchedEffect

            val subUri = Uri.parse(externalTrack.uriString)
            val subConfig = MediaItem.SubtitleConfiguration.Builder(subUri)
                .setMimeType(MimeTypes.APPLICATION_SUBRIP)
                .setLanguage("fr")
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()

            val curPos = exoPlayer.currentPosition
            val mediaItem = MediaItem.Builder()
                .setUri(uri)
                .setSubtitleConfigurations(listOf(subConfig))
                .build()

            exoPlayer.setMediaItem(mediaItem, curPos)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        }
    }

    // Load media source into ExoPlayer
    LaunchedEffect(uiState.currentVideo) {
        val video = uiState.currentVideo ?: return@LaunchedEffect
        val uri = extractUri(video) ?: return@LaunchedEffect

        if (isYouTubeUrl(uri.toString()) || isYouTubeUrl(video.name) || isYouTubeUrl(video.url)) {
            runCatching {
                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }.onFailure {
                viewModel.setErrorMessage("Impossible d'ouvrir le lien YouTube")
            }
            return@LaunchedEffect
        }

        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        isPlayerReady = false
        val mediaItem = MediaItem.fromUri(uri)
        val startPos = uiState.initialPositionMs
        if (startPos > 0L) {
            exoPlayer.setMediaItem(mediaItem, startPos)
        } else {
            exoPlayer.setMediaItem(mediaItem)
        }
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    // Continuously update position flow
    LaunchedEffect(exoPlayer) {
        while (true) {
            if (exoPlayer.isPlaying && isPlayerReady) {
                viewModel.onPositionChanged(
                    positionMs = exoPlayer.currentPosition,
                    durationMs = exoPlayer.duration.coerceAtLeast(0L),
                )
            }
            delay(500L)
        }
    }

    // Apply playback speed changes
    LaunchedEffect(uiState.playbackSpeed) {
        exoPlayer.setPlaybackSpeed(uiState.playbackSpeed)
    }

    BackHandler {
        onBack()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
            .pointerInput(Unit) {
                detectPlayerGestures(
                    PlayerGestureCallbacks(
                        onTap = {
                            viewModel.toggleControlsVisibility()
                        },
                        onDoubleTapLeft = {
                            if (!uiState.isLocked) {
                                viewModel.seekBy(-10000L)
                                exoPlayer.seekTo((exoPlayer.currentPosition - 10000L).coerceAtLeast(0L))
                            }
                        },
                        onDoubleTapRight = {
                            if (!uiState.isLocked) {
                                viewModel.seekBy(10000L)
                                exoPlayer.seekTo((exoPlayer.currentPosition + 10000L).coerceAtMost(exoPlayer.duration))
                            }
                        },
                        onVerticalDragLeft = { deltaRatio ->
                            if (!uiState.isLocked) {
                                val current = if (uiState.brightnessPercent >= 0f) uiState.brightnessPercent else 0.8f
                                viewModel.setBrightnessPercent(current + deltaRatio)
                            }
                        },
                        onVerticalDragRight = { deltaRatio ->
                            if (!uiState.isLocked) {
                                val deltaVol = (deltaRatio * 100f).roundToInt()
                                viewModel.setVolumePercent(uiState.volumePercent + deltaVol)
                            }
                        },
                        onHorizontalDrag = { ratio ->
                            if (!uiState.isLocked) {
                                val dur = exoPlayer.duration.coerceAtLeast(1L)
                                val seekOffset = (ratio * 60000L).toLong()
                                val targetPos = (exoPlayer.currentPosition + seekOffset).coerceIn(0L, dur)
                                viewModel.seekBy(seekOffset)
                                exoPlayer.seekTo(targetPos)
                            }
                        },
                    )
                )
            },
    ) {
        // ExoPlayer AndroidView
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                }
            },
            update = { playerView ->
                playerView.resizeMode = when (uiState.aspectRatioMode) {
                    AspectRatioMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    AspectRatioMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    AspectRatioMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    AspectRatioMode.RATIO_16_9 -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
                    AspectRatioMode.RATIO_4_3 -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Buffering Indicator Overlay
        if (uiState.isBuffering && uiState.errorMessage == null) {
            CircularProgressIndicator(
                color = Red600,
                modifier = Modifier
                    .size(56.dp)
                    .align(Alignment.Center),
            )
        }

        // Error Message Overlay
        uiState.errorMessage?.let { errorMsg ->
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Zinc900.copy(alpha = 0.95f)),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = Icons.Filled.ErrorOutline,
                        contentDescription = null,
                        tint = Red600,
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Erreur de lecture",
                        color = White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMsg,
                        color = Color.LightGray,
                        fontSize = 14.sp,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            viewModel.retryPlayback()
                            exoPlayer.prepare()
                            exoPlayer.play()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Red600),
                    ) {
                        Text("Réessayer", color = White)
                    }
                }
            }
        }

        // Gesture feedback overlay (Volume / Brightness / Seek)
        uiState.gestureFeedback?.let { feedback ->
            GestureFeedbackCard(
                feedback = feedback,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        // Video controls overlay
        AnimatedVisibility(
            visible = uiState.isControlsVisible && !uiState.isLocked,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f)),
            ) {
                // Top Bar
                TopPlayerBar(
                    title = uiState.currentVideo?.cleanTitle ?: uiState.currentVideo?.name ?: videoName,
                    aspectRatioMode = uiState.aspectRatioMode,
                    playbackSpeed = uiState.playbackSpeed,
                    onBack = onBack,
                    onOpenTracks = { showTracksSheet = true },
                    onCycleAspect = viewModel::cycleAspectRatio,
                    onCycleSpeed = viewModel::cyclePlaybackSpeed,
                    modifier = Modifier.align(Alignment.TopCenter),
                )

                // Center controls (Rewind, Play/Pause, Fast-Forward, Next Episode)
                CenterPlayerControls(
                    isPlaying = uiState.isPlaying,
                    hasNextVideo = uiState.nextVideo != null,
                    onTogglePlay = {
                        if (exoPlayer.isPlaying) {
                            exoPlayer.pause()
                        } else {
                            exoPlayer.play()
                        }
                    },
                    onRewind = {
                        viewModel.seekBy(-10000L)
                        exoPlayer.seekTo((exoPlayer.currentPosition - 10000L).coerceAtLeast(0L))
                    },
                    onForward = {
                        viewModel.seekBy(10000L)
                        exoPlayer.seekTo((exoPlayer.currentPosition + 10000L).coerceAtMost(exoPlayer.duration))
                    },
                    onNextVideo = viewModel::playNextVideo,
                    modifier = Modifier.align(Alignment.Center),
                )

                // Bottom Bar (Slider, Time, PiP, Lock)
                BottomPlayerBar(
                    positionMs = uiState.positionMs,
                    durationMs = uiState.durationMs,
                    isLocked = uiState.isLocked,
                    onSeek = { newPos ->
                        viewModel.onPositionChanged(newPos, uiState.durationMs)
                        exoPlayer.seekTo(newPos)
                    },
                    onToggleLock = viewModel::toggleLock,
                    onEnterPip = {
                        enterPipMode(activity)
                    },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }

        // Unlock button when controls are locked
        if (uiState.isLocked) {
            IconButton(
                onClick = viewModel::toggleLock,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(24.dp)
                    .background(Zinc900.copy(alpha = 0.8f), CircleShape),
            ) {
                Icon(Icons.Filled.Lock, contentDescription = "Déverrouiller", tint = Red600)
            }
        }

        // Next Episode End-of-video overlay with auto countdown
        if (uiState.isEnded && uiState.nextVideo != null) {
            NextEpisodeOverlay(
                nextVideo = uiState.nextVideo!!,
                onPlayNext = viewModel::playNextVideo,
                onDismiss = onBack,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(24.dp),
            )
        }
    }

    // Audio & Subtitle Tracks Bottom Sheet
    if (showTracksSheet) {
        TracksSelectionSheet(
            audioTracks = uiState.audioTracks,
            subtitleTracks = uiState.subtitleTracks,
            onSelectAudio = viewModel::selectAudioTrack,
            onSelectSubtitle = viewModel::selectSubtitleTrack,
            onPickSubtitleFile = {
                subtitleFilePicker.launch("application/x-subrip")
            },
            onDismiss = { showTracksSheet = false },
        )
    }
}

private data class PlayerGestureCallbacks(
    val onTap: () -> Unit,
    val onDoubleTapLeft: () -> Unit,
    val onDoubleTapRight: () -> Unit,
    val onVerticalDragLeft: (Float) -> Unit,
    val onVerticalDragRight: (Float) -> Unit,
    val onHorizontalDrag: (Float) -> Unit,
    val onDragEnd: () -> Unit = {},
)

private suspend fun PointerInputScope.detectPlayerGestures(
    callbacks: PlayerGestureCallbacks,
) {
    var lastTapTime = 0L
    var lastTapX = 0f

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val startX = down.position.x
        val touchSlop = viewConfiguration.touchSlop
        val pointerId = down.id
        var isDrag = false
        var dragMode = 0
        var pointerActive = true

        while (pointerActive) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == pointerId }
            if (change == null || !change.pressed) {
                pointerActive = false
                if (isDrag) {
                    callbacks.onDragEnd()
                } else {
                    lastTapTime = processTapRelease(startX, size.width.toFloat(), lastTapTime, lastTapX, callbacks)
                    lastTapX = startX
                }
            } else {
                val dx = change.position.x - startX
                val dy = change.position.y - down.position.y
                if (!isDrag && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                    isDrag = true
                    dragMode = if (abs(dx) > abs(dy)) 1 else if (startX < size.width * 0.5f) 2 else 3
                }
                if (isDrag) {
                    change.consume()
                    dispatchDragEvent(dragMode, dx / size.width.toFloat(), dy / size.height.toFloat(), callbacks)
                }
            }
        }
    }
}

private fun processTapRelease(
    startX: Float,
    width: Float,
    lastTapTime: Long,
    lastTapX: Float,
    callbacks: PlayerGestureCallbacks,
): Long {
    val currentTime = System.currentTimeMillis()
    val isDoubleTap = currentTime - lastTapTime < 300L && abs(startX - lastTapX) < 150f
    if (isDoubleTap) {
        dispatchDoubleTap(startX, width, callbacks)
        return 0L
    } else {
        callbacks.onTap()
        return currentTime
    }
}

private fun dispatchDoubleTap(
    startX: Float,
    width: Float,
    callbacks: PlayerGestureCallbacks,
) {
    if (startX < width * 0.4f) {
        callbacks.onDoubleTapLeft()
    } else if (startX > width * 0.6f) {
        callbacks.onDoubleTapRight()
    } else {
        callbacks.onTap()
    }
}

private fun dispatchDragEvent(
    dragMode: Int,
    ratioX: Float,
    ratioY: Float,
    callbacks: PlayerGestureCallbacks,
) {
    when (dragMode) {
        1 -> callbacks.onHorizontalDrag(ratioX)
        2 -> callbacks.onVerticalDragLeft(-ratioY)
        3 -> callbacks.onVerticalDragRight(-ratioY)
    }
}

private fun launchExternalPlayer(context: Context, video: VideoItem, packageName: String) {
    val uri = extractUri(video) ?: return

    val intent = Intent(Intent.ACTION_VIEW).apply {
        if (isYouTubeUrl(uri.toString()) || isYouTubeUrl(video.name)) {
            setDataAndType(uri, "text/html")
        } else {
            setDataAndType(uri, "video/*")
        }
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        if (packageName.isNotEmpty()) {
            setPackage(packageName)
        }
    }
    runCatching {
        context.startActivity(Intent.createChooser(intent, video.name))
    }.onFailure {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        }
    }
}

private fun extractUri(video: VideoItem?): Uri? {
    if (video == null) return null
    val target = when {
        !video.nativeUri.isNullOrEmpty() -> video.nativeUri
        !video.url.isNullOrEmpty() -> video.url
        video.path.isNotEmpty() -> return Uri.fromFile(File(video.path))
        video.name.startsWith("http://") || video.name.startsWith("https://") || video.name.startsWith("content://") -> video.name
        else -> null
    } ?: return null
    return runCatching { Uri.parse(target) }.getOrNull()
}

private fun isYouTubeUrl(urlStr: String?): Boolean {
    if (urlStr == null) return false
    val lower = urlStr.lowercase()
    return lower.contains("youtube.com") || lower.contains("youtu.be")
}

private fun enterPipMode(activity: Activity?) {
    if (activity != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val params = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
            .build()
        activity.enterPictureInPictureMode(params)
    }
}

@Composable
private fun TopPlayerBar(
    title: String,
    aspectRatioMode: AspectRatioMode,
    playbackSpeed: Float,
    onBack: () -> Unit,
    onOpenTracks: () -> Unit,
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onOpenTracks) {
                Icon(Icons.Filled.Subtitles, contentDescription = "Sous-titres & Audio", tint = White)
            }
            IconButton(onClick = onCycleAspect) {
                Icon(Icons.Filled.AspectRatio, contentDescription = "Format : ${aspectRatioMode.label}", tint = White)
            }
            Button(
                onClick = onCycleSpeed,
                colors = ButtonDefaults.buttonColors(containerColor = Zinc800),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Icon(Icons.Filled.Speed, contentDescription = null, tint = White, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("${playbackSpeed}x", color = White, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun CenterPlayerControls(
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
            Icon(Icons.Filled.Replay10, contentDescription = "Reculer 10s", tint = White, modifier = Modifier.size(36.dp))
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
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Lecture",
                tint = White,
                modifier = Modifier.size(40.dp),
            )
        }

        IconButton(onClick = onForward, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Filled.Forward10, contentDescription = "Avancer 10s", tint = White, modifier = Modifier.size(36.dp))
        }

        if (hasNextVideo) {
            IconButton(onClick = onNextVideo, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.SkipNext, contentDescription = "Épisode suivant", tint = White, modifier = Modifier.size(36.dp))
            }
        }
    }
}

@Composable
private fun BottomPlayerBar(
    positionMs: Long,
    durationMs: Long,
    isLocked: Boolean,
    onSeek: (Long) -> Unit,
    onToggleLock: () -> Unit,
    onEnterPip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isSeeking by remember { mutableStateOf(false) }
    var seekPositionMs by remember { mutableLongStateOf(0L) }

    val displayPos = if (isSeeking) seekPositionMs else positionMs

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
                text = "${formatTimeMs(displayPos)} / ${formatTimeMs(durationMs)}",
                color = White,
                fontSize = 12.sp,
            )
            Row {
                IconButton(onClick = onEnterPip) {
                    Icon(Icons.Filled.PictureInPicture, contentDescription = "PiP", tint = White)
                }
                IconButton(onClick = onToggleLock) {
                    Icon(
                        imageVector = if (isLocked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                        contentDescription = "Verrouiller",
                        tint = White,
                    )
                }
            }
        }

        Slider(
            value = displayPos.coerceIn(0L, durationMs.coerceAtLeast(1L)).toFloat(),
            onValueChange = {
                isSeeking = true
                seekPositionMs = it.toLong()
            },
            onValueChangeFinished = {
                onSeek(seekPositionMs)
                isSeeking = false
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

@Composable
private fun GestureFeedbackCard(
    feedback: GestureFeedback,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Zinc900.copy(alpha = 0.9f)),
        modifier = modifier.padding(16.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = when (feedback.type) {
                    FeedbackType.VOLUME -> Icons.Filled.VolumeUp
                    FeedbackType.BRIGHTNESS -> Icons.Filled.Brightness6
                    FeedbackType.SEEK_FORWARD -> Icons.Filled.Forward10
                    FeedbackType.SEEK_REWIND -> Icons.Filled.Replay10
                },
                contentDescription = null,
                tint = White,
                modifier = Modifier.size(28.dp),
            )
            Text(text = feedback.text, color = White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun NextEpisodeOverlay(
    nextVideo: VideoItem,
    onPlayNext: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var countdown by remember { mutableIntStateOf(5) }

    LaunchedEffect(nextVideo) {
        countdown = 5
        while (countdown > 0) {
            delay(1000L)
            countdown--
        }
        onPlayNext()
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Zinc900),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Épisode suivant dans $countdown s", color = Red600, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = nextVideo.cleanTitle ?: nextVideo.name,
                color = White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = Zinc800),
                ) {
                    Text("Fermer", color = White)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onPlayNext,
                    colors = ButtonDefaults.buttonColors(containerColor = Red600),
                ) {
                    Text("Lire maintenant", color = White)
                }
            }
        }
    }
}

@Composable
private fun TracksSelectionSheet(
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

private fun formatTimeMs(ms: Long): String {
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
