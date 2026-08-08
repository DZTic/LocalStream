package com.localstream.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.localstream.app.data.db.entity.PlaybackStateEntity
import com.localstream.app.di.AppContainer
import com.localstream.app.domain.TitleCleaner
import com.localstream.app.domain.model.TmdbMetadata
import com.localstream.app.domain.model.VideoItem
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

data class HistoryUiState(
    val items: List<HistoryItemUiState> = emptyList(),
    val isLoading: Boolean = false,
)

class HistoryViewModel(
    private val container: AppContainer,
) : ViewModel() {

    val uiState: StateFlow<HistoryUiState> = combine(
        container.watchStateRepository.observeWatched,
        container.watchStateRepository.observePlaybackStates,
        container.videoRepository.observeVideos,
        container.settingsRepository.observeForceAvailable,
    ) { watchedSet: Set<String>, playbackMap: Map<String, PlaybackStateEntity>, diskVideos: List<VideoItem>, forceSet: Set<String> ->
        val allNames = (watchedSet + playbackMap.keys).distinct()
        val diskVideoNames = diskVideos.map { it.name }.toSet()

        val historyItems = allNames.map { name ->
            val pb = playbackMap[name]
            val isWatched = watchedSet.contains(name)
            val isDiskAvailable = diskVideoNames.contains(name)
            val isForceAvailable = forceSet.contains(name)
            val effectiveAvailable = isDiskAvailable || isForceAvailable

            val diskVideo = diskVideos.find { it.name == name }
            val durationMs = (diskVideo?.duration ?: 0L) * 1000L
            val positionMs = pb?.positionMs ?: 0L
            val progressPercent = if (durationMs > 0L) {
                (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
            } else {
                ((pb?.progressPct ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f)
            }
            val watchedAt = pb?.lastPlayedAt ?: 0L

            val cleanTitle = TitleCleaner.getCleanTitle(name)

            HistoryItemUiState(
                videoName = name,
                cleanTitle = cleanTitle,
                isAvailableOnDisk = effectiveAvailable,
                isForceAvailable = isForceAvailable,
                watchedAt = watchedAt,
                progressPercent = progressPercent,
                positionMs = positionMs,
                isWatched = isWatched,
                metadata = null,
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
            container.watchStateRepository.savePlaybackState(videoName, 0L, 0L, 0)
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
            val isUrl = clean.startsWith("http://") || clean.startsWith("https://") || clean.startsWith("content://") || clean.startsWith("file://")
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
