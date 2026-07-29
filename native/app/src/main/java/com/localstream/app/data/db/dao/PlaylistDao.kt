package com.localstream.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.localstream.app.data.db.entity.PlaylistEntity
import com.localstream.app.data.db.entity.PlaylistItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
@Suppress("TooManyFunctions")
interface PlaylistDao {

    // -------- Playlists --------

    @Query("SELECT * FROM playlist ORDER BY created_at ASC")
    fun observePlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlist ORDER BY created_at ASC")
    suspend fun getAllPlaylists(): List<PlaylistEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlaylist(playlist: PlaylistEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlaylists(playlists: List<PlaylistEntity>)

    @Query("DELETE FROM playlist WHERE id = :id")
    suspend fun deletePlaylist(id: String)

    @Query("DELETE FROM playlist")
    suspend fun deleteAllPlaylists()

    // -------- Items --------

    @Query("SELECT * FROM playlist_item WHERE playlist_id = :playlistId ORDER BY position ASC")
    fun observeItems(playlistId: String): Flow<List<PlaylistItemEntity>>

    @Query("SELECT * FROM playlist_item WHERE playlist_id = :playlistId ORDER BY position ASC")
    suspend fun getItems(playlistId: String): List<PlaylistItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItem(item: PlaylistItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItems(items: List<PlaylistItemEntity>)

    @Query("DELETE FROM playlist_item WHERE playlist_id = :playlistId AND video_name = :videoName")
    suspend fun deleteItem(playlistId: String, videoName: String)

    @Query("DELETE FROM playlist_item WHERE playlist_id = :playlistId")
    suspend fun deleteAllItems(playlistId: String)

    @Query("SELECT * FROM playlist_item")
    suspend fun getAllItems(): List<PlaylistItemEntity>

    // -------- Transactions --------

    /** Remplace enti\u00e8rement les items d'une playlist (utilis\u00e9 lors du r\u00e9ordonnancement). */
    @Transaction
    suspend fun replaceItems(playlistId: String, items: List<PlaylistItemEntity>) {
        deleteAllItems(playlistId)
        upsertItems(items)
    }
}
