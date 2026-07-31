package com.localstream.app.ui.player

import java.net.URLDecoder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.localstream.app.di.AppContainer
import com.localstream.app.domain.model.VideoItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AspectRatioMode(val label: String) {
    FIT("Fit"),
    FILL("Remplir"),
    ZOOM("Zoom"),
    RATIO_16_9("16:9"),
    RATIO_4_3("4:3"),
}

enum class FeedbackType {
    VOLUME,
    BRIGHTNESS,
    SEEK_FORWARD,
    SEEK_REWIND,
}

data class GestureFeedback(
    val type: FeedbackType,
    val valuePercent: Int = 0,
    val text: String = "",
    val timestamp: Long = System.currentTimeMillis(),
)

data class AudioTrackUiState(
    val id: String,
    val label: String,
    val language: String? = null,
    val isSelected: Boolean = false,
)

data class SubtitleTrackUiState(
    val id: String,
    val label: String,
    val language: String? = null,
    val isSelected: Boolean = false,
    val isExternal: Boolean = false,
    val uriString: String? = null,
)

data class PlayerUiState(
    val currentVideo: VideoItem? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isControlsVisible: Boolean = true,
    val isLocked: Boolean = false,
    val volumePercent: Int = 100,
    val brightnessPercent: Float = 1.0f,
    val aspectRatioMode: AspectRatioMode = AspectRatioMode.FIT,
    val playbackSpeed: Float = 1.0f,
    val audioTracks: List<AudioTrackUiState> = emptyList(),
    val subtitleTracks: List<SubtitleTrackUiState> = emptyList(),
    val selectedAudioTrackId: String? = null,
    val selectedSubtitleTrackId: String? = null,
    val gestureFeedback: GestureFeedback? = null,
    val nextVideo: VideoItem? = null,
    val isEnded: Boolean = false,
    val playerMode: String = "internal",
    val selectedExternalPlayer: String = "",
    val initialPositionMs: Long = 0L,
)

@Suppress("TooManyFunctions", "LongMethod", "UNCHECKED_CAST")
class PlayerViewModel(
    val videoName: String,
    private val container: AppContainer,
) : ViewModel() {

    private val currentVideoFlow = MutableStateFlow<VideoItem?>(null)
    private val isPlayingFlow = MutableStateFlow(false)
    private val positionMsFlow = MutableStateFlow(0L)
    private val durationMsFlow = MutableStateFlow(0L)
    private val isControlsVisibleFlow = MutableStateFlow(true)
    private val isLockedFlow = MutableStateFlow(false)
    private val volumePercentFlow = MutableStateFlow(100)
    private val brightnessPercentFlow = MutableStateFlow(1.0f)
    private val aspectRatioModeFlow = MutableStateFlow(AspectRatioMode.FIT)
    private val playbackSpeedFlow = MutableStateFlow(1.0f)
    private val audioTracksFlow = MutableStateFlow<List<AudioTrackUiState>>(emptyList())
    private val subtitleTracksFlow = MutableStateFlow<List<SubtitleTrackUiState>>(emptyList())
    private val selectedAudioTrackIdFlow = MutableStateFlow<String?>(null)
    private val selectedSubtitleTrackIdFlow = MutableStateFlow<String?>(null)
    private val gestureFeedbackFlow = MutableStateFlow<GestureFeedback?>(null)
    private val nextVideoFlow = MutableStateFlow<VideoItem?>(null)
    private val isEndedFlow = MutableStateFlow(false)
    private val initialPositionMsFlow = MutableStateFlow(0L)

    init {
        loadVideoDetails()
    }

    val uiState: StateFlow<PlayerUiState> = combine(
        combine(
            listOf(
                currentVideoFlow,
                isPlayingFlow,
                positionMsFlow,
                durationMsFlow,
                isControlsVisibleFlow,
            )
        ) { arr -> arr },
        combine(
            listOf(
                isLockedFlow,
                volumePercentFlow,
                brightnessPercentFlow,
                aspectRatioModeFlow,
                playbackSpeedFlow,
            )
        ) { arr -> arr },
        combine(
            listOf(
                audioTracksFlow,
                subtitleTracksFlow,
                selectedAudioTrackIdFlow,
                selectedSubtitleTrackIdFlow,
                gestureFeedbackFlow,
            )
        ) { arr -> arr },
        combine(
            listOf(
                nextVideoFlow,
                isEndedFlow,
                container.settingsRepository.observePlayerMode,
                container.settingsRepository.observeExternalPlayer,
                initialPositionMsFlow,
            )
        ) { arr -> arr },
    ) { p1, p2, p3, p4 ->
        @Suppress("UNCHECKED_CAST")
        PlayerUiState(
            currentVideo = p1[0] as VideoItem?,
            isPlaying = p1[1] as Boolean,
            positionMs = p1[2] as Long,
            durationMs = p1[3] as Long,
            isControlsVisible = p1[4] as Boolean,
            isLocked = p2[0] as Boolean,
            volumePercent = p2[1] as Int,
            brightnessPercent = p2[2] as Float,
            aspectRatioMode = p2[3] as AspectRatioMode,
            playbackSpeed = p2[4] as Float,
            audioTracks = (p3[0] as List<*>).filterIsInstance<AudioTrackUiState>(),
            subtitleTracks = (p3[1] as List<*>).filterIsInstance<SubtitleTrackUiState>(),
            selectedAudioTrackId = p3[2] as String?,
            selectedSubtitleTrackId = p3[3] as String?,
            gestureFeedback = p3[4] as GestureFeedback?,
            nextVideo = p4[0] as VideoItem?,
            isEnded = p4[1] as Boolean,
            playerMode = p4[2] as String,
            selectedExternalPlayer = p4[3] as String,
            initialPositionMs = p4[4] as Long,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = PlayerUiState(),
    )

    private fun loadVideoDetails() {
        viewModelScope.launch {
            val decodedName = decodeUri(videoName)
            val allRaw = container.videoRepository.getRawVideos()
            val allGrouped = container.videoRepository.getGroupedVideos()

            val targetVideo = resolveTargetVideo(decodedName, videoName, allGrouped, allRaw)

            val watchedMap = container.watchStateRepository.getWatchedMap()
            val isWatched = watchedMap[targetVideo.name] == true
            val state = container.watchStateRepository.getPlaybackState(targetVideo.name)
            val rawPos = state?.positionMs ?: 0L
            val pct = state?.progressPct ?: 0.0
            val pos = if (isFinished(isWatched, pct, rawPos, targetVideo.duration * 1000L)) 0L else rawPos

            initialPositionMsFlow.value = pos
            positionMsFlow.value = pos
            currentVideoFlow.value = targetVideo

            resolveNextVideo(targetVideo, allGrouped, allRaw)
        }
    }

    private suspend fun resolveTargetVideo(
        decodedName: String,
        rawName: String,
        allGrouped: List<VideoItem>,
        allRaw: List<VideoItem>,
    ): VideoItem {
        val rawVideo = allRaw.find { it.name == decodedName || it.name == rawName }
        val foundGroup = allGrouped.find { group ->
            group.name == decodedName || group.name == rawName
        }

        return when {
            foundGroup != null && foundGroup.isSeriesGroup && !foundGroup.episodes.isNullOrEmpty() -> {
                val watchedMap = container.watchStateRepository.getWatchedMap()
                foundGroup.episodes.firstOrNull { ep ->
                    val state = container.watchStateRepository.getPlaybackState(ep.name)
                    (state?.positionMs ?: 0L) > 0L && watchedMap[ep.name] != true
                } ?: foundGroup.episodes.firstOrNull { ep ->
                    watchedMap[ep.name] != true
                } ?: foundGroup.episodes.first()
            }
            rawVideo != null -> rawVideo
            else -> allGrouped.firstNotNullOfOrNull { group ->
                group.episodes?.find { it.name == decodedName || it.name == rawName }
            } ?: VideoItem(name = decodedName)
        }
    }

    private fun resolveNextVideo(
        video: VideoItem,
        allGrouped: List<VideoItem>,
        allRaw: List<VideoItem>,
    ) {
        val parentGroup = allGrouped.find { group ->
            (video.seriesName != null && group.name == video.seriesName) ||
                group.episodes?.any { it.name == video.name } == true
        }
        val episodes = parentGroup?.episodes.orEmpty()
        val currentIndex = episodes.indexOfFirst { it.name == video.name }
        if (currentIndex >= 0 && currentIndex < episodes.size - 1) {
            nextVideoFlow.value = episodes[currentIndex + 1]
            return
        }
        val indexInRaw = allRaw.indexOfFirst { it.name == video.name }
        if (indexInRaw >= 0 && indexInRaw < allRaw.size - 1) {
            nextVideoFlow.value = allRaw[indexInRaw + 1]
        }
    }

    fun onPlayingStateChanged(isPlaying: Boolean) {
        isPlayingFlow.value = isPlaying
    }

    fun onPositionChanged(positionMs: Long, durationMs: Long) {
        positionMsFlow.value = positionMs
        if (durationMs > 0L) {
            durationMsFlow.value = durationMs
        }
        val video = currentVideoFlow.value ?: return

        viewModelScope.launch {
            if (positionMs > 0L) {
                container.watchStateRepository.savePlaybackState(
                    videoName = video.name,
                    positionMs = positionMs,
                    durationMs = durationMs,
                )
            }
            if (durationMs > 0L && positionMs >= (durationMs * WATCHED_THRESHOLD_RATIO)) {
                container.watchStateRepository.setWatched(
                    videoName = video.name,
                    watched = true,
                    mediaStoreId = video.mediaStoreId,
                )
            }
        }
    }

    fun onVideoEnded() {
        isEndedFlow.value = true
        isPlayingFlow.value = false
        val video = currentVideoFlow.value ?: return
        viewModelScope.launch {
            container.watchStateRepository.setWatched(
                videoName = video.name,
                watched = true,
                mediaStoreId = video.mediaStoreId,
            )
        }
    }

    fun toggleControlsVisibility() {
        if (!isLockedFlow.value) {
            isControlsVisibleFlow.value = !isControlsVisibleFlow.value
        }
    }

    fun setControlsVisible(visible: Boolean) {
        if (!isLockedFlow.value) {
            isControlsVisibleFlow.value = visible
        }
    }

    fun toggleLock() {
        val newLocked = !isLockedFlow.value
        isLockedFlow.value = newLocked
        if (newLocked) {
            isControlsVisibleFlow.value = false
        } else {
            isControlsVisibleFlow.value = true
        }
    }

    fun cycleAspectRatio() {
        val current = aspectRatioModeFlow.value
        val next = when (current) {
            AspectRatioMode.FIT -> AspectRatioMode.FILL
            AspectRatioMode.FILL -> AspectRatioMode.ZOOM
            AspectRatioMode.ZOOM -> AspectRatioMode.RATIO_16_9
            AspectRatioMode.RATIO_16_9 -> AspectRatioMode.RATIO_4_3
            AspectRatioMode.RATIO_4_3 -> AspectRatioMode.FIT
        }
        aspectRatioModeFlow.value = next
    }

    fun cyclePlaybackSpeed() {
        val current = playbackSpeedFlow.value
        val next = when (current) {
            0.5f -> 0.75f
            0.75f -> 1.0f
            1.0f -> 1.25f
            1.25f -> 1.5f
            1.5f -> 2.0f
            else -> 0.5f
        }
        playbackSpeedFlow.value = next
    }

    fun adjustVolume(deltaPercent: Int) {
        setVolumePercent(volumePercentFlow.value + deltaPercent)
    }

    fun setVolumePercent(newVol: Int) {
        val coerced = newVol.coerceIn(0, 100)
        volumePercentFlow.value = coerced
        gestureFeedbackFlow.value = GestureFeedback(
            type = FeedbackType.VOLUME,
            valuePercent = coerced,
            text = "Volume: $coerced%",
        )
    }

    fun adjustBrightness(deltaPercent: Float) {
        setBrightnessPercent(brightnessPercentFlow.value + deltaPercent)
    }

    fun setBrightnessPercent(newBright: Float) {
        val coerced = newBright.coerceIn(0.05f, 1.0f)
        brightnessPercentFlow.value = coerced
        val pct = (coerced * 100).toInt()
        gestureFeedbackFlow.value = GestureFeedback(
            type = FeedbackType.BRIGHTNESS,
            valuePercent = pct,
            text = "Luminosit?: $pct%",
        )
    }

    fun seekBy(deltaMs: Long) {
        val newPos = (positionMsFlow.value + deltaMs).coerceIn(0L, durationMsFlow.value.coerceAtLeast(1L))
        positionMsFlow.value = newPos
        val type = if (deltaMs >= 0) FeedbackType.SEEK_FORWARD else FeedbackType.SEEK_REWIND
        val text = if (deltaMs >= 0) "+10s" else "-10s"
        gestureFeedbackFlow.value = GestureFeedback(
            type = type,
            text = text,
        )
    }

    fun clearGestureFeedback() {
        gestureFeedbackFlow.value = null
    }

    fun updateTracks(
        audio: List<AudioTrackUiState>,
        subtitles: List<SubtitleTrackUiState>,
    ) {
        audioTracksFlow.value = audio
        subtitleTracksFlow.value = subtitles
    }

    fun selectAudioTrack(id: String) {
        selectedAudioTrackIdFlow.value = id
        audioTracksFlow.value = audioTracksFlow.value.map {
            it.copy(isSelected = it.id == id)
        }
    }

    fun selectSubtitleTrack(id: String?) {
        selectedSubtitleTrackIdFlow.value = id
        subtitleTracksFlow.value = subtitleTracksFlow.value.map {
            it.copy(isSelected = it.id == id)
        }
    }

    fun addExternalSubtitle(label: String, uriString: String) {
        val id = "ext_${System.currentTimeMillis()}"
        val newTrack = SubtitleTrackUiState(
            id = id,
            label = label,
            isSelected = true,
            isExternal = true,
            uriString = uriString,
        )
        val updated = subtitleTracksFlow.value.map { it.copy(isSelected = false) } + newTrack
        subtitleTracksFlow.value = updated
        selectedSubtitleTrackIdFlow.value = id
    }

    fun playNextVideo() {
        val next = nextVideoFlow.value ?: return
        videoNameFlowOrLoad(next.name)
    }

    private fun videoNameFlowOrLoad(newName: String) {
        viewModelScope.launch {
            isEndedFlow.value = false
            val decodedName = decodeUri(newName)
            val allRaw = container.videoRepository.getRawVideos()
            val allGrouped = container.videoRepository.getGroupedVideos()

            val foundInGrouped = allGrouped.firstNotNullOfOrNull { group ->
                if (group.name == decodedName || group.name == newName) {
                    group
                } else {
                    group.episodes?.find { it.name == decodedName || it.name == newName }
                }
            }
            val video = allRaw.find { it.name == decodedName || it.name == newName }
                ?: foundInGrouped
                ?: VideoItem(name = decodedName)

            val watchedMap = container.watchStateRepository.getWatchedMap()
            val isWatched = watchedMap[video.name] == true
            val state = container.watchStateRepository.getPlaybackState(video.name)
            val rawPos = state?.positionMs ?: 0L
            val pct = state?.progressPct ?: 0.0
            val pos = if (isFinished(isWatched, pct, rawPos, video.duration * 1000L)) 0L else rawPos

            initialPositionMsFlow.value = pos
            positionMsFlow.value = pos
            currentVideoFlow.value = video

            resolveNextVideo(video, allGrouped, allRaw)
        }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5000L
        private const val WATCHED_THRESHOLD_RATIO = 0.90

        fun factory(videoName: String, container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return PlayerViewModel(videoName, container) as T
                }
            }
    }
}

private fun isFinished(isWatched: Boolean, pct: Double, pos: Long, durMs: Long): Boolean {
    if (isWatched || pct >= 90.0) {
        return true
    }
    return durMs > 0L && pos >= durMs - 5000L
}

private fun decodeUri(value: String): String =
    runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)
