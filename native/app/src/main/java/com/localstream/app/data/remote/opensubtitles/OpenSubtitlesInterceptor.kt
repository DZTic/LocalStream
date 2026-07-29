package com.localstream.app.data.remote.opensubtitles

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Interceptor OkHttp centralisant les en-têtes exigés par OpenSubtitles :
 * `Api-Key` (obligatoire sur toutes les routes) et `User-Agent` (identifiant
 * applicatif requis par leur politique d'usage).
 */
class OpenSubtitlesInterceptor(
    private val apiKeyProvider: () -> String,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("Api-Key", apiKeyProvider())
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .build()
        return chain.proceed(request)
    }

    companion object {
        const val USER_AGENT = "LocalStream v0.1"
    }
}
