package com.localstream.app.data

import com.localstream.app.data.db.dao.PlaybackStateDao
import com.localstream.app.data.db.dao.WatchedItemDao
import com.localstream.app.data.db.entity.PlaybackStateEntity
import com.localstream.app.data.db.entity.WatchedItemEntity
import com.localstream.app.data.repository.WatchStateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class WatchStateRepositoryTest {

    @Test
    fun calculateProgressPct_computesCorrectClampedPercentage() {
        assertEquals(50.0, WatchStateRepository.calculateProgressPct(500L, 1000L), 0.001)
        assertEquals(100.0, WatchStateRepository.calculateProgressPct(1200L, 1000L), 0.001)
        assertEquals(0.0, WatchStateRepository.calculateProgressPct(-10L, 1000L), 0.001)
        assertEquals(42.0, WatchStateRepository.calculateProgressPct(0L, 0L, fallbackPct = 42.0), 0.001)
    }

    @Test
    fun savePlaybackState_performsDirectUpsertWithoutFindByName() = runBlocking {
        var findByNameCalls = 0
        var upsertCalls = 0
        var savedEntity: PlaybackStateEntity? = null

        val playbackDao = object : PlaybackStateDao {
            override fun observeActivePlaybackStates(): Flow<List<PlaybackStateEntity>> = emptyFlow()
            override suspend fun getRecentlyPlayed(limit: Int): List<PlaybackStateEntity> = emptyList()
            override suspend fun getAll(): List<PlaybackStateEntity> = emptyList()
            override suspend fun upsert(state: PlaybackStateEntity) {
                upsertCalls++
                savedEntity = state
            }
            override suspend fun upsertAll(states: List<PlaybackStateEntity>) {
                /* no-op */
            }
            override suspend fun deleteByName(name: String) {
                /* no-op */
            }
            override suspend fun deleteAll() {
                /* no-op */
            }
            override suspend fun findByName(name: String): PlaybackStateEntity? {
                findByNameCalls++
                return null
            }
        }

        val watchedDao = object : WatchedItemDao {
            override fun observeWatchedItems(): Flow<List<WatchedItemEntity>> = emptyFlow()
            override suspend fun getAllWatchedItems(): List<WatchedItemEntity> = emptyList()
            override suspend fun upsert(item: WatchedItemEntity) {
                /* no-op */
            }
            override suspend fun upsertAll(items: List<WatchedItemEntity>) {
                /* no-op */
            }
            override suspend fun deleteByName(name: String) {
                /* no-op */
            }
            override suspend fun deleteByNames(names: List<String>) {
                /* no-op */
            }
            override suspend fun deleteAll() {
                /* no-op */
            }
            override suspend fun findByName(name: String): WatchedItemEntity? = null
        }

        val repository = WatchStateRepository(watchedDao, playbackDao)
        repository.savePlaybackState(
            videoName = "Video.mp4",
            positionMs = 3000L,
            durationMs = 6000L,
            mediaStoreId = 123L,
        )

        assertEquals("Aucun read avant ecriture", 0, findByNameCalls)
        assertEquals(1, upsertCalls)
        assertNotNull(savedEntity)
        assertEquals("Video.mp4", savedEntity?.name)
        assertEquals(50.0, savedEntity?.progressPct ?: 0.0, 0.001)
        assertEquals(3000L, savedEntity?.positionMs)
        assertEquals(123L, savedEntity?.mediaStoreId)
    }

    @Test
    fun updateProgress_performsDirectUpsertWithoutFindByName() = runBlocking {
        var findByNameCalls = 0
        var upsertCalls = 0

        val playbackDao = object : PlaybackStateDao {
            override fun observeActivePlaybackStates(): Flow<List<PlaybackStateEntity>> = emptyFlow()
            override suspend fun getRecentlyPlayed(limit: Int): List<PlaybackStateEntity> = emptyList()
            override suspend fun getAll(): List<PlaybackStateEntity> = emptyList()
            override suspend fun upsert(state: PlaybackStateEntity) {
                upsertCalls++
            }
            override suspend fun upsertAll(states: List<PlaybackStateEntity>) {
                /* no-op */
            }
            override suspend fun deleteByName(name: String) {
                /* no-op */
            }
            override suspend fun deleteAll() {
                /* no-op */
            }
            override suspend fun findByName(name: String): PlaybackStateEntity? {
                findByNameCalls++
                return null
            }
        }

        val watchedDao = object : WatchedItemDao {
            override fun observeWatchedItems(): Flow<List<WatchedItemEntity>> = emptyFlow()
            override suspend fun getAllWatchedItems(): List<WatchedItemEntity> = emptyList()
            override suspend fun upsert(item: WatchedItemEntity) {
                /* no-op */
            }
            override suspend fun upsertAll(items: List<WatchedItemEntity>) {
                /* no-op */
            }
            override suspend fun deleteByName(name: String) {
                /* no-op */
            }
            override suspend fun deleteByNames(names: List<String>) {
                /* no-op */
            }
            override suspend fun deleteAll() {
                /* no-op */
            }
            override suspend fun findByName(name: String): WatchedItemEntity? = null
        }

        val repository = WatchStateRepository(watchedDao, playbackDao)
        repository.updateProgress(
            videoName = "Video.mp4",
            progressPct = 75.0,
            positionMs = 4500L,
        )

        assertEquals("Aucun read avant ecriture lors de updateProgress", 0, findByNameCalls)
        assertEquals(1, upsertCalls)
    }
}
