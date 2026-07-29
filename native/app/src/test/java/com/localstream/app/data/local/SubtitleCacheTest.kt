package com.localstream.app.data.local

import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SubtitleCacheTest {

    private lateinit var cacheDir: java.io.File

    @Before
    fun setUp() {
        cacheDir = Files.createTempDirectory("subtitle_cache_test").toFile()
    }

    @After
    fun tearDown() {
        cacheDir.deleteRecursively()
    }

    @Test
    fun storeThenGetReturnsFile() {
        val cache = SubtitleCache(cacheDir)

        val file = cache.store("42", "contenu".toByteArray())

        assertEquals("42.srt", file.name)
        assertEquals(SubtitleCache.SUBTITLES_DIR, file.parentFile?.name)
        assertNotNull(cache.get("42"))
        assertEquals("contenu", cache.get("42")!!.readText())
    }

    @Test
    fun getReturnsNullForUnknownFile() {
        val cache = SubtitleCache(cacheDir)

        assertNull(cache.get("unknown"))
    }

    @Test
    fun lruEvictionRemovesOldestFilesAboveMaxSize() {
        val maxSize = 100L
        val cache = SubtitleCache(cacheDir, maxSize)

        val old = cache.store("old", ByteArray(60))
        old.setLastModified(1_000L)
        cache.store("new", ByteArray(60))

        assertNull("Le plus ancien fichier doit être évincé", cache.get("old"))
        assertNotNull(cache.get("new"))
    }

    @Test
    fun noEvictionBelowMaxSize() {
        val cache = SubtitleCache(cacheDir, 1_000L)

        cache.store("a", ByteArray(40))
        cache.store("b", ByteArray(40))

        assertNotNull(cache.get("a"))
        assertNotNull(cache.get("b"))
    }

    @Test
    fun clearEmptiesCache() {
        val cache = SubtitleCache(cacheDir)
        cache.store("a", ByteArray(10))

        cache.clear()

        assertNull(cache.get("a"))
        assertTrue(true)
    }
}
