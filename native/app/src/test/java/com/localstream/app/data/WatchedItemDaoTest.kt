package com.localstream.app.data

import com.localstream.app.data.db.dao.WatchedItemDao
import com.localstream.app.data.db.entity.WatchedItemEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Test unitaire du DAO [WatchedItemDao] via une impl\u00e9mentation en m\u00e9moire ([InMemoryWatchedItemDao]).
 * Les tests instrumentaux Room (base SQLite r\u00e9elle) se trouvent dans `androidTest/`.
 */
class WatchedItemDaoTest {

    private lateinit var dao: WatchedItemDao

    @Before
    fun setUp() {
        dao = InMemoryWatchedItemDao()
    }

    @Test
    fun upsert_insertsAndFindsItem() = runBlocking {
        dao.upsert(WatchedItemEntity(name = "Film.mkv", watched = true))
        val found = dao.findByName("Film.mkv")
        assertEquals(true, found?.watched)
    }

    @Test
    fun upsert_replacesExistingItem() = runBlocking {
        dao.upsert(WatchedItemEntity(name = "Film.mkv", watched = true))
        dao.upsert(WatchedItemEntity(name = "Film.mkv", watched = false))
        val found = dao.findByName("Film.mkv")
        assertEquals(false, found?.watched)
    }

    @Test
    fun deleteByName_removesItem() = runBlocking {
        dao.upsert(WatchedItemEntity(name = "Film.mkv", watched = true))
        dao.deleteByName("Film.mkv")
        assertNull(dao.findByName("Film.mkv"))
    }

    @Test
    fun upsertAll_insertsMultipleItems() = runBlocking {
        val items = listOf(
            WatchedItemEntity(name = "A.mkv", watched = true),
            WatchedItemEntity(name = "B.mkv", watched = false),
        )
        dao.upsertAll(items)
        assertEquals(2, dao.getAllWatchedItems().size)
    }

    @Test
    fun deleteAll_clearsTable() = runBlocking {
        dao.upsertAll(listOf(
            WatchedItemEntity(name = "A.mkv"),
            WatchedItemEntity(name = "B.mkv"),
        ))
        dao.deleteAll()
        assertTrue(dao.getAllWatchedItems().isEmpty())
    }

    @Test
    fun findByName_returnsNullWhenAbsent() = runBlocking {
        assertNull(dao.findByName("absent.mkv"))
    }

    // -------- Helper --------

    private fun runBlocking(block: suspend () -> Unit) {
        kotlinx.coroutines.runBlocking { block() }
    }
}

// -------- Impl\u00e9mentation en m\u00e9moire --------

private class InMemoryWatchedItemDao : WatchedItemDao {
    private val store = mutableMapOf<String, WatchedItemEntity>()

    override fun observeWatchedItems() = kotlinx.coroutines.flow.flow {
        emit(store.values.filter { it.watched })
    }

    override suspend fun getAllWatchedItems() = store.values.toList()

    override suspend fun upsert(item: WatchedItemEntity) { store[item.name] = item }

    override suspend fun upsertAll(items: List<WatchedItemEntity>) {
        items.forEach { store[it.name] = it }
    }

    override suspend fun deleteByName(name: String) { store.remove(name) }

    override suspend fun deleteAll() { store.clear() }

    override suspend fun findByName(name: String) = store[name]
}
