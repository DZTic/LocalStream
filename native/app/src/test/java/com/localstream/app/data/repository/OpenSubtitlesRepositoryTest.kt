package com.localstream.app.data.repository

import com.localstream.app.data.local.SubtitleCache
import com.localstream.app.data.remote.opensubtitles.OpenSubtitlesApi
import com.localstream.app.data.remote.opensubtitles.OpenSubtitlesInterceptor
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
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

@Suppress("MaxLineLength", "TooManyFunctions")
class OpenSubtitlesRepositoryTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var api: OpenSubtitlesApi
    private lateinit var settings: FakeOsSettingsRepository
    private lateinit var cacheDir: File
    private lateinit var repository: OpenSubtitlesRepository

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        val json = Json { ignoreUnknownKeys = true; isLenient = true }
        val contentType = "application/json".toMediaType()
        val client = OkHttpClient.Builder()
            .addInterceptor(OpenSubtitlesInterceptor { "os_test_key" })
            .build()
        val retrofit = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()

        api = retrofit.create(OpenSubtitlesApi::class.java)
        settings = FakeOsSettingsRepository()
        cacheDir = Files.createTempDirectory("os_cache_test").toFile()
        repository = OpenSubtitlesRepository(api, settings, SubtitleCache(cacheDir))
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
        cacheDir.deleteRecursively()
    }

    private fun jsonResponse(body: String, code: Int = 200): MockResponse =
        MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)

    @Test
    fun loginSuccessSavesEncryptedToken() = runTest {
        mockWebServer.enqueue(jsonResponse("""{"token":"tok123"}"""))

        val result = repository.login()

        assertTrue(result.isSuccess)
        assertEquals("tok123", result.getOrNull())
        assertEquals("tok123", settings.token)

        val request = mockWebServer.takeRequest()
        assertEquals("/login", request.path)
        assertEquals("os_test_key", request.getHeader("Api-Key"))
        assertEquals(OpenSubtitlesInterceptor.USER_AGENT, request.getHeader("User-Agent"))
    }

    @Test
    fun loginUnauthorizedReturnsAuthException() = runTest {
        mockWebServer.enqueue(jsonResponse("""{"message":"Unauthorized"}""", code = 401))

        val result = repository.login()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is OpenSubtitlesAuthException)
        assertEquals("", settings.token)
    }

    @Test
    fun loginFailsWithoutCredentials() = runTest {
        settings.username = ""
        settings.password = ""

        val result = repository.login()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun searchMapsResultsLikeOsSearch() = runTest {
        val body = """
            {"data":[
              {"id":"1","type":"subtitle","attributes":{"language":"fr","files":[{"file_id":456,"file_name":"fight.club.fr.srt"}]}},
              {"id":"2","type":"subtitle","attributes":{"language":"en","files":[{"file_id":457,"file_name":"fight.club.en.srt"}]}},
              {"id":"3","type":"subtitle","attributes":{"language":"fr","files":[]}}
            ]}
        """.trimIndent()
        mockWebServer.enqueue(jsonResponse(body))

        val result = repository.search("Fight Club (1999) 1080p")

        assertTrue(result.isSuccess)
        val subtitles = result.getOrThrow()
        assertEquals(2, subtitles.size)
        assertEquals("456", subtitles[0].id)
        assertEquals("fr", subtitles[0].language)
        assertEquals("fight.club.fr.srt", subtitles[0].filename)

        val request = mockWebServer.takeRequest()
        assertTrue(request.path!!.startsWith("/subtitles?"))
        assertTrue(request.path!!.contains("languages=fr%2Cen"))
    }

    @Test
    fun searchFailsWithoutApiKey() = runTest {
        settings.apiKey = ""

        val result = repository.search("Fight Club")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun downloadSavesSrtInCache() = runTest {
        settings.token = "valid_token"
        val link = mockWebServer.url("/file/456.srt").toString()
        mockWebServer.enqueue(jsonResponse("""{"link":"$link"}"""))
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("1\n00:00:01,000 --> 00:00:02,000\nBonjour\n"))

        val result = repository.downloadSubtitle("456")

        assertTrue(result.isSuccess)
        val file = result.getOrThrow()
        assertEquals("456.srt", file.name)
        assertEquals("subtitles", file.parentFile?.name)
        assertTrue(file.readText().contains("Bonjour"))

        val downloadRequest = mockWebServer.takeRequest()
        assertEquals("/download", downloadRequest.path)
        assertEquals("Bearer valid_token", downloadRequest.getHeader("Authorization"))
        assertTrue(downloadRequest.body.readUtf8().contains("456"))
    }

    @Test
    fun downloadReturnsCachedFileWithoutNetwork() = runTest {
        val cache = SubtitleCache(cacheDir)
        val cached = cache.store("789", "cached".toByteArray())

        val result = repository.downloadSubtitle("789")

        assertTrue(result.isSuccess)
        assertEquals(cached.absolutePath, result.getOrThrow().absolutePath)
        assertEquals(0, mockWebServer.requestCount)
    }

    @Test
    fun downloadRelogsInSilentlyOn401ThenSucceeds() = runTest {
        settings.token = "expired"
        mockWebServer.enqueue(jsonResponse("""{"message":"Unauthorized"}""", code = 401))
        mockWebServer.enqueue(jsonResponse("""{"token":"fresh"}"""))
        val link = mockWebServer.url("/file/456.srt").toString()
        mockWebServer.enqueue(jsonResponse("""{"link":"$link"}"""))
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("srt-content"))

        val result = repository.downloadSubtitle("456")

        assertTrue(result.isSuccess)
        assertEquals("fresh", settings.token)

        mockWebServer.takeRequest() // 401 download
        mockWebServer.takeRequest() // login
        val retryRequest = mockWebServer.takeRequest()
        assertEquals("Bearer fresh", retryRequest.getHeader("Authorization"))
    }

    @Test
    fun downloadFailsWithClearAuthErrorAfterRelogin() = runTest {
        settings.token = "expired"
        mockWebServer.enqueue(jsonResponse("""{"message":"Unauthorized"}""", code = 401))
        mockWebServer.enqueue(jsonResponse("""{"token":"fresh"}"""))
        mockWebServer.enqueue(jsonResponse("""{"message":"Unauthorized"}""", code = 401))

        val result = repository.downloadSubtitle("456")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is OpenSubtitlesAuthException)
        assertEquals("", settings.token)
    }

    @Test
    fun downloadLogsInWhenNoTokenStored() = runTest {
        settings.token = ""
        mockWebServer.enqueue(jsonResponse("""{"token":"fresh"}"""))
        val link = mockWebServer.url("/file/456.srt").toString()
        mockWebServer.enqueue(jsonResponse("""{"link":"$link"}"""))
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("srt-content"))

        val result = repository.downloadSubtitle("456")

        assertTrue(result.isSuccess)
        assertEquals("fresh", settings.token)
        assertNotNull(result.getOrThrow())
    }
}

class FakeOsSettingsRepository : SettingsRepository() {
    var apiKey = "os_test_key"
    var username = "user"
    var password = "pass"
    var token = ""

    override fun getOpenSubtitlesApiKey(): String = apiKey
    override fun saveOpenSubtitlesApiKey(key: String) { apiKey = key }
    override fun getOpenSubtitlesUsername(): String = username
    override fun saveOpenSubtitlesUsername(username: String) { this.username = username }
    override fun getOpenSubtitlesPassword(): String = password
    override fun saveOpenSubtitlesPassword(password: String) { this.password = password }
    override fun getOpenSubtitlesToken(): String = token
    override fun saveOpenSubtitlesToken(token: String) { this.token = token }
}
