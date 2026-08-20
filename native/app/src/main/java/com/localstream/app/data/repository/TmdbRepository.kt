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
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
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

import com.localstream.app.data.remote.tmdb.TmdbAuthInterceptor

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
open class TmdbRepository(
    private val tmdbApi: TmdbApi,
    private val tmdbMetadataDao: TmdbMetadataDao,
    private val settingsRepository: SettingsRepository,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
    maxConcurrentRequests: Int = DEFAULT_CONCURRENCY,
) {

    private val semaphore = Semaphore(maxConcurrentRequests)
    private val activeRequests = AtomicInteger(0)
    private val peakConcurrentRequests = AtomicInteger(0)

    // Cache mémoire thread-safe persistant jusqu'à la fermeture du processus applicatif
    private val metadataMemoryCache = ConcurrentHashMap<String, TmdbMetadata>()
    private val episodeMemoryCache = ConcurrentHashMap<String, TmdbEpisode>()
    private val notFoundMemoryKeys: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap())
    @Volatile
    private var isCachePrewarmed = false

    val currentActiveRequests: Int get() = activeRequests.get()
    val maxObservedConcurrentRequests: Int get() = peakConcurrentRequests.get()

    fun resetMetrics() {
        activeRequests.set(0)
        peakConcurrentRequests.set(0)
    }

    /**
     * Préchauffe le cache mémoire en une seule requête Room globale au démarrage
     * pour que les affiches et métadonnées soient immédiatement disponibles en RAM.
     */
    suspend fun prewarmCache() {
        if (isCachePrewarmed) return
        try {
            val entities = tmdbMetadataDao.getAll()
            for (entity in entities) {
                if (entity.json == NOT_FOUND_JSON) {
                    notFoundMemoryKeys.add(entity.queryKey)
                } else {
                    try {
                        if (entity.queryKey.contains("_s") && entity.queryKey.contains("_e")) {
                            val episode = json.decodeFromString<TmdbEpisode>(entity.json)
                            episodeMemoryCache[entity.queryKey] = episode
                        } else {
                            val meta = json.decodeFromString<TmdbMetadata>(entity.json)
                            metadataMemoryCache[entity.queryKey] = meta
                        }
                    } catch (_: Exception) {
                    }
                }
            }
            isCachePrewarmed = true
        } catch (_: Exception) {
        }
    }

    /**
     * Retourne l'ensemble des métadonnées TMDB actuellement en cache mémoire (ou préchauffées).
     */
    suspend fun getAllCachedMetadata(): Map<String, TmdbMetadata> {
        prewarmCache()
        return metadataMemoryCache.toMap()
    }

    open suspend fun testApiKey(apiKeyOverride: String? = null): Result<Boolean> {
        val rawCandidate = apiKeyOverride ?: settingsRepository.getTmdbApiKey()
        val apiKey = TmdbAuthInterceptor.cleanKey(rawCandidate)
        if (apiKey.isBlank()) {
            return Result.failure(IllegalArgumentException("Veuillez saisir une clé API TMDB"))
        }
        return try {
            val response = tmdbApi.validateApiKey(overrideKey = apiKey)
            if (response.isSuccessful) {
                Result.success(true)
            } else if (response.code() == HTTP_UNAUTHORIZED) {
                Result.failure(TmdbAuthException("Clé API TMDB invalide (erreur 401)"))
            } else {
                Result.failure(IllegalStateException("Erreur HTTP ${response.code()} lors du test TMDB"))
            }
        } catch (e: TmdbAuthException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getCachedMetadata(queryKey: String): TmdbMetadata? {
        metadataMemoryCache[queryKey]?.let { return it }
        if (notFoundMemoryKeys.contains(queryKey)) return null
        val entity = tmdbMetadataDao.getMetadata(queryKey) ?: return null
        if (entity.json == NOT_FOUND_JSON) {
            notFoundMemoryKeys.add(queryKey)
            return null
        }
        return try {
            val meta = json.decodeFromString<TmdbMetadata>(entity.json)
            metadataMemoryCache[queryKey] = meta
            meta
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getCachedEpisode(lookupName: String, season: Int, episode: Int): TmdbEpisode? {
        val epKey = "${lookupName}_s${season}_e${episode}"
        episodeMemoryCache[epKey]?.let { return it }
        if (notFoundMemoryKeys.contains(epKey)) return null
        val entity = tmdbMetadataDao.getMetadata(epKey) ?: return null
        if (entity.json == NOT_FOUND_JSON) {
            notFoundMemoryKeys.add(epKey)
            return null
        }
        return try {
            val ep = json.decodeFromString<TmdbEpisode>(entity.json)
            episodeMemoryCache[epKey] = ep
            ep
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

        if (!forceRefresh) {
            metadataMemoryCache[lookupName]?.let { return Result.success(it) }
            if (notFoundMemoryKeys.contains(lookupName)) {
                return Result.failure(NoSuchElementException("TMDB: Aucun résultat pour $cleanTitle"))
            }
        }

        val cachedEntity = tmdbMetadataDao.getMetadata(lookupName)
        val now = System.currentTimeMillis()

        if (!forceRefresh && cachedEntity != null) {
            if (cachedEntity.json == NOT_FOUND_JSON) {
                notFoundMemoryKeys.add(lookupName)
                return Result.failure(NoSuchElementException("TMDB: Aucun résultat pour $cleanTitle"))
            }
            if (now - cachedEntity.fetchedAt < TTL_MS) {
                try {
                    val meta = json.decodeFromString<TmdbMetadata>(cachedEntity.json)
                    metadataMemoryCache[lookupName] = meta
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
                    metadataMemoryCache[lookupName] = sagaMetadata
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
            metadataMemoryCache[lookupName] = metadata
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
            notFoundMemoryKeys.add(lookupName)
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
            if (!forceRefresh && cachedEntity != null && cachedEntity.json != NOT_FOUND_JSON) {
                try {
                    val meta = json.decodeFromString<TmdbMetadata>(cachedEntity.json)
                    metadataMemoryCache[lookupName] = meta
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
        val targetYear = video.year ?: TitleCleaner.extractYear(video.name)

        val searchResponse = executeWithRetryAndThrottling {
            tmdbApi.searchMulti(apiKey, cleanTitle)
        }

        val results = searchResponse.results
        if (results.isEmpty()) {
            throw NoSuchElementException("Aucun résultat pour $cleanTitle")
        }

        val bestResult = selectBestMatch(results, cleanTitle, video.isTvSeries, targetYear)
            ?: throw NoSuchElementException("Aucun résultat valide pour $cleanTitle")

        var collectionId: Long? = null
        var collectionName: String? = null
        var details: TmdbMovieDetailsDto? = null

        val isMovieResult = bestResult.mediaType == "movie" || (!video.isTvSeries && !video.isSeriesGroup)
        if (isMovieResult) {
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
                val entities = seasonDetails.episodes.map { epDto ->
                    val epKey = "${lookupName}_s${epDto.seasonNumber}_e${epDto.episodeNumber}"
                    val episode = TmdbEpisode(
                        epKey = epKey,
                        name = epDto.name ?: "Épisode ${epDto.episodeNumber}",
                        overview = epDto.overview ?: "(Pas de synopsis disponible)",
                        stillPath = epDto.stillPath,
                        seasonNumber = epDto.seasonNumber,
                        episodeNumber = epDto.episodeNumber,
                    )
                    episodeMemoryCache[epKey] = episode
                    TmdbMetadataEntity(
                        queryKey = epKey,
                        json = json.encodeToString(episode),
                        fetchedAt = now,
                    )
                }
                if (entities.isNotEmpty()) {
                    tmdbMetadataDao.insertMetadataList(entities)
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun selectBestMatch(
        results: List<TmdbSearchResultDto>,
        cleanTitle: String,
        isTvSeries: Boolean,
        targetYear: Int? = null,
    ): TmdbSearchResultDto? {
        if (results.isEmpty()) return null
        return results.maxByOrNull { dto ->
            scoreCandidate(dto, cleanTitle, isTvSeries, targetYear)
        }
    }

    @Suppress("MagicNumber")
    private fun scoreCandidate(
        dto: TmdbSearchResultDto,
        cleanTitle: String,
        isTvSeries: Boolean,
        targetYear: Int?,
    ): Int {
        var score = 0
        val cleanLower = cleanTitle.lowercase().trim()
        val candidateTitleRaw = (dto.title ?: dto.name ?: "").lowercase().trim()
        val candidateTitleNormalized = candidateTitleRaw.removePrefix("the ").trim()
        val cleanNormalized = cleanLower.removePrefix("the ").trim()

        val isExactTitle = candidateTitleRaw == cleanLower
        val isNormalizedTitleMatch = candidateTitleNormalized == cleanNormalized

        if (isExactTitle) {
            score += 100
        } else if (isNormalizedTitleMatch) {
            score += 80
        } else if (candidateTitleRaw.contains(cleanLower) || cleanLower.contains(candidateTitleRaw)) {
            score += 40
        }

        val mediaType = dto.mediaType
        if (isTvSeries) {
            if (mediaType == "tv") score += 50
            else if (mediaType == "movie") score -= 30
        } else {
            if (mediaType == "movie") score += 50
            else if (mediaType == "tv") score -= 30
        }

        val candidateYear = extractYearFromDateString(dto.releaseDate ?: dto.firstAirDate)
        if (targetYear != null && candidateYear != null) {
            if (candidateYear == targetYear) {
                score += 150
            } else {
                val yearDiff = kotlin.math.abs(candidateYear - targetYear)
                if (yearDiff == 1) {
                    score += 20
                } else {
                    score -= 80
                }
            }
        }

        if (!dto.posterPath.isNullOrBlank()) {
            score += 10
        }

        return score
    }

    private fun extractYearFromDateString(dateStr: String?): Int? {
        if (dateStr.isNullOrBlank() || dateStr.length < 4) return null
        return dateStr.take(4).toIntOrNull()
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
        metadataMemoryCache.clear()
        episodeMemoryCache.clear()
        notFoundMemoryKeys.clear()
        isCachePrewarmed = false
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
