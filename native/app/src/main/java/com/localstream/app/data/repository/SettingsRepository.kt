package com.localstream.app.data.repository

import com.localstream.app.data.local.PreferencesDataSource

/**
 * Repository gérant la lecture et la mise à jour des paramètres de l'application.
 */
class SettingsRepository(
    private val preferencesDataSource: PreferencesDataSource
) {
    fun getTmdbApiKey(): String = preferencesDataSource.getTmdbApiKey()
    fun saveTmdbApiKey(key: String) = preferencesDataSource.saveTmdbApiKey(key)

    fun getOpenSubtitlesApiKey(): String = preferencesDataSource.getOpenSubtitlesApiKey()
    fun saveOpenSubtitlesApiKey(key: String) = preferencesDataSource.saveOpenSubtitlesApiKey(key)

    fun getOpenSubtitlesUsername(): String = preferencesDataSource.getOpenSubtitlesUsername()
    fun saveOpenSubtitlesUsername(username: String) = preferencesDataSource.saveOpenSubtitlesUsername(username)

    fun getOpenSubtitlesPassword(): String = preferencesDataSource.getOpenSubtitlesPassword()
    fun saveOpenSubtitlesPassword(password: String) = preferencesDataSource.saveOpenSubtitlesPassword(password)

    fun getExternalPlayerPackage(): String = preferencesDataSource.getExternalPlayerPackage()
    fun saveExternalPlayerPackage(packageName: String) = preferencesDataSource.saveExternalPlayerPackage(packageName)
}
