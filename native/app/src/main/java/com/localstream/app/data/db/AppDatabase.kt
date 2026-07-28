package com.localstream.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.localstream.app.data.db.dao.PlaybackStateDao
import com.localstream.app.data.db.dao.PlaylistDao
import com.localstream.app.data.db.dao.WatchedItemDao
import com.localstream.app.data.db.entity.PlaybackStateEntity
import com.localstream.app.data.db.entity.PlaylistEntity
import com.localstream.app.data.db.entity.PlaylistItemEntity
import com.localstream.app.data.db.entity.WatchedItemEntity

/**
 * Base de donn\u00e9es Room principale de LocalStream (Phase 4).
 *
 * Version 1 : tables watched_items, playback_state, playlist, playlist_item.
 * Les migrations futures incr\u00e9menteront [version].
 */
@Database(
    entities = [
        WatchedItemEntity::class,
        PlaybackStateEntity::class,
        PlaylistEntity::class,
        PlaylistItemEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun watchedItemDao(): WatchedItemDao
    abstract fun playbackStateDao(): PlaybackStateDao
    abstract fun playlistDao(): PlaylistDao

    companion object {
        private const val DB_NAME = "localstream.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME,
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }

        /** Uniquement pour les tests instrumentaux : base en m\u00e9moire sans passer par le singleton. */
        fun createInMemory(context: Context): AppDatabase =
            Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
    }
}
