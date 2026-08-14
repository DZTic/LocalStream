package com.localstream.app.data.remote.tmdb

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class TmdbAuthInterceptorTest {

    private lateinit var mockWebServer: MockWebServer

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `v3 api key injects query parameter`() {
        mockWebServer.enqueue(MockResponse().setBody("{}"))

        val client = OkHttpClient.Builder()
            .addInterceptor(TmdbAuthInterceptor { "1234567890abcdef1234567890abcdef" })
            .build()

        val request = Request.Builder()
            .url(mockWebServer.url("/3/movie/popular"))
            .build()

        client.newCall(request).execute().close()

        val recorded = mockWebServer.takeRequest()
        assertEquals("1234567890abcdef1234567890abcdef", recorded.requestUrl?.queryParameter("api_key"))
        assertNull(recorded.getHeader("Authorization"))
    }

    @Test
    fun `v4 JWT token injects Authorization Bearer header and removes api_key query param`() {
        mockWebServer.enqueue(MockResponse().setBody("{}"))

        val jwtToken = "eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiIxMjM0NTY3ODkwIiwic3ViIjoiMTIzNDU2Nzg5MCJ9.signature"
        val client = OkHttpClient.Builder()
            .addInterceptor(TmdbAuthInterceptor { jwtToken })
            .build()

        val request = Request.Builder()
            .url(mockWebServer.url("/3/movie/popular?api_key=old_key"))
            .build()

        client.newCall(request).execute().close()

        val recorded = mockWebServer.takeRequest()
        assertEquals("Bearer $jwtToken", recorded.getHeader("Authorization"))
        assertNull(recorded.requestUrl?.queryParameter("api_key"))
    }

    @Test
    fun `override header takes precedence and is cleaned`() {
        mockWebServer.enqueue(MockResponse().setBody("{}"))

        val jwtToken = "eyJhbGciOiJIUzI1NiJ9.override"
        val client = OkHttpClient.Builder()
            .addInterceptor(TmdbAuthInterceptor { "default_v3_key" })
            .build()

        val request = Request.Builder()
            .url(mockWebServer.url("/3/movie/popular"))
            .header(TmdbAuthInterceptor.HEADER_OVERRIDE_KEY, "  Bearer $jwtToken  ")
            .build()

        client.newCall(request).execute().close()

        val recorded = mockWebServer.takeRequest()
        assertEquals("Bearer $jwtToken", recorded.getHeader("Authorization"))
        assertNull(recorded.getHeader(TmdbAuthInterceptor.HEADER_OVERRIDE_KEY))
        assertNull(recorded.requestUrl?.queryParameter("api_key"))
    }
}
