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

    private class DummyContainer(
        overrideWatchRepo: WatchStateRepository,
        overrideOsRepo: OpenSubtitlesRepository,
        overrideSettingsRepo: SettingsRepository,
        overrideVideoRepo: VideoRepository,
    ) : AppContainer(
        context = null,
        overrideWatchStateRepository = overrideWatchRepo,
        overrideOpenSubtitlesRepository = overrideOsRepo,
        overrideSettingsRepository = overrideSettingsRepo,
        overrideVideoRepository = overrideVideoRepo,
    )

    private class FakeScanner : MediaScanner {
        override fun scanVideoFiles(): List<VideoItem> = emptyList()
        override fun scanSubtitleFiles(): List<SubtitleEntry> = emptyList()
        override fun scanAndGroup(
            whitelistedVideos: Set<String>,
            movieCollections: Map<String, MovieCollection>,
            releaseDates: Map<String, String>,
        ): List<VideoItem> = emptyList()
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

    private class UnusedOsApi : OpenSubtitlesApi {
        private fun unused(): Nothing = throw UnsupportedOperationException("appel inattendu")
        override suspend fun login(body: OsLoginRequest): OsLoginResponse = unused()
        override suspend fun search(query: String, languages: String): OsSearchResponse = unused()
        override suspend fun requestDownload(body: OsDownloadRequest, authorization: String): Response<OsDownloadResponse> = unused()
        override suspend fun downloadFile(url: String): Response<ResponseBody> = unused()
    }
}
