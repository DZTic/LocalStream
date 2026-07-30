package com.localstream.app.ui.settings

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.localstream.app.di.AppContainer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ExternalPlayerInfo(
    val packageName: String,
    val appName: String,
)

data class SettingsUiState(
    val tmdbApiKey: String = "",
    val isTestingTmdbKey: Boolean = false,
    val tmdbTestResult: String? = null,
    val osApiKey: String = "",
    val osUsername: String = "",
    val osPassword: String = "",
    val osLoginStatus: String = "Non connecté",
    val isLoggingInOs: Boolean = false,
    val playerMode: String = "internal",
    val selectedExternalPlayer: String = "",
    val installedPlayers: List<ExternalPlayerInfo> = emptyList(),
)

@Suppress("TooManyFunctions", "UNCHECKED_CAST")
class SettingsViewModel(
    private val container: AppContainer,
) : ViewModel() {

    private val isTestingTmdbFlow = MutableStateFlow(false)
    private val tmdbTestResultFlow = MutableStateFlow<String?>(null)
    private val isLoggingInOsFlow = MutableStateFlow(false)
    private val osLoginStatusFlow = MutableStateFlow("Non connecté")
    private val installedPlayersFlow = MutableStateFlow<List<ExternalPlayerInfo>>(emptyList())

    val uiState: StateFlow<SettingsUiState> = combine(
        listOf<Flow<*>>(
            container.settingsRepository.observePlayerMode,
            container.settingsRepository.observeExternalPlayer,
            isTestingTmdbFlow,
            tmdbTestResultFlow,
            isLoggingInOsFlow,
            osLoginStatusFlow,
            installedPlayersFlow,
        )
    ) { args: Array<Any?> ->
        val mode = args[0] as String
        val extPlayer = args[1] as String
        val testingTmdb = args[2] as Boolean
        val tmdbRes = args[3] as String?
        val loggingOs = args[4] as Boolean
        val osStatus = args[5] as String
        val installed = args[6] as List<ExternalPlayerInfo>

        val tmdbKey = container.settingsRepository.getTmdbApiKey()
        val osKey = container.settingsRepository.getOpenSubtitlesApiKey()
        val osUser = container.settingsRepository.getOpenSubtitlesUsername()
        val osPass = container.settingsRepository.getOpenSubtitlesPassword()
        val osToken = container.settingsRepository.getOpenSubtitlesToken()

        val currentOsStatus = if (osToken.isNotBlank()) "Connecté" else osStatus

        SettingsUiState(
            tmdbApiKey = tmdbKey,
            isTestingTmdbKey = testingTmdb,
            tmdbTestResult = tmdbRes,
            osApiKey = osKey,
            osUsername = osUser,
            osPassword = osPass,
            osLoginStatus = currentOsStatus,
            isLoggingInOs = loggingOs,
            playerMode = mode,
            selectedExternalPlayer = extPlayer,
            installedPlayers = installed,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState(),
    )

    fun saveTmdbApiKey(key: String) {
        viewModelScope.launch {
            container.settingsRepository.saveTmdbApiKey(key.trim())
        }
    }

    fun testTmdbApiKey(apiKeyOverride: String? = null) {
        viewModelScope.launch {
            isTestingTmdbFlow.value = true
            tmdbTestResultFlow.value = null
            val result = container.tmdbRepository.testApiKey(apiKeyOverride)
            if (result.isSuccess && result.getOrDefault(false)) {
                tmdbTestResultFlow.value = "Clé API valide !"
            } else {
                tmdbTestResultFlow.value = result.exceptionOrNull()?.message ?: "Clé API TMDB invalide"
            }
            isTestingTmdbFlow.value = false
        }
    }

    fun saveOsCredentials(apiKey: String, user: String, pass: String) {
        viewModelScope.launch {
            container.settingsRepository.saveOpenSubtitlesApiKey(apiKey.trim())
            container.settingsRepository.saveOpenSubtitlesUsername(user.trim())
            container.settingsRepository.saveOpenSubtitlesPassword(pass.trim())
        }
    }

    fun loginOpenSubtitles() {
        viewModelScope.launch {
            isLoggingInOsFlow.value = true
            osLoginStatusFlow.value = "Connexion..."
            val result = container.openSubtitlesRepository.login()
            if (result.isSuccess) {
                osLoginStatusFlow.value = "Connecté"
            } else {
                osLoginStatusFlow.value = result.exceptionOrNull()?.message ?: "Échec de connexion"
            }
            isLoggingInOsFlow.value = false
        }
    }

    fun setPlayerMode(mode: String) {
        viewModelScope.launch {
            container.settingsRepository.setPlayerMode(mode)
        }
    }

    fun setSelectedExternalPlayer(packageName: String) {
        viewModelScope.launch {
            container.settingsRepository.setSelectedExternalPlayer(packageName)
        }
    }

    fun scanInstalledPlayers(context: Context) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse("file:///dummy.mp4"), "video/*")
        }
        val pm = context.packageManager
        val activities = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        val players = activities.map { resolve ->
            ExternalPlayerInfo(
                packageName = resolve.activityInfo.packageName,
                appName = resolve.loadLabel(pm).toString(),
            )
        }.distinctBy { it.packageName }
        installedPlayersFlow.value = players
    }

    fun openSystemAppSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
        context.startActivity(intent)
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SettingsViewModel(container) as T
                }
            }
    }
}
