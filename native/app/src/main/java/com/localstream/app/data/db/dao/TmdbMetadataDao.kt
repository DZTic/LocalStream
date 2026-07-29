package com.localstream.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.localstream.app.data.db.entity.TmdbMetadataEntity

@Dao
interface TmdbMetadataDao {

    @Query("SELECT * FROM tmdb_metadata WHERE query_key = :queryKey")
    suspend fun getMetadata(queryKey: String): TmdbMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMetadata(entity: TmdbMetadataEntity)

    @Query("DELETE FROM tmdb_metadata WHERE query_key = :queryKey")
    suspend fun deleteMetadata(queryKey: String)

    @Query("DELETE FROM tmdb_metadata")
    suspend fun clearAll()

    @Query("SELECT * FROM tmdb_metadata")
    suspend fun getAll(): List<TmdbMetadataEntity>
}
