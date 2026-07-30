package com.localstream.app.ui.subtitles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.localstream.app.di.AppContainer
import com.localstream.app.domain.model.SubtitleInfo
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SubtitlePickerUiState(
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val searchResults: List<SubtitleInfo> = emptyList(),
    val isDownloading: Boolean = false,
    val downloadedFile: File? = null,
    val error: String? = null,
)

class SubtitlePickerViewModel(
    private val container: AppContainer,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SubtitlePickerUiState())
    val uiState: StateFlow<SubtitlePickerUiState> = _uiState.asStateFlow()

    fun onQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun searchOpenSubtitles(rawQuery: String) {
        if (rawQuery.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSearching = true, error = null)
            val result = container.openSubtitlesRepository.search(rawQuery)
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    isSearching = false,
                    searchResults = result.getOrDefault(emptyList()),
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isSearching = false,
                    error = result.exceptionOrNull()?.message ?: "Erreur de recherche OpenSubtitles",
                )
            }
        }
    }

    fun downloadSubtitle(fileId: String, onDownloaded: (File) -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDownloading = true, error = null)
            val result = container.openSubtitlesRepository.downloadSubtitle(fileId)
            if (result.isSuccess) {
                val file = result.getOrThrow()
                _uiState.value = _uiState.value.copy(isDownloading = false, downloadedFile = file)
                onDownloaded(file)
            } else {
                _uiState.value = _uiState.value.copy(
                    isDownloading = false,
                    error = result.exceptionOrNull()?.message ?: "Erreur de téléchargement",
                )
            }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SubtitlePickerViewModel(container) as T
                }
            }
    }
}
