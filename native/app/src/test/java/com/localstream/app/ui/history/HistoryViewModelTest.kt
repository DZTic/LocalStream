package com.localstream.app.ui.history

import com.localstream.app.data.db.dao.PlaybackStateDao
import com.localstream.app.data.db.dao.WatchedItemDao
import com.localstream.app.data.db.entity.PlaybackStateEntity
import com.localstream.app.data.db.entity.WatchedItemEntity
import com.localstream.app.data.local.SubtitleCache
import com.localstream.app.data.remote.opensubtitles.OpenSubtitlesApi
import com.localstream.app.data.remote.opensubtitles.dto.OsDownloadRequest
import com.localstream.app.data.remote.opensubtitles.dto.OsDownloadResponse
import com.localstream.app.data.remote.opensubtitles.dto.OsLoginRequest
import com.localstream.app.data.remote.opensubtitles.dto.OsLoginResponse
import com.localstream.app.data.remote.opensubtitles.dto.OsSearchResponse
import com.localstream.app.data.repository.OpenSubtitlesRepository
import com.localstream.app.data.repository.SettingsRepository
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var watchedDao: FakeWatchedItemDao
    private lateinit var playbackDao: FakePlaybackStateDao

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        watchedDao = FakeWatchedItemDao()
        playbackDao = FakePlaybackStateDao()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `addManualTitle ajoute la video dans le watched dao`() = runTest(testDispatcher) {
        val watchRepo = WatchStateRepository(watchedDao, playbackDao)
        val settingsRepo = SettingsRepository(dataStore = null)
        val osRepo = OpenSubtitlesRepository(UnusedOsApi(), settingsRepo, SubtitleCache(File("/tmp")))
        val videoRepo = VideoRepository(FakeScanner())

        val dummyContainer = DummyContainer(watchRepo, osRepo, settingsRepo, videoRepo)
        val viewModel = HistoryViewModel(dummyContainer)

        viewModel.addManualTitle("Inception.2010.mkv")
        advanceUntilIdle()

        assertTrue(watchedDao.items.value.any { it.name == "Inception" })
    }

    @Suppress("LongMethod")
    @Test
    fun `uiState regroupe les episodes d'une serie TV en un seul element dans l'historique`() = runTest(testDispatcher) {
        val watchRepo = WatchStateRepository(watchedDao, playbackDao)
        val settingsRepo = SettingsRepository(dataStore = null)
        val osRepo = OpenSubtitlesRepository(UnusedOsApi(), settingsRepo, SubtitleCache(File("/tmp")))

        val episode1 = VideoItem(name = "Breaking.Bad.S01E01.mkv", duration = 3600, path = "/storage/BB/S01E01.mkv", season = 1, episode = 1)
        val episode2 = VideoItem(name = "Breaking.Bad.S01E02.mkv", duration = 3600, path = "/storage/BB/S01E02.mkv", season = 1, episode = 2)
        val seriesGroup = VideoItem(
            name = "Breaking Bad",
            seriesName = "Breaking Bad",
            isSeriesGroup = true,
            isTvSeries = true,
            episodes = listOf(episode1, episode2),
        )
        val movie = VideoItem(name = "Inception.2010.mkv", duration = 7200, path = "/storage/Inception.mkv")

        val scanner = object : MediaScanner {
            override fun scanVideoFiles(): List<VideoItem> = listOf(episode1, episode2, movie)
            override fun scanSubtitleFiles(): List<SubtitleEntry> = emptyList()
            override fun scanAndGroup(
                whitelistedVideos: Set<String>,
                movieCollections: Map<String, MovieCollection>,
                releaseDates: Map<String, String>,
                rawVideos: List<VideoItem>?,
            ): List<VideoItem> = listOf(seriesGroup, movie)
        }
        val videoRepo = VideoRepository(scanner)
        videoRepo.scanAndLoad()

        val fakeTmdbDao = FakeTmdbDao()
        val tmdbMeta = com.localstream.app.domain.model.TmdbMetadata(
            queryKey = "Breaking Bad",
            tmdbId = 1396,
            title = "Breaking Bad",
            posterPath = "/breaking_bad.jpg",
        )
        fakeTmdbDao.insertMetadata(
            com.localstream.app.data.db.entity.TmdbMetadataEntity(
                queryKey = "Breaking Bad",
                json = kotlinx.serialization.json.Json.encodeToString(com.localstream.app.domain.model.TmdbMetadata.serializer(), tmdbMeta),
                fetchedAt = System.currentTimeMillis(),
            )
        )
        val tmdbRepo = com.localstream.app.data.repository.TmdbRepository(
            tmdbApi = com.localstream.app.di.NoOpTmdbApi(),
            tmdbMetadataDao = fakeTmdbDao,
            settingsRepository = settingsRepo,
        )

        watchedDao.upsert(WatchedItemEntity(name = "Breaking.Bad.S01E01.mkv", watched = true, watchedAt = 1000L))
        playbackDao.upsert(PlaybackStateEntity(name = "Breaking.Bad.S01E02.mkv", progressPct = 40.0, positionMs = 1440000L, lastPlayedAt = 1500L))
        playbackDao.upsert(PlaybackStateEntity(name = "Inception.2010.mkv", progressPct = 50.0, positionMs = 3600000L, lastPlayedAt = 2000L))

        val dummyContainer = DummyContainer(
            overrideWatchRepo = watchRepo,
            overrideOsRepo = osRepo,
            overrideSettingsRepo = settingsRepo,
            overrideVideoRepo = videoRepo,
            overrideTmdbRepo = tmdbRepo,
        )
        val viewModel = HistoryViewModel(dummyContainer)
        backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        val items = viewModel.uiState.value.items
        org.junit.Assert.assertEquals(2, items.size)

        val firstItem = items[0]
        org.junit.Assert.assertEquals("Inception.2010.mkv", firstItem.videoName)
        assertTrue(firstItem.isAvailableOnDisk)
        org.junit.Assert.assertEquals(2000L, firstItem.watchedAt)
        org.junit.Assert.assertFalse(firstItem.isSeriesGroup)

        val secondItem = items[1]
        org.junit.Assert.assertEquals("Breaking Bad", secondItem.videoName)
        assertTrue("La série doit être détectée sur disque", secondItem.isAvailableOnDisk)
        org.junit.Assert.assertEquals(1500L, secondItem.watchedAt)
        assertTrue(secondItem.isSeriesGroup)
        assertTrue(secondItem.isTvSeries)
        org.junit.Assert.assertNotNull(secondItem.metadata)
        org.junit.Assert.assertEquals("Breaking Bad", secondItem.metadata?.title)
    }

    @Test
    fun `uiState regroupe les films d'une saga MovieCollection en un seul element dans l'historique`() = runTest(testDispatcher) {
        val watchRepo = WatchStateRepository(watchedDao, playbackDao)
        val settingsRepo = SettingsRepository(dataStore = null)
        val osRepo = OpenSubtitlesRepository(UnusedOsApi(), settingsRepo, SubtitleCache(File("/tmp")))

        val movie1 = VideoItem(name = "Harry.Potter.1.2001.mkv", cleanTitle = "Harry Potter 1", duration = 7200, path = "/storage/HP1.mkv")
        val movie2 = VideoItem(name = "Harry.Potter.2.2002.mkv", cleanTitle = "Harry Potter 2", duration = 7200, path = "/storage/HP2.mkv")
        val sagaGroup = VideoItem(
            name = "Harry Potter Collection",
            seriesName = "Harry Potter Collection",
            cleanTitle = "Harry Potter Collection",
            isSeriesGroup = true,
            isTvSeries = false,
            episodes = listOf(movie1, movie2),
        )

        val scanner = object : MediaScanner {
            override fun scanVideoFiles(): List<VideoItem> = listOf(movie1, movie2)
            override fun scanSubtitleFiles(): List<SubtitleEntry> = emptyList()
            override fun scanAndGroup(
                whitelistedVideos: Set<String>,
                movieCollections: Map<String, MovieCollection>,
                releaseDates: Map<String, String>,
                rawVideos: List<VideoItem>?,
            ): List<VideoItem> = listOf(sagaGroup)
        }
        val videoRepo = VideoRepository(scanner)
        videoRepo.scanAndLoad()

        watchedDao.upsert(WatchedItemEntity(name = "Harry.Potter.1.2001.mkv", watched = true, watchedAt = 1000L))
        playbackDao.upsert(PlaybackStateEntity(name = "Harry.Potter.2.2002.mkv", progressPct = 30.0, positionMs = 2160000L, lastPlayedAt = 3000L))

        val dummyContainer = DummyContainer(
            overrideWatchRepo = watchRepo,
            overrideOsRepo = osRepo,
            overrideSettingsRepo = settingsRepo,
            overrideVideoRepo = videoRepo,
        )
        val viewModel = HistoryViewModel(dummyContainer)
        backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        val items = viewModel.uiState.value.items
        org.junit.Assert.assertEquals(1, items.size)

        val sagaItem = items[0]
        org.junit.Assert.assertEquals("Harry Potter Collection", sagaItem.videoName)
        assertTrue(sagaItem.isSeriesGroup)
        org.junit.Assert.assertFalse(sagaItem.isTvSeries)
        assertTrue(sagaItem.isAvailableOnDisk)
        org.junit.Assert.assertEquals(3000L, sagaItem.watchedAt)
    }

    @Test
    fun `removeFromHistory nettoie l'etat vu et la progression de lecture pour un groupe`() = runTest(testDispatcher) {
        val watchRepo = WatchStateRepository(watchedDao, playbackDao)
        val settingsRepo = SettingsRepository(dataStore = null)
        val osRepo = OpenSubtitlesRepository(UnusedOsApi(), settingsRepo, SubtitleCache(File("/tmp")))

        val ep1 = VideoItem(name = "BB.S01E01.mkv", path = "/BB1.mp4")
        val ep2 = VideoItem(name = "BB.S01E02.mkv", path = "/BB2.mp4")
        val series = VideoItem(
            name = "Breaking Bad",
            seriesName = "Breaking Bad",
            isSeriesGroup = true,
            isTvSeries = true,
            episodes = listOf(ep1, ep2),
        )
        val scanner = object : MediaScanner {
            override fun scanVideoFiles(): List<VideoItem> = listOf(ep1, ep2)
            override fun scanSubtitleFiles(): List<SubtitleEntry> = emptyList()
            override fun scanAndGroup(
                whitelistedVideos: Set<String>,
                movieCollections: Map<String, MovieCollection>,
                releaseDates: Map<String, String>,
                rawVideos: List<VideoItem>?,
            ): List<VideoItem> = listOf(series)
        }
        val videoRepo = VideoRepository(scanner)
        videoRepo.scanAndLoad()

        watchedDao.upsert(WatchedItemEntity(name = "BB.S01E01.mkv", watched = true))
        playbackDao.upsert(PlaybackStateEntity(name = "BB.S01E02.mkv", progressPct = 50.0, positionMs = 1000L))

        val dummyContainer = DummyContainer(watchRepo, osRepo, settingsRepo, videoRepo)
        val viewModel = HistoryViewModel(dummyContainer)
        advanceUntilIdle()

        viewModel.removeFromHistory("Breaking Bad")
        advanceUntilIdle()

        assertTrue(watchedDao.items.value.none { it.name == "BB.S01E01.mkv" })
        assertTrue(playbackDao.items.value.none { it.name == "BB.S01E02.mkv" })
    }

    private class DummyContainer(
        overrideWatchRepo: WatchStateRepository,
        overrideOsRepo: OpenSubtitlesRepository,
        overrideSettingsRepo: SettingsRepository,
        overrideVideoRepo: VideoRepository,
        overrideTmdbRepo: com.localstream.app.data.repository.TmdbRepository? = null,
    ) : AppContainer(
        context = null,
        overrideWatchStateRepository = overrideWatchRepo,
        overrideOpenSubtitlesRepository = overrideOsRepo,
        overrideSettingsRepository = overrideSettingsRepo,
        overrideVideoRepository = overrideVideoRepo,
        overrideTmdbRepository = overrideTmdbRepo,
    )

    private class FakeScanner : MediaScanner {
        override fun scanVideoFiles(): List<VideoItem> = emptyList()
        override fun scanSubtitleFiles(): List<SubtitleEntry> = emptyList()
        override fun scanAndGroup(
            whitelistedVideos: Set<String>,
            movieCollections: Map<String, MovieCollection>,
            releaseDates: Map<String, String>,
            rawVideos: List<VideoItem>?,
        ): List<VideoItem> = emptyList()
    }

    private class FakeTmdbDao : com.localstream.app.data.db.dao.TmdbMetadataDao {
        private val listFlow = MutableStateFlow<List<com.localstream.app.data.db.entity.TmdbMetadataEntity>>(emptyList())
        override fun observeAll(): Flow<List<com.localstream.app.data.db.entity.TmdbMetadataEntity>> = listFlow
        override suspend fun getMetadata(queryKey: String): com.localstream.app.data.db.entity.TmdbMetadataEntity? =
            listFlow.value.find { it.queryKey == queryKey }
        override suspend fun insertMetadata(entity: com.localstream.app.data.db.entity.TmdbMetadataEntity) {
            listFlow.value = listFlow.value.filterNot { it.queryKey == entity.queryKey } + entity
        }
        override suspend fun insertMetadataList(entities: List<com.localstream.app.data.db.entity.TmdbMetadataEntity>) {
            entities.forEach { insertMetadata(it) }
        }
        override suspend fun deleteMetadata(queryKey: String) {
            listFlow.value = listFlow.value.filterNot { it.queryKey == queryKey }
        }
        override suspend fun clearAll() { listFlow.value = emptyList() }
        override suspend fun getAll(): List<com.localstream.app.data.db.entity.TmdbMetadataEntity> = listFlow.value
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
        override suspend fun deleteByNames(names: List<String>) {
            items.value = items.value.filterNot { it.name in names }
        }
        override suspend fun deleteAll() { items.value = emptyList() }
        override suspend fun findByName(name: String): WatchedItemEntity? = items.value.find { it.name == name }
    }

    private class FakePlaybackStateDao : PlaybackStateDao {
        val items = MutableStateFlow<List<PlaybackStateEntity>>(emptyList())
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

    private class UnusedOsApi : OpenSubtitlesApi {
        private fun unused(): Nothing = throw UnsupportedOperationException("appel inattendu")
        override suspend fun login(body: OsLoginRequest): OsLoginResponse = unused()
        override suspend fun search(query: String, languages: String): OsSearchResponse = unused()
        override suspend fun requestDownload(body: OsDownloadRequest, authorization: String): Response<OsDownloadResponse> = unused()
        override suspend fun downloadFile(url: String): Response<ResponseBody> = unused()
    }
}
