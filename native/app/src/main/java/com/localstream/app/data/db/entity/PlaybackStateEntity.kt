package com.localstream.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Table Room : progression de lecture.
 * Fusionne les trois cl\u00e9s localStorage : watchProgress, watchPositions et recentlyWatched.
 *
 * - [progressPct]  : pourcentage (0.0 – 100.0), \u00e9quivalent "watchProgress"
 * - [positionMs]   : position en millisecondes, \u00e9quivalent "watchPositions"
 * - [lastPlayedAt] : timestamp ms, permet de r\u00e9construire "recentlyWatched" par tri DESC
 * - [mediaStoreId] : nullable, fiabilise la cl\u00e9 \u00e0 terme (renommage de fichier)
 */
@Entity(tableName = "playback_state")
data class PlaybackStateEntity(
    @PrimaryKey
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "progress_pct") val progressPct: Double = 0.0,
    @ColumnInfo(name = "position_ms") val positionMs: Long = 0L,
    @ColumnInfo(name = "last_played_at") val lastPlayedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "media_store_id") val mediaStoreId: Long? = null,
)
