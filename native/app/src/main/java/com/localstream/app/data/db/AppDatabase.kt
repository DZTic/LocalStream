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
    version = 3,
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

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME,
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
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
