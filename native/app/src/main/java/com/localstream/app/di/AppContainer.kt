package com.localstream.app.di

import android.content.Context
import com.localstream.app.data.db.AppDatabase
import com.localstream.app.data.local.EncryptedPreferencesManager
import com.localstream.app.data.local.SubtitleCache
import com.localstream.app.data.local.UserPreferencesDataStore
import com.localstream.app.data.remote.opensubtitles.OpenSubtitlesApi
import com.localstream.app.data.remote.opensubtitles.OpenSubtitlesInterceptor
import com.localstream.app.data.remote.tmdb.TmdbApi
import com.localstream.app.data.remote.tmdb.TmdbAuthInterceptor
import com.localstream.app.data.repository.OpenSubtitlesRepository
import com.localstream.app.data.repository.PlaylistRepository
import com.localstream.app.data.repository.SettingsRepository
import com.localstream.app.data.repository.TmdbRepository
import com.localstream.app.data.repository.VideoRepository
import com.localstream.app.data.repository.WatchStateRepository
import com.localstream.app.data.scanner.MediaStoreScanner
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Conteneur d'injection manuelle (pas de Hilt, cf. README du module).
 * Construit le graphe de dépendances une fois au démarrage de l'application :
 * Room, DataStore, Retrofit/TMDB/OpenSubtitles, repositories partagés par les ViewModels.
 */
@Suppress("LongParameterList")
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

    private val okHttpClient: OkHttpClient by lazy {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)

        appContext?.cacheDir?.let { cacheDir ->
            builder.cache(Cache(File(cacheDir, "http_cache"), 50L * 1024 * 1024))
        }
        builder.build()
    }

    private val tmdbApi: TmdbApi by lazy {
        if (appContext == null) NoOpTmdbApi() else {
            val client = okHttpClient.newBuilder()
                .addInterceptor(TmdbAuthInterceptor { settingsRepository.getTmdbApiKey() })
                .build()
            Retrofit.Builder()
                .baseUrl(TmdbApi.BASE_URL)
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(TmdbApi::class.java)
        }
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
            encryptedPrefs = appContext?.let { ctx -> runCatching { EncryptedPreferencesManager(ctx) }.getOrNull() },
            dataStore = appContext?.let { UserPreferencesDataStore(it) },
        )
    }

    open val videoRepository: VideoRepository by lazy {
        overrideVideoRepository ?: VideoRepository(
            mediaScanner = appContext?.let { MediaStoreScanner(it) } ?: NoOpScanner()
        )
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
}
