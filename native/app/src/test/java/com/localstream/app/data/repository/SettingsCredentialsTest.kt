package com.localstream.app.data.repository

import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Test

class SettingsCredentialsTest {
    @Test
    fun `construction is nonblocking and concurrent first reads initialize once on IO`() = runTest {
        val callerThread = Thread.currentThread()
        val creations = AtomicInteger()
        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { dispatcher ->
            val repository = SettingsRepository(
                encryptedPrefsFactory = {
                    assertNotSame(callerThread, Thread.currentThread())
                    creations.incrementAndGet()
                    null
                },
                ioDispatcher = dispatcher,
            )
            assertEquals(0, creations.get())
            val keys = listOf(
                async { repository.getTmdbApiKey() },
                async { repository.getOpenSubtitlesApiKey() },
                async { repository.getOpenSubtitlesToken() },
            ).awaitAll()
            assertEquals(listOf("", "", ""), keys)
            assertEquals(1, creations.get())

            repository.saveTmdbApiKey("tmdb")
            repository.saveOpenSubtitlesApiKey("opensubtitles")
            repository.saveOpenSubtitlesUsername("user")
            repository.saveOpenSubtitlesPassword("password")
            repository.saveOpenSubtitlesToken("token")
            assertEquals("tmdb", repository.getTmdbApiKey())
            assertEquals("opensubtitles", repository.getOpenSubtitlesApiKey())
            assertEquals("user", repository.getOpenSubtitlesUsername())
            assertEquals("password", repository.getOpenSubtitlesPassword())
            assertEquals("token", repository.getOpenSubtitlesToken())
            repository.saveOpenSubtitlesToken("")
            assertEquals("", repository.getOpenSubtitlesToken())
            assertEquals(1, creations.get())
        }
    }
}
