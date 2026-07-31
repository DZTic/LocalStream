package com.localstream.app.ui.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.localstream.app.data.db.entity.PlaybackStateEntity
import com.localstream.app.di.AppContainer
import com.localstream.app.domain.VideoUiSelectors
import com.localstream.app.domain.model.PlaylistInfo
import com.localstream.app.domain.model.TmdbEpisode
import com.localstream.app.domain.model.TmdbMetadata
import com.localstream.app.domain.model.VideoItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class EpisodeUiState(
    val video: VideoItem,
    val tmdbEpisode: TmdbEpisode? = null,
    val isWatched: Boolean = false,
    val progressPercent: Float = 0f,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isExpanded: Boolean = false,
    val fallbackImageUrl: String? = null,
)

data class DetailsUiState(
    val videoGroup: VideoItem? = null,
    val metadata: TmdbMetadata? = null,
    val isWatched: Boolean = false,
    val watchPositionMs: Long = 0L,
    val watchDurationMs: Long = 0L,
    val activeEpisodeName: String? = null,
    val activeEpisodeLabel: String? = null,
    val userPlaylists: List<PlaylistInfo> = emptyList(),
    val selectedSeason: Int = 1,
    val availableSeasons: List<Int> = listOf(1),
    val episodes: List<EpisodeUiState> = emptyList(),
    val isLoadingTmdb: Boolean = false,
    val tmdbError: String? = null,
)

@Suppress("TooManyFunctions", "LongMethod", "UNCHECKED_CAST")
class DetailsViewModel(
    val id: String,
    private val container: AppContainer,
) : ViewModel() {

    private val selectedSeasonFlow = MutableStateFlow(1)
    private val expandedEpisodesFlow = MutableStateFlow<Set<String>>(emptySet())
    private val isLoadingTmdbFlow = MutableStateFlow(false)
    private val tmdbErrorFlow = MutableStateFlow<String?>(null)
    private val cachedMetadataFlow = MutableStateFlow<TmdbMetadata?>(null)
    private val cachedEpisodesFlow = MutableStateFlow<Map<String, TmdbEpisode>>(emptyMap())

    init {
        loadMetadata()
    }

    private fun loadMetadata() {
        viewModelScope.launch {
            val meta = container.tmdbRepository.getCachedMetadata(id)
            if (meta != null) {
                cachedMetadataFlow.value = meta
            }

            container.videoRepository.observeVideos.collect { videos ->
                val group = videos.find { it.name == id || it.seriesName == id }
                    ?: videos.find { it.name.lowercase() == id.lowercase() }
                    ?: return@collect

                val lookupName = if (group.isSeriesGroup && !group.seriesName.isNullOrEmpty()) {
                    group.seriesName
                } else {
                    group.name
                }

                group.episodes?.let { eps ->
                    loadEpisodesFromCache(lookupName, eps)
                }

                if (cachedMetadataFlow.value == null && !isLoadingTmdbFlow.value) {
                    isLoadingTmdbFlow.value = true
                    val result = container.tmdbRepository.fetchMetadataForVideo(group)
                    if (result.isSuccess) {
                        cachedMetadataFlow.value = result.getOrNull()
                        group.episodes?.let { eps ->
                            loadEpisodesFromCache(lookupName, eps)
                        }
                    }
                    isLoadingTmdbFlow.value = false
                }
            }
        }
    }

    private suspend fun loadEpisodesFromCache(lookupName: String, episodes: List<VideoItem>) {
        val map = mutableMapOf<String, TmdbEpisode>()
        episodes.forEachIndexed { index, ep ->
            val s = ep.season ?: 1
            val e = ep.episode ?: (index + 1)
            val cachedEp = container.tmdbRepository.getCachedEpisode(lookupName, s, e)
            if (cachedEp != null) {
                map[ep.name] = cachedEp
            }
        }
        cachedEpisodesFlow.value = map
    }

    val uiState: StateFlow<DetailsUiState> = combine(
        listOf<Flow<*>>(
            container.videoRepository.observeVideos,
            container.watchStateRepository.observeWatched,
            container.watchStateRepository.observePlaybackStates,
            container.playlistRepository.observePlaylists,
            selectedSeasonFlow,
            expandedEpisodesFlow,
            isLoadingTmdbFlow,
            tmdbErrorFlow,
            cachedMetadataFlow,
            cachedEpisodesFlow,
        )
    ) { args: Array<Any?> ->
        val videos = args[0] as List<VideoItem>
        val watchedSet = args[1] as Set<String>
        val playbackMap = args[2] as Map<String, PlaybackStateEntity>
        val playlists = args[3] as List<PlaylistInfo>
        val season = args[4] as Int
        val expandedSet = args[5] as Set<String>
        val isLoading = args[6] as Boolean
        val tmdbErr = args[7] as String?
        val meta = args[8] as TmdbMetadata?
        val cachedEpisodes = args[9] as Map<String, TmdbEpisode>

        val group = videos.find { it.name == id || it.seriesName == id }
            ?: videos.find { it.name.lowercase() == id.lowercase() }
            ?: VideoItem(name = id, path = "", size = 0, duration = 0)

        val isGroupWatched = watchedSet.contains(group.name) ||
            (group.episodes?.isNotEmpty() == true && group.episodes.all { watchedSet.contains(it.name) })

        val watchedMap = watchedSet.associateWith { true }
        val progressMap = playbackMap.mapValues { it.value.progressPct }
        val activeEp = VideoUiSelectors.getActiveEpisode(group, progressMap, watchedMap)
        val activeEpPb = activeEp?.let { playbackMap[it.name] }
        val posMs = activeEpPb?.positionMs ?: playbackMap[group.name]?.positionMs ?: 0L
        val durMs = (activeEp?.duration ?: group.duration) * 1000L
        val activeName = activeEp?.name ?: group.name
        val activeLabel = activeEp?.let { VideoUiSelectors.formatEpisodeLabel(group, it) }

        val seasons = group.episodes?.mapNotNull { it.season }?.distinct()?.sorted()?.ifEmpty { listOf(1) } ?: listOf(1)
        val currentSeason = if (seasons.contains(season)) season else seasons.firstOrNull() ?: 1

        val currentSeasonEpisodes = group.episodes?.filter { (it.season ?: 1) == currentSeason } ?: emptyList()

        val episodeUiStates = currentSeasonEpisodes.map { ep ->
            val epWatched = watchedSet.contains(ep.name)
            val epPb = playbackMap[ep.name]
            val epPos = epPb?.positionMs ?: 0L
            val epDur = ep.duration * 1000L
            val epProgress = if (epDur > 0L) (epPos.toFloat() / epDur.toFloat()).coerceIn(0f, 1f) else 0f

            val tmdbEp = cachedEpisodes[ep.name]

            EpisodeUiState(
                video = ep,
                tmdbEpisode = tmdbEp,
                isWatched = epWatched,
                progressPercent = epProgress,
                positionMs = epPos,
                durationMs = epDur,
                isExpanded = expandedSet.contains(ep.name),
                fallbackImageUrl = meta?.backdropUrl() ?: meta?.posterUrl(),
            )
        }

        DetailsUiState(
            videoGroup = group,
            metadata = meta,
            isWatched = isGroupWatched,
            watchPositionMs = posMs,
            watchDurationMs = durMs,
            activeEpisodeName = activeName,
            activeEpisodeLabel = activeLabel,
            userPlaylists = playlists,
            selectedSeason = currentSeason,
            availableSeasons = seasons,
            episodes = episodeUiStates,
            isLoadingTmdb = isLoading,
            tmdbError = tmdbErr,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DetailsUiState(),
    )

    fun selectSeason(season: Int) {
        selectedSeasonFlow.value = season
    }

    fun toggleEpisodeExpanded(episodeName: String) {
        val current = expandedEpisodesFlow.value
        expandedEpisodesFlow.value = if (current.contains(episodeName)) {
            current - episodeName
        } else {
            current + episodeName
        }
    }

    fun toggleGroupWatched() {
        val currentGroup = uiState.value.videoGroup ?: return
        val targetWatched = !uiState.value.isWatched
        viewModelScope.launch {
            container.watchStateRepository.setWatched(currentGroup.name, targetWatched)
            currentGroup.episodes?.forEach { ep ->
                container.watchStateRepository.setWatched(ep.name, targetWatched)
            }
        }
    }

    fun toggleEpisodeWatched(episodeName: String) {
        val currentGroup = uiState.value.videoGroup ?: return
        viewModelScope.launch {
            val episodes = currentGroup.episodes ?: emptyList()
            val ep = episodes.find { it.name == episodeName } ?: return@launch
            val isEpWatched = container.watchStateRepository.getWatched(ep.name)
            val newEpWatched = !isEpWatched
            container.watchStateRepository.setWatched(ep.name, newEpWatched)

            val updatedEpisodes = episodes.map { item ->
                if (item.name == ep.name) newEpWatched else container.watchStateRepository.getWatched(item.name)
            }
            if (updatedEpisodes.all { it }) {
                container.watchStateRepository.setWatched(currentGroup.name, true)
            } else {
                container.watchStateRepository.setWatched(currentGroup.name, false)
            }
        }
    }

    fun resetProgress(videoName: String) {
        viewModelScope.launch {
            container.watchStateRepository.savePlaybackState(
                videoName = videoName,
                positionMs = 0L,
                durationMs = 0L,
                progressPercent = 0,
            )
        }
    }

    fun refreshTmdbMetadata() {
        val group = uiState.value.videoGroup ?: return
        viewModelScope.launch {
            isLoadingTmdbFlow.value = true
            tmdbErrorFlow.value = null
            val result = container.tmdbRepository.fetchMetadataForVideo(group, forceRefresh = true)
            if (result.isSuccess) {
                cachedMetadataFlow.value = result.getOrNull()
            } else {
                tmdbErrorFlow.value = result.exceptionOrNull()?.message ?: "Erreur de récupération TMDB"
            }
            isLoadingTmdbFlow.value = false
        }
    }

    fun createPlaylist(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            container.playlistRepository.createPlaylist(name.trim())
        }
    }

    fun togglePlaylistMembership(playlistId: String) {
        val groupName = uiState.value.videoGroup?.name ?: return
        val playlist = uiState.value.userPlaylists.find { it.id == playlistId } ?: return
        viewModelScope.launch {
            if (playlist.videoNames.contains(groupName)) {
                container.playlistRepository.removeFromPlaylist(playlistId, groupName)
            } else {
                container.playlistRepository.addToPlaylist(playlistId, groupName)
            }
        }
    }

    companion object {
        fun factory(id: String, container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return DetailsViewModel(id, container) as T
                }
            }
    }
}
