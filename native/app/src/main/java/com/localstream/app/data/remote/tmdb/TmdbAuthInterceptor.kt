package com.localstream.app.data.remote.tmdb

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Intercepteur OkHttp pour l'authentification TMDB.
 *
 * Gère de manière transparente les deux types d'authentification TMDB :
 * 1. Clé API v3 (32 caractères hexadécimaux) -> paramètre d'URL `api_key=<clé>`.
 * 2. API Read Access Token v4 (jeton JWT commençant par `ey...`) -> en-tête `Authorization: Bearer <token>`.
 *
 * Supporte également la substitution dynamique via l'en-tête [HEADER_OVERRIDE_KEY]
 * pour tester une clé avant son enregistrement.
 */
class TmdbAuthInterceptor(
    private val apiKeyProvider: () -> String,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val originalUrl = originalRequest.url

        val overrideKey = originalRequest.header(HEADER_OVERRIDE_KEY)
        val explicitQueryKey = originalUrl.queryParameter("api_key")
        val rawCandidate = overrideKey ?: apiKeyProvider().takeIf { it.isNotBlank() } ?: explicitQueryKey
        val cleanedKey = cleanKey(rawCandidate)

        val requestBuilder = originalRequest.newBuilder()
            .removeHeader(HEADER_OVERRIDE_KEY)

        if (cleanedKey.isBlank()) {
            return chain.proceed(requestBuilder.build())
        }

        val isV4Bearer = isV4Token(cleanedKey)
        if (isV4Bearer) {
            // Jeton v4 JWT : injection en-tête Bearer et suppression de tout api_key dans l'URL
            val newUrl = originalUrl.newBuilder()
                .removeAllQueryParameters("api_key")
                .build()
            requestBuilder
                .url(newUrl)
                .header("Authorization", "Bearer $cleanedKey")
        } else {
            // Clé API v3 : paramètre api_key dans l'URL
            val newUrl = originalUrl.newBuilder()
                .setQueryParameter("api_key", cleanedKey)
                .build()
            requestBuilder.url(newUrl)
        }

        return chain.proceed(requestBuilder.build())
    }

    companion object {
        const val HEADER_OVERRIDE_KEY = "X-Tmdb-Override-Key"

        fun cleanKey(key: String?): String {
            if (key == null) return ""
            return key.trim()
                .removePrefix("Bearer ")
                .removePrefix("bearer ")
                .removePrefix("BEARER ")
                .trim()
        }

        fun isV4Token(key: String): Boolean {
            val cleaned = cleanKey(key)
            return cleaned.startsWith("ey") || cleaned.length > 40
        }
    }
}
