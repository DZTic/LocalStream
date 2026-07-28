package com.localstream.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.localstream.app.data.db.entity.WatchedItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchedItemDao {

    /** Observe tous les enregistrements "vu" (watched = true). */
    @Query("SELECT * FROM watched_items WHERE watched = 1")
    fun observeWatchedItems(): Flow<List<WatchedItemEntity>>

    /** Retourne tous les enregistrements (y compris watched = false apr\u00e8s import). */
    @Query("SELECT * FROM watched_items")
    suspend fun getAllWatchedItems(): List<WatchedItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: WatchedItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<WatchedItemEntity>)

    @Query("DELETE FROM watched_items WHERE name = :name")
    suspend fun deleteByName(name: String)

    @Query("DELETE FROM watched_items")
    suspend fun deleteAll()

    @Query("SELECT * FROM watched_items WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): WatchedItemEntity?
}
