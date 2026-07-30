package com.localstream.app.data.repository

import com.localstream.app.data.db.dao.TmdbMetadataDao
import com.localstream.app.data.db.entity.TmdbMetadataEntity
import com.localstream.app.data.remote.tmdb.TmdbApi
import com.localstream.app.data.remote.tmdb.dto.TmdbMovieDetailsDto
import com.localstream.app.data.remote.tmdb.dto.TmdbSearchResultDto
import com.localstream.app.domain.TitleCleaner
import com.localstream.app.domain.model.TmdbEpisode
import com.localstream.app.domain.model.TmdbMetadata
import com.localstream.app.domain.model.VideoItem
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import retrofit2.HttpException

class TmdbAuthException(message: String = "Clé API TMDB invalide") : Exception(message)

@Suppress(
    "TooManyFunctions",
    "LargeClass",
    "LongMethod",
    "CyclomaticComplexMethod",
    "TooGenericExceptionCaught",
    "SwallowedException",
    "ReturnCount"
)
class TmdbRepository(
    private val tmdbApi: TmdbApi,
    private val tmdbMetadataDao: TmdbMetadataDao,
    private val settingsRepository: SettingsRepository,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
    maxConcurrentRequests: Int = DEFAULT_CONCURRENCY,
) {

    private val semaphore = Semaphore(maxConcurrentRequests)
    private val activeRequests = AtomicInteger(0)
    private val peakConcurrentRequests = AtomicInteger(0)

    val currentActiveRequests: Int get() = activeRequests.get()
    val maxObservedConcurrentRequests: Int get() = peakConcurrentRequests.get()

    fun resetMetrics() {
        activeRequests.set(0)
        peakConcurrentRequests.set(0)
    }

    suspend fun testApiKey(apiKeyOverride: String? = null): Result<Boolean> {
        val apiKey = apiKeyOverride ?: settingsRepository.getTmdbApiKey()
        if (apiKey.isBlank()) return Result.success(false)
        return try {
            val response = executeWithRetryAndThrottling {
                tmdbApi.getPopular(apiKey)
            }
            if (response.isSuccessful) {
                Result.success(true)
            } else if (response.code() == HTTP_UNAUTHORIZED) {
                Result.failure(TmdbAuthException())
            } else {
                Result.success(false)
            }
        } catch (e: TmdbAuthException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getCachedMetadata(queryKey: String): TmdbMetadata? {
        val entity = tmdbMetadataDao.getMetadata(queryKey) ?: return null
        if (entity.json == NOT_FOUND_JSON) return null
        return try {
            json.decodeFromString<TmdbMetadata>(entity.json)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getCachedEpisode(lookupName: String, season: Int, episode: Int): TmdbEpisode? {
        val epKey = "${lookupName}_s${season}_e${episode}"
        val entity = tmdbMetadataDao.getMetadata(epKey) ?: return null
        return try {
            json.decodeFromString<TmdbEpisode>(entity.json)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun fetchMetadataForVideo(
        video: VideoItem,
        forceRefresh: Boolean = false,
    ): Result<TmdbMetadata> {
        val lookupName = if (video.isSeriesGroup && !video.seriesName.isNullOrEmpty()) {
            video.seriesName
        } else {
            video.name
        }

        val cleanTitle = if (video.isSeriesGroup && !video.seriesName.isNullOrEmpty()) {
            video.seriesName
        } else {
            TitleCleaner.getCleanTitle(video.name)
        }

        val cachedEntity = tmdbMetadataDao.getMetadata(lookupName)
        val now = System.currentTimeMillis()

        if (!forceRefresh && cachedEntity != null) {
            if (cachedEntity.json == NOT_FOUND_JSON) {
                return Result.failure(NoSuchElementException("TMDB: Aucun résultat pour $cleanTitle"))
            }
            if (now - cachedEntity.fetchedAt < TTL_MS) {
                try {
                    val meta = json.decodeFromString<TmdbMetadata>(cachedEntity.json)
                    return Result.success(meta)
                } catch (e: Exception) {
                    // Ignorer et re-fetch si JSON invalide
                }
            }
        }

        val apiKey = settingsRepository.getTmdbApiKey()
        if (apiKey.isBlank()) {
            return Result.failure(IllegalStateException("Clé API TMDB manquante"))
        }

        // Cas spécial : groupe saga de films (isSeriesGroup == true && isTvSeries == false)
        if (video.isSeriesGroup && !video.isTvSeries && !video.episodes.isNullOrEmpty()) {
            val firstEpWithCollection = video.episodes.mapNotNull { ep ->
                val meta = getCachedMetadata(ep.name)
                meta?.collectionId
            }.firstOrNull()

            if (firstEpWithCollection != null) {
                try {
                    val colDetails = executeWithRetryAndThrottling {
                        tmdbApi.getCollection(firstEpWithCollection, apiKey)
                    }
                    val sagaMetadata = TmdbMetadata(
                        queryKey = lookupName,
                        tmdbId = colDetails.id,
                        title = colDetails.name,
                        overview = colDetails.overview,
                        posterPath = colDetails.posterPath ?: video.episodes.firstOrNull()?.let { ep -> getCachedMetadata(ep.name)?.posterPath },
                        backdropPath = colDetails.backdropPath ?: video.episodes.firstOrNull()?.let { ep -> getCachedMetadata(ep.name)?.backdropPath },
                        genreIds = emptyList(),
                        releaseDate = null,
                        mediaType = "collection",
                        collectionId = colDetails.id,
                        collectionName = colDetails.name,
                    )
                    val jsonStr = json.encodeToString(sagaMetadata)
                    tmdbMetadataDao.insertMetadata(
                        TmdbMetadataEntity(
                            queryKey = lookupName,
                            json = jsonStr,
                            fetchedAt = now,
                        )
                    )
                    return Result.success(sagaMetadata)
                } catch (_: Exception) {
                }
            }
        }

        return try {
            val metadata = fetchFromRemote(apiKey, lookupName, cleanTitle, video)
            val jsonStr = json.encodeToString(metadata)
            tmdbMetadataDao.insertMetadata(
                TmdbMetadataEntity(
                    queryKey = lookupName,
                    json = jsonStr,
                    fetchedAt = now,
                )
            )
            Result.success(metadata)
        } catch (e: NoSuchElementException) {
            tmdbMetadataDao.insertMetadata(
                TmdbMetadataEntity(
                    queryKey = lookupName,
                    json = NOT_FOUND_JSON,
                    fetchedAt = now,
                )
            )
            Result.failure(e)
        } catch (e: Exception) {
            // En cas d'erreur réseau / HTTP, repli sur le cache expiré si présent
            if (cachedEntity != null && cachedEntity.json != NOT_FOUND_JSON) {
                try {
                    val meta = json.decodeFromString<TmdbMetadata>(cachedEntity.json)
                    return Result.success(meta)
                } catch (_: Exception) {
                }
            }
            Result.failure(e)
        }
    }

    private suspend fun fetchFromRemote(
        apiKey: String,
        lookupName: String,
        cleanTitle: String,
        video: VideoItem,
    ): TmdbMetadata {
        val searchResponse = executeWithRetryAndThrottling {
            tmdbApi.searchMulti(apiKey, cleanTitle)
        }

        val results = searchResponse.results
        if (results.isEmpty()) {
            throw NoSuchElementException("Aucun résultat pour $cleanTitle")
        }

        val bestResult = selectBestMatch(results, cleanTitle, video.isTvSeries)
            ?: throw NoSuchElementException("Aucun résultat valide pour $cleanTitle")

        var collectionId: Long? = null
        var collectionName: String? = null
        var details: TmdbMovieDetailsDto? = null

        val isMovie = bestResult.mediaType == "movie" || (!video.isTvSeries && !video.isSeriesGroup)
        if (isMovie) {
            try {
                details = executeWithRetryAndThrottling {
                    tmdbApi.getMovieDetails(bestResult.id, apiKey)
                }
                details.belongsToCollection?.let { col ->
                    collectionId = col.id
                    collectionName = col.name
                }
            } catch (_: Exception) {
            }
        }

        val metadata = TmdbMetadata(
            queryKey = lookupName,
            tmdbId = bestResult.id,
            title = details?.title ?: bestResult.title ?: bestResult.name ?: cleanTitle,
            overview = details?.overview ?: bestResult.overview,
            posterPath = details?.posterPath ?: bestResult.posterPath,
            backdropPath = details?.backdropPath ?: bestResult.backdropPath,
            genreIds = bestResult.genreIds ?: emptyList(),
            releaseDate = details?.releaseDate ?: bestResult.releaseDate ?: bestResult.firstAirDate,
            mediaType = bestResult.mediaType ?: if (video.isTvSeries) "tv" else "movie",
            collectionId = collectionId,
            collectionName = collectionName,
        )

        if (video.isTvSeries) {
            fetchEpisodesForSeries(apiKey, lookupName, bestResult.id, video)
        }

        return metadata
    }

    private suspend fun fetchEpisodesForSeries(
        apiKey: String,
        lookupName: String,
        tvId: Long,
        video: VideoItem,
    ) {
        val seasons = video.episodes?.mapNotNull { it.season }?.distinct()?.ifEmpty { listOf(1) } ?: listOf(1)
        for (seasonNum in seasons) {
            try {
                val seasonDetails = executeWithRetryAndThrottling {
                    tmdbApi.getSeason(tvId, seasonNum, apiKey)
                }
                val now = System.currentTimeMillis()
                for (epDto in seasonDetails.episodes) {
                    val epKey = "${lookupName}_s${epDto.seasonNumber}_e${epDto.episodeNumber}"
                    val episode = TmdbEpisode(
                        epKey = epKey,
                        name = epDto.name ?: "Épisode ${epDto.episodeNumber}",
                        overview = epDto.overview ?: "(Pas de synopsis disponible)",
                        stillPath = epDto.stillPath,
                        seasonNumber = epDto.seasonNumber,
                        episodeNumber = epDto.episodeNumber,
                    )
                    tmdbMetadataDao.insertMetadata(
                        TmdbMetadataEntity(
                            queryKey = epKey,
                            json = json.encodeToString(episode),
                            fetchedAt = now,
                        )
                    )
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun selectBestMatch(
        results: List<TmdbSearchResultDto>,
        cleanTitle: String,
        isTvSeries: Boolean,
    ): TmdbSearchResultDto? {
        val cleanLower = cleanTitle.lowercase()
        val exactMatchWithPoster = results.find { dto ->
            val name = (dto.name ?: dto.title ?: "").lowercase()
            name == cleanLower && !dto.posterPath.isNullOrBlank()
        }
        if (exactMatchWithPoster != null) return exactMatchWithPoster

        val exactMatchAny = results.find { dto ->
            val name = (dto.name ?: dto.title ?: "").lowercase()
            name == cleanLower
        }
        if (exactMatchAny != null) return exactMatchAny

        return if (isTvSeries) {
            results.find { it.mediaType == "tv" && !it.posterPath.isNullOrBlank() }
                ?: results.find { !it.posterPath.isNullOrBlank() }
                ?: results.firstOrNull()
        } else {
            results.find { it.mediaType == "movie" && !it.posterPath.isNullOrBlank() }
                ?: results.find { !it.posterPath.isNullOrBlank() }
                ?: results.firstOrNull()
        }
    }

    suspend fun fetchAllMetadata(
        videos: List<VideoItem>,
        forceRefresh: Boolean = false,
    ): List<TmdbMetadata> = coroutineScope {
        videos.map { video ->
            async {
                fetchMetadataForVideo(video, forceRefresh).getOrNull()
            }
        }.awaitAll().filterNotNull()
    }

    suspend fun clearCache() {
        tmdbMetadataDao.clearAll()
    }

    private suspend fun <T> executeWithRetryAndThrottling(
        maxRetries: Int = DEFAULT_MAX_RETRIES,
        initialDelayMs: Long = INITIAL_RETRY_DELAY_MS,
        block: suspend () -> T,
    ): T {
        return semaphore.withPermit {
            val active = activeRequests.incrementAndGet()
            var peak = peakConcurrentRequests.get()
            while (active > peak) {
                if (peakConcurrentRequests.compareAndSet(peak, active)) break
                peak = peakConcurrentRequests.get()
            }
            val result = try {
                var currentDelay = initialDelayMs
                var attempt = 0
                var lastResult: T? = null
                while (true) {
                    try {
                        lastResult = block()
                        break
                    } catch (e: HttpException) {
                        if (e.code() == HTTP_UNAUTHORIZED) {
                            throw TmdbAuthException()
                        }
                        if (e.code() == HTTP_TOO_MANY_REQUESTS && attempt < maxRetries) {
                            attempt++
                            delay(currentDelay)
                            currentDelay *= RETRY_BACKOFF_FACTOR
                        } else {
                            throw e
                        }
                    }
                }
                @Suppress("UNCHECKED_CAST")
                lastResult as T
            } finally {
                activeRequests.decrementAndGet()
            }
            result
        }
    }

    companion object {
        const val DEFAULT_CONCURRENCY = 4
        const val TTL_MS = 30L * 24 * 3600 * 1000 // 30 jours
        const val NOT_FOUND_JSON = "{}"
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_TOO_MANY_REQUESTS = 429
        const val DEFAULT_MAX_RETRIES = 3
        const val INITIAL_RETRY_DELAY_MS = 100L
        const val RETRY_BACKOFF_FACTOR = 2
    }
}
