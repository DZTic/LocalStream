package com.localstream.app.data.repository

import com.localstream.app.data.db.dao.PlaybackStateDao
import com.localstream.app.data.db.dao.WatchedItemDao
import com.localstream.app.data.db.entity.PlaybackStateEntity
import com.localstream.app.data.db.entity.WatchedItemEntity
import com.localstream.app.domain.model.VideoItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val RECENTLY_WATCHED_LIMIT = 30

/**
 * Repository de l'état de visionnage : "vu/non vu" et progression de lecture.
 *
 * Toutes les écritures sont suspendues (coroutine-friendly).
 * Les lectures observables retournent des [Flow] pour l'UI Compose.
 */
@Suppress("TooManyFunctions")
class WatchStateRepository(
    private val watchedItemDao: WatchedItemDao,
    private val playbackStateDao: PlaybackStateDao,
) {

    // -------- Watched --------

    /** Flow des items marqués "vu". */
    val watchedItems: Flow<Map<String, Boolean>> =
        watchedItemDao.observeWatchedItems().map { list ->
            list.associate { it.name to it.watched }
        }

    val observeWatched: Flow<Set<String>> =
        watchedItemDao.observeWatchedItems().map { list ->
            list.filter { it.watched }.map { it.name }.toSet()
        }

    suspend fun getWatchedMap(): Map<String, Boolean> =
        watchedItemDao.getAllWatchedItems().associate { it.name to it.watched }

    suspend fun getWatched(name: String): Boolean =
        watchedItemDao.findByName(name)?.watched ?: false

    suspend fun setWatched(videoName: String, watched: Boolean, mediaStoreId: Long? = null) {
        if (watched) {
            watchedItemDao.upsert(
                WatchedItemEntity(name = videoName, watched = true, mediaStoreId = mediaStoreId)
            )
        } else {
            watchedItemDao.deleteByName(videoName)
        }
    }

    suspend fun toggleWatched(videoName: String): Boolean {
        val current = watchedItemDao.findByName(videoName)?.watched ?: false
        val next = !current
        setWatched(videoName, next)
        return next
    }

    /** Marque tous les épisodes d'une série comme vus / non vus. */
    suspend fun markSeriesWatched(series: VideoItem, watched: Boolean) {
        val episodes = series.episodes.orEmpty()
        if (watched) {
            watchedItemDao.upsertAll(
                episodes.map { ep ->
                    WatchedItemEntity(name = ep.name, watched = true, mediaStoreId = ep.mediaStoreId)
                }
            )
        } else {
            episodes.forEach { ep -> watchedItemDao.deleteByName(ep.name) }
        }
    }

    // -------- Progression --------

    /** Flow des progressions en cours (pct > 0). */
    val activePlaybackStates: Flow<Map<String, Double>> =
        playbackStateDao.observeActivePlaybackStates().map { list ->
            list.associate { it.name to it.progressPct }
        }

    val observePlaybackStates: Flow<Map<String, PlaybackStateEntity>> =
        playbackStateDao.observeActivePlaybackStates().map { list ->
            list.associateBy { it.name }
        }

    suspend fun getProgressMap(): Map<String, Double> =
        playbackStateDao.getAll().associate { it.name to it.progressPct }

    suspend fun getPositionsMap(): Map<String, Long> =
        playbackStateDao.getAll().associate { it.name to it.positionMs }

    suspend fun getPlaybackState(name: String): PlaybackStateEntity? =
        playbackStateDao.findByName(name)

    suspend fun savePlaybackState(
        videoName: String,
        positionMs: Long,
        durationMs: Long = 0L,
        progressPercent: Int = 0,
    ) {
        if (positionMs <= 0L && progressPercent <= 0) {
            playbackStateDao.deleteByName(videoName)
            return
        }
        val existing = playbackStateDao.findByName(videoName)
        val calculatedPct = if (durationMs > 0L) {
            (positionMs.toDouble() / durationMs.toDouble() * 100.0).coerceIn(0.0, 100.0)
        } else {
            progressPercent.toDouble()
        }
        playbackStateDao.upsert(
            PlaybackStateEntity(
                name = videoName,
                progressPct = calculatedPct,
                positionMs = positionMs,
                lastPlayedAt = System.currentTimeMillis(),
                mediaStoreId = existing?.mediaStoreId,
            )
        )
    }

    suspend fun updateProgress(
        videoName: String,
        progressPct: Double,
        positionMs: Long,
        mediaStoreId: Long? = null,
    ) {
        if (progressPct <= 0.0 && positionMs <= 0L) {
            playbackStateDao.deleteByName(videoName)
            return
        }
        val existing = playbackStateDao.findByName(videoName)
        playbackStateDao.upsert(
            PlaybackStateEntity(
                name = videoName,
                progressPct = progressPct,
                positionMs = positionMs,
                lastPlayedAt = System.currentTimeMillis(),
                mediaStoreId = mediaStoreId ?: existing?.mediaStoreId,
            )
        )
    }

    suspend fun clearProgress(videoName: String) {
        playbackStateDao.deleteByName(videoName)
    }

    suspend fun clearHistory() {
        watchedItemDao.deleteAll()
        playbackStateDao.deleteAll()
    }

    /** Retourne les 30 derniers fichiers lus, triés par date décroissante. */
    suspend fun getRecentlyWatched(): List<String> =
        playbackStateDao.getRecentlyPlayed(RECENTLY_WATCHED_LIMIT).map { it.name }
}
