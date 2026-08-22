package com.localstream.app.ui.history

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.localstream.app.data.db.entity.PlaybackStateEntity
import com.localstream.app.data.db.entity.WatchedItemEntity
import com.localstream.app.di.AppContainer
import com.localstream.app.domain.TitleCleaner
import com.localstream.app.domain.VideoGrouper
import com.localstream.app.domain.VideoUiSelectors
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
    val isSeriesGroup: Boolean = false,
    val isTvSeries: Boolean = false,
    val episodeLabel: String? = null,
)

@Immutable
data class HistoryUiState(
    val items: List<HistoryItemUiState> = emptyList(),
    val isLoading: Boolean = false,
)

private data class HistoryContext(
    val watchedMap: Map<String, WatchedItemEntity>,
    val playbackMap: Map<String, PlaybackStateEntity>,
    val metadataMap: Map<String, TmdbMetadata>,
    val forceSet: Set<String>,
    val rawVideos: List<VideoItem> = emptyList(),
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

        val context = HistoryContext(
            watchedMap = watchedMap,
            playbackMap = playbackMap,
            metadataMap = metadataMap,
            forceSet = forceSet,
            rawVideos = container.videoRepository.getRawVideos(),
        )

        val historyItems = buildHistoryItems(diskVideos, context)
        HistoryUiState(items = historyItems)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HistoryUiState(),
    )

    private fun buildHistoryItems(
        diskVideos: List<VideoItem>,
        context: HistoryContext,
    ): List<HistoryItemUiState> {
        val handledNames = mutableSetOf<String>()
        val result = mutableListOf<HistoryItemUiState>()

        diskVideos.forEach { video ->
            if (video.isSeriesGroup) {
                val groupItem = processDiskGroup(video, context, handledNames)
                if (groupItem != null) {
                    result.add(groupItem)
                }
            } else {
                val standaloneItem = processDiskStandalone(video, context, handledNames)
                if (standaloneItem != null) {
                    result.add(standaloneItem)
                }
            }
        }

        val remainingNames = (context.watchedMap.keys + context.playbackMap.keys)
            .filter { it !in handledNames }
            .distinct()

        if (remainingNames.isNotEmpty()) {
            val orphanItems = processOrphanItems(remainingNames, context)
            result.addAll(orphanItems)
        }

        return result.sortedByDescending { it.watchedAt }
    }

    private fun processDiskGroup(
        video: VideoItem,
        context: HistoryContext,
        handledNames: MutableSet<String>,
    ): HistoryItemUiState? {
        val episodes = video.episodes.orEmpty()
        val allGroupKeys = (listOfNotNull(video.name, video.seriesName) +
            episodes.map { it.name } +
            episodes.mapNotNull { it.cleanTitle }).distinct()
        handledNames.addAll(allGroupKeys)

        val groupWatchedEntities = allGroupKeys.mapNotNull { context.watchedMap[it] }
        val groupPlaybackStates = allGroupKeys.mapNotNull { context.playbackMap[it] }

        val hasWatched = groupWatchedEntities.any { it.watched }
        val hasPlayback = groupPlaybackStates.isNotEmpty()
        if (!hasWatched && !hasPlayback) return null

        val maxWatchedAt = maxOf(
            groupWatchedEntities.maxOfOrNull { it.watchedAt } ?: 0L,
            groupPlaybackStates.maxOfOrNull { it.lastPlayedAt } ?: 0L,
        )

        val isGroupWatched = context.watchedMap[video.name]?.watched == true ||
            (episodes.isNotEmpty() && episodes.all { ep -> context.watchedMap[ep.name]?.watched == true })

        val isForceAvailable = context.forceSet.contains(video.name) ||
            episodes.any { ep -> context.forceSet.contains(ep.name) }

        val latestPbEntry = allGroupKeys.mapNotNull { key ->
            context.playbackMap[key]?.let { key to it }
        }.maxByOrNull { it.second.lastPlayedAt }

        val (progressPct, posMs) = if (latestPbEntry != null) {
            val (key, pb) = latestPbEntry
            val ep = episodes.find { it.name == key || it.cleanTitle == key }
            val durMs = (ep?.duration ?: 0L) * 1000L
            val p = if (durMs > 0L) {
                (pb.positionMs.toFloat() / durMs.toFloat()).coerceIn(0f, 1f)
            } else {
                ((pb.progressPct) / 100.0).toFloat().coerceIn(0f, 1f)
            }
            p to pb.positionMs
        } else {
            0f to 0L
        }

        val episodeLabel = if (video.isTvSeries) {
            VideoUiSelectors.activeEpisodeLabel(
                video = video,
                progress = context.playbackMap.mapValues { it.value.progressPct },
                watched = context.watchedMap.mapValues { it.value.watched },
            )
        } else {
            val watchedCount = episodes.count { context.watchedMap[it.name]?.watched == true }
            if (watchedCount > 0) "$watchedCount/${episodes.size} vus" else null
        }

        val cleanTitle = video.cleanTitle ?: video.seriesName ?: TitleCleaner.getCleanTitle(video.name)

        val metadata = context.metadataMap[video.name]
            ?: context.metadataMap[cleanTitle]
            ?: video.seriesName?.let { context.metadataMap[it] ?: context.metadataMap[TitleCleaner.getCleanTitle(it)] }
            ?: context.metadataMap[TitleCleaner.getCleanTitle(video.name)]
            ?: episodes.firstNotNullOfOrNull { ep -> context.metadataMap[ep.name] ?: context.metadataMap[ep.cleanTitle] }

        return HistoryItemUiState(
            videoName = video.name,
            cleanTitle = cleanTitle,
            isAvailableOnDisk = true,
            isForceAvailable = isForceAvailable,
            watchedAt = maxWatchedAt,
            progressPercent = progressPct,
            positionMs = posMs,
            isWatched = isGroupWatched,
            metadata = metadata,
            isSeriesGroup = true,
            isTvSeries = video.isTvSeries,
            episodeLabel = episodeLabel,
        )
    }

    private fun processDiskStandalone(
        video: VideoItem,
        context: HistoryContext,
        handledNames: MutableSet<String>,
    ): HistoryItemUiState? {
        val name = video.name
        val clean = video.cleanTitle ?: TitleCleaner.getCleanTitle(name)
        handledNames.add(name)
        handledNames.add(clean)

        val pb = context.playbackMap[name] ?: context.playbackMap[clean]
        val we = context.watchedMap[name] ?: context.watchedMap[clean]
        val isWatched = we?.watched ?: false
        if (!isWatched && pb == null) return null

        val isForceAvailable = context.forceSet.contains(name) || context.forceSet.contains(clean)
        val durMs = video.duration * 1000L
        val posMs = pb?.positionMs ?: 0L
        val progressPercent = if (durMs > 0L) {
            (posMs.toFloat() / durMs.toFloat()).coerceIn(0f, 1f)
        } else {
            ((pb?.progressPct ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f)
        }
        val watchedAt = maxOf(pb?.lastPlayedAt ?: 0L, we?.watchedAt ?: 0L)
        val metadata = context.metadataMap[name] ?: context.metadataMap[clean] ?: context.metadataMap[TitleCleaner.getCleanTitle(name)]

        return HistoryItemUiState(
            videoName = name,
            cleanTitle = clean,
            isAvailableOnDisk = true,
            isForceAvailable = isForceAvailable,
            watchedAt = watchedAt,
            progressPercent = progressPercent,
            positionMs = posMs,
            isWatched = isWatched,
            metadata = metadata,
            isSeriesGroup = false,
            isTvSeries = false,
            episodeLabel = null,
        )
    }

    private fun processOrphanItems(
        remainingNames: List<String>,
        context: HistoryContext,
    ): List<HistoryItemUiState> {
        val orphanDummyVideos = remainingNames.map { name ->
            val raw = context.rawVideos.find { it.name == name || it.path == name }
            raw ?: VideoItem(name = name, url = "", path = "", size = 0, duration = 0)
        }
        val groupedOrphans = VideoGrouper.groupVideos(
            videos = orphanDummyVideos,
            movieCollections = emptyMap(),
            releaseDates = emptyMap(),
            whitelistedVideos = emptySet(),
        )

        return groupedOrphans.mapNotNull { item ->
            if (item.isSeriesGroup) {
                processOrphanGroup(item, context)
            } else {
                processOrphanStandalone(item, context)
            }
        }
    }

    private fun processOrphanGroup(
        item: VideoItem,
        context: HistoryContext,
    ): HistoryItemUiState? {
        val eps = item.episodes.orEmpty()
        val keys = (listOfNotNull(item.name, item.seriesName) + eps.map { it.name }).distinct()
        val wEntities = keys.mapNotNull { context.watchedMap[it] }
        val pbStates = keys.mapNotNull { context.playbackMap[it] }
        val isWatched = wEntities.any { it.watched }
        val hasPb = pbStates.isNotEmpty()
        if (!isWatched && !hasPb) return null

        val watchedAt = maxOf(
            wEntities.maxOfOrNull { it.watchedAt } ?: 0L,
            pbStates.maxOfOrNull { it.lastPlayedAt } ?: 0L,
        )
        val isGroupWatched = context.watchedMap[item.name]?.watched == true ||
            (eps.isNotEmpty() && eps.all { ep -> context.watchedMap[ep.name]?.watched == true })

        val isDiskAvail = eps.any { ep -> context.rawVideos.any { r -> r.name == ep.name || r.path == ep.name } }
        val isForceAvail = keys.any { context.forceSet.contains(it) }
        val cleanTitle = item.cleanTitle ?: item.seriesName ?: TitleCleaner.getCleanTitle(item.name)

        val latestPbEntry = keys.mapNotNull { key -> context.playbackMap[key]?.let { key to it } }
            .maxByOrNull { it.second.lastPlayedAt }

        val (progressPct, posMs) = if (latestPbEntry != null) {
            val (key, pb) = latestPbEntry
            val ep = eps.find { it.name == key }
            val durMs = (ep?.duration ?: 0L) * 1000L
            val p = if (durMs > 0L) {
                (pb.positionMs.toFloat() / durMs.toFloat()).coerceIn(0f, 1f)
            } else {
                ((pb.progressPct) / 100.0).toFloat().coerceIn(0f, 1f)
            }
            p to pb.positionMs
        } else {
            0f to 0L
        }

        val metadata = context.metadataMap[item.name]
            ?: context.metadataMap[cleanTitle]
            ?: item.seriesName?.let { context.metadataMap[it] ?: context.metadataMap[TitleCleaner.getCleanTitle(it)] }
            ?: context.metadataMap[TitleCleaner.getCleanTitle(item.name)]

        val episodeLabel = if (item.isTvSeries) {
            VideoUiSelectors.activeEpisodeLabel(
                video = item,
                progress = context.playbackMap.mapValues { it.value.progressPct },
                watched = context.watchedMap.mapValues { it.value.watched },
            )
        } else {
            val watchedCount = eps.count { context.watchedMap[it.name]?.watched == true }
            if (watchedCount > 0) "$watchedCount/${eps.size} vus" else null
        }

        return HistoryItemUiState(
            videoName = item.name,
            cleanTitle = cleanTitle,
            isAvailableOnDisk = isDiskAvail || isForceAvail,
            isForceAvailable = isForceAvail,
            watchedAt = watchedAt,
            progressPercent = progressPct,
            positionMs = posMs,
            isWatched = isGroupWatched,
            metadata = metadata,
            isSeriesGroup = true,
            isTvSeries = item.isTvSeries,
            episodeLabel = episodeLabel,
        )
    }

    private fun processOrphanStandalone(
        item: VideoItem,
        context: HistoryContext,
    ): HistoryItemUiState? {
        val name = item.name
        val pb = context.playbackMap[name]
        val we = context.watchedMap[name]
        val isWatched = we?.watched ?: false
        if (!isWatched && pb == null) return null

        val isDiskAvail = context.rawVideos.any { it.name == name || it.path == name }
        val isForceAvail = context.forceSet.contains(name)
        val cleanTitle = item.cleanTitle ?: TitleCleaner.getCleanTitle(name)
        val watchedAt = maxOf(pb?.lastPlayedAt ?: 0L, we?.watchedAt ?: 0L)
        val posMs = pb?.positionMs ?: 0L
        val durMs = item.duration * 1000L
        val progressPercent = if (durMs > 0L) {
            (posMs.toFloat() / durMs.toFloat()).coerceIn(0f, 1f)
        } else {
            ((pb?.progressPct ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f)
        }
        val metadata = context.metadataMap[name] ?: context.metadataMap[cleanTitle] ?: context.metadataMap[TitleCleaner.getCleanTitle(name)]

        return HistoryItemUiState(
            videoName = name,
            cleanTitle = cleanTitle,
            isAvailableOnDisk = isDiskAvail || isForceAvail,
            isForceAvailable = isForceAvail,
            watchedAt = watchedAt,
            progressPercent = progressPercent,
            positionMs = posMs,
            isWatched = isWatched,
            metadata = metadata,
            isSeriesGroup = false,
            isTvSeries = false,
            episodeLabel = null,
        )
    }

    fun removeFromHistory(videoName: String) {
        viewModelScope.launch {
            container.watchStateRepository.setWatched(videoName, false)
            container.watchStateRepository.clearProgress(videoName)
            val clean = TitleCleaner.getCleanTitle(videoName)
            container.watchStateRepository.setWatched(clean, false)
            container.watchStateRepository.clearProgress(clean)

            val grouped = container.videoRepository.getGroupedVideos()
            val group = grouped.find {
                it.name == videoName || it.seriesName == videoName ||
                    it.name.equals(videoName, ignoreCase = true) ||
                    it.seriesName?.equals(videoName, ignoreCase = true) == true ||
                    it.cleanTitle?.equals(clean, ignoreCase = true) == true
            }
            group?.episodes?.forEach { ep ->
                container.watchStateRepository.setWatched(ep.name, false)
                container.watchStateRepository.clearProgress(ep.name)
                ep.cleanTitle?.let {
                    container.watchStateRepository.setWatched(it, false)
                    container.watchStateRepository.clearProgress(it)
                }
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
            val grouped = container.videoRepository.getGroupedVideos()
            val group = grouped.find { it.name == videoName || it.seriesName == videoName }
            group?.episodes?.forEach { ep ->
                container.settingsRepository.toggleForceAvailable(ep.name)
            }
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
