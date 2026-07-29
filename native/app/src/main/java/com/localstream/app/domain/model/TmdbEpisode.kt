package com.localstream.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class TmdbEpisode(
    val epKey: String = "",
    val name: String,
    val overview: String,
    val stillPath: String? = null,
    val seasonNumber: Int,
    val episodeNumber: Int,
) {
    fun stillUrl(): String? = stillPath?.let {
        if (it.startsWith("http")) it else "https://image.tmdb.org/t/p/w300$it"
    }
}
