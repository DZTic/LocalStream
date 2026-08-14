package com.localstream.app.di

import com.localstream.app.data.db.dao.PlaybackStateDao
import com.localstream.app.data.db.dao.PlaylistDao
import com.localstream.app.data.db.dao.TmdbMetadataDao
import com.localstream.app.data.db.dao.WatchedItemDao
import com.localstream.app.data.db.entity.PlaybackStateEntity
import com.localstream.app.data.db.entity.PlaylistEntity
import com.localstream.app.data.db.entity.PlaylistItemEntity
import com.localstream.app.data.db.entity.TmdbMetadataEntity
import com.localstream.app.data.db.entity.WatchedItemEntity
import com.localstream.app.data.remote.opensubtitles.OpenSubtitlesApi
import com.localstream.app.data.remote.opensubtitles.dto.OsDownloadRequest
import com.localstream.app.data.remote.opensubtitles.dto.OsDownloadResponse
import com.localstream.app.data.remote.opensubtitles.dto.OsLoginRequest
import com.localstream.app.data.remote.opensubtitles.dto.OsLoginResponse
import com.localstream.app.data.remote.opensubtitles.dto.OsSearchResponse
import com.localstream.app.data.remote.tmdb.TmdbApi
import com.localstream.app.data.remote.tmdb.dto.TmdbCollectionDetailsDto
import com.localstream.app.data.remote.tmdb.dto.TmdbMovieDetailsDto
import com.localstream.app.data.remote.tmdb.dto.TmdbSearchResponse
import com.localstream.app.data.remote.tmdb.dto.TmdbSeasonDetailsDto
import com.localstream.app.data.scanner.MediaScanner
import com.localstream.app.domain.model.MovieCollection
import com.localstream.app.domain.model.SubtitleEntry
import com.localstream.app.domain.model.VideoItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.ResponseBody
import retrofit2.Response

@Suppress("EmptyFunctionBlock")
class NoOpScanner : MediaScanner {
    override fun scanVideoFiles(): List<VideoItem> = emptyList()
    override fun scanSubtitleFiles(): List<SubtitleEntry> = emptyList()
    override fun scanAndGroup(
        whitelistedVideos: Set<String>,
        movieCollections: Map<String, MovieCollection>,
        releaseDates: Map<String, String>,
    ): List<VideoItem> = emptyList()
}

@Suppress("EmptyFunctionBlock")
class NoOpWatchedItemDao : WatchedItemDao {
    override fun observeWatchedItems(): Flow<List<WatchedItemEntity>> = MutableStateFlow(emptyList())
    override suspend fun getAllWatchedItems(): List<WatchedItemEntity> = emptyList()
    override suspend fun upsert(item: WatchedItemEntity) {}
    override suspend fun upsertAll(items: List<WatchedItemEntity>) {}
    override suspend fun deleteByName(name: String) {}
    override suspend fun deleteAll() {}
    override suspend fun findByName(name: String): WatchedItemEntity? = null
}

@Suppress("EmptyFunctionBlock")
class NoOpPlaybackStateDao : PlaybackStateDao {
    override fun observeActivePlaybackStates(): Flow<List<PlaybackStateEntity>> = MutableStateFlow(emptyList())
    override suspend fun getRecentlyPlayed(limit: Int): List<PlaybackStateEntity> = emptyList()
    override suspend fun getAll(): List<PlaybackStateEntity> = emptyList()
    override suspend fun upsert(state: PlaybackStateEntity) {}
    override suspend fun upsertAll(states: List<PlaybackStateEntity>) {}
    override suspend fun deleteByName(name: String) {}
    override suspend fun deleteAll() {}
    override suspend fun findByName(name: String): PlaybackStateEntity? = null
}

@Suppress("EmptyFunctionBlock")
class NoOpTmdbMetadataDao : TmdbMetadataDao {
    override suspend fun getMetadata(queryKey: String): TmdbMetadataEntity? = null
    override suspend fun insertMetadata(entity: TmdbMetadataEntity) {}
    override suspend fun deleteMetadata(queryKey: String) {}
    override suspend fun clearAll() {}
    override suspend fun getAll(): List<TmdbMetadataEntity> = emptyList()
}

@Suppress("EmptyFunctionBlock")
class NoOpPlaylistDao : PlaylistDao {
    override fun observePlaylists(): Flow<List<PlaylistEntity>> = MutableStateFlow(emptyList())
    override suspend fun getAllPlaylists(): List<PlaylistEntity> = emptyList()
    override suspend fun upsertPlaylist(playlist: PlaylistEntity) {}
    override suspend fun upsertPlaylists(playlists: List<PlaylistEntity>) {}
    override suspend fun deletePlaylist(id: String) {}
    override suspend fun deleteAllPlaylists() {}
    override fun observeItems(playlistId: String): Flow<List<PlaylistItemEntity>> = MutableStateFlow(emptyList())
    override suspend fun getItems(playlistId: String): List<PlaylistItemEntity> = emptyList()
    override suspend fun upsertItem(item: PlaylistItemEntity) {}
    override suspend fun upsertItems(items: List<PlaylistItemEntity>) {}
    override suspend fun deleteItem(playlistId: String, videoName: String) {}
    override suspend fun deleteAllItems(playlistId: String) {}
    override suspend fun getAllItems(): List<PlaylistItemEntity> = emptyList()
}

class NoOpTmdbApi : TmdbApi {
    private fun unused(): Nothing = throw UnsupportedOperationException("NoOp")
    override suspend fun searchMulti(apiKey: String, query: String, language: String, overrideKey: String?): TmdbSearchResponse = unused()
    override suspend fun searchMovie(
        apiKey: String,
        query: String,
        language: String,
        primaryReleaseYear: Int?,
        year: Int?,
        overrideKey: String?,
    ): TmdbSearchResponse = unused()
    override suspend fun getMovieDetails(movieId: Long, apiKey: String, language: String, overrideKey: String?): TmdbMovieDetailsDto = unused()
    override suspend fun getCollection(collectionId: Long, apiKey: String, language: String, overrideKey: String?): TmdbCollectionDetailsDto = unused()
    override suspend fun getSeason(tvId: Long, seasonNumber: Int, apiKey: String, language: String, overrideKey: String?): TmdbSeasonDetailsDto = unused()
    override suspend fun getPopular(apiKey: String, language: String, overrideKey: String?): Response<TmdbSearchResponse> = unused()
}

class NoOpOpenSubtitlesApi : OpenSubtitlesApi {
    private fun unused(): Nothing = throw UnsupportedOperationException("NoOp")
    override suspend fun login(body: OsLoginRequest): OsLoginResponse = unused()
    override suspend fun search(query: String, languages: String): OsSearchResponse = unused()
    override suspend fun requestDownload(body: OsDownloadRequest, authorization: String): Response<OsDownloadResponse> = unused()
    override suspend fun downloadFile(url: String): Response<ResponseBody> = unused()
}
