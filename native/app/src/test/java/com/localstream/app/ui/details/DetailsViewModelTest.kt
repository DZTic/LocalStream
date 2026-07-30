package com.localstream.app.ui.details

import com.localstream.app.data.db.dao.PlaybackStateDao
import com.localstream.app.data.db.dao.PlaylistDao
import com.localstream.app.data.db.dao.TmdbMetadataDao
import com.localstream.app.data.db.dao.WatchedItemDao
import com.localstream.app.data.db.entity.PlaybackStateEntity
import com.localstream.app.data.db.entity.PlaylistEntity
import com.localstream.app.data.db.entity.PlaylistItemEntity
import com.localstream.app.data.db.entity.TmdbMetadataEntity
import com.localstream.app.data.db.entity.WatchedItemEntity
import com.localstream.app.data.local.SubtitleCache
import com.localstream.app.data.remote.opensubtitles.OpenSubtitlesApi
import com.localstream.app.data.remote.opensubtitles.dto.OsDownloadRequest
import com.localstream.app.data.remote.opensubtitles.dto.OsDownloadResponse
import com.localstream.app.data.remote.opensubtitles.dto.OsLoginRequest
import com.localstream.app.data.remote.opensubtitles.dto.OsLoginResponse
import com.localstream.app.data.remote.opensubtitles.dto.OsSearchResponse
import com.localstream.app.data.remote.tmdb.TmdbApi
import com.localstream.app.data.remote.tmdb.dto.TmdbCollectionDetailsDto
import com.localstream.app.data.remote.tmdb.dto.TmdbMovieDetailsDto
import com.localstream.app.data.remote.tmdb.dto.TmdbSearchResponse
import com.localstream.app.data.remote.tmdb.dto.TmdbSeasonDetailsDto
import com.localstream.app.data.repository.OpenSubtitlesRepository
import com.localstream.app.data.repository.PlaylistRepository
import com.localstream.app.data.repository.SettingsRepository
import com.localstream.app.data.repository.TmdbRepository
import com.localstream.app.data.repository.VideoRepository
import com.localstream.app.data.repository.WatchStateRepository
import com.localstream.app.data.scanner.MediaScanner
import com.localstream.app.di.AppContainer
import com.localstream.app.domain.model.MovieCollection
import com.localstream.app.domain.model.SubtitleEntry
import com.localstream.app.domain.model.VideoItem
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.ResponseBody
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class DetailsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val ep1 = VideoItem(name = "Breaking.Bad.S01E01.mkv", path = "/p1", size = 500L, duration = 2400L, season = 1, episode = 1)
    private val ep2 = VideoItem(name = "Breaking.Bad.S01E02.mkv", path = "/p2", size = 500L, duration = 2400L, season = 1, episode = 2)
    private val seriesGroup = VideoItem(
        name = "Breaking Bad",
        path = "",
        size = 1000L,
        duration = 4800L,
        isSeriesGroup = true,
        seriesName = "Breaking Bad",
        episodes = listOf(ep1, ep2),
    )

    private lateinit var watchedDao: FakeWatchedItemDao
    private lateinit var playbackDao: FakePlaybackStateDao
    private lateinit var playlistDao: FakePlaylistDao

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        watchedDao = FakeWatchedItemDao()
        playbackDao = FakePlaybackStateDao()
        playlistDao = FakePlaylistDao()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `toggleGroupWatched coche et decoche la serie et ses episodes`() = runTest(testDispatcher) {
        val videoRepo = VideoRepository(FakeScanner(listOf(seriesGroup), listOf(seriesGroup)))
        val watchRepo = WatchStateRepository(watchedDao, playbackDao)
        val playlistRepo = PlaylistRepository(playlistDao)
        val settingsRepo = SettingsRepository(dataStore = null)
        val tmdbRepo = TmdbRepository(UnusedTmdbApi(), FakeTmdbMetadataDao(), settingsRepo)
        val osRepo = OpenSubtitlesRepository(UnusedOsApi(), settingsRepo, SubtitleCache(File("/tmp")))

        videoRepo.scanAndLoad()

        val dummyContainer = DummyContainer(videoRepo, watchRepo, playlistRepo, tmdbRepo, settingsRepo, osRepo)
        val viewModel = DetailsViewModel("Breaking Bad", dummyContainer)

        backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isWatched)

        viewModel.toggleGroupWatched()
        advanceUntilIdle()

        assertTrue(watchedDao.items.value.any { it.name == "Breaking Bad" })
        assertTrue(watchedDao.items.value.any { it.name == "Breaking.Bad.S01E01.mkv" })
    }

    private class DummyContainer(
        videoRepo: VideoRepository,
        watchRepo: WatchStateRepository,
        playlistRepo: PlaylistRepository,
        tmdbRepo: TmdbRepository,
        settingsRepo: SettingsRepository,
        osRepo: OpenSubtitlesRepository,
    ) : AppContainer(
        context = null,
        overrideVideoRepository = videoRepo,
        overrideWatchStateRepository = watchRepo,
        overridePlaylistRepository = playlistRepo,
        overrideTmdbRepository = tmdbRepo,
        overrideSettingsRepository = settingsRepo,
        overrideOpenSubtitlesRepository = osRepo,
    )

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
        val items = MutableStateFlow<List<WatchedItemEntity>>(emptyList())
        override fun observeWatchedItems(): Flow<List<WatchedItemEntity>> = items
        override suspend fun getAllWatchedItems(): List<WatchedItemEntity> = items.value
        override suspend fun upsert(item: WatchedItemEntity) {
            items.value = items.value.filterNot { it.name == item.name } + item
        }
        override suspend fun upsertAll(items: List<WatchedItemEntity>) = items.forEach { upsert(it) }
        override suspend fun deleteByName(name: String) {
            items.value = items.value.filterNot { it.name == name }
        }
        override suspend fun deleteAll() { items.value = emptyList() }
        override suspend fun findByName(name: String): WatchedItemEntity? = items.value.find { it.name == name }
    }

    private class FakePlaybackStateDao : PlaybackStateDao {
        private val items = MutableStateFlow<List<PlaybackStateEntity>>(emptyList())
        override fun observeActivePlaybackStates(): Flow<List<PlaybackStateEntity>> = items
        override suspend fun getRecentlyPlayed(limit: Int): List<PlaybackStateEntity> = items.value.take(limit)
        override suspend fun getAll(): List<PlaybackStateEntity> = items.value
        override suspend fun upsert(state: PlaybackStateEntity) {
            items.value = items.value.filterNot { it.name == state.name } + state
        }
        override suspend fun upsertAll(states: List<PlaybackStateEntity>) = states.forEach { upsert(it) }
        override suspend fun deleteByName(name: String) {
            items.value = items.value.filterNot { it.name == name }
        }
        override suspend fun deleteAll() { items.value = emptyList() }
        override suspend fun findByName(name: String): PlaybackStateEntity? = items.value.find { it.name == name }
    }

    private class FakePlaylistDao : PlaylistDao {
        private val playlists = MutableStateFlow<List<PlaylistEntity>>(emptyList())
        private val items = mutableListOf<PlaylistItemEntity>()

        override fun observePlaylists(): Flow<List<PlaylistEntity>> = playlists
        override suspend fun getAllPlaylists(): List<PlaylistEntity> = playlists.value
        override suspend fun upsertPlaylist(playlist: PlaylistEntity) {
            playlists.value = playlists.value.filterNot { it.id == playlist.id } + playlist
        }
        override suspend fun upsertPlaylists(playlists: List<PlaylistEntity>) {
            playlists.forEach { upsertPlaylist(it) }
        }
        override suspend fun deletePlaylist(id: String) {
            playlists.value = playlists.value.filterNot { it.id == id }
            items.removeAll { it.playlistId == id }
        }
        override suspend fun deleteAllPlaylists() {
            playlists.value = emptyList()
            items.clear()
        }

        override fun observeItems(playlistId: String): Flow<List<PlaylistItemEntity>> =
            MutableStateFlow(items.filter { it.playlistId == playlistId })
        override suspend fun getItems(playlistId: String): List<PlaylistItemEntity> =
            items.filter { it.playlistId == playlistId }
        override suspend fun upsertItem(item: PlaylistItemEntity) {
            items.removeAll { it.playlistId == item.playlistId && it.videoName == item.videoName }
            items.add(item)
        }
        override suspend fun upsertItems(items: List<PlaylistItemEntity>) {
            items.forEach { upsertItem(it) }
        }
        override suspend fun deleteItem(playlistId: String, videoName: String) {
            items.removeAll { it.playlistId == playlistId && it.videoName == videoName }
        }
        override suspend fun deleteAllItems(playlistId: String) {
            items.removeAll { it.playlistId == playlistId }
        }
        override suspend fun getAllItems(): List<PlaylistItemEntity> = items
    }

    private class FakeTmdbMetadataDao : TmdbMetadataDao {
        private val items = mutableMapOf<String, TmdbMetadataEntity>()
        override suspend fun getMetadata(queryKey: String): TmdbMetadataEntity? = items[queryKey]
        override suspend fun insertMetadata(entity: TmdbMetadataEntity) { items[entity.queryKey] = entity }
        override suspend fun deleteMetadata(queryKey: String) { items.remove(queryKey) }
        override suspend fun clearAll() = items.clear()
        override suspend fun getAll(): List<TmdbMetadataEntity> = items.values.toList()
    }

    private class UnusedTmdbApi : TmdbApi {
        private fun unused(): Nothing = throw UnsupportedOperationException("appel inattendu")
        override suspend fun searchMulti(apiKey: String, query: String, language: String): TmdbSearchResponse = unused()
        override suspend fun searchMovie(apiKey: String, query: String, language: String): TmdbSearchResponse = unused()
        override suspend fun getMovieDetails(movieId: Long, apiKey: String, language: String): TmdbMovieDetailsDto = unused()
        override suspend fun getCollection(collectionId: Long, apiKey: String, language: String): TmdbCollectionDetailsDto = unused()
        override suspend fun getSeason(tvId: Long, seasonNumber: Int, apiKey: String, language: String): TmdbSeasonDetailsDto = unused()
        override suspend fun getPopular(apiKey: String, language: String): Response<TmdbSearchResponse> = unused()
    }

    private class UnusedOsApi : OpenSubtitlesApi {
        private fun unused(): Nothing = throw UnsupportedOperationException("appel inattendu")
        override suspend fun login(body: OsLoginRequest): OsLoginResponse = unused()
        override suspend fun search(query: String, languages: String): OsSearchResponse = unused()
        override suspend fun requestDownload(body: OsDownloadRequest, authorization: String): Response<OsDownloadResponse> = unused()
        override suspend fun downloadFile(url: String): Response<ResponseBody> = unused()
    }
}
