package com.localstream.app.data.remote.tmdb.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TmdbSearchResponse(
    @SerialName("results") val results: List<TmdbSearchResultDto> = emptyList(),
)

@Serializable
data class TmdbSearchResultDto(
    val id: Long,
    val title: String? = null,
    val name: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("overview") val overview: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("genre_ids") val genreIds: List<Int>? = null,
    @SerialName("media_type") val mediaType: String? = null,
)

@Serializable
data class TmdbMovieDetailsDto(
    val id: Long,
    val title: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("overview") val overview: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("genres") val genres: List<TmdbGenreDto>? = null,
    @SerialName("belongs_to_collection") val belongsToCollection: TmdbBelongsToCollectionDto? = null,
)

@Serializable
data class TmdbBelongsToCollectionDto(
    val id: Long,
    val name: String,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
)

@Serializable
data class TmdbCollectionDetailsDto(
    val id: Long,
    val name: String,
    @SerialName("overview") val overview: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("parts") val parts: List<TmdbSearchResultDto>? = null,
)

@Serializable
data class TmdbSeasonDetailsDto(
    val id: Long? = null,
    @SerialName("season_number") val seasonNumber: Int = 1,
    @SerialName("episodes") val episodes: List<TmdbEpisodeDto> = emptyList(),
)

@Serializable
data class TmdbEpisodeDto(
    val id: Long,
    val name: String? = null,
    @SerialName("overview") val overview: String? = null,
    @SerialName("still_path") val stillPath: String? = null,
    @SerialName("season_number") val seasonNumber: Int = 1,
    @SerialName("episode_number") val episodeNumber: Int = 1,
)

@Serializable
data class TmdbGenreDto(
    val id: Int,
    val name: String,
)
