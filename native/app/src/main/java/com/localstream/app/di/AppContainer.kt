package com.localstream.app.di

import android.content.Context
import com.localstream.app.data.db.AppDatabase
import com.localstream.app.data.local.EncryptedPreferencesManager
import com.localstream.app.data.local.UserPreferencesDataStore
import com.localstream.app.data.remote.tmdb.TmdbApi
import com.localstream.app.data.repository.SettingsRepository
import com.localstream.app.data.repository.TmdbRepository
import com.localstream.app.data.repository.VideoRepository
import com.localstream.app.data.repository.WatchStateRepository
import com.localstream.app.data.scanner.MediaStoreScanner
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Conteneur d'injection manuelle (pas de Hilt, cf. README du module).
 * Construit le graphe de dépendances une fois au démarrage de l'application :
 * Room, DataStore, Retrofit/TMDB, repositories partagés par les ViewModels.
 */
class AppContainer(context: Context) {

    private val appContext: Context = context.applicationContext

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val database: AppDatabase by lazy { AppDatabase.getInstance(appContext) }

    private val okHttpClient: OkHttpClient by lazy { OkHttpClient.Builder().build() }

    private val tmdbApi: TmdbApi by lazy {
        Retrofit.Builder()
            .baseUrl(TmdbApi.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(TmdbApi::class.java)
    }

    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(
            encryptedPrefs = EncryptedPreferencesManager(appContext),
            dataStore = UserPreferencesDataStore(appContext),
        )
    }

    val videoRepository: VideoRepository by lazy {
        VideoRepository(mediaScanner = MediaStoreScanner(appContext))
    }

    val watchStateRepository: WatchStateRepository by lazy {
        WatchStateRepository(
            watchedItemDao = database.watchedItemDao(),
            playbackStateDao = database.playbackStateDao(),
        )
    }

    val tmdbRepository: TmdbRepository by lazy {
        TmdbRepository(
            tmdbApi = tmdbApi,
            tmdbMetadataDao = database.tmdbMetadataDao(),
            settingsRepository = settingsRepository,
            json = json,
        )
    }
}
