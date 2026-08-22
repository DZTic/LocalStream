package com.localstream.app.ui.history

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.localstream.app.data.db.entity.PlaybackStateEntity
import com.localstream.app.data.db.entity.WatchedItemEntity
import com.localstream.app.di.AppContainer
import com.localstream.app.domain.TitleCleaner
import com.localstream.app.domain.model.TmdbMetadata
import com.localstream.app.domain.model.VideoItem
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class HistoryItemUiState(
    val videoName: String,
    val cleanTitle: String,
    val isAvailableOnDisk: Boolean,
    val isForceAvailable: Boolean,
    val watchedAt: Long,
    val progressPercent: Float,
    val positionMs: Long,
    val isWatched: Boolean,
    val metadata: TmdbMetadata? = null,
)

@Immutable
data class HistoryUiState(
    val items: List<HistoryItemUiState> = emptyList(),
    val isLoading: Boolean = false,
)

@Suppress("LongMethod", "CyclomaticComplexMethod")
class HistoryViewModel(
    private val container: AppContainer,
) : ViewModel() {

    init {
        viewModelScope.launch {
            container.tmdbRepository.prewarmCache()
        }
    }

    val uiState: StateFlow<HistoryUiState> = combine(
        container.watchStateRepository.observeWatchedEntities,
        container.watchStateRepository.observePlaybackStates,
        container.videoRepository.observeVideos,
        container.tmdbRepository.observeAllMetadata,
        container.settingsRepository.observeForceAvailable,
    ) { watchedMap: Map<String, WatchedItemEntity>,
        playbackMap: Map<String, PlaybackStateEntity>,
        diskVideos: List<VideoItem>,
        metadataMap: Map<String, TmdbMetadata>,
        forceSet: Set<String> ->

        val allNames = (watchedMap.keys + playbackMap.keys).distinct()
        val rawVideos = container.videoRepository.getRawVideos()
        val allVideos = (rawVideos + diskVideos + diskVideos.flatMap { it.episodes.orEmpty() })
        val allVideosMap = allVideos.associateBy { it.name }
        val diskNames = allVideosMap.keys

        val historyItems = allNames.mapNotNull { name ->
            val pb = playbackMap[name]
            val watchedEntity = watchedMap[name]
            val isWatched = watchedEntity?.watched ?: false
            if (!isWatched && pb == null) return@mapNotNull null

            val isDiskAvailable = diskNames.contains(name) || rawVideos.any { it.name == name || it.path == name }
            val isForceAvailable = forceSet.contains(name)
            val effectiveAvailable = isDiskAvailable || isForceAvailable

            val diskVideo = allVideosMap[name]
            val durationMs = (diskVideo?.duration ?: 0L) * 1000L
            val positionMs = pb?.positionMs ?: 0L
            val progressPercent = if (durationMs > 0L) {
                (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
            } else {
                ((pb?.progressPct ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f)
            }
            val watchedAt = maxOf(pb?.lastPlayedAt ?: 0L, watchedEntity?.watchedAt ?: 0L)

            val cleanTitle = diskVideo?.cleanTitle ?: TitleCleaner.getCleanTitle(name)

            val metadata = metadataMap[name]
                ?: metadataMap[cleanTitle]
                ?: diskVideo?.seriesName?.let { metadataMap[it] ?: metadataMap[TitleCleaner.getCleanTitle(it)] }
                ?: metadataMap[TitleCleaner.getCleanTitle(name)]

            HistoryItemUiState(
                videoName = name,
                cleanTitle = cleanTitle,
                isAvailableOnDisk = effectiveAvailable,
                isForceAvailable = isForceAvailable,
                watchedAt = watchedAt,
                progressPercent = progressPercent,
                positionMs = positionMs,
                isWatched = isWatched,
                metadata = metadata,
            )
        }.sortedByDescending { it.watchedAt }

        HistoryUiState(items = historyItems)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HistoryUiState(),
    )

    fun removeFromHistory(videoName: String) {
        viewModelScope.launch {
            container.watchStateRepository.setWatched(videoName, false)
            container.watchStateRepository.clearProgress(videoName)
            val grouped = container.videoRepository.getGroupedVideos()
            val group = grouped.find { it.name == videoName || it.seriesName == videoName }
            group?.episodes?.forEach { ep ->
                container.watchStateRepository.setWatched(ep.name, false)
                container.watchStateRepository.clearProgress(ep.name)
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            container.watchStateRepository.clearHistory()
        }
    }

    fun toggleForceAvailable(videoName: String) {
        viewModelScope.launch {
            container.settingsRepository.toggleForceAvailable(videoName)
        }
    }

    fun addManualTitle(title: String) {
        if (title.isBlank()) return
        val clean = TitleCleaner.getCleanTitle(title.trim())
        viewModelScope.launch {
            container.watchStateRepository.setWatched(clean, true)
            val isUrl = clean.startsWith("http://") || clean.startsWith("https://") ||
                clean.startsWith("content://") || clean.startsWith("file://")
            val dummyVideo = VideoItem(name = clean, url = if (isUrl) clean else "", path = "", size = 0, duration = 0)
            container.tmdbRepository.fetchMetadataForVideo(dummyVideo)
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return HistoryViewModel(container) as T
                }
            }
    }
}
