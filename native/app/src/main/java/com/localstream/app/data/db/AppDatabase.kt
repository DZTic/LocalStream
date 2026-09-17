package com.localstream.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.localstream.app.data.db.dao.PlaybackStateDao
import com.localstream.app.data.db.dao.PlaylistDao
import com.localstream.app.data.db.dao.TmdbMetadataDao
import com.localstream.app.data.db.dao.WatchedItemDao
import com.localstream.app.data.db.entity.PlaybackStateEntity
import com.localstream.app.data.db.entity.PlaylistEntity
import com.localstream.app.data.db.entity.PlaylistItemEntity
import com.localstream.app.data.db.entity.TmdbMetadataEntity
import com.localstream.app.data.db.entity.WatchedItemEntity
import com.localstream.app.domain.model.TmdbEpisode
import kotlinx.serialization.json.Json

/**
 * Base de données Room principale de LocalStream (Phase 4, Phase 5).
 *
 * Version 1 : tables watched_items, playback_state, playlist, playlist_item.
 * Version 2 : ajout de la table tmdb_metadata (Phase 5).
 */
@Database(
    entities = [
        WatchedItemEntity::class,
        PlaybackStateEntity::class,
        PlaylistEntity::class,
        PlaylistItemEntity::class,
        TmdbMetadataEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun watchedItemDao(): WatchedItemDao
    abstract fun playbackStateDao(): PlaybackStateDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun tmdbMetadataDao(): TmdbMetadataDao

    companion object {
        private const val DB_NAME = "localstream.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `tmdb_metadata` (
                        `query_key` TEXT NOT NULL,
                        `json` TEXT NOT NULL,
                        `fetched_at` INTEGER NOT NULL,
                        PRIMARY KEY(`query_key`)
                    )
                    """.trimIndent(),
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_playback_state_last_played_at` ON `playback_state` (`last_played_at`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_playback_state_progress_pct` ON `playback_state` (`progress_pct`)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_playback_state_name` ON `playback_state` (`name`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_watched_items_name` ON `watched_items` (`name`)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_watched_items_watched` ON `watched_items` (`watched`)")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tmdb_metadata ADD COLUMN is_episode INTEGER NOT NULL DEFAULT 0")
                val decoder = Json { ignoreUnknownKeys = true; isLenient = true }
                val episodeKey = Regex("_s[0-9]+_e[0-9]+$")
                db.query("SELECT query_key, json FROM tmdb_metadata").use { cursor ->
                    while (cursor.moveToNext()) {
                        val key = cursor.getString(0)
                        val payload = cursor.getString(1)
                        // Decode the payload to preserve movie titles containing _s / _e.
                        // Negative entries have no payload; only those need the key convention.
                        val isEpisode = if (payload == "{}") {
                            episodeKey.containsMatchIn(key)
                        } else {
                            runCatching { decoder.decodeFromString<TmdbEpisode>(payload) }.isSuccess
                        }
                        if (isEpisode) {
                            db.execSQL("UPDATE tmdb_metadata SET is_episode = 1 WHERE query_key = ?", arrayOf(key))
                        }
                    }
                }
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME,
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .build()
                    .also { INSTANCE = it }
            }
        }

        /** Uniquement pour les tests instrumentaux : base en mémoire sans passer par le singleton. */
        fun createInMemory(context: Context): AppDatabase =
            Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
    }
}
