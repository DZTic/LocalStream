package com.localstream.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.localstream.app.data.db.entity.PlaybackStateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaybackStateDao {

    /** Observe toutes les progressions actives (progress > 0). */
    @Query("SELECT * FROM playback_state WHERE progress_pct > 0")
    fun observeActivePlaybackStates(): Flow<List<PlaybackStateEntity>>

    /** Historique tri\u00e9 par derni\u00e8re lecture (limit 30 — \u00e9quivalent recentlyWatched). */
    @Query("SELECT * FROM playback_state ORDER BY last_played_at DESC LIMIT :limit")
    suspend fun getRecentlyPlayed(limit: Int = 30): List<PlaybackStateEntity>

    @Query("SELECT * FROM playback_state")
    suspend fun getAll(): List<PlaybackStateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: PlaybackStateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(states: List<PlaybackStateEntity>)

    @Query("DELETE FROM playback_state WHERE name = :name")
    suspend fun deleteByName(name: String)

    @Query("DELETE FROM playback_state")
    suspend fun deleteAll()

    @Query("SELECT * FROM playback_state WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): PlaybackStateEntity?
}
