package com.localstream.app.data

import androidx.sqlite.db.SupportSQLiteDatabase
import com.localstream.app.data.db.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class AppDatabaseMigrationTest {

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
}

