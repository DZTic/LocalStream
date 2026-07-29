package com.localstream.app.ui.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.localstream.app.di.AppContainer
import com.localstream.app.domain.model.PlaylistInfo
import com.localstream.app.domain.model.TmdbMetadata
import com.localstream.app.domain.model.VideoItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PlaylistsUiState(
    val playlists: List<PlaylistInfo> = emptyList(),
    val selectedPlaylist: PlaylistInfo? = null,
    val selectedPlaylistVideos: List<VideoItem> = emptyList(),
    val metadata: Map<String, TmdbMetadata> = emptyMap(),
)

class PlaylistsViewModel(
    private val container: AppContainer,
) : ViewModel() {

    private val selectedPlaylistIdFlow = MutableStateFlow<String?>(null)

    val uiState: StateFlow<PlaylistsUiState> = combine(
        container.playlistRepository.observePlaylists,
        selectedPlaylistIdFlow,
        container.videoRepository.observeVideos,
    ) { playlists, selectedId, videos ->
        val selected = playlists.find { it.id == selectedId }
        val playlistVideos = selected?.videoNames?.mapNotNull { name ->
            videos.find { it.name == name }
        } ?: emptyList()

        PlaylistsUiState(
            playlists = playlists,
            selectedPlaylist = selected,
            selectedPlaylistVideos = playlistVideos,
            metadata = emptyMap(),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PlaylistsUiState(),
    )

    fun selectPlaylist(playlistId: String?) {
        selectedPlaylistIdFlow.value = playlistId
    }

    fun createPlaylist(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val created = container.playlistRepository.createPlaylist(name.trim())
            selectedPlaylistIdFlow.value = created.id
        }
    }

    fun deletePlaylist(playlistId: String) {
        viewModelScope.launch {
            container.playlistRepository.deletePlaylist(playlistId)
            if (selectedPlaylistIdFlow.value == playlistId) {
                selectedPlaylistIdFlow.value = null
            }
        }
    }

    fun removeFromPlaylist(playlistId: String, videoName: String) {
        viewModelScope.launch {
            container.playlistRepository.removeFromPlaylist(playlistId, videoName)
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return PlaylistsViewModel(container) as T
                }
            }
    }
}
