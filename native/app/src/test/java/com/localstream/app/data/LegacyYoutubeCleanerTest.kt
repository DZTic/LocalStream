package com.localstream.app.data

import com.localstream.app.data.legacy.LegacyYoutubeCleaner
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LegacyYoutubeCleanerTest {

    private lateinit var noBackupDir: File

    @Before
    fun setUp() {
        noBackupDir = Files.createTempDirectory("no_backup").toFile()
    }

    @Test
    fun cleanup_deletesRuntimeAndPreferences() {
        val lib = File(noBackupDir, "youtubedl-android/packages/python/usr/lib").apply { mkdirs() }
        File(lib, "libpython.so").writeText("x")
        val unrelated = File(noBackupDir, "other.bin").apply { writeText("keep") }
        var prefsDeleted = 0

        val cleaned = LegacyYoutubeCleaner.cleanup(noBackupDir) { prefsDeleted++ }

        assertTrue(cleaned)
        assertFalse(File(noBackupDir, "youtubedl-android").exists())
        assertEquals(1, prefsDeleted)
        assertTrue(unrelated.exists())
    }

    @Test
    fun cleanup_isNoOpWhenNothingToDelete() {
        var prefsDeleted = 0

        val cleaned = LegacyYoutubeCleaner.cleanup(noBackupDir) { prefsDeleted++ }

        assertFalse(cleaned)
        assertEquals(0, prefsDeleted)
    }
}
