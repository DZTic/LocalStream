package com.localstream.app.data

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import com.localstream.app.data.db.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class AppDatabaseMigrationTest {

    @Test
    fun migration5To6_classifiesPayloadsAndPreservesMainMetadata() {
        val rows = listOf(
            "Series_s1_e1" to """{"name":"Pilot","overview":"Plot","seasonNumber":1,"episodeNumber":1}""",
            "Movie_s1_e1" to """{"queryKey":"Movie_s1_e1","title":"Movie"}""",
            "Series_s1_e2" to "{}",
            "Movie_something_else" to "{}",
            "Broken" to "invalid json",
        )
        var index = -1
        val cursor = Proxy.newProxyInstance(
            Cursor::class.java.classLoader, arrayOf(Cursor::class.java),
        ) { _, method, args ->
            when (method.name) {
                "moveToNext" -> ++index < rows.size
                "getString" -> if (args!![0] == 0) rows[index].first else rows[index].second
                else -> null
            }
        } as Cursor
        val statements = mutableListOf<String>()
        val updatedKeys = mutableListOf<String>()
        val database = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader, arrayOf(SupportSQLiteDatabase::class.java),
        ) { _, method, args ->
            when (method.name) {
                "query" -> cursor
                "execSQL" -> {
                    statements.add(args!![0].toString())
                    if (args.size > 1) updatedKeys.add((args[1] as Array<*>).single().toString())
                    null
                }
                else -> null
            }
        } as SupportSQLiteDatabase

        assertEquals(5, AppDatabase.MIGRATION_5_6.startVersion)
        assertEquals(6, AppDatabase.MIGRATION_5_6.endVersion)
        AppDatabase.MIGRATION_5_6.migrate(database)
        assertEquals("ALTER TABLE tmdb_metadata ADD COLUMN is_episode INTEGER NOT NULL DEFAULT 0", statements.first())
        assertEquals(listOf("Series_s1_e1", "Series_s1_e2"), updatedKeys)
    }

    @Test
    fun migration1To2_hasCorrectVersions() {
        assertEquals(1, AppDatabase.MIGRATION_1_2.startVersion)
        assertEquals(2, AppDatabase.MIGRATION_1_2.endVersion)
    }

    @Test
    fun migration1To2_executesCreateTableTmdbMetadata() {
        val executedQueries = mutableListOf<String>()

        val dbProxy = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java),
        ) { _, method, args ->
            if (method.name == "execSQL" && args != null && args.isNotEmpty()) {
                executedQueries.add(args[0].toString())
            }
            null
        } as SupportSQLiteDatabase

        AppDatabase.MIGRATION_1_2.migrate(dbProxy)

        assertEquals(1, executedQueries.size)
        val sql = executedQueries.first()
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `tmdb_metadata`"))
        assertTrue(sql.contains("`query_key` TEXT NOT NULL"))
        assertTrue(sql.contains("`json` TEXT NOT NULL"))
        assertTrue(sql.contains("`fetched_at` INTEGER NOT NULL"))
        assertTrue(sql.contains("PRIMARY KEY(`query_key`)"))
    }

    @Test
    fun migration2To3_hasCorrectVersions() {
        assertEquals(2, AppDatabase.MIGRATION_2_3.startVersion)
        assertEquals(3, AppDatabase.MIGRATION_2_3.endVersion)
    }

    @Test
    fun migration2To3_executesCreateIndicesOnPlaybackState() {
        val executedQueries = mutableListOf<String>()

        val dbProxy = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java),
        ) { _, method, args ->
            if (method.name == "execSQL" && args != null && args.isNotEmpty()) {
                executedQueries.add(args[0].toString())
            }
            null
        } as SupportSQLiteDatabase

        AppDatabase.MIGRATION_2_3.migrate(dbProxy)

        assertEquals(2, executedQueries.size)
        assertTrue(executedQueries.any { it.contains("CREATE INDEX IF NOT EXISTS `index_playback_state_last_played_at`") })
        assertTrue(executedQueries.any { it.contains("CREATE INDEX IF NOT EXISTS `index_playback_state_progress_pct`") })
    }

    @Test
    fun migration3To4_hasCorrectVersions() {
        assertEquals(3, AppDatabase.MIGRATION_3_4.startVersion)
        assertEquals(4, AppDatabase.MIGRATION_3_4.endVersion)
    }

    @Test
    fun migration3To4_executesCreateUniqueIndices() {
        val executedQueries = mutableListOf<String>()

        val dbProxy = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java),
        ) { _, method, args ->
            if (method.name == "execSQL" && args != null && args.isNotEmpty()) {
                executedQueries.add(args[0].toString())
            }
            null
        } as SupportSQLiteDatabase

        AppDatabase.MIGRATION_3_4.migrate(dbProxy)

        assertEquals(2, executedQueries.size)
        assertTrue(executedQueries.any { it.contains("CREATE UNIQUE INDEX IF NOT EXISTS `index_playback_state_name`") })
        assertTrue(executedQueries.any { it.contains("CREATE UNIQUE INDEX IF NOT EXISTS `index_watched_items_name`") })
    }

    @Test
    fun migration4To5_hasCorrectVersions() {
        assertEquals(4, AppDatabase.MIGRATION_4_5.startVersion)
        assertEquals(5, AppDatabase.MIGRATION_4_5.endVersion)
    }

    @Test
    fun migration4To5_executesCreateIndexOnWatched() {
        val executedQueries = mutableListOf<String>()

        val dbProxy = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java),
        ) { _, method, args ->
            if (method.name == "execSQL" && args != null && args.isNotEmpty()) {
                executedQueries.add(args[0].toString())
            }
            null
        } as SupportSQLiteDatabase

        AppDatabase.MIGRATION_4_5.migrate(dbProxy)

        assertEquals(1, executedQueries.size)
        assertTrue(executedQueries.first().contains("CREATE INDEX IF NOT EXISTS `index_watched_items_watched` ON `watched_items` (`watched`)"))
    }
}
