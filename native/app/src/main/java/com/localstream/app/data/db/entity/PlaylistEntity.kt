package com.localstream.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Table Room : playlists utilisateur.
 * Correspond \u00e0 la cl\u00e9 localStorage "playlists" (usePlaylists.ts).
 */
@Entity(tableName = "playlist")
data class PlaylistEntity(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)

/**
 * Table Room : \u00e9l\u00e9ments d'une playlist, avec ordre explicite.
 */
@Entity(
    tableName = "playlist_item",
    primaryKeys = ["playlist_id", "video_name"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlist_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("playlist_id")],
)
data class PlaylistItemEntity(
    @ColumnInfo(name = "playlist_id") val playlistId: String,
    @ColumnInfo(name = "video_name") val videoName: String,
    /** Position 0-based dans la playlist. */
    @ColumnInfo(name = "position") val position: Int = 0,
)
