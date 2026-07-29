package com.localstream.app.data.repository

import com.localstream.app.data.local.SubtitleCache
import com.localstream.app.data.remote.opensubtitles.OpenSubtitlesApi
import com.localstream.app.data.remote.opensubtitles.dto.OsDownloadRequest
import com.localstream.app.data.remote.opensubtitles.dto.OsLoginRequest
import com.localstream.app.domain.TitleCleaner
import com.localstream.app.domain.model.SubtitleInfo
import java.io.File
import java.io.IOException
import retrofit2.HttpException

/** Échec d'authentification OpenSubtitles — l'utilisateur doit se reconnecter. */
class OpenSubtitlesAuthException(
    message: String = "Session OpenSubtitles expirée — reconnectez-vous",
) : Exception(message)

/**
 * Repository OpenSubtitles natif (Phase 6) : login, recherche, téléchargement.
 *
 * - Jeton stocké chiffré via [SettingsRepository] (Phase 4).
 * - Sur 401 : re-login silencieux une fois, puis [OpenSubtitlesAuthException].
 * - Les .srt sont écrits dans [SubtitleCache] — pas de conversion VTT, ExoPlayer
 *   lit le SRT directement (MimeTypes.APPLICATION_SUBRIP).
 */
@Suppress("TooGenericExceptionCaught", "ReturnCount", "ThrowsCount")
class OpenSubtitlesRepository(
    private val api: OpenSubtitlesApi,
    private val settingsRepository: SettingsRepository,
    private val subtitleCache: SubtitleCache,
) {

    /** Authentifie l'utilisateur et mémorise le jeton (chiffré). */
    suspend fun login(): Result<String> = loginInternal()

    /** Recherche des sous-titres FR/EN pour un titre (nettoyé via [TitleCleaner]). */
    suspend fun search(rawQuery: String): Result<List<SubtitleInfo>> {
        if (settingsRepository.getOpenSubtitlesApiKey().isBlank()) {
            return Result.failure(IllegalStateException("Clé API OpenSubtitles manquante"))
        }
        return try {
            val query = TitleCleaner.getCleanTitle(rawQuery)
            val response = api.search(query)
            val subtitles = response.data.mapNotNull { dto ->
                val attributes = dto.attributes ?: return@mapNotNull null
                val file = attributes.files.firstOrNull() ?: return@mapNotNull null
                SubtitleInfo(
                    id = file.fileId.toString(),
                    language = attributes.language,
                    filename = file.fileName,
                )
            }
            Result.success(subtitles)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Télécharge le .srt dans le cache (ou renvoie le fichier déjà en cache). */
    suspend fun downloadSubtitle(fileId: String): Result<File> {
        subtitleCache.get(fileId)?.let { return Result.success(it) }

        var token = settingsRepository.getOpenSubtitlesToken()
        if (token.isBlank()) {
            token = loginInternal().getOrElse { return Result.failure(it) }
        }

        return try {
            Result.success(downloadWithToken(fileId, token, allowRelogin = true))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun downloadWithToken(fileId: String, token: String, allowRelogin: Boolean): File {
        val response = api.requestDownload(
            OsDownloadRequest(fileId.toLong()),
            bearer(token),
        )
        if (response.code() == HTTP_UNAUTHORIZED) {
            if (!allowRelogin) {
                settingsRepository.saveOpenSubtitlesToken("")
                throw OpenSubtitlesAuthException()
            }
            // Jeton expiré : re-login silencieux une seule fois.
            val newToken = loginInternal().getOrThrow()
            return downloadWithToken(fileId, newToken, allowRelogin = false)
        }
        if (!response.isSuccessful) throw HttpException(response)

        val link = response.body()?.link
            ?: throw IOException("Lien de téléchargement OpenSubtitles indisponible")

        val fileResponse = api.downloadFile(link)
        if (!fileResponse.isSuccessful) throw HttpException(fileResponse)
        val bytes = fileResponse.body()?.bytes()
            ?: throw IOException("Fichier de sous-titres vide")
        return subtitleCache.store(fileId, bytes)
    }

    private suspend fun loginInternal(): Result<String> {
        val username = settingsRepository.getOpenSubtitlesUsername()
        val password = settingsRepository.getOpenSubtitlesPassword()
        if (username.isBlank() || password.isBlank()) {
            return Result.failure(IllegalStateException("Identifiants OpenSubtitles manquants"))
        }
        return try {
            val token = api.login(OsLoginRequest(username, password)).token
            if (token.isNullOrBlank()) {
                Result.failure(OpenSubtitlesAuthException("Connexion OpenSubtitles refusée"))
            } else {
                settingsRepository.saveOpenSubtitlesToken(token)
                Result.success(token)
            }
        } catch (e: HttpException) {
            if (e.code() == HTTP_UNAUTHORIZED) {
                Result.failure(OpenSubtitlesAuthException("Identifiants OpenSubtitles invalides"))
            } else {
                Result.failure(e)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun bearer(token: String) = "Bearer $token"

    companion object {
        const val HTTP_UNAUTHORIZED = 401
    }
}
