package com.localstream.app.data.remote.tmdb

import com.localstream.app.data.remote.tmdb.dto.TmdbCollectionDetailsDto
import com.localstream.app.data.remote.tmdb.dto.TmdbMovieDetailsDto
import com.localstream.app.data.remote.tmdb.dto.TmdbSearchResponse
import com.localstream.app.data.remote.tmdb.dto.TmdbSeasonDetailsDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface TmdbApi {

    @GET("search/multi")
    suspend fun searchMulti(
        @Query("api_key") apiKey: String,
        @Query("query") query: String,
        @Query("language") language: String = DEFAULT_LANGUAGE,
    ): TmdbSearchResponse

    @GET("search/movie")
    suspend fun searchMovie(
        @Query("api_key") apiKey: String,
        @Query("query") query: String,
        @Query("language") language: String = DEFAULT_LANGUAGE,
    ): TmdbSearchResponse

    @GET("movie/{id}")
    suspend fun getMovieDetails(
        @Path("id") movieId: Long,
        @Query("api_key") apiKey: String,
        @Query("language") language: String = DEFAULT_LANGUAGE,
    ): TmdbMovieDetailsDto

    @GET("collection/{id}")
    suspend fun getCollection(
        @Path("id") collectionId: Long,
        @Query("api_key") apiKey: String,
        @Query("language") language: String = DEFAULT_LANGUAGE,
    ): TmdbCollectionDetailsDto

    @GET("tv/{tv_id}/season/{season_number}")
    suspend fun getSeason(
        @Path("tv_id") tvId: Long,
        @Path("season_number") seasonNumber: Int,
        @Query("api_key") apiKey: String,
        @Query("language") language: String = DEFAULT_LANGUAGE,
    ): TmdbSeasonDetailsDto

    @GET("movie/popular")
    suspend fun getPopular(
        @Query("api_key") apiKey: String,
        @Query("language") language: String = DEFAULT_LANGUAGE,
    ): Response<TmdbSearchResponse>

    companion object {
        const val BASE_URL = "https://api.themoviedb.org/3/"
        const val DEFAULT_LANGUAGE = "fr-FR"
    }
}
