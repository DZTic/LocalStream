package com.localstream.app.data

import com.localstream.app.data.db.dao.PlaybackStateDao
import com.localstream.app.data.db.entity.PlaybackStateEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PlaybackStateDaoTest {

    private lateinit var dao: PlaybackStateDao

    @Before
    fun setUp() {
        dao = InMemoryPlaybackStateDao()
    }

    @Test
    fun upsert_insertsAndFindsItem() = runBlocking {
        dao.upsert(PlaybackStateEntity(name = "Film.mkv", progressPct = 42.0, positionMs = 5000L))
        val found = dao.findByName("Film.mkv")
        assertEquals(42.0, found?.progressPct ?: 0.0, 0.01)
        assertEquals(5000L, found?.positionMs)
    }

    @Test
    fun upsert_replacesExistingItem() = runBlocking {
        dao.upsert(PlaybackStateEntity(name = "Film.mkv", progressPct = 42.0))
        dao.upsert(PlaybackStateEntity(name = "Film.mkv", progressPct = 80.0))
        assertEquals(80.0, dao.findByName("Film.mkv")?.progressPct ?: 0.0, 0.01)
    }

    @Test
    fun deleteByName_removesItem() = runBlocking {
        dao.upsert(PlaybackStateEntity(name = "Film.mkv", progressPct = 50.0))
        dao.deleteByName("Film.mkv")
        assertNull(dao.findByName("Film.mkv"))
    }

    @Test
    fun getRecentlyPlayed_returnsTopNByLastPlayedAt() = runBlocking {
        val now = System.currentTimeMillis()
        dao.upsertAll(listOf(
            PlaybackStateEntity(name = "Old.mkv", progressPct = 10.0, lastPlayedAt = now - 10_000L),
            PlaybackStateEntity(name = "New.mkv", progressPct = 20.0, lastPlayedAt = now),
            PlaybackStateEntity(name = "Mid.mkv", progressPct = 5.0, lastPlayedAt = now - 5_000L),
        ))
        val recent = dao.getRecentlyPlayed(2)
        assertEquals(2, recent.size)
        assertEquals("New.mkv", recent[0].name)
        assertEquals("Mid.mkv", recent[1].name)
    }

    @Test
    fun deleteAll_clearsTable() = runBlocking {
        dao.upsertAll(listOf(
            PlaybackStateEntity(name = "A.mkv"),
            PlaybackStateEntity(name = "B.mkv"),
        ))
        dao.deleteAll()
        assertTrue(dao.getAll().isEmpty())
    }

    @Test
    fun findByName_returnsNullWhenAbsent() = runBlocking {
        assertNull(dao.findByName("absent.mkv"))
    }
}

private class InMemoryPlaybackStateDao : PlaybackStateDao {
    private val store = mutableMapOf<String, PlaybackStateEntity>()

    override fun observeActivePlaybackStates(): Flow<List<PlaybackStateEntity>> = flow {
        emit(store.values.filter { it.progressPct > 0 })
    }

    override suspend fun getRecentlyPlayed(limit: Int): List<PlaybackStateEntity> =
        store.values.sortedByDescending { it.lastPlayedAt }.take(limit)

    override suspend fun getAll(): List<PlaybackStateEntity> = store.values.toList()

    override suspend fun upsert(state: PlaybackStateEntity) { store[state.name] = state }

    override suspend fun upsertAll(states: List<PlaybackStateEntity>) {
        states.forEach { store[it.name] = it }
    }

    override suspend fun deleteByName(name: String) { store.remove(name) }

    override suspend fun deleteAll() { store.clear() }

    override suspend fun findByName(name: String): PlaybackStateEntity? = store[name]
}
