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
import com.localstream.app.data.repository.TmdbRepository
import com.localstream.app.di.AppContainer
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

        viewModel.saveTmdbApiKey("  my_secret_tmdb_key  ")
        advanceUntilIdle()

        assertEquals("my_secret_tmdb_key", settingsRepo.getTmdbApiKey())
    }

    @Test
    fun `testTmdbApiKey met à jour l'état de succès et d'échec`() = runTest(testDispatcher) {
        val settingsRepo = SettingsRepository(dataStore = null)
        val osRepo = OpenSubtitlesRepository(UnusedOsApi(), settingsRepo, SubtitleCache(File("/tmp")))
        val fakeTmdbRepo = FakeTestTmdbRepo(settingsRepo)
        val dummyContainer = DummyContainer(settingsRepo, osRepo, fakeTmdbRepo)
        val viewModel = SettingsViewModel(dummyContainer)

        val collectJob = launch { viewModel.uiState.collect() }

        fakeTmdbRepo.shouldSucceed = true
        viewModel.testTmdbApiKey("valid_key")
        advanceUntilIdle()

        assertEquals("Clé API valide !", viewModel.uiState.value.tmdbTestResult)
        assertEquals(true, viewModel.uiState.value.tmdbTestSuccess)

        fakeTmdbRepo.shouldSucceed = false
        fakeTmdbRepo.errorMessage = "Clé API TMDB invalide (erreur 401)"
        viewModel.testTmdbApiKey("invalid_key")
        advanceUntilIdle()

        assertEquals("Clé API TMDB invalide (erreur 401)", viewModel.uiState.value.tmdbTestResult)
        assertEquals(false, viewModel.uiState.value.tmdbTestSuccess)

        collectJob.cancel()
    }

    private class DummyContainer(
        overrideSettingsRepo: SettingsRepository,
        overrideOsRepo: OpenSubtitlesRepository,
        overrideTmdbRepo: TmdbRepository? = null,
    ) : AppContainer(
        context = null,
        overrideSettingsRepository = overrideSettingsRepo,
        overrideOpenSubtitlesRepository = overrideOsRepo,
        overrideTmdbRepository = overrideTmdbRepo,
    )

    private class FakeTestTmdbRepo(settingsRepo: SettingsRepository) : TmdbRepository(
        tmdbApi = UnusedTmdbApi(),
        tmdbMetadataDao = FakeDao(),
        settingsRepository = settingsRepo,
    ) {
        var shouldSucceed = true
        var errorMessage = "Clé API TMDB invalide"

        override suspend fun testApiKey(apiKeyOverride: String?): Result<Boolean> {
            return if (shouldSucceed) Result.success(true) else Result.failure(Exception(errorMessage))
        }
    }

    private class FakeDao : com.localstream.app.data.db.dao.TmdbMetadataDao {
        override suspend fun getMetadata(queryKey: String): com.localstream.app.data.db.entity.TmdbMetadataEntity? = null
        override suspend fun insertMetadata(entity: com.localstream.app.data.db.entity.TmdbMetadataEntity) {}
        override suspend fun deleteMetadata(queryKey: String) {}
        override suspend fun clearAll() {}
        override suspend fun getAll(): List<com.localstream.app.data.db.entity.TmdbMetadataEntity> = emptyList()
    }

    private class UnusedTmdbApi : com.localstream.app.data.remote.tmdb.TmdbApi {
        private fun unused(): Nothing = throw UnsupportedOperationException("appel inattendu")
        override suspend fun searchMulti(apiKey: String, query: String, language: String, overrideKey: String?): com.localstream.app.data.remote.tmdb.dto.TmdbSearchResponse = unused()
        override suspend fun searchMovie(apiKey: String, query: String, language: String, primaryReleaseYear: Int?, year: Int?, overrideKey: String?): com.localstream.app.data.remote.tmdb.dto.TmdbSearchResponse = unused()
        override suspend fun getMovieDetails(movieId: Long, apiKey: String, language: String, overrideKey: String?): com.localstream.app.data.remote.tmdb.dto.TmdbMovieDetailsDto = unused()
        override suspend fun getCollection(collectionId: Long, apiKey: String, language: String, overrideKey: String?): com.localstream.app.data.remote.tmdb.dto.TmdbCollectionDetailsDto = unused()
        override suspend fun getSeason(tvId: Long, seasonNumber: Int, apiKey: String, language: String, overrideKey: String?): com.localstream.app.data.remote.tmdb.dto.TmdbSeasonDetailsDto = unused()
        override suspend fun getPopular(apiKey: String, language: String, overrideKey: String?): Response<com.localstream.app.data.remote.tmdb.dto.TmdbSearchResponse> = unused()
    }

    private class UnusedOsApi : OpenSubtitlesApi {
        private fun unused(): Nothing = throw UnsupportedOperationException("appel inattendu")
        override suspend fun login(body: OsLoginRequest): OsLoginResponse = unused()
        override suspend fun search(query: String, languages: String): OsSearchResponse = unused()
        override suspend fun requestDownload(body: OsDownloadRequest, authorization: String): Response<OsDownloadResponse> = unused()
        override suspend fun downloadFile(url: String): Response<ResponseBody> = unused()
    }
}
