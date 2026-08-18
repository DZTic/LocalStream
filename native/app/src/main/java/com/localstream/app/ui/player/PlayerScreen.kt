@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.media3.common.util.UnstableApi::class,
)

package com.localstream.app.ui.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.util.Rational
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
import com.localstream.app.ui.theme.Black
import com.localstream.app.ui.theme.Red600
import com.localstream.app.ui.theme.White
import com.localstream.app.ui.theme.Zinc900
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

private const val CONTROLS_TIMEOUT_MS = 4000L
private const val FEEDBACK_TIMEOUT_MS = 1500L
private const val DRAG_SEEK_THROTTLE_MS = 100L

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

    DisposableEffect(activity) {
        val window = activity?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

        var isVolumeInitialized by remember { mutableStateOf(false) }
    var dragStartVolume by remember { mutableFloatStateOf(0f) }
    var dragStartBrightness by remember { mutableFloatStateOf(0f) }
    var dragStartSeekPos by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        audioManager?.let { am ->
            val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            if (maxVol > 0) {
                val curVol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                val curPct = ((curVol.toFloat() / maxVol) * 100f).coerceIn(0f, 100f)
                viewModel.setInitialVolumePercent(curPct)
            }
        }
        isVolumeInitialized = true
    }

    LaunchedEffect(uiState.volumePercent, isVolumeInitialized) {
        if (!isVolumeInitialized) return@LaunchedEffect
        audioManager?.let { am ->
            val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            if (maxVol > 0) {
                val targetVol = ((uiState.volumePercent / 100f) * maxVol).roundToInt().coerceIn(0, maxVol)
                val curVol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                if (curVol != targetVol) {
                    am.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
                }
            }
        }
    }

    LaunchedEffect(uiState.playerMode, uiState.currentVideo) {
        val video = uiState.currentVideo
        if (uiState.playerMode == "external" && video != null) {
            launchExternalPlayer(context, video, uiState.selectedExternalPlayer)
            onBack()
        }
    }

    var showTracksSheet by remember { mutableStateOf(false) }

    val subtitleFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val fileName = it.lastPathSegment?.substringAfterLast('/') ?: "Sous-titre"
            viewModel.addExternalSubtitle(fileName, it.toString())
        }
    }

    LaunchedEffect(uiState.isControlsVisible, uiState.isPlaying, uiState.isLocked) {
        if (uiState.isControlsVisible && uiState.isPlaying && !uiState.isLocked) {
            delay(CONTROLS_TIMEOUT_MS)
            viewModel.setControlsVisible(false)
        }
    }

    LaunchedEffect(uiState.gestureFeedback) {
        if (uiState.gestureFeedback != null) {
            delay(FEEDBACK_TIMEOUT_MS)
            viewModel.clearGestureFeedback()
        }
    }

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

    val exoPlayer = remember(context) {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()
        ExoPlayer.Builder(context)
            .setAudioAttributes(audioAttributes, true)
            .build()
    }

    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            val isEnteringPip = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                activity?.isInPictureInPictureMode == true
            if (event == Lifecycle.Event.ON_PAUSE && !isEnteringPip) {
                exoPlayer.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var isPlayerReady by remember { mutableStateOf(false) }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_BUFFERING -> viewModel.setBuffering(true)
                    Player.STATE_READY -> {
                        viewModel.setBuffering(false)
                        viewModel.setErrorMessage(null)
                        isPlayerReady = true
                        val dur = exoPlayer.duration.coerceAtLeast(0L)
                        if (dur > 0L) {
                            viewModel.onPositionChanged(exoPlayer.currentPosition.coerceAtLeast(0L), dur)
                        }
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

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                val dur = exoPlayer.duration.coerceAtLeast(0L)
                if (dur > 0L) {
                    viewModel.onPositionChanged(exoPlayer.currentPosition.coerceAtLeast(0L), dur)
                }
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

    LaunchedEffect(uiState.selectedAudioTrackId, uiState.selectedSubtitleTrackId, exoPlayer.currentTracks) {
        val tracks = exoPlayer.currentTracks
        val builder = exoPlayer.trackSelectionParameters.buildUpon()

        val selAudioId = uiState.selectedAudioTrackId
        if (selAudioId != null) {
            findTrackOverride(tracks, C.TRACK_TYPE_AUDIO, selAudioId)?.let {
                builder.setOverrideForType(it)
            }
        }

        val selSubId = uiState.selectedSubtitleTrackId
        if (selSubId == null) {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        } else {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            findTrackOverride(tracks, C.TRACK_TYPE_TEXT, selSubId)?.let {
                builder.setOverrideForType(it)
            }
        }

        exoPlayer.trackSelectionParameters = builder.build()
    }

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

    LaunchedEffect(uiState.currentVideo) {
        val video = uiState.currentVideo ?: return@LaunchedEffect
        val uri = extractUri(video) ?: return@LaunchedEffect

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

    LaunchedEffect(exoPlayer, isPlayerReady, uiState.isPlaying) {
        if (isPlayerReady && uiState.isPlaying) {
            while (isActive) {
                viewModel.onPositionChanged(
                    positionMs = exoPlayer.currentPosition.coerceAtLeast(0L),
                    durationMs = exoPlayer.duration.coerceAtLeast(0L),
                )
                delay(250L)
            }
        }
    }

    LaunchedEffect(uiState.playbackSpeed) {
        exoPlayer.setPlaybackSpeed(uiState.playbackSpeed)
    }

    BackHandler {
        onBack()
    }

    var pendingSeekTargetMs by remember { mutableStateOf<Long?>(null) }
    var lastRealSeekAtMs by remember { mutableStateOf(0L) }

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
                        onDragStart = {
                            dragStartVolume = uiState.volumePercent
                            val curB = uiState.brightnessPercent
                            dragStartBrightness = if (curB >= 0f) curB else getScreenBrightness(activity)
                            dragStartSeekPos = pendingSeekTargetMs ?: exoPlayer.currentPosition
                        },
                        onVerticalDragLeft = { deltaRatio ->
                            if (!uiState.isLocked) {
                                val target = (dragStartBrightness + deltaRatio).coerceIn(0.05f, 1.0f)
                                viewModel.setBrightnessPercent(target)
                            }
                        },
                        onVerticalDragRight = { deltaRatio ->
                            if (!uiState.isLocked) {
                                val target = (dragStartVolume + deltaRatio * 100f).coerceIn(0f, 100f)
                                viewModel.setVolumePercent(target)
                            }
                        },
                        onHorizontalDrag = { ratio ->
                            if (!uiState.isLocked) {
                                val dur = exoPlayer.duration.coerceAtLeast(1L)
                                val seekOffset = (ratio * 60000L).toLong()
                                val targetPos = (dragStartSeekPos + seekOffset).coerceIn(0L, dur)
                                pendingSeekTargetMs = targetPos
                                viewModel.onPositionChanged(targetPos, dur)
                                val now = SystemClock.elapsedRealtime()
                                if (now - lastRealSeekAtMs >= DRAG_SEEK_THROTTLE_MS) {
                                    lastRealSeekAtMs = now
                                    exoPlayer.seekTo(targetPos)
                                }
                            }
                        },
                        onDragEnd = {
                            val target = pendingSeekTargetMs
                            if (target != null) {
                                exoPlayer.seekTo(target)
                            }
                            pendingSeekTargetMs = null
                        },
                    )
                )
            },
    ) {
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

        if (uiState.isBuffering && uiState.errorMessage == null) {
            CircularProgressIndicator(
                color = Red600,
                modifier = Modifier
                    .size(56.dp)
                    .align(Alignment.Center),
            )
        }

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

        uiState.gestureFeedback?.let { feedback ->
            GestureFeedbackCard(
                feedback = feedback,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        PlayerControlsOverlay(
            isVisible = uiState.isControlsVisible,
            isLocked = uiState.isLocked,
            title = uiState.currentVideo?.cleanTitle ?: uiState.currentVideo?.name ?: videoName,
            aspectRatioMode = uiState.aspectRatioMode,
            playbackSpeed = uiState.playbackSpeed,
            isPlaying = uiState.isPlaying,
            hasNextVideo = uiState.nextVideo != null,
            positionMsFlow = viewModel.positionMs,
            durationMs = uiState.durationMs,
            onBack = onBack,
            onOpenTracks = { showTracksSheet = true },
            onCycleAspect = viewModel::cycleAspectRatio,
            onCycleSpeed = viewModel::cyclePlaybackSpeed,
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
            onSeek = { newPos ->
                viewModel.onPositionChanged(newPos, uiState.durationMs)
                exoPlayer.seekTo(newPos)
            },
            onToggleLock = viewModel::toggleLock,
            onEnterPip = {
                enterPipMode(activity)
            },
        )

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
    val uri = extractUri(video) ?: return

    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "video/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (packageName.isNotBlank()) {
            setPackage(packageName)
        }
    }

    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        val fallbackIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "video/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(fallbackIntent)
        } catch (_: Exception) {
        }
    }
}

@Suppress("ReturnCount")
private fun extractUri(video: VideoItem?): Uri? {
    if (video == null) return null
    if (video.url.isNotBlank()) {
        return Uri.parse(video.url)
    }
    if (video.path.isNotBlank()) {
        return Uri.parse(video.path)
    }
    return null
}

private fun enterPipMode(activity: Activity?) {
    if (activity == null) return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val params = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
            .build()
        activity.enterPictureInPictureMode(params)
    }
}

@Suppress("CyclomaticComplexMethod", "ReturnCount")
private fun getScreenBrightness(activity: Activity?): Float {
    if (activity != null) {
        val lp = activity.window.attributes.screenBrightness
        if (lp >= 0f) return lp
        try {
            val sysBright = android.provider.Settings.System.getInt(
                activity.contentResolver,
                android.provider.Settings.System.SCREEN_BRIGHTNESS,
            )
            return (sysBright / 255f).coerceIn(0.05f, 1.0f)
        } catch (_: Exception) {
        }
    }
    return 0.5f
}

private fun findTrackOverride(tracks: Tracks, trackType: @C.TrackType Int, targetId: String): TrackSelectionOverride? {
    for (group in tracks.groups) {
        if (group.type != trackType) continue
        for (i in 0 until group.length) {
            val format = group.getTrackFormat(i)
            val id = format.id ?: "$trackType-$i"
            if (id == targetId) {
                return TrackSelectionOverride(group.mediaTrackGroup, i)
            }
        }
    }
    return null
}
