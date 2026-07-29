package com.localstream.app.ui.library

import com.localstream.app.data.db.dao.PlaybackStateDao
import com.localstream.app.data.db.dao.TmdbMetadataDao
import com.localstream.app.data.db.dao.WatchedItemDao
import com.localstream.app.data.db.entity.PlaybackStateEntity
import com.localstream.app.data.db.entity.TmdbMetadataEntity
import com.localstream.app.data.db.entity.WatchedItemEntity
import com.localstream.app.data.remote.tmdb.TmdbApi
import com.localstream.app.data.remote.tmdb.dto.TmdbCollectionDetailsDto
import com.localstream.app.data.remote.tmdb.dto.TmdbMovieDetailsDto
import com.localstream.app.data.remote.tmdb.dto.TmdbSearchResponse
import com.localstream.app.data.remote.tmdb.dto.TmdbSeasonDetailsDto
import com.localstream.app.data.repository.SettingsRepository
import com.localstream.app.data.repository.TmdbRepository
import com.localstream.app.data.repository.VideoRepository
import com.localstream.app.data.repository.WatchStateRepository
import retrofit2.Response
import com.localstream.app.data.scanner.MediaScanner
import com.localstream.app.domain.model.MovieCollection
import com.localstream.app.domain.model.ResolutionFilter
import com.localstream.app.domain.model.SortBy
import com.localstream.app.domain.model.SubtitleEntry
import com.localstream.app.domain.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests du pipeline de [LibraryViewModel] : scan → état, tri/filtres
 * (parité web), recherche débouncée, reset de progression.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val film4k = VideoItem(
        url = "u1", name = "Avatar.2009.2160p.mkv", path = "/storage/emulated/0/Movies/Avatar.2009.2160p.mkv",
        size = 4000L, duration = 7200L,
    )
    private val filmSd = VideoItem(
        url = "u2", name = "Old.Movie.avi", path = "/storage/emulated/0/Movies/Old.Movie.avi",
        size = 1000L, duration = 3600L,
    )
    private val filmHd = VideoItem(
        url = "u3", name = "Blockbuster.2024.1080p.mkv", path = "/storage/emulated/0/Movies/Blockbuster.2024.1080p.mkv",
        size = 2000L, duration = 5400L,
    )
    private val raw = listOf(film4k, filmSd, filmHd)
    private val grouped = listOf(film4k, filmSd, filmHd)

    private lateinit var playbackDao: FakePlaybackStateDao
    private lateinit var viewModel: LibraryViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        playbackDao = FakePlaybackStateDao()
        val videoRepository = VideoRepository(FakeScanner(raw, grouped))
        val watchStateRepository = WatchStateRepository(FakeWatchedItemDao(), playbackDao)
        val tmdbRepository = TmdbRepository(
            tmdbApi = UnusedTmdbApi(),
            tmdbMetadataDao = FakeTmdbMetadataDao(),
            settingsRepository = SettingsRepository(),
        )
        viewModel = LibraryViewModel(
            videoRepository = videoRepository,
            watchStateRepository = watchStateRepository,
            tmdbRepository = tmdbRepository,
            settingsRepository = SettingsRepository(),
            ioDispatcher = testDispatcher,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `le scan publie les vidéos triées A-Z par défaut`() = runTest(testDispatcher) {
        viewModel.refreshLibrary()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.hasScanned)
        assertEquals(grouped, state.videos)
        assertEquals(
            listOf("Avatar.2009.2160p.mkv", "Blockbuster.2024.1080p.mkv", "Old.Movie.avi"),
            state.filteredSorted.map { it.name },
        )
    }

    @Test
    fun `le tri par taille ordonne par taille décroissante comme le web`() = runTest(testDispatcher) {
        viewModel.refreshLibrary()
        advanceUntilIdle()

        viewModel.setSortBy(SortBy.SIZE)
        advanceUntilIdle()

        assertEquals(
            listOf("Avatar.2009.2160p.mkv", "Blockbuster.2024.1080p.mkv", "Old.Movie.avi"),
            viewModel.uiState.value.filteredSorted.map { it.name },
        )
    }

    @Test
    fun `le filtre qualité 4K ne garde que les fichiers 2160p ou 4k`() = runTest(testDispatcher) {
        viewModel.refreshLibrary()
        advanceUntilIdle()

        viewModel.setFilterResolution(ResolutionFilter.FOUR_K)
        advanceUntilIdle()

        assertEquals(listOf("Avatar.2009.2160p.mkv"), viewModel.uiState.value.filteredSorted.map { it.name })
    }

    @Test
    fun `la recherche est débouncée et insensible à la casse`() = runTest(testDispatcher) {
        viewModel.refreshLibrary()
        advanceUntilIdle()

        viewModel.onSearchChange("BLOCK")
        advanceTimeBy(100)
        assertTrue(viewModel.uiState.value.searchResults.isEmpty()) // pas encore émis

        advanceTimeBy(300)
        assertEquals(listOf("Blockbuster.2024.1080p.mkv"), viewModel.uiState.value.searchResults.map { it.name })
    }

    @Test
    fun `resetProgress supprime la progression persistée`() = runTest(testDispatcher) {
        viewModel.resetProgress("Avatar.2009.2160p.mkv")
        advanceUntilIdle()

        assertEquals(listOf("Avatar.2009.2160p.mkv"), playbackDao.deletedNames)
    }

    // -------- Fakes --------

    private class FakeScanner(
        private val rawVideos: List<VideoItem>,
        private val groupedVideos: List<VideoItem>,
    ) : MediaScanner {
        override fun scanVideoFiles(): List<VideoItem> = rawVideos
        override fun scanSubtitleFiles(): List<SubtitleEntry> = emptyList()
        override fun scanAndGroup(
            whitelistedVideos: Set<String>,
            movieCollections: Map<String, MovieCollection>,
            releaseDates: Map<String, String>,
        ): List<VideoItem> = groupedVideos
    }

    private class FakeWatchedItemDao : WatchedItemDao {
        private val items = MutableStateFlow<List<WatchedItemEntity>>(emptyList())
        override fun observeWatchedItems(): Flow<List<WatchedItemEntity>> = items
        override suspend fun getAllWatchedItems(): List<WatchedItemEntity> = items.value
        override suspend fun upsert(item: WatchedItemEntity) {
            items.value = items.value.filterNot { it.name == item.name } + item
        }
        override suspend fun upsertAll(items: List<WatchedItemEntity>) = items.forEach { upsert(it) }
        override suspend fun deleteByName(name: String) {
            items.value = items.value.filterNot { it.name == name }
        }
        override suspend fun deleteAll() {
            items.value = emptyList()
        }
        override suspend fun findByName(name: String): WatchedItemEntity? = items.value.find { it.name == name }
    }

    private class FakePlaybackStateDao : PlaybackStateDao {
        val deletedNames = mutableListOf<String>()
        private val items = MutableStateFlow<List<PlaybackStateEntity>>(emptyList())
        override fun observeActivePlaybackStates(): Flow<List<PlaybackStateEntity>> = items
        override suspend fun getRecentlyPlayed(limit: Int): List<PlaybackStateEntity> = items.value.take(limit)
        override suspend fun getAll(): List<PlaybackStateEntity> = items.value
        override suspend fun upsert(state: PlaybackStateEntity) {
            items.value = items.value.filterNot { it.name == state.name } + state
        }
        override suspend fun upsertAll(states: List<PlaybackStateEntity>) = states.forEach { upsert(it) }
        override suspend fun deleteByName(name: String) {
            deletedNames += name
            items.value = items.value.filterNot { it.name == name }
        }
        override suspend fun deleteAll() {
            items.value = emptyList()
        }
        override suspend fun findByName(name: String): PlaybackStateEntity? = items.value.find { it.name == name }
    }

    private class FakeTmdbMetadataDao : TmdbMetadataDao {
        private val items = mutableMapOf<String, TmdbMetadataEntity>()
        override suspend fun getMetadata(queryKey: String): TmdbMetadataEntity? = items[queryKey]
        override suspend fun insertMetadata(entity: TmdbMetadataEntity) {
            items[entity.queryKey] = entity
        }
        override suspend fun deleteMetadata(queryKey: String) {
            items.remove(queryKey)
        }
        override suspend fun clearAll() = items.clear()
        override suspend fun getAll(): List<TmdbMetadataEntity> = items.values.toList()
    }

    /** Sans clé API configurée, aucune méthode distante ne doit être appelée. */
    private class UnusedTmdbApi : TmdbApi {
        private fun unused(): Nothing = throw UnsupportedOperationException("appel réseau inattendu")
        override suspend fun searchMulti(apiKey: String, query: String, language: String): TmdbSearchResponse = unused()
        override suspend fun searchMovie(apiKey: String, query: String, language: String): TmdbSearchResponse = unused()
        override suspend fun getMovieDetails(movieId: Long, apiKey: String, language: String): TmdbMovieDetailsDto = unused()
        override suspend fun getCollection(collectionId: Long, apiKey: String, language: String): TmdbCollectionDetailsDto = unused()
        override suspend fun getSeason(tvId: Long, seasonNumber: Int, apiKey: String, language: String): TmdbSeasonDetailsDto = unused()
        override suspend fun getPopular(apiKey: String, language: String): Response<TmdbSearchResponse> = unused()
    }
}
