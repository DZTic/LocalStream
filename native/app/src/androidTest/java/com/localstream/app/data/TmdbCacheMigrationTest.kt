package com.localstream.app.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.localstream.app.data.db.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TmdbCacheMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test
    fun migrateExistingCacheAndQueryOnlyMainMetadata() = runBlocking {
        val name = "tmdb-cache-migration"
        val rows = listOf(
            "Movie_s1_e1" to """{"queryKey":"Movie_s1_e1","title":"Movie"}""",
            "Series_s1_e1" to """{"name":"Pilot","overview":"Plot","seasonNumber":1,"episodeNumber":1}""",
            "Series_s1_e2" to "{}",
            "Missing" to "{}",
        )
        helper.createDatabase(name, 5).use { database ->
            rows.forEach { (key, payload) ->
                database.execSQL(
                    "INSERT INTO tmdb_metadata (query_key, json, fetched_at) VALUES (?, ?, ?)",
                    arrayOf(key, payload, 123L),
                )
            }
        }
        helper.runMigrationsAndValidate(name, 6, true, AppDatabase.MIGRATION_5_6).close()

        val database = Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java, name,
        ).build()
        try {
            val dao = database.tmdbMetadataDao()
            assertEquals(setOf("Movie_s1_e1", "Missing"), dao.getMainMetadata().map { it.queryKey }.toSet())
            assertEquals(4, dao.getAll().size)
            assertTrue(dao.getMetadata("Series_s1_e1")!!.isEpisode)
            assertTrue(dao.getMetadata("Series_s1_e2")!!.isEpisode)
            rows.forEach { (key, payload) ->
                assertEquals(payload, dao.getMetadata(key)?.json)
                assertEquals(123L, dao.getMetadata(key)?.fetchedAt)
            }
        } finally {
            database.close()
        }
    }
}
