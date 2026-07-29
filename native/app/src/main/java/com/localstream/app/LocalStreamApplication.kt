package com.localstream.app

import android.app.Application
import com.localstream.app.di.AppContainer

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
        container = AppContainer(this)
    }
}
