package com.localstream.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Table Room : \u00e9tats "vu / non vu".
 * Cl\u00e9 : [name] (nom de fichier) — m\u00eame cl\u00e9 que localStorage "watchedVideos".
 * [mediaStoreId] est nullable pour compatibilit\u00e9 (renseign\u00e9 progressivement).
 */
@Entity(tableName = "watched_items")
data class WatchedItemEntity(
    @PrimaryKey
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "watched") val watched: Boolean = true,
    @ColumnInfo(name = "watched_at") val watchedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "media_store_id") val mediaStoreId: Long? = null,
)
