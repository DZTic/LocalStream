package com.localstream.app.ui.player

import java.net.URLDecoder
import kotlin.math.roundToInt
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.localstream.app.di.AppContainer
import com.localstream.app.domain.YoutubeUtils
import com.localstream.app.domain.model.VideoItem
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
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
    val volumePercent: Float = 100f,
    val brightnessPercent: Float = -1f,
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
    val isBuffering: Boolean = false,
    val errorMessage: String? = null,
)

@Suppress("TooManyFunctions", "LongMethod")
class PlayerViewModel(
    val videoName: String,
    private val container: AppContainer,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private data class SavePositionRequest(
        val videoName: String,
        val positionMs: Long,
        val durationMs: Long,
    )

    private val savePositionChannel = Channel<SavePositionRequest>(Channel.CONFLATED)

    init {
        @OptIn(FlowPreview::class)
        viewModelScope.launch {
            savePositionChannel.receiveAsFlow()
                .debounce(2000L)
                .collect { req ->
                    container.watchStateRepository.savePlaybackState(
                        videoName = req.videoName,
                        positionMs = req.positionMs,
                        durationMs = req.durationMs,
                    )
                }
        }
        viewModelScope.launch {
            container.settingsRepository.observePlayerMode.collect { mode ->
                _uiState.update { it.copy(playerMode = mode) }
            }
        }
        viewModelScope.launch {
            container.settingsRepository.observeExternalPlayer.collect { extPlayer ->
                _uiState.update { it.copy(selectedExternalPlayer = extPlayer) }
            }
        }
        loadVideoDetails()
    }

    private fun loadVideoDetails() {
        viewModelScope.launch {
            val decodedName = decodeUri(videoName)
            val youtubeId = YoutubeUtils.extractVideoId(decodedName) ?: YoutubeUtils.extractVideoId(videoName)

            if (youtubeId != null) {
                val watchKey = YoutubeUtils.buildWatchStateKey(youtubeId)
                val targetVideo = VideoItem(
                    name = watchKey,
                    url = YoutubeUtils.buildYoutubeUrl(youtubeId),
                    path = "youtube:$youtubeId",
                    type = "video/youtube",
                )
                val watchedMap = container.watchStateRepository.getWatchedMap()
                val isWatched = watchedMap[targetVideo.name] == true
                val state = container.watchStateRepository.getPlaybackState(targetVideo.name)
                val rawPos = state?.positionMs ?: 0L
                val pct = state?.progressPct ?: 0.0
                val pos = if (isFinished(isWatched, pct, rawPos, targetVideo.duration * 1000L)) 0L else rawPos

                _uiState.update {
                    it.copy(
                        initialPositionMs = pos,
                        positionMs = pos,
                        currentVideo = targetVideo,
                    )
                }
                return@launch
            }

            val allRaw = container.videoRepository.getRawVideos()
            val allGrouped = container.videoRepository.getGroupedVideos()

            val targetVideo = resolveTargetVideo(decodedName, videoName, allGrouped, allRaw)

            val watchedMap = container.watchStateRepository.getWatchedMap()
            val isWatched = watchedMap[targetVideo.name] == true
            val state = container.watchStateRepository.getPlaybackState(targetVideo.name)
            val rawPos = state?.positionMs ?: 0L
            val pct = state?.progressPct ?: 0.0
            val pos = if (isFinished(isWatched, pct, rawPos, targetVideo.duration * 1000L)) 0L else rawPos

            val targetDur = if (targetVideo.duration > 0L) {
                targetVideo.duration * 1000L
            } else if (pct > 0.0 && rawPos > 0L) {
                (rawPos / (pct / 100.0)).toLong()
            } else {
                0L
            }

            _uiState.update {
                it.copy(
                    initialPositionMs = pos,
                    positionMs = pos,
                    durationMs = if (targetDur > 0L) targetDur else it.durationMs,
                    currentVideo = targetVideo,
                )
            }

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
            } ?: createFallbackVideoItem(decodedName)
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
            val nextVid = episodes[currentIndex + 1]
            _uiState.update { it.copy(nextVideo = nextVid) }
            return
        }
        val indexInRaw = allRaw.indexOfFirst { it.name == video.name }
        if (indexInRaw >= 0 && indexInRaw < allRaw.size - 1) {
            val nextVid = allRaw[indexInRaw + 1]
            _uiState.update { it.copy(nextVideo = nextVid) }
        }
    }

    fun setBuffering(isBuffering: Boolean) {
        _uiState.update { it.copy(isBuffering = isBuffering) }
    }

    fun setErrorMessage(msg: String?) {
        _uiState.update { it.copy(errorMessage = msg) }
    }

    fun retryPlayback() {
        _uiState.update { it.copy(errorMessage = null, isBuffering = true) }
    }

    fun onPlayingStateChanged(isPlaying: Boolean) {
        _uiState.update { it.copy(isPlaying = isPlaying) }
        if (!isPlaying) {
            saveCurrentPosition()
        }
    }

    fun onPositionChanged(positionMs: Long, durationMs: Long) {
        _uiState.update { state ->
            val newDur = if (durationMs > 0L) durationMs else state.durationMs
            state.copy(positionMs = positionMs, durationMs = newDur)
        }
        val video = _uiState.value.currentVideo ?: return

        if (positionMs > 0L) {
            savePositionChannel.trySend(
                SavePositionRequest(
                    videoName = video.name,
                    positionMs = positionMs,
                    durationMs = durationMs,
                )
            )
        }

        val isThresholdReached = durationMs > 0L && positionMs >= (durationMs * WATCHED_THRESHOLD_RATIO)
        if (isThresholdReached) {
            viewModelScope.launch {
                container.watchStateRepository.setWatched(
                    videoName = video.name,
                    watched = true,
                    mediaStoreId = video.mediaStoreId,
                )
            }
        }
    }

    private fun saveCurrentPosition() {
        val video = _uiState.value.currentVideo ?: return
        val pos = _uiState.value.positionMs
        val dur = _uiState.value.durationMs
        if (pos > 0L) {
            viewModelScope.launch {
                container.watchStateRepository.savePlaybackState(
                    videoName = video.name,
                    positionMs = pos,
                    durationMs = dur,
                )
            }
        }
    }

    fun onVideoEnded() {
        _uiState.update { it.copy(isEnded = true, isPlaying = false) }
        val video = _uiState.value.currentVideo ?: return
        viewModelScope.launch {
            container.watchStateRepository.setWatched(
                videoName = video.name,
                watched = true,
                mediaStoreId = video.mediaStoreId,
            )
        }
    }

    fun toggleControlsVisibility() {
        _uiState.update { if (!it.isLocked) it.copy(isControlsVisible = !it.isControlsVisible) else it }
    }

    fun setControlsVisible(visible: Boolean) {
        _uiState.update { if (!it.isLocked) it.copy(isControlsVisible = visible) else it }
    }

    fun toggleLock() {
        _uiState.update {
            val newLocked = !it.isLocked
            it.copy(isLocked = newLocked, isControlsVisible = !newLocked)
        }
    }

    fun cycleAspectRatio() {
        _uiState.update {
            val next = when (it.aspectRatioMode) {
                AspectRatioMode.FIT -> AspectRatioMode.FILL
                AspectRatioMode.FILL -> AspectRatioMode.ZOOM
                AspectRatioMode.ZOOM -> AspectRatioMode.RATIO_16_9
                AspectRatioMode.RATIO_16_9 -> AspectRatioMode.RATIO_4_3
                AspectRatioMode.RATIO_4_3 -> AspectRatioMode.FIT
            }
            it.copy(aspectRatioMode = next)
        }
    }

    fun cyclePlaybackSpeed() {
        _uiState.update {
            val next = when (it.playbackSpeed) {
                0.5f -> 0.75f
                0.75f -> 1.0f
                1.0f -> 1.25f
                1.25f -> 1.5f
                1.5f -> 2.0f
                else -> 0.5f
            }
            it.copy(playbackSpeed = next)
        }
    }

    fun adjustVolume(deltaPercent: Float) {
        setVolumePercent(_uiState.value.volumePercent + deltaPercent)
    }

    fun setInitialVolumePercent(newVol: Float) {
        setInitialVolumePercentInternal(newVol)
    }

    fun initVolumePercent(newVol: Float) {
        setInitialVolumePercent(newVol)
    }

    private fun setInitialVolumePercentInternal(newVol: Float) {
        _uiState.update { it.copy(volumePercent = newVol.coerceIn(0f, MAX_VOLUME_PERCENT)) }
    }

    fun setVolumePercent(newVol: Float) {
        val coerced = newVol.coerceIn(0f, MAX_VOLUME_PERCENT)
        val coercedInt = coerced.roundToInt()
        _uiState.update {
            it.copy(
                volumePercent = coerced,
                gestureFeedback = GestureFeedback(
                    type = FeedbackType.VOLUME,
                    valuePercent = coercedInt,
                    text = "Volume: $coercedInt%",
                ),
            )
        }
    }

    fun adjustBrightness(deltaPercent: Float) {
        val current = _uiState.value.brightnessPercent
        val baseline = if (current < 0f) 1.0f else current
        setBrightnessPercent(baseline + deltaPercent)
    }

    fun scaleBrightness(factor: Float) {
        val current = _uiState.value.brightnessPercent
        val baseline = if (current < 0f) DEFAULT_BRIGHTNESS else current
        setBrightnessPercent(baseline * factor)
    }

    fun setBrightnessPercent(newBright: Float) {
        val coerced = newBright.coerceIn(MIN_BRIGHTNESS, MAX_BRIGHTNESS)
        val pct = (coerced * 100).roundToInt()
        _uiState.update {
            it.copy(
                brightnessPercent = coerced,
                gestureFeedback = GestureFeedback(
                    type = FeedbackType.BRIGHTNESS,
                    valuePercent = pct,
                    text = "Luminosité: $pct%",
                ),
            )
        }
    }

    fun seekBy(deltaMs: Long) {
        val state = _uiState.value
        val newPos = (state.positionMs + deltaMs).coerceIn(0L, state.durationMs.coerceAtLeast(1L))
        val type = if (deltaMs >= 0) FeedbackType.SEEK_FORWARD else FeedbackType.SEEK_REWIND
        val text = if (deltaMs >= 0) "+10s" else "-10s"
        _uiState.update {
            it.copy(
                positionMs = newPos,
                gestureFeedback = GestureFeedback(
                    type = type,
                    text = text,
                ),
            )
        }
    }

    fun clearGestureFeedback() {
        _uiState.update { it.copy(gestureFeedback = null) }
    }

    fun updateTracks(
        audio: List<AudioTrackUiState>,
        subtitles: List<SubtitleTrackUiState>,
    ) {
        _uiState.update { state ->
            val selectedAudio = state.selectedAudioTrackId ?: audio.firstOrNull { it.isSelected }?.id
            val selectedSub = state.selectedSubtitleTrackId ?: subtitles.firstOrNull { it.isSelected }?.id
            state.copy(
                audioTracks = audio,
                subtitleTracks = subtitles,
                selectedAudioTrackId = selectedAudio,
                selectedSubtitleTrackId = selectedSub,
            )
        }
    }

    fun selectAudioTrack(id: String) {
        _uiState.update { state ->
            state.copy(
                selectedAudioTrackId = id,
                audioTracks = state.audioTracks.map { it.copy(isSelected = it.id == id) },
            )
        }
    }

    fun selectSubtitleTrack(id: String?) {
        _uiState.update { state ->
            state.copy(
                selectedSubtitleTrackId = id,
                subtitleTracks = state.subtitleTracks.map { it.copy(isSelected = it.id == id) },
            )
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
        _uiState.update { state ->
            val updated = state.subtitleTracks.map { it.copy(isSelected = false) } + newTrack
            state.copy(
                subtitleTracks = updated,
                selectedSubtitleTrackId = id,
            )
        }
    }

    fun playNextVideo() {
        val next = _uiState.value.nextVideo ?: return
        videoNameFlowOrLoad(next.name)
    }

    private fun videoNameFlowOrLoad(newName: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isEnded = false) }
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
                ?: createFallbackVideoItem(decodedName)

            val watchedMap = container.watchStateRepository.getWatchedMap()
            val isWatched = watchedMap[video.name] == true
            val state = container.watchStateRepository.getPlaybackState(video.name)
            val rawPos = state?.positionMs ?: 0L
            val pct = state?.progressPct ?: 0.0
            val pos = if (isFinished(isWatched, pct, rawPos, video.duration * 1000L)) 0L else rawPos

            val targetDur = if (video.duration > 0L) {
                video.duration * 1000L
            } else if (pct > 0.0 && rawPos > 0L) {
                (rawPos / (pct / 100.0)).toLong()
            } else {
                0L
            }

            _uiState.update {
                it.copy(
                    initialPositionMs = pos,
                    positionMs = pos,
                    durationMs = if (targetDur > 0L) targetDur else it.durationMs,
                    currentVideo = video,
                )
            }

            resolveNextVideo(video, allGrouped, allRaw)
        }
    }

    override fun onCleared() {
        super.onCleared()
        saveCurrentPosition()
    }

    companion object {
        private const val WATCHED_THRESHOLD_RATIO = 0.90
        const val MAX_VOLUME_PERCENT: Float = 100f
        const val MIN_BRIGHTNESS: Float = 0.05f
        const val MAX_BRIGHTNESS: Float = 1.0f
        private const val DEFAULT_BRIGHTNESS: Float = 1.0f

        fun factory(videoName: String, container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
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

private fun createFallbackVideoItem(name: String): VideoItem {
    val isUrl = name.startsWith("http://") || name.startsWith("https://") ||
            name.startsWith("content://") || name.startsWith("file://")
    return VideoItem(
        name = name,
        url = if (isUrl) name else "",
        nativeUri = if (name.startsWith("content://") || name.startsWith("file://")) name else null,
    )
}
