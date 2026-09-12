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
import android.media.audiofx.LoudnessEnhancer
import android.graphics.Color as AndroidColor
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.OpenableColumns
import android.util.Rational
import android.view.ViewGroup
import android.view.WindowManager
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import com.localstream.app.LocalStreamApplication
import com.localstream.app.domain.model.VideoItem
import com.localstream.app.ui.subtitles.SubtitlePickerSheet
import com.localstream.app.ui.subtitles.SubtitlePickerViewModel
import com.localstream.app.ui.theme.AppIcons
import com.localstream.app.ui.theme.Black
import com.localstream.app.ui.theme.Red600
import com.localstream.app.ui.theme.White
import com.localstream.app.ui.theme.Zinc800
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
    val appContainer = (context.applicationContext as LocalStreamApplication).container
    val activity = context as? Activity
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val subtitlePickerViewModel: SubtitlePickerViewModel = viewModel(
        factory = SubtitlePickerViewModel.factory(appContainer)
    )
    val subPickerUiState by subtitlePickerViewModel.uiState.collectAsStateWithLifecycle()

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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && activity != null) {
                runCatching {
                    activity.setPictureInPictureParams(
                        PictureInPictureParams.Builder().setAutoEnterEnabled(false).build(),
                    )
                }
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
        try {
            audioManager?.let { am ->
                val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                if (maxVol > 0) {
                    val curVol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                    val curPct = ((curVol.toFloat() / maxVol) * 100f).coerceIn(0f, 100f)
                    viewModel.setInitialVolumePercent(curPct)
                }
            }
        } catch (_: Exception) {
        }
        isVolumeInitialized = true
    }

    LaunchedEffect(uiState.volumePercent, isVolumeInitialized) {
        if (!isVolumeInitialized) return@LaunchedEffect
        try {
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
        } catch (_: Exception) {
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
            val (fileName, targetUriString) = copyUriToCache(context, it)
            viewModel.addExternalSubtitle(fileName, targetUriString)
            showTracksSheet = false
            viewModel.setOnlineSubtitlesSheetVisible(false)
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
        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            .setEnableDecoderFallback(true)
        ExoPlayer.Builder(context, renderersFactory)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
    }

    var loudnessEnhancer by remember { mutableStateOf<LoudnessEnhancer?>(null) }

    LaunchedEffect(uiState.isAudioBoostEnabled) {
        if (uiState.isAudioBoostEnabled && loudnessEnhancer == null) {
            try {
                val audioSessionId = exoPlayer.audioSessionId
                if (audioSessionId != C.AUDIO_SESSION_ID_UNSET && audioSessionId != 0) {
                    loudnessEnhancer = LoudnessEnhancer(audioSessionId)
                }
            } catch (_: Exception) {
                loudnessEnhancer = null
            }
        } else if (!uiState.isAudioBoostEnabled && loudnessEnhancer != null) {
            try {
                loudnessEnhancer?.release()
            } catch (_: Exception) {
            }
            loudnessEnhancer = null
        }
    }

    LaunchedEffect(uiState.isAudioBoostEnabled, uiState.audioBoostLevel, loudnessEnhancer) {
        try {
            loudnessEnhancer?.let { enhancer ->
                enhancer.enabled = uiState.isAudioBoostEnabled
                if (uiState.isAudioBoostEnabled) {
                    enhancer.setTargetGain((uiState.audioBoostLevel * 20).coerceIn(0, 2000))
                }
            }
        } catch (_: Exception) {
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                loudnessEnhancer?.release()
                loudnessEnhancer = null
            } catch (_: Exception) {
            }
        }
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

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity != null) {
                    val width = videoSize.width
                    val height = videoSize.height
                    if (width > 0 && height > 0) {
                        val floatRatio = width.toFloat() / height.toFloat()
                        if (floatRatio in 0.42f..2.38f) {
                            val rational = Rational(width.coerceIn(1, 2390), height.coerceIn(1, 2390))
                            val pipBuilder = PictureInPictureParams.Builder().setAspectRatio(rational)
                            runCatching { activity.setPictureInPictureParams(pipBuilder.build()) }
                        }
                    }
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

                for (groupIndex in 0 until tracks.groups.size) {
                    val group = tracks.groups[groupIndex]
                    val trackType = group.type
                    for (i in 0 until group.length) {
                        val format = group.getTrackFormat(i)
                        val id = format.id ?: "$trackType-$groupIndex-$i"
                        val isSelected = group.isTrackSelected(i)

                        if (trackType == C.TRACK_TYPE_AUDIO) {
                            val audioChannels = when (format.channelCount) {
                                1 -> "Mono"
                                2 -> "Stéréo"
                                6 -> "5.1"
                                8 -> "7.1"
                                else -> if (format.channelCount > 0) "${format.channelCount} ch" else null
                            }
                            val audioCodec = format.sampleMimeType?.substringAfterLast('/')?.uppercase()
                            val richLabel = buildString {
                                append(format.label ?: format.language ?: "Piste ${i + 1}")
                                val extras = listOfNotNull(audioChannels, audioCodec).joinToString(", ")
                                if (extras.isNotBlank()) {
                                    append(" ($extras)")
                                }
                            }
                            audioList.add(
                                AudioTrackUiState(
                                    id = id,
                                    label = richLabel,
                                    language = format.language,
                                    isSelected = isSelected,
                                )
                            )
                        } else if (trackType == C.TRACK_TYPE_TEXT) {
                            val label = format.label ?: format.language ?: "Piste ${i + 1}"
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
            builder.clearOverridesOfType(C.TRACK_TYPE_AUDIO)
            findTrackOverride(tracks, C.TRACK_TYPE_AUDIO, selAudioId)?.let {
                builder.setOverrideForType(it)
            }
        }

        val selSubId = uiState.selectedSubtitleTrackId
        if (selSubId == null) {
            builder.clearOverridesOfType(C.TRACK_TYPE_TEXT)
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        } else {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            builder.clearOverridesOfType(C.TRACK_TYPE_TEXT)
            findTrackOverride(tracks, C.TRACK_TYPE_TEXT, selSubId)?.let {
                builder.setOverrideForType(it)
            }
        }

        exoPlayer.trackSelectionParameters = builder.build()
    }

    var attachedSubtitleUris by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(uiState.currentVideo) {
        val video = uiState.currentVideo ?: return@LaunchedEffect
        val uri = extractUri(video) ?: return@LaunchedEffect

        attachedSubtitleUris = emptySet()
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

    val externalTracks = remember(uiState.subtitleTracks) {
        uiState.subtitleTracks.filter { it.isExternal && !it.uriString.isNullOrBlank() }
    }
    val externalUris = remember(externalTracks) {
        externalTracks.mapNotNull { it.uriString }.toSet()
    }

    LaunchedEffect(externalUris) {
        if (externalUris.isEmpty() || externalUris == attachedSubtitleUris) return@LaunchedEffect
        val currentVideo = uiState.currentVideo ?: return@LaunchedEffect
        val uri = extractUri(currentVideo) ?: return@LaunchedEffect

        val subConfigs = externalTracks.map { track ->
            val trackUriString = track.uriString!!
            val mimeType = when {
                trackUriString.endsWith(".vtt", ignoreCase = true) -> MimeTypes.TEXT_VTT
                trackUriString.endsWith(".ass", ignoreCase = true) ||
                    trackUriString.endsWith(".ssa", ignoreCase = true) -> MimeTypes.TEXT_SSA
                else -> MimeTypes.APPLICATION_SUBRIP
            }

            val subUri = if (trackUriString.startsWith("content://") || trackUriString.startsWith("file://")) {
                Uri.parse(trackUriString)
            } else {
                Uri.fromFile(File(trackUriString))
            }
            MediaItem.SubtitleConfiguration.Builder(subUri)
                .setId(track.id)
                .setLabel(track.label)
                .setMimeType(mimeType)
                .setLanguage(track.language ?: "fr")
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()
        }

        val currentPos = exoPlayer.currentPosition.coerceAtLeast(0L)
        val currentMediaItem = exoPlayer.currentMediaItem
        val mediaItem = (currentMediaItem?.buildUpon() ?: MediaItem.Builder().setUri(uri))
            .setSubtitleConfigurations(subConfigs)
            .build()

        attachedSubtitleUris = externalUris
        exoPlayer.setMediaItem(mediaItem, currentPos)
        exoPlayer.prepare()
        exoPlayer.play()
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
        if (!uiState.isQuickSpeedActive) {
            exoPlayer.setPlaybackSpeed(uiState.playbackSpeed)
        }
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
                                viewModel.triggerDoubleTapSeek(RippleSide.LEFT, 10)
                                exoPlayer.seekTo((exoPlayer.currentPosition - 10000L).coerceAtLeast(0L))
                            }
                        },
                        onDoubleTapRight = {
                            if (!uiState.isLocked) {
                                viewModel.triggerDoubleTapSeek(RippleSide.RIGHT, 10)
                                exoPlayer.seekTo((exoPlayer.currentPosition + 10000L).coerceAtMost(exoPlayer.duration))
                            }
                        },
                        onLongPressStart = {
                            if (!uiState.isLocked) {
                                viewModel.setQuickSpeedActive(true)
                                exoPlayer.setPlaybackSpeed(2.0f)
                            }
                        },
                        onLongPressEnd = {
                            if (!uiState.isLocked) {
                                viewModel.setQuickSpeedActive(false)
                                exoPlayer.setPlaybackSpeed(uiState.playbackSpeed)
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
                    ),
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
                    subtitleView?.apply {
                        val captionStyle = CaptionStyleCompat(
                            AndroidColor.WHITE,
                            AndroidColor.TRANSPARENT,
                            AndroidColor.TRANSPARENT,
                            CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                            AndroidColor.BLACK,
                            Typeface.DEFAULT_BOLD,
                        )
                        setStyle(captionStyle)
                        setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * 0.95f)
                        setBottomPaddingFraction(SubtitleView.DEFAULT_BOTTOM_PADDING_FRACTION)
                    }
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
                        imageVector = AppIcons.ErrorOutline,
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
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        Button(
                            onClick = {
                                uiState.currentVideo?.let { launchExternalPlayer(context, it, "") }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Zinc800),
                        ) {
                            Text("Lecteur externe", color = White)
                        }
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

        DoubleTapRippleOverlay(
            rippleState = uiState.doubleTapRipple,
            onDismiss = viewModel::clearDoubleTapRipple,
        )

        if (uiState.showResumeBanner && uiState.resumePositionMs > 0L) {
            ResumeBanner(
                positionMs = uiState.resumePositionMs,
                onRestart = {
                    viewModel.restartFromBeginning()
                    exoPlayer.seekTo(0L)
                },
                onDismiss = viewModel::dismissResumeBanner,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 24.dp, bottom = 80.dp),
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
            hasEpisodes = uiState.availableEpisodes.isNotEmpty(),
            sleepTimerActive = uiState.sleepTimerRemainingSeconds != null,
            isQuickSpeedActive = uiState.isQuickSpeedActive,
            positionMsFlow = viewModel.positionMs,
            durationMs = uiState.durationMs,
            onBack = onBack,
            onOpenTracks = { showTracksSheet = true },
            onOpenEpisodes = { viewModel.setEpisodesSheetVisible(true) },
            onOpenSleepTimer = { viewModel.setSleepTimerDialogVisible(true) },
            onSkipIntro = viewModel::skipIntro,
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
            subtitleOffsetMs = uiState.subtitleOffsetMs,
            isAudioBoostEnabled = uiState.isAudioBoostEnabled,
            onSelectAudio = viewModel::selectAudioTrack,
            onSelectSubtitle = viewModel::selectSubtitleTrack,
            onAdjustSubtitleOffset = viewModel::adjustSubtitleOffset,
            onResetSubtitleOffset = viewModel::resetSubtitleOffset,
            onToggleAudioBoost = { viewModel.setAudioBoost(it) },
            onPickSubtitleFile = {
                subtitleFilePicker.launch("*/*")
            },
            onOpenOnlineSubtitles = {
                showTracksSheet = false
                viewModel.setOnlineSubtitlesSheetVisible(true)
            },
            onDismiss = { showTracksSheet = false },
        )
    }

    if (uiState.isEpisodesSheetVisible) {
        EpisodesSelectionSheet(
            episodes = uiState.availableEpisodes,
            currentVideoName = uiState.currentVideo?.name,
            onSelectEpisode = viewModel::selectEpisode,
            onDismiss = { viewModel.setEpisodesSheetVisible(false) },
        )
    }

    if (uiState.isSleepTimerDialogVisible) {
        SleepTimerDialog(
            remainingSeconds = uiState.sleepTimerRemainingSeconds,
            onSetTimer = viewModel::startSleepTimer,
            onCancelTimer = viewModel::cancelSleepTimer,
            onDismiss = { viewModel.setSleepTimerDialogVisible(false) },
        )
    }

    if (uiState.isOnlineSubtitlesSheetVisible) {
        SubtitlePickerSheet(
            uiState = subPickerUiState,
            initialQuery = uiState.currentVideo?.cleanTitle ?: uiState.currentVideo?.name ?: videoName,
            onQueryChange = subtitlePickerViewModel::onQueryChange,
            onSearch = subtitlePickerViewModel::searchOpenSubtitles,
            onDownload = { subId, onDownloaded ->
                subtitlePickerViewModel.downloadSubtitle(subId, onDownloaded)
            },
            onPickLocal = { subtitleFilePicker.launch("*/*") },
            onSubtitleSelected = { path ->
                val fileName = path.substringAfterLast('/')
                val targetUri = if (path.startsWith("content://") || path.startsWith("file://")) {
                    path
                } else {
                    Uri.fromFile(File(path)).toString()
                }
                viewModel.addExternalSubtitle(fileName, targetUri)
            },
            onDismiss = { viewModel.setOnlineSubtitlesSheetVisible(false) },
        )
    }
}

@Composable
private fun ResumeBanner(
    positionMs: Long,
    onRestart: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Zinc900.copy(alpha = 0.95f)),
        modifier = modifier.padding(16.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Reprise à ${formatTimeMs(positionMs)}",
                color = White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Button(
                onClick = onRestart,
                colors = ButtonDefaults.buttonColors(containerColor = Red600),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text("Recommencer", color = White, fontSize = 12.sp)
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(24.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Fermer",
                    tint = White.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
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
        val parsed = Uri.parse(video.url)
        return if (parsed.scheme != null) parsed else Uri.fromFile(File(video.url))
    }
    if (video.path.isNotBlank()) {
        val parsed = Uri.parse(video.path)
        return if (parsed.scheme != null) parsed else Uri.fromFile(File(video.path))
    }
    if (!video.nativeUri.isNullOrBlank()) {
        val parsed = Uri.parse(video.nativeUri)
        return if (parsed.scheme != null) parsed else Uri.fromFile(File(video.nativeUri))
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
    for (groupIndex in 0 until tracks.groups.size) {
        val group = tracks.groups[groupIndex]
        if (group.type != trackType) continue
        for (i in 0 until group.length) {
            val format = group.getTrackFormat(i)
            val id = format.id ?: "$trackType-$groupIndex-$i"
            if (id == targetId) {
                return TrackSelectionOverride(group.mediaTrackGroup, i)
            }
        }
    }
    return null
}

private fun resolveDisplayName(context: Context, uri: Uri): String {
    if (uri.scheme == "content") {
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) {
                        cursor.getString(idx)?.let { return it }
                    }
                }
            }
        }
    }
    return uri.lastPathSegment?.substringAfterLast('/') ?: "Sous-titre"
}

private fun resolveExtension(fileName: String): String = when {
    fileName.endsWith(".vtt", ignoreCase = true) -> ".vtt"
    fileName.endsWith(".ass", ignoreCase = true) -> ".ass"
    fileName.endsWith(".ssa", ignoreCase = true) -> ".ssa"
    fileName.endsWith(".srt", ignoreCase = true) -> ".srt"
    else -> ".srt"
}

private val UTF8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
private val UTF16_LE_BOM = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
private val UTF16_BE_BOM = byteArrayOf(0xFE.toByte(), 0xFF.toByte())

private fun hasPrefix(bytes: ByteArray, prefix: ByteArray): Boolean {
    return bytes.size >= prefix.size && prefix.indices.none { bytes[it] != prefix[it] }
}

@Suppress("ReturnCount")
private fun normalizeSubtitleEncoding(bytes: ByteArray): ByteArray {
    if (hasPrefix(bytes, UTF8_BOM)) {
        return bytes.copyOfRange(UTF8_BOM.size, bytes.size)
    }
    if (hasPrefix(bytes, UTF16_LE_BOM)) {
        return String(bytes, UTF16_LE_BOM.size, bytes.size - UTF16_LE_BOM.size, Charsets.UTF_16LE)
            .toByteArray(Charsets.UTF_8)
    }
    if (hasPrefix(bytes, UTF16_BE_BOM)) {
        return String(bytes, UTF16_BE_BOM.size, bytes.size - UTF16_BE_BOM.size, Charsets.UTF_16BE)
            .toByteArray(Charsets.UTF_8)
    }

    val isUtf8 = runCatching {
        val decoder = Charsets.UTF_8.newDecoder()
        decoder.onMalformedInput(CodingErrorAction.REPORT)
        decoder.onUnmappableCharacter(CodingErrorAction.REPORT)
        decoder.decode(ByteBuffer.wrap(bytes))
        true
    }.getOrDefault(false)

    return if (isUtf8) {
        bytes
    } else {
        val cp1252 = Charset.forName("windows-1252")
        String(bytes, cp1252).toByteArray(Charsets.UTF_8)
    }
}

private fun copyUriToCache(context: Context, uri: Uri): Pair<String, String> {
    val fileName = resolveDisplayName(context, uri)
    val extension = resolveExtension(fileName)
    val subDir = File(context.cacheDir, "subtitles").apply { mkdirs() }
    val cacheFile = File(subDir, "local_${System.currentTimeMillis()}$extension")
    val copied = runCatching {
        val rawBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return@runCatching false
        if (rawBytes.isEmpty()) return@runCatching false

        val normalized = normalizeSubtitleEncoding(rawBytes)
        cacheFile.writeBytes(normalized)
        cacheFile.exists() && cacheFile.length() > 0
    }.getOrDefault(false)

    val targetUriString = if (copied) {
        Uri.fromFile(cacheFile).toString()
    } else {
        uri.toString()
    }

    return Pair(fileName, targetUriString)
}
