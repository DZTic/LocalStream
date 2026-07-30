package com.localstream.app.ui.playlists

import com.localstream.app.data.db.dao.PlaylistDao
import com.localstream.app.data.db.entity.PlaylistEntity
import com.localstream.app.data.db.entity.PlaylistItemEntity
import com.localstream.app.data.local.SubtitleCache
import com.localstream.app.data.remote.opensubtitles.OpenSubtitlesApi
import com.localstream.app.data.remote.opensubtitles.dto.OsDownloadRequest
import com.localstream.app.data.remote.opensubtitles.dto.OsDownloadResponse
import com.localstream.app.data.remote.opensubtitles.dto.OsLoginRequest
import com.localstream.app.data.remote.opensubtitles.dto.OsLoginResponse
import com.localstream.app.data.remote.opensubtitles.dto.OsSearchResponse
import com.localstream.app.data.repository.OpenSubtitlesRepository
import com.localstream.app.data.repository.PlaylistRepository
import com.localstream.app.data.repository.SettingsRepository
import com.localstream.app.data.repository.VideoRepository
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var playlistDao: FakePlaylistDao

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        playlistDao = FakePlaylistDao()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `createPlaylist ajoute une playlist et la selectionne`() = runTest(testDispatcher) {
        val playlistRepo = PlaylistRepository(playlistDao)
        val settingsRepo = SettingsRepository(dataStore = null)
        val osRepo = OpenSubtitlesRepository(UnusedOsApi(), settingsRepo, SubtitleCache(File("/tmp")))
        val videoRepo = VideoRepository(FakeScanner())

        val dummyContainer = DummyContainer(playlistRepo, osRepo, settingsRepo, videoRepo)
        val viewModel = PlaylistsViewModel(dummyContainer)

        backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        viewModel.createPlaylist("Ma Liste Test")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.playlists.size)
        assertEquals("Ma Liste Test", state.playlists.first().name)
        assertNotNull(state.selectedPlaylist)
    }

    private class DummyContainer(
        overridePlaylistRepo: PlaylistRepository,
        overrideOsRepo: OpenSubtitlesRepository,
        overrideSettingsRepo: SettingsRepository,
        overrideVideoRepo: VideoRepository,
    ) : AppContainer(
        context = null,
        overridePlaylistRepository = overridePlaylistRepo,
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

    private class UnusedOsApi : OpenSubtitlesApi {
        private fun unused(): Nothing = throw UnsupportedOperationException("appel inattendu")
        override suspend fun login(body: OsLoginRequest): OsLoginResponse = unused()
        override suspend fun search(query: String, languages: String): OsSearchResponse = unused()
        override suspend fun requestDownload(body: OsDownloadRequest, authorization: String): Response<OsDownloadResponse> = unused()
        override suspend fun downloadFile(url: String): Response<ResponseBody> = unused()
    }
}
