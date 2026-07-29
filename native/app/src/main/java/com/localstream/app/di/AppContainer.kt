package com.localstream.app.di

import android.content.Context
import com.localstream.app.data.db.AppDatabase
import com.localstream.app.data.db.dao.PlaybackStateDao
import com.localstream.app.data.db.dao.PlaylistDao
import com.localstream.app.data.db.dao.TmdbMetadataDao
import com.localstream.app.data.db.dao.WatchedItemDao
import com.localstream.app.data.db.entity.PlaybackStateEntity
import com.localstream.app.data.db.entity.PlaylistEntity
import com.localstream.app.data.db.entity.PlaylistItemEntity
import com.localstream.app.data.db.entity.TmdbMetadataEntity
import com.localstream.app.data.db.entity.WatchedItemEntity
import com.localstream.app.data.local.EncryptedPreferencesManager
import com.localstream.app.data.local.SubtitleCache
import com.localstream.app.data.local.UserPreferencesDataStore
import com.localstream.app.data.remote.opensubtitles.OpenSubtitlesApi
import com.localstream.app.data.remote.opensubtitles.OpenSubtitlesInterceptor
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
import com.localstream.app.data.repository.OpenSubtitlesRepository
import com.localstream.app.data.repository.PlaylistRepository
import com.localstream.app.data.repository.SettingsRepository
import com.localstream.app.data.repository.TmdbRepository
import com.localstream.app.data.repository.VideoRepository
import com.localstream.app.data.repository.WatchStateRepository
import com.localstream.app.data.scanner.MediaScanner
import com.localstream.app.data.scanner.MediaStoreScanner
import com.localstream.app.domain.model.MovieCollection
import com.localstream.app.domain.model.SubtitleEntry
import com.localstream.app.domain.model.VideoItem
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Conteneur d'injection manuelle (pas de Hilt, cf. README du module).
 * Construit le graphe de dépendances une fois au démarrage de l'application :
 * Room, DataStore, Retrofit/TMDB/OpenSubtitles, repositories partagés par les ViewModels.
 */
@Suppress("LongParameterList", "EmptyFunctionBlock")
open class AppContainer(
    context: Context? = null,
    overrideSettingsRepository: SettingsRepository? = null,
    overrideVideoRepository: VideoRepository? = null,
    overrideWatchStateRepository: WatchStateRepository? = null,
    overrideTmdbRepository: TmdbRepository? = null,
    overridePlaylistRepository: PlaylistRepository? = null,
    overrideOpenSubtitlesRepository: OpenSubtitlesRepository? = null,
) {

    private val appContext: Context? = context?.applicationContext

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val database: AppDatabase? by lazy {
        appContext?.let { AppDatabase.getInstance(it) }
    }

    private val okHttpClient: OkHttpClient by lazy { OkHttpClient.Builder().build() }

    private val tmdbApi: TmdbApi by lazy {
        if (appContext == null) NoOpTmdbApi() else Retrofit.Builder()
            .baseUrl(TmdbApi.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(TmdbApi::class.java)
    }

    private val openSubtitlesApi: OpenSubtitlesApi by lazy {
        if (appContext == null) NoOpOpenSubtitlesApi() else {
            val client = okHttpClient.newBuilder()
                .addInterceptor(OpenSubtitlesInterceptor { settingsRepository.getOpenSubtitlesApiKey() })
                .build()
            Retrofit.Builder()
                .baseUrl(OpenSubtitlesApi.BASE_URL)
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(OpenSubtitlesApi::class.java)
        }
    }

    open val settingsRepository: SettingsRepository by lazy {
        overrideSettingsRepository ?: SettingsRepository(
            encryptedPrefs = appContext?.let { EncryptedPreferencesManager(it) },
            dataStore = appContext?.let { UserPreferencesDataStore(it) },
        )
    }

    open val videoRepository: VideoRepository by lazy {
        overrideVideoRepository ?: VideoRepository(mediaScanner = appContext?.let { MediaStoreScanner(it) } ?: NoOpScanner())
    }

    open val watchStateRepository: WatchStateRepository by lazy {
        overrideWatchStateRepository ?: WatchStateRepository(
            watchedItemDao = database?.watchedItemDao() ?: NoOpWatchedItemDao(),
            playbackStateDao = database?.playbackStateDao() ?: NoOpPlaybackStateDao(),
        )
    }

    open val tmdbRepository: TmdbRepository by lazy {
        overrideTmdbRepository ?: TmdbRepository(
            tmdbApi = tmdbApi,
            tmdbMetadataDao = database?.tmdbMetadataDao() ?: NoOpTmdbMetadataDao(),
            settingsRepository = settingsRepository,
            json = json,
        )
    }

    open val playlistRepository: PlaylistRepository by lazy {
        overridePlaylistRepository ?: PlaylistRepository(database?.playlistDao() ?: NoOpPlaylistDao())
    }

    open val openSubtitlesRepository: OpenSubtitlesRepository by lazy {
        overrideOpenSubtitlesRepository ?: OpenSubtitlesRepository(
            api = openSubtitlesApi,
            settingsRepository = settingsRepository,
            subtitleCache = SubtitleCache(appContext?.cacheDir ?: File("/tmp")),
        )
    }

    // -------- Helpers test / fallbacks --------

    private class NoOpScanner : MediaScanner {
        override fun scanVideoFiles(): List<VideoItem> = emptyList()
        override fun scanSubtitleFiles(): List<SubtitleEntry> = emptyList()
        override fun scanAndGroup(
            whitelistedVideos: Set<String>,
            movieCollections: Map<String, MovieCollection>,
            releaseDates: Map<String, String>,
        ): List<VideoItem> = emptyList()
    }

    private class NoOpWatchedItemDao : WatchedItemDao {
        override fun observeWatchedItems(): Flow<List<WatchedItemEntity>> = MutableStateFlow(emptyList())
        override suspend fun getAllWatchedItems(): List<WatchedItemEntity> = emptyList()
        override suspend fun upsert(item: WatchedItemEntity) {}
        override suspend fun upsertAll(items: List<WatchedItemEntity>) {}
        override suspend fun deleteByName(name: String) {}
        override suspend fun deleteAll() {}
        override suspend fun findByName(name: String): WatchedItemEntity? = null
    }

    private class NoOpPlaybackStateDao : PlaybackStateDao {
        override fun observeActivePlaybackStates(): Flow<List<PlaybackStateEntity>> = MutableStateFlow(emptyList())
        override suspend fun getRecentlyPlayed(limit: Int): List<PlaybackStateEntity> = emptyList()
        override suspend fun getAll(): List<PlaybackStateEntity> = emptyList()
        override suspend fun upsert(state: PlaybackStateEntity) {}
        override suspend fun upsertAll(states: List<PlaybackStateEntity>) {}
        override suspend fun deleteByName(name: String) {}
        override suspend fun deleteAll() {}
        override suspend fun findByName(name: String): PlaybackStateEntity? = null
    }

    private class NoOpTmdbMetadataDao : TmdbMetadataDao {
        override suspend fun getMetadata(queryKey: String): TmdbMetadataEntity? = null
        override suspend fun insertMetadata(entity: TmdbMetadataEntity) {}
        override suspend fun deleteMetadata(queryKey: String) {}
        override suspend fun clearAll() {}
        override suspend fun getAll(): List<TmdbMetadataEntity> = emptyList()
    }

    private class NoOpPlaylistDao : PlaylistDao {
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

    private class NoOpTmdbApi : TmdbApi {
        private fun unused(): Nothing = throw UnsupportedOperationException("NoOp")
        override suspend fun searchMulti(apiKey: String, query: String, language: String): TmdbSearchResponse = unused()
        override suspend fun searchMovie(apiKey: String, query: String, language: String): TmdbSearchResponse = unused()
        override suspend fun getMovieDetails(movieId: Long, apiKey: String, language: String): TmdbMovieDetailsDto = unused()
        override suspend fun getCollection(collectionId: Long, apiKey: String, language: String): TmdbCollectionDetailsDto = unused()
        override suspend fun getSeason(tvId: Long, seasonNumber: Int, apiKey: String, language: String): TmdbSeasonDetailsDto = unused()
        override suspend fun getPopular(apiKey: String, language: String): Response<TmdbSearchResponse> = unused()
    }

    private class NoOpOpenSubtitlesApi : OpenSubtitlesApi {
        private fun unused(): Nothing = throw UnsupportedOperationException("NoOp")
        override suspend fun login(body: OsLoginRequest): OsLoginResponse = unused()
        override suspend fun search(query: String, languages: String): OsSearchResponse = unused()
        override suspend fun requestDownload(body: OsDownloadRequest, authorization: String): Response<OsDownloadResponse> = unused()
        override suspend fun downloadFile(url: String): Response<ResponseBody> = unused()
    }
}
