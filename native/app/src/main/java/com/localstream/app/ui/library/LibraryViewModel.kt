package com.localstream.app.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.localstream.app.data.repository.SettingsRepository
import com.localstream.app.data.repository.TmdbRepository
import com.localstream.app.data.repository.VideoRepository
import com.localstream.app.data.repository.WatchStateRepository
import com.localstream.app.di.AppContainer
import com.localstream.app.domain.VideoFilterSorter
import com.localstream.app.domain.VideoUiSelectors
import com.localstream.app.domain.model.FilterSortOptions
import com.localstream.app.domain.model.MovieCollection
import com.localstream.app.domain.model.ResolutionFilter
import com.localstream.app.domain.model.SortBy
import com.localstream.app.domain.model.TmdbMetadata
import com.localstream.app.domain.model.VideoItem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * État de la bibliothèque partagé par les écrans Accueil / Recherche / Bibliothèque.
 * [videos] = groupé (séries + sagas) ; [filteredSorted] = après tri/filtres
 * (VideoFilterSorter, Phase 2) ; [metadata] = métadonnées TMDB indexées par clé
 * de lookup (nom de série pour les groupes, nom de fichier sinon).
 */
data class LibraryUiState(
    val isScanning: Boolean = false,
    val hasScanned: Boolean = false,
    val isFetchingMetadata: Boolean = false,
    val videos: List<VideoItem> = emptyList(),
    val filteredSorted: List<VideoItem> = emptyList(),
    val metadata: Map<String, TmdbMetadata> = emptyMap(),
    val videoDurations: Map<String, Long> = emptyMap(),
    val watched: Map<String, Boolean> = emptyMap(),
    val progress: Map<String, Double> = emptyMap(),
    val sortBy: SortBy = SortBy.ALPHA,
    val filterGenre: Int? = null,
    val filterResolution: ResolutionFilter = ResolutionFilter.ALL,
    val searchResults: List<VideoItem> = emptyList(),
    val hasTmdbKey: Boolean = false,
    val tmdbBannerDismissed: Boolean = false,
) {
    /** Dates de sortie par clé de lookup (tri par date). */
    val releaseDates: Map<String, String>
        get() = metadata.mapNotNull { (key, meta) ->
            meta.releaseDate?.takeIf { it.isNotBlank() }?.let { key to it }
        }.toMap()

    /** Genres TMDB par clé de lookup (filtre par genre). */
    val videoGenres: Map<String, List<Int>>
        get() = metadata.mapValues { it.value.genreIds }
}

/**
 * ViewModel maître de la bibliothèque (Phase 7) : pilote le scan MediaStore
 * (Phase 3), l'enrichissement TMDB (Phase 5) et observe l'état de visionnage
 * (Phase 4). Partagé entre les écrans via le scope de l'activité.
 */
@Suppress("TooManyFunctions")
class LibraryViewModel(
    private val videoRepository: VideoRepository,
    private val watchStateRepository: WatchStateRepository,
    private val tmdbRepository: TmdbRepository,
    private val settingsRepository: SettingsRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    /** Requête de recherche immédiate (champ texte) ; les résultats sont débouncés. */
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private var scanJob: Job? = null

    init {
        observeWatchState()
        observePreferences()
        observeSearchQuery()
    }

    // -------- Pipeline scan + métadonnées --------

    /** Lance le scan complet si aucun n'est en cours (appelé une fois la permission accordée). */
    fun refreshLibrary() {
        if (scanJob?.isActive == true) return
        scanJob = viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true) }
            try {
                val whitelist = settingsRepository.whitelistedVideos.firstOrNull() ?: emptySet()
                val hasKey = settingsRepository.getTmdbApiKey().isNotBlank()
                _uiState.update { it.copy(hasTmdbKey = hasKey) }

                val grouped = withContext(ioDispatcher) {
                    videoRepository.scanAndLoad(whitelistedVideos = whitelist)
                }
                publishVideos(grouped)

                // Cache Room d'abord : les affiches s'affichent immédiatement.
                val cached = loadCachedMetadata(grouped)
                if (cached.isNotEmpty()) {
                    _uiState.update { it.copy(metadata = it.metadata + cached).withDerived() }
                }

                if (hasKey) {
                    _uiState.update { it.copy(isFetchingMetadata = true) }
                    enrichAndRegroup(grouped, whitelist)
                }
            } finally {
                _uiState.update {
                    it.copy(isScanning = false, hasScanned = true, isFetchingMetadata = false)
                }
            }
        }
    }

    private fun publishVideos(grouped: List<VideoItem>) {
        val durations = videoRepository.getRawVideos().associate { it.name to it.duration }
        _uiState.update {
            it.copy(videos = grouped, videoDurations = durations).withDerived()
        }
    }

    private suspend fun loadCachedMetadata(videos: List<VideoItem>): Map<String, TmdbMetadata> =
        withContext(ioDispatcher) {
            videos.mapNotNull { video ->
                val key = VideoUiSelectors.metadataKey(video)
                tmdbRepository.getCachedMetadata(key)?.let { key to it }
            }.toMap()
        }

    /**
     * Enrichit via le réseau (par paquets, mises à jour progressives), puis
     * regroupe les films en sagas grâce aux collections TMDB (2ᵉ passe de
     * regroupement, comme le web qui groupe après récupération des métadonnées).
     */
    private suspend fun enrichAndRegroup(grouped: List<VideoItem>, whitelist: Set<String>) {
        fetchMetadataIntoState(grouped)

        val meta = _uiState.value.metadata
        val collections = meta.values
            .filter { it.collectionId != null && !it.collectionName.isNullOrBlank() }
            .associate {
                it.queryKey to MovieCollection(
                    id = it.collectionId.toString(),
                    name = it.collectionName.orEmpty(),
                )
            }

        if (collections.isEmpty()) return

        val regrouped = withContext(ioDispatcher) {
            videoRepository.regroup(
                movieCollections = collections,
                releaseDates = _uiState.value.releaseDates,
                whitelistedVideos = whitelist,
            )
        }
        if (regrouped.map { it.name } != _uiState.value.videos.map { it.name }) {
            publishVideos(regrouped)
            // Enrichit les nouveaux groupes (sagas) absents de la map.
            val missing = regrouped.filter {
                VideoUiSelectors.metadataKey(it) !in _uiState.value.metadata
            }
            fetchMetadataIntoState(missing)
        }
    }

    /** Récupère les métadonnées par paquets et publie chaque paquet dès réception. */
    private suspend fun fetchMetadataIntoState(videos: List<VideoItem>) {
        for (chunk in videos.chunked(METADATA_CHUNK_SIZE)) {
            val results = coroutineScope {
                chunk.map { video ->
                    async(ioDispatcher) {
                        tmdbRepository.fetchMetadataForVideo(video).getOrNull()
                    }
                }.awaitAll()
            }.filterNotNull()
            if (results.isNotEmpty()) {
                val additions = results.associate { it.queryKey to it }
                _uiState.update { it.copy(metadata = it.metadata + additions).withDerived() }
            }
        }
    }

    // -------- Observation des états persistés --------

    private fun observeWatchState() {
        viewModelScope.launch {
            watchStateRepository.watchedItems.collect { watched ->
                _uiState.update { it.copy(watched = watched).withDerived() }
            }
        }
        viewModelScope.launch {
            watchStateRepository.activePlaybackStates.collect { progress ->
                _uiState.update { it.copy(progress = progress).withDerived() }
            }
        }
    }

    private fun observePreferences() {
        viewModelScope.launch {
            settingsRepository.tmdbBannerDismissed.collect { dismissed ->
                _uiState.update { it.copy(tmdbBannerDismissed = dismissed) }
            }
        }
    }

    /** Résultats de recherche débouncés (250 ms comme le web) sur la liste filtrée/triée. */
    @OptIn(FlowPreview::class)
    private fun observeSearchQuery() {
        viewModelScope.launch {
            _searchQuery.debounce(SEARCH_DEBOUNCE_MS).collectLatest { query ->
                _uiState.update {
                    it.copy(searchResults = VideoUiSelectors.filterByQuery(it.filteredSorted, query))
                }
            }
        }
    }

    // -------- Actions utilisateur --------

    fun onSearchChange(query: String) {
        _searchQuery.value = query
    }

    fun setSortBy(sortBy: SortBy) {
        _uiState.update { it.copy(sortBy = sortBy).withDerived() }
    }

    fun setFilterGenre(genreId: Int?) {
        _uiState.update { it.copy(filterGenre = genreId).withDerived() }
    }

    fun setFilterResolution(resolution: ResolutionFilter) {
        _uiState.update { it.copy(filterResolution = resolution).withDerived() }
    }

    /** Clic sur le logo : retour à l'état d'accueil (filtres et recherche réinitialisés). */
    fun resetFilters() {
        _searchQuery.value = ""
        _uiState.update {
            it.copy(
                sortBy = SortBy.ALPHA,
                filterGenre = null,
                filterResolution = ResolutionFilter.ALL,
                searchResults = emptyList(),
            ).withDerived()
        }
    }

    fun resetProgress(videoName: String) {
        viewModelScope.launch { watchStateRepository.clearProgress(videoName) }
    }

    fun dismissTmdbBanner() {
        viewModelScope.launch { settingsRepository.dismissTmdbBanner() }
    }

    // -------- Dérivation filtre/tri --------

    private fun LibraryUiState.withDerived(): LibraryUiState {
        val filtered = VideoFilterSorter.filterAndSortVideos(
            videos,
            FilterSortOptions(
                sortBy = sortBy,
                filterGenre = filterGenre,
                filterResolution = filterResolution,
                releaseDates = releaseDates,
                videoGenres = videoGenres,
                videoDurations = videoDurations,
                watchedVideos = watched,
            ),
        )
        return copy(
            filteredSorted = filtered,
            searchResults = VideoUiSelectors.filterByQuery(filtered, _searchQuery.value),
        )
    }

    companion object {
        private const val METADATA_CHUNK_SIZE = 8
        private const val SEARCH_DEBOUNCE_MS = 250L

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                LibraryViewModel(
                    videoRepository = container.videoRepository,
                    watchStateRepository = container.watchStateRepository,
                    tmdbRepository = container.tmdbRepository,
                    settingsRepository = container.settingsRepository,
                )
            }
        }
    }
}
