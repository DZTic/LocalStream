package com.localstream.app.data.repository

import com.localstream.app.data.db.dao.TmdbMetadataDao
import com.localstream.app.data.db.entity.TmdbMetadataEntity
import com.localstream.app.data.remote.tmdb.TmdbApi
import com.localstream.app.domain.model.VideoItem
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@Suppress("MaxLineLength", "TooManyFunctions", "LargeClass", "MagicNumber")
class TmdbRepositoryTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var tmdbApi: TmdbApi
    private lateinit var fakeDao: FakeTmdbMetadataDao
    private lateinit var settingsRepository: FakeSettingsRepository
    private lateinit var repository: TmdbRepository

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        val contentType = "application/json".toMediaType()
        val retrofit = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()

        tmdbApi = retrofit.create(TmdbApi::class.java)
        fakeDao = FakeTmdbMetadataDao()
        settingsRepository = FakeSettingsRepository("test_api_key")

        repository = TmdbRepository(
            tmdbApi = tmdbApi,
            tmdbMetadataDao = fakeDao,
            settingsRepository = settingsRepository,
            json = json,
            maxConcurrentRequests = 2,
        )
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    private fun jsonResponse(body: String, code: Int = 200): MockResponse =
        MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)

    @Test
    fun testFetchMetadataForMovieSuccess() = runTest {
        val searchJson = """
            {
              "results": [
                {
                  "id": 550,
                  "title": "Fight Club",
                  "overview": "An unmotivated office worker...",
                  "poster_path": "/poster_fight_club.jpg",
                  "backdrop_path": "/backdrop_fight_club.jpg",
                  "release_date": "1999-10-15",
                  "genre_ids": [18, 53],
                  "media_type": "movie"
                }
              ]
            }
        """.trimIndent()

        val detailsJson = """
            {
              "id": 550,
              "title": "Fight Club",
              "poster_path": "/poster_fight_club.jpg",
              "backdrop_path": "/backdrop_fight_club.jpg",
              "belongs_to_collection": {
                "id": 10,
                "name": "Fight Club Collection"
              }
            }
        """.trimIndent()

        mockWebServer.enqueue(jsonResponse(searchJson))
        mockWebServer.enqueue(jsonResponse(detailsJson))

        val video = VideoItem(name = "Fight.Club.1999.1080p.mp4")
        val result = repository.fetchMetadataForVideo(video)

        assertTrue(result.isSuccess)
        val metadata = result.getOrNull()
        assertNotNull(metadata)
        assertEquals("Fight Club", metadata?.title)
        assertEquals(550L, metadata?.tmdbId)
        assertEquals("https://image.tmdb.org/t/p/w500/poster_fight_club.jpg", metadata?.posterUrl())
        assertEquals("https://image.tmdb.org/t/p/w1280/backdrop_fight_club.jpg", metadata?.backdropUrl())
        assertEquals(10L, metadata?.collectionId)
        assertEquals("Fight Club Collection", metadata?.collectionName)

        val cached = repository.getCachedMetadata("Fight.Club.1999.1080p.mp4")
        assertNotNull(cached)
        assertEquals("Fight Club", cached?.title)
    }

    @Test
    fun testCacheHitDoesNotHitNetwork() = runTest {
        val searchJson = """
            {
              "results": [
                {
                  "id": 100,
                  "title": "Inception",
                  "poster_path": "/poster.jpg",
                  "media_type": "movie"
                }
              ]
            }
        """.trimIndent()

        mockWebServer.enqueue(jsonResponse(searchJson))
        mockWebServer.enqueue(jsonResponse("""{"id": 100}"""))

        val video = VideoItem(name = "Inception.mp4")
        val firstResult = repository.fetchMetadataForVideo(video)
        assertTrue(firstResult.isSuccess)
        assertEquals(2, mockWebServer.requestCount)

        val secondResult = repository.fetchMetadataForVideo(video, forceRefresh = false)
        assertTrue(secondResult.isSuccess)
        assertEquals(2, mockWebServer.requestCount)
    }

    @Test
    fun testCacheExpiredTriggersNetworkFetch() = runTest {
        val searchJson = """
            {
              "results": [
                {
                  "id": 200,
                  "title": "Matrix",
                  "poster_path": "/matrix.jpg",
                  "media_type": "movie"
                }
              ]
            }
        """.trimIndent()

        val expiredTimestamp = System.currentTimeMillis() - (35L * 24 * 3600 * 1000)
        fakeDao.insertMetadata(
            TmdbMetadataEntity(
                queryKey = "Matrix.mp4",
                json = """{"queryKey":"Matrix.mp4","tmdbId":200,"title":"Old Matrix"}""",
                fetchedAt = expiredTimestamp,
            )
        )

        mockWebServer.enqueue(jsonResponse(searchJson))
        mockWebServer.enqueue(jsonResponse("""{"id": 200}"""))

        val video = VideoItem(name = "Matrix.mp4")
        val result = repository.fetchMetadataForVideo(video, forceRefresh = false)
        assertTrue(result.isSuccess)
        assertEquals("Matrix", result.getOrNull()?.title)
        assertEquals(2, mockWebServer.requestCount)
    }

    @Test
    fun testOfflineFallbackWithExpiredCache() = runTest {
        val expiredTimestamp = System.currentTimeMillis() - (35L * 24 * 3600 * 1000)
        fakeDao.insertMetadata(
            TmdbMetadataEntity(
                queryKey = "Interstellar.mp4",
                json = """{"queryKey":"Interstellar.mp4","tmdbId":300,"title":"Interstellar Cached"}""",
                fetchedAt = expiredTimestamp,
            )
        )

        mockWebServer.enqueue(jsonResponse("Internal Error", 500))

        val video = VideoItem(name = "Interstellar.mp4")
        val result = repository.fetchMetadataForVideo(video, forceRefresh = false)
        assertTrue(result.isSuccess)
        assertEquals("Interstellar Cached", result.getOrNull()?.title)
    }

    @Test
    fun testTvSeriesAndEpisodesFetch() = runTest {
        val tvSearchJson = """
            {
              "results": [
                {
                  "id": 1399,
                  "name": "Game of Thrones",
                  "poster_path": "/got.jpg",
                  "media_type": "tv"
                }
              ]
            }
        """.trimIndent()

        val seasonJson = """
            {
              "season_number": 1,
              "episodes": [
                {
                  "id": 63056,
                  "name": "Winter Is Coming",
                  "overview": "Lord Eddard Stark is asked to serve as Hand of the King.",
                  "still_path": "/still_ep1.jpg",
                  "season_number": 1,
                  "episode_number": 1
                }
              ]
            }
        """.trimIndent()

        mockWebServer.enqueue(jsonResponse(tvSearchJson))
        mockWebServer.enqueue(jsonResponse(seasonJson))

        val seriesVideo = VideoItem(
            name = "Game.of.Thrones.S01E01.mp4",
            isSeriesGroup = true,
            isTvSeries = true,
            seriesName = "Game of Thrones",
            episodes = listOf(VideoItem(name = "GOT S01E01", season = 1, episode = 1)),
        )

        val result = repository.fetchMetadataForVideo(seriesVideo)
        assertTrue(result.isSuccess)

        val episode = repository.getCachedEpisode("Game of Thrones", 1, 1)
        assertNotNull(episode)
        assertEquals("Winter Is Coming", episode?.name)
        assertEquals("https://image.tmdb.org/t/p/w300/still_ep1.jpg", episode?.stillUrl())
    }

    @Test
    fun testNotFoundReturnsErrorAndCachesMarker() = runTest {
        val emptySearch = """{"results": []}"""
        mockWebServer.enqueue(jsonResponse(emptySearch))

        val video = VideoItem(name = "UnknownHomeMovie.mp4")
        val result = repository.fetchMetadataForVideo(video)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is NoSuchElementException)

        val secondResult = repository.fetchMetadataForVideo(video, forceRefresh = false)
        assertTrue(secondResult.isFailure)
        assertEquals(1, mockWebServer.requestCount)
    }

    @Test
    fun test401UnauthorizedThrowsTmdbAuthException() = runTest {
        mockWebServer.enqueue(jsonResponse("""{"status_message": "Invalid API key"}""", 401))

        val video = VideoItem(name = "TestMovie.mp4")
        val result = repository.fetchMetadataForVideo(video)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is TmdbAuthException)
    }

    @Test
    fun test429RateLimitBackoffAndRetry() = runTest {
        val searchJson = """
            {
              "results": [
                {
                  "id": 99,
                  "title": "Avatar",
                  "poster_path": "/avatar.jpg",
                  "media_type": "movie"
                }
              ]
            }
        """.trimIndent()

        mockWebServer.enqueue(jsonResponse("""{"status_message": "Rate limit exceeded"}""", 429))
        mockWebServer.enqueue(jsonResponse(searchJson))
        mockWebServer.enqueue(jsonResponse("""{"id": 99}"""))

        val video = VideoItem(name = "Avatar.mp4")
        val result = repository.fetchMetadataForVideo(video)

        assertTrue(result.isSuccess)
        assertEquals("Avatar", result.getOrNull()?.title)
        assertEquals(3, mockWebServer.requestCount)
    }

    @Test
    fun testThrottlingLimitsConcurrency() {
        val searchJson = """
            {
              "results": [
                {
                  "id": 1,
                  "title": "Movie",
                  "media_type": "movie"
                }
              ]
            }
        """.trimIndent()

        repeat(6) {
            mockWebServer.enqueue(jsonResponse(searchJson))
            mockWebServer.enqueue(jsonResponse("""{"id": 1}"""))
        }

        repository.resetMetrics()

        val threadPool = Executors.newFixedThreadPool(4)
        val customDispatcher = threadPool.asCoroutineDispatcher()
        try {
            runBlocking(customDispatcher) {
                val videos = (1..3).map { VideoItem(name = "Movie $it.mp4") }
                val deferreds = videos.map { v ->
                    async { repository.fetchMetadataForVideo(v) }
                }
                deferreds.awaitAll()
            }
        } finally {
            customDispatcher.close()
            threadPool.shutdownNow()
        }

        assertTrue(
            "Expected max concurrent <= 2, got ${repository.maxObservedConcurrentRequests}",
            repository.maxObservedConcurrentRequests <= 2
        )
    }

    @Test
    fun testTestApiKey() = runTest {
        val popularJson = """{"results": [{"id": 1, "title": "Popular Movie"}]}"""
        mockWebServer.enqueue(jsonResponse(popularJson))

        val validResult = repository.testApiKey("valid_key")
        assertTrue(validResult.isSuccess)
        assertTrue(validResult.getOrNull() == true)

        mockWebServer.enqueue(jsonResponse("""{"status_message": "Invalid API Key"}""", 401))
        val invalidResult = repository.testApiKey("invalid_key")
        assertTrue(invalidResult.isFailure)
        assertTrue(invalidResult.exceptionOrNull() is TmdbAuthException)
    }
}

class FakeSettingsRepository(var key: String = "test_api_key") : SettingsRepository() {
    override fun getTmdbApiKey(): String = key
    override fun saveTmdbApiKey(key: String) { this.key = key }
}

class FakeTmdbMetadataDao : TmdbMetadataDao {
    private val map = mutableMapOf<String, TmdbMetadataEntity>()

    override suspend fun getMetadata(queryKey: String): TmdbMetadataEntity? {
        return map[queryKey]
    }

    override suspend fun insertMetadata(entity: TmdbMetadataEntity) {
        map[entity.queryKey] = entity
    }

    override suspend fun deleteMetadata(queryKey: String) {
        map.remove(queryKey)
    }

    override suspend fun clearAll() {
        map.clear()
    }

    override suspend fun getAll(): List<TmdbMetadataEntity> {
        return map.values.toList()
    }
}
