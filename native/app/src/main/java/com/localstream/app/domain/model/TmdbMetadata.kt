package com.localstream.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class TmdbMetadata(
    val queryKey: String = "",
    val tmdbId: Long? = null,
    val title: String? = null,
    val overview: String? = null,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val genreIds: List<Int> = emptyList(),
    val releaseDate: String? = null,
    val mediaType: String? = null,
    val collectionId: Long? = null,
    val collectionName: String? = null,
) {
    fun posterUrl(): String? = posterPath?.let {
        if (it.startsWith("http")) it else "https://image.tmdb.org/t/p/w500$it"
    }

    fun backdropUrl(): String? = backdropPath?.let {
        if (it.startsWith("http")) it else "https://image.tmdb.org/t/p/w1280$it"
    }
}
