package com.localstream.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tmdb_metadata")
data class TmdbMetadataEntity(
    @PrimaryKey
    @ColumnInfo(name = "query_key")
    val queryKey: String,
    @ColumnInfo(name = "json")
    val json: String,
    @ColumnInfo(name = "fetched_at")
    val fetchedAt: Long,
)
