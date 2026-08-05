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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
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
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.io.File
import java.util.Locale
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

    // Masquer la barre de statut et la barre de navigation pendant la lecture video (mode immersif)
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

    // Garder l'ecran allume tant que la lecture video est en cours
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

    // Initialisation du volume depuis l'AudioManager système au lancement (conserve le volume du téléphone)
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

    // Synchronisation du volume avec AudioManager système
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

    // Mode externe : lancement direct de l'application vidéo choisie
    LaunchedEffect(uiState.playerMode, uiState.currentVideo) {
        val video = uiState.currentVideo
        if (uiState.playerMode == "external" && video != null) {
            launchExternalPlayer(context, video, uiState.selectedExternalPlayer)
            onBack()
        }
    }

    var showTracksSheet by remember { mutableStateOf(false) }

    // Launcher pour sélection SAF d'un fichier .srt externe
    val subtitleFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val fileName = it.lastPathSegment?.substringAfterLast('/') ?: "Sous-titre"
            viewModel.addExternalSubtitle(fileName, it.toString())
        }
    }

    // Gestion de l'inactivité des contrôles
    LaunchedEffect(uiState.isControlsVisible, uiState.isPlaying) {
        if (uiState.isControlsVisible && uiState.isPlaying && !uiState.isLocked) {
            delay(CONTROLS_TIMEOUT_MS)
            viewModel.setControlsVisible(false)
        }
    }

    // Effacement automatique du feedback de geste
    LaunchedEffect(uiState.gestureFeedback) {
        if (uiState.gestureFeedback != null) {
            delay(FEEDBACK_TIMEOUT_MS)
            viewModel.clearGestureFeedback()
        }
    }

    // Ajustement de la luminosite de la fenetre : respecte la luminosite systeme par defaut (-1f)
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
                screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
        }
    }

    // Initialisation et gestion d'ExoPlayer
    val exoPlayer = remember(context) {
        val audioAttributes = androidx.media3.common.AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()
        ExoPlayer.Builder(context)
            .setAudioAttributes(audioAttributes, true)
            .build()
    }

    // Guard: don't report positions until ExoPlayer has completed its initial seek
    var isPlayerReady by remember { mutableStateOf(false) }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    viewModel.onVideoEnded()
                }
                if (state == Player.STATE_READY) {
                    isPlayerReady = true
                }
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

    // Chargement de la source média dans ExoPlayer
    LaunchedEffect(uiState.currentVideo) {
        val video = uiState.currentVideo ?: return@LaunchedEffect
        val uri = when {
            !video.nativeUri.isNullOrEmpty() -> Uri.parse(video.nativeUri)
            !video.url.isNullOrEmpty() -> Uri.parse(video.url)
            video.path.isNotEmpty() -> Uri.fromFile(File(video.path))
            else -> null
        } ?: return@LaunchedEffect

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

    // Mise à jour continue de la position de lecture
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

    // Application de la vitesse de lecture
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
                coroutineScope {
                    launch {
                        detectTapGestures(
                            onTap = {
                                viewModel.toggleControlsVisibility()
                            },
                            onDoubleTap = { offset ->
                                val screenWidth = size.width
                                if (offset.x < screenWidth / 2) {
                                    viewModel.seekBy(-10000L)
                                    exoPlayer.seekTo((exoPlayer.currentPosition - 10000L).coerceAtLeast(0L))
                                } else {
                                    viewModel.seekBy(10000L)
                                    exoPlayer.seekTo((exoPlayer.currentPosition + 10000L).coerceAtMost(exoPlayer.duration))
                                }
                            },
                        )
                    }
                    launch {
                        var isLeftSide = false
                        var startY = 0f
                        var startVolume = 0
                        var startBrightness = 1.0f

                        detectVerticalDragGestures(
                            onDragStart = { offset ->
                                isLeftSide = offset.x < size.width / 2
                                startY = offset.y
                                audioManager?.let { am ->
                                    val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                    if (maxVol > 0) {
                                        val curVol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                                        val curPct = ((curVol.toFloat() / maxVol) * 100).toInt()
                                        startVolume = curPct
                                    } else {
                                        startVolume = uiState.volumePercent
                                    }
                                } ?: run {
                                    startVolume = uiState.volumePercent
                                }
                                startBrightness = if (uiState.brightnessPercent >= 0f) {
                                    uiState.brightnessPercent
                                } else {
                                    try {
                                        android.provider.Settings.System.getInt(
                                            context.contentResolver,
                                            android.provider.Settings.System.SCREEN_BRIGHTNESS
                                        ) / 255f
                                    } catch (_: android.provider.Settings.SettingNotFoundException) {
                                        1.0f
                                    }
                                }
                            },
                            onVerticalDrag = { change, _ ->
                                change.consume()
                                val height = size.height.toFloat().coerceAtLeast(1f)
                                val totalDeltaY = startY - change.position.y
                                val swipeRatio = totalDeltaY / (height * 0.45f)

                                if (isLeftSide) {
                                    val newBrightness = (startBrightness + swipeRatio).coerceIn(0.05f, 1.0f)
                                    viewModel.setBrightnessPercent(newBrightness)
                                } else {
                                    val newVolume = (startVolume + (swipeRatio * 100f)).roundToInt().coerceIn(0, 100)
                                    viewModel.setVolumePercent(newVolume)
                                }
                            },
                        )
                    }
                }
            },
    ) {
        // Vue vidéo ExoPlayer
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

        // Indicateur de geste (Volume / Luminosité / Skip)
        uiState.gestureFeedback?.let { feedback ->
            GestureFeedbackCard(
                feedback = feedback,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        // Overlay des contrôles vidéo
        AnimatedVisibility(
            visible = uiState.isControlsVisible && !uiState.isLocked,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f)),
            ) {
                // Barre supérieure
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

                // Contrôles centraux (Replay, Play/Pause, Forward, Next)
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

                // Barre inférieure (SeekBar, temps, PiP, Lock)
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

        // Bouton déverrouillage quand les contrôles sont verrouillés
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

        // Bannière fin de vidéo / Épisode suivant
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

    // BottomSheet sélection Pistes Audio / Sous-titres
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

private fun launchExternalPlayer(context: Context, video: VideoItem, packageName: String) {
    val uri = when {
        !video.nativeUri.isNullOrEmpty() -> Uri.parse(video.nativeUri)
        !video.url.isNullOrEmpty() -> Uri.parse(video.url)
        video.path.isNotEmpty() -> Uri.fromFile(File(video.path))
        else -> null
    } ?: return

    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "video/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (packageName.isNotEmpty()) {
            setPackage(packageName)
        }
    }
    context.startActivity(Intent.createChooser(intent, video.name))
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
                text = "${formatTimeMs(positionMs)} / ${formatTimeMs(durationMs)}",
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
            value = positionMs.toFloat(),
            onValueChange = { onSeek(it.toLong()) },
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
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Zinc900),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Épisode suivant", color = Red600, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
