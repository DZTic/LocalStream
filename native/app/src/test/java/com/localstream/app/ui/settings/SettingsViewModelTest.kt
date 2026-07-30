package com.localstream.app.ui.settings

import com.localstream.app.data.local.SubtitleCache
import com.localstream.app.data.remote.opensubtitles.OpenSubtitlesApi
import com.localstream.app.data.remote.opensubtitles.dto.OsDownloadRequest
import com.localstream.app.data.remote.opensubtitles.dto.OsDownloadResponse
import com.localstream.app.data.remote.opensubtitles.dto.OsLoginRequest
import com.localstream.app.data.remote.opensubtitles.dto.OsLoginResponse
import com.localstream.app.data.remote.opensubtitles.dto.OsSearchResponse
import com.localstream.app.data.repository.OpenSubtitlesRepository
import com.localstream.app.data.repository.SettingsRepository
import com.localstream.app.di.AppContainer
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.ResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `saveTmdbApiKey mémorise la clé API`() = runTest(testDispatcher) {
        val settingsRepo = SettingsRepository(dataStore = null)
        val osRepo = OpenSubtitlesRepository(UnusedOsApi(), settingsRepo, SubtitleCache(File("/tmp")))
        val dummyContainer = DummyContainer(settingsRepo, osRepo)
        val viewModel = SettingsViewModel(dummyContainer)

        viewModel.saveTmdbApiKey("my_secret_tmdb_key")
        advanceUntilIdle()

        assertEquals("my_secret_tmdb_key", settingsRepo.getTmdbApiKey())
    }

    private class DummyContainer(
        overrideSettingsRepo: SettingsRepository,
        overrideOsRepo: OpenSubtitlesRepository,
    ) : AppContainer(
        context = null,
        overrideSettingsRepository = overrideSettingsRepo,
        overrideOpenSubtitlesRepository = overrideOsRepo,
    )

    private class UnusedOsApi : OpenSubtitlesApi {
        private fun unused(): Nothing = throw UnsupportedOperationException("appel inattendu")
        override suspend fun login(body: OsLoginRequest): OsLoginResponse = unused()
        override suspend fun search(query: String, languages: String): OsSearchResponse = unused()
        override suspend fun requestDownload(body: OsDownloadRequest, authorization: String): Response<OsDownloadResponse> = unused()
        override suspend fun downloadFile(url: String): Response<ResponseBody> = unused()
    }
}
