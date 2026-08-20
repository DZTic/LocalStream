package com.localstream.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.localstream.app.di.AppContainer

import kotlinx.coroutines.Dispatchers

/**
 * Application de l'app native : héberge le [AppContainer] (injection manuelle)
 * et configure le chargeur d'images Coil global avec cache mémoire et disque optimisés.
 */
class LocalStreamApplication : Application(), ImageLoaderFactory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.35)
                    .strongReferencesEnabled(true)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(150L * 1024 * 1024)
                    .build()
            }
            .crossfade(false)
            .allowRgb565(true)
            .allowHardware(true)
            .fetcherDispatcher(Dispatchers.IO)
            .decoderDispatcher(Dispatchers.IO)
            .respectCacheHeaders(false)
            .build()
    }
}

