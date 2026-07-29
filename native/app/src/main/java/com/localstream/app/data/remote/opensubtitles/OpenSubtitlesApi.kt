package com.localstream.app.data.remote.opensubtitles

import com.localstream.app.data.remote.opensubtitles.dto.OsDownloadRequest
import com.localstream.app.data.remote.opensubtitles.dto.OsDownloadResponse
import com.localstream.app.data.remote.opensubtitles.dto.OsLoginRequest
import com.localstream.app.data.remote.opensubtitles.dto.OsLoginResponse
import com.localstream.app.data.remote.opensubtitles.dto.OsSearchResponse
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Url

/**
 * API Retrofit OpenSubtitles (Phase 6).
 *
 * Remplace `src/lib/opensubtitles.ts` : appels directs, sans proxy Express ni
 * CapacitorHttp. Les en-têtes `Api-Key` / `User-Agent` sont ajoutés par
 * [OpenSubtitlesInterceptor] ; seul le `Authorization: Bearer` (jeton de
 * téléchargement) est passé en paramètre.
 */
interface OpenSubtitlesApi {

    @POST("login")
    suspend fun login(@Body body: OsLoginRequest): OsLoginResponse

    @GET("subtitles")
    suspend fun search(
        @Query("query") query: String,
        @Query("languages") languages: String = DEFAULT_LANGUAGES,
    ): OsSearchResponse

    @POST("download")
    suspend fun requestDownload(
        @Body body: OsDownloadRequest,
        @Header("Authorization") authorization: String,
    ): Response<OsDownloadResponse>

    /** Téléchargement du fichier .srt depuis le lien direct renvoyé par [requestDownload]. */
    @GET
    suspend fun downloadFile(@Url url: String): Response<ResponseBody>

    companion object {
        const val BASE_URL = "https://api.opensubtitles.com/api/v1/"
        const val DEFAULT_LANGUAGES = "fr,en"
    }
}
