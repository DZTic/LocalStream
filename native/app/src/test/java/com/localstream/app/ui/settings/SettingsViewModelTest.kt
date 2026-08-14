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
import com.localstream.app.di.NoOpOpenSubtitlesApi
import com.localstream.app.di.NoOpTmdbApi
import com.localstream.app.di.NoOpTmdbMetadataDao
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

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
        val osRepo = OpenSubtitlesRepository(NoOpOpenSubtitlesApi(), settingsRepo, SubtitleCache(File("/tmp")))
        val dummyContainer = DummyContainer(settingsRepo, osRepo)
        val viewModel = SettingsViewModel(dummyContainer)

        viewModel.saveTmdbApiKey("  my_secret_tmdb_key  ")
        advanceUntilIdle()

        assertEquals("my_secret_tmdb_key", settingsRepo.getTmdbApiKey())
    }

    @Test
    fun `testTmdbApiKey met à jour l'état de succès et d'échec`() = runTest(testDispatcher) {
        val settingsRepo = SettingsRepository(dataStore = null)
        val osRepo = OpenSubtitlesRepository(NoOpOpenSubtitlesApi(), settingsRepo, SubtitleCache(File("/tmp")))
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
        tmdbApi = NoOpTmdbApi(),
        tmdbMetadataDao = NoOpTmdbMetadataDao(),
        settingsRepository = settingsRepo,
    ) {
        var shouldSucceed = true
        var errorMessage = "Clé API TMDB invalide"

        override suspend fun testApiKey(apiKeyOverride: String?): Result<Boolean> {
            return if (shouldSucceed) Result.success(true) else Result.failure(Exception(errorMessage))
        }
    }
}
