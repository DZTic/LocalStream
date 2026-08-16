package com.localstream.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

import androidx.room.Index

/**
 * Table Room : progression de lecture.
 * Fusionne les trois clés localStorage : watchProgress, watchPositions et recentlyWatched.
 *
 * - [progressPct]  : pourcentage (0.0 – 100.0), équivalent "watchProgress"
 * - [positionMs]   : position en millisecondes, équivalent "watchPositions"
 * - [lastPlayedAt] : timestamp ms, permet de reconstruire "recentlyWatched" par tri DESC
 * - [mediaStoreId] : nullable, fiabilise la clé à terme (renommage de fichier)
 */
@Entity(
    tableName = "playback_state",
    indices = [
        Index(value = ["last_played_at"]),
        Index(value = ["progress_pct"]),
    ],
)
data class PlaybackStateEntity(
    @PrimaryKey
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "progress_pct") val progressPct: Double = 0.0,
    @ColumnInfo(name = "position_ms") val positionMs: Long = 0L,
    @ColumnInfo(name = "last_played_at") val lastPlayedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "media_store_id") val mediaStoreId: Long? = null,
)
