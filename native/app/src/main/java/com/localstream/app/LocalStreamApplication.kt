package com.localstream.app

import android.app.Application
import android.util.Log
import com.localstream.app.di.AppContainer
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException

/**
 * Application de l'app native : héberge le [AppContainer] (injection manuelle).
 * Instanciée avant toute activité ; le conteneur est accessible via
 * `(context.applicationContext as LocalStreamApplication).container`.
 */
class LocalStreamApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        try {
            YoutubeDL.getInstance().init(this)
        } catch (error: YoutubeDLException) {
            Log.e("LocalStream", "Impossible d'initialiser yt-dlp", error)
        }
        container = AppContainer(this)
    }
}
