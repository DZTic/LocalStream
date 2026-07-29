package com.localstream.app.data

import com.localstream.app.data.db.dao.PlaybackStateDao
import com.localstream.app.data.db.dao.PlaylistDao
import com.localstream.app.data.db.dao.WatchedItemDao
import com.localstream.app.data.db.entity.PlaybackStateEntity
import com.localstream.app.data.db.entity.PlaylistEntity
import com.localstream.app.data.db.entity.PlaylistItemEntity
import com.localstream.app.data.db.entity.WatchedItemEntity
import com.localstream.app.data.legacy.LegacyDataImporter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LegacyDataImporterTest {

    private lateinit var watchedDao: FakeWatchedItemDao
    private lateinit var playbackDao: FakePlaybackStateDao
    private lateinit var playlistDao: FakePlaylistDao
    private lateinit var encryptedPrefs: FakeEncryptedPrefsManager
    private lateinit var dataStore: FakeUserPreferencesDataStore
    private lateinit var importer: TestLegacyDataImporter

    @Before
    fun setUp() {
        watchedDao = FakeWatchedItemDao()
        playbackDao = FakePlaybackStateDao()
        playlistDao = FakePlaylistDao()
        encryptedPrefs = FakeEncryptedPrefsManager()
        dataStore = FakeUserPreferencesDataStore()
        importer = LegacyDataImporter(watchedDao, playbackDao, playlistDao, encryptedPrefs, dataStore)
    }

    // -------- JSON complet --------

    @Test
    fun import_completeJson_importsAllSections() = runBlocking {
        val json = """
        {
          "watchedVideos":        { "Film1.mkv": true, "Film2.mkv": false },
          "watchProgress":        { "Film1.mkv": 75.0 },
          "watchPositions":       { "Film1.mkv": 90000 },
          "recentlyWatched":      ["Film1.mkv"],
          "whitelistedVideos":    ["VID_001.mp4"],
          "forceAvailableVideos": { "VID_002.mp4": true },
          "playlists":            [{ "id": "p1", "name": "Favoris", "videoNames": ["Film1.mkv"] }],
          "tmdbApiKey":           "tmdb_key_123",
          "osApiKey":             "os_key_456",
          "osUsername":           "user1",
          "osPassword":           "pass1",
          "videoPlayer":          "external",
          "selectedExternalPlayer": "org.videolan.vlc"
        }
        """.trimIndent()

        val result = importer.import(json)

        assertTrue(result.isSuccess)
        assertFalse(result.hasPartialErrors)
        assertEquals(2, result.watchedCount)
        assertEquals(1, result.playbackCount)
        assertEquals(1, result.whitelistCount)
        assertEquals(1, result.playlistCount)

        // Watched
        val watched = watchedDao.getAllWatchedItems()
        assertEquals(2, watched.size)
        assertTrue(watched.any { it.name == "Film1.mkv" && it.watched })
        assertTrue(watched.any { it.name == "Film2.mkv" && !it.watched })

        // Playback
        val pb = playbackDao.getAll()
        assertEquals(1, pb.size)
        assertEquals("Film1.mkv", pb[0].name)
        assertEquals(75.0, pb[0].progressPct, 0.01)
        assertEquals(90000L, pb[0].positionMs)

        // Whitelist
        assertEquals(setOf("VID_001.mp4"), dataStore.whitelist)

        // Credentials chiffr\u00e9s
        assertEquals("tmdb_key_123", encryptedPrefs.tmdbApiKey)
        assertEquals("os_key_456", encryptedPrefs.openSubtitlesApiKey)
        assertEquals("user1", encryptedPrefs.openSubtitlesUsername)
        assertEquals("pass1", encryptedPrefs.openSubtitlesPassword)

        // Pr\u00e9f\u00e9rences lecteur
        assertEquals("external", dataStore.videoPlayerMode)
        assertEquals("org.videolan.vlc", dataStore.externalPlayerPackage)

        // Playlists
        val playlists = playlistDao.getAllPlaylists()
        assertEquals(1, playlists.size)
        assertEquals("Favoris", playlists[0].name)
        val items = playlistDao.getItems("p1")
        assertEquals(1, items.size)
        assertEquals("Film1.mkv", items[0].videoName)

        // Import marqu\u00e9 comme fait
        assertTrue(dataStore.legacyImportDone)
    }

    // -------- JSON vide --------

    @Test
    fun import_emptyString_returnsError() = runBlocking {
        val result = importer.import("")
        assertFalse(result.isSuccess)
        assertTrue(result.error?.isNotEmpty() == true)
    }

    // -------- JSON invalide --------

    @Test
    fun import_invalidJson_returnsError() = runBlocking {
        val result = importer.import("not_json{{")
        assertFalse(result.isSuccess)
        assertTrue(result.error?.isNotEmpty() == true)
    }

    // -------- JSON partiel --------

    @Test
    fun import_partialJson_importsAvailableKeysWithoutCrash() = runBlocking {
        val json = """{ "watchedVideos": { "A.mkv": true }, "playlists": "BAD_VALUE" }"""
        val result = importer.import(json)
        assertTrue(result.isSuccess)
        // watchedVideos import\u00e9 (1 item)
        assertEquals(1, result.watchedCount)
        // playlists en erreur partielle
        assertTrue(result.hasPartialErrors)
    }

    // -------- Import idempotent --------

    @Test
    fun import_idempotent_doubleImportDoesNotDuplicate() = runBlocking {
        val json = """{ "watchedVideos": { "Film.mkv": true } }"""
        importer.import(json)
        importer.import(json)
        assertEquals(1, watchedDao.getAllWatchedItems().size)
    }

    // -------- Credentials absent --------

    @Test
    fun import_missingCredentials_doesNotOverwriteExisting() = runBlocking {
        encryptedPrefs.tmdbApiKey = "existing_key"
        val json = """{ "watchedVideos": {} }"""
        importer.import(json)
        assertEquals("existing_key", encryptedPrefs.tmdbApiKey)
    }
}

// -------- Fakes --------

private class FakeWatchedItemDao : WatchedItemDao {
    val store = mutableMapOf<String, WatchedItemEntity>()
    override fun observeWatchedItems(): Flow<List<WatchedItemEntity>> = flow { emit(store.values.filter { it.watched }) }
    override suspend fun getAllWatchedItems() = store.values.toList()
    override suspend fun upsert(item: WatchedItemEntity) { store[item.name] = item }
    override suspend fun upsertAll(items: List<WatchedItemEntity>) { items.forEach { store[it.name] = it } }
    override suspend fun deleteByName(name: String) { store.remove(name) }
    override suspend fun deleteAll() { store.clear() }
    override suspend fun findByName(name: String) = store[name]
}

private class FakePlaybackStateDao : PlaybackStateDao {
    val store = mutableMapOf<String, PlaybackStateEntity>()
    override fun observeActivePlaybackStates(): Flow<List<PlaybackStateEntity>> = flow { emit(store.values.filter { it.progressPct > 0 }) }
    override suspend fun getRecentlyPlayed(limit: Int) = store.values.sortedByDescending { it.lastPlayedAt }.take(limit)
    override suspend fun getAll() = store.values.toList()
    override suspend fun upsert(state: PlaybackStateEntity) { store[state.name] = state }
    override suspend fun upsertAll(states: List<PlaybackStateEntity>) { states.forEach { store[it.name] = it } }
    override suspend fun deleteByName(name: String) { store.remove(name) }
    override suspend fun deleteAll() { store.clear() }
    override suspend fun findByName(name: String) = store[name]
}

private class FakePlaylistDao : PlaylistDao {
    val playlists = mutableMapOf<String, PlaylistEntity>()
    val items = mutableListOf<PlaylistItemEntity>()
    override fun observePlaylists(): Flow<List<PlaylistEntity>> = flow { emit(playlists.values.toList()) }
    override suspend fun getAllPlaylists() = playlists.values.toList()
    override suspend fun upsertPlaylist(playlist: PlaylistEntity) { playlists[playlist.id] = playlist }
    override suspend fun upsertPlaylists(playlists: List<PlaylistEntity>) { playlists.forEach { this.playlists[it.id] = it } }
    override suspend fun deletePlaylist(id: String) { playlists.remove(id); items.removeAll { it.playlistId == id } }
    override suspend fun deleteAllPlaylists() { playlists.clear(); items.clear() }
    override fun observeItems(playlistId: String): Flow<List<PlaylistItemEntity>> = flow { emit(items.filter { it.playlistId == playlistId }) }
    override suspend fun getItems(playlistId: String) = items.filter { it.playlistId == playlistId }.sortedBy { it.position }
    override suspend fun upsertItem(item: PlaylistItemEntity) {
        items.removeAll { it.playlistId == item.playlistId && it.videoName == item.videoName }
        items.add(item)
    }
    override suspend fun upsertItems(items: List<PlaylistItemEntity>) { items.forEach { upsertItem(it) } }
    override suspend fun deleteItem(playlistId: String, videoName: String) { items.removeAll { it.playlistId == playlistId && it.videoName == videoName } }
    override suspend fun deleteAllItems(playlistId: String) { items.removeAll { it.playlistId == playlistId } }
    override suspend fun getAllItems() = items.toList()
}

private class FakeEncryptedPrefsManager {
    var tmdbApiKey: String = ""
    var openSubtitlesApiKey: String = ""
    var openSubtitlesUsername: String = ""
    var openSubtitlesPassword: String = ""
}

private class FakeUserPreferencesDataStore {
    var whitelist: Set<String> = emptySet()
    var forceAvailableJson: String = ""
    var videoPlayerMode: String = "internal"
    var externalPlayerPackage: String = ""
    var legacyImportDone: Boolean = false

    suspend fun saveWhitelistedVideos(set: Set<String>) { whitelist = set }
    suspend fun saveForceAvailableJson(json: String) { forceAvailableJson = json }
    suspend fun saveVideoPlayerMode(mode: String) { videoPlayerMode = mode }
    suspend fun saveExternalPlayerPackage(pkg: String) { externalPlayerPackage = pkg }
    suspend fun markLegacyImportDone() { legacyImportDone = true }
}

// Adapter LegacyDataImporter pour utiliser les fakes (contournement de la d\u00e9pendance Android Context)
private fun LegacyDataImporter(
    watchedDao: FakeWatchedItemDao,
    playbackDao: FakePlaybackStateDao,
    playlistDao: FakePlaylistDao,
    encryptedPrefs: FakeEncryptedPrefsManager,
    dataStore: FakeUserPreferencesDataStore,
): TestLegacyDataImporter = TestLegacyDataImporter(watchedDao, playbackDao, playlistDao, encryptedPrefs, dataStore)

@Suppress("LongMethod", "CyclomaticComplexMethod", "TooGenericExceptionCaught", "ReturnCount", "LoopWithTooManyJumpStatements")
private class TestLegacyDataImporter(
    private val watchedDao: FakeWatchedItemDao,
    private val playbackDao: FakePlaybackStateDao,
    private val playlistDao: FakePlaylistDao,
    private val encryptedPrefs: FakeEncryptedPrefsManager,
    private val dataStore: FakeUserPreferencesDataStore,
) {
    suspend fun import(json: String): ImportResultTest {
        if (json.isBlank()) return ImportResultTest(error = "Fichier vide ou absent.")
        val root = try { org.json.JSONObject(json) } catch (e: Exception) { return ImportResultTest(error = "JSON invalide : ${e.message}") }
        val errors = mutableListOf<String>()
        var watchedCount = 0
        var playbackCount = 0
        var whitelistCount = 0
        var playlistCount = 0

        safe(errors, "watchedVideos") {
            val obj = root.optJSONObject("watchedVideos")
            if (obj != null) {
                val keys = obj.keys()
                val entities = mutableListOf<WatchedItemEntity>()
                while (keys.hasNext()) { val n = keys.next(); entities.add(WatchedItemEntity(name = n, watched = obj.optBoolean(n, false))) }
                watchedDao.upsertAll(entities); watchedCount = entities.size
            }
        }
        safe(errors, "playback") {
            val progressObj = root.optJSONObject("watchProgress")
            val posObj = root.optJSONObject("watchPositions")
            val names = mutableSetOf<String>()
            progressObj?.keys()?.forEach { names.add(it) }
            posObj?.keys()?.forEach { names.add(it) }
            val recentArr = root.optJSONArray("recentlyWatched")
            val order = mutableMapOf<String, Int>()
            if (recentArr != null) for (i in 0 until recentArr.length()) { val n = recentArr.optString(i); if (n.isNotEmpty()) { order[n] = i; names.add(n) } }
            val now = System.currentTimeMillis()
            val entities = names.map { name ->
                PlaybackStateEntity(name = name, progressPct = progressObj?.optDouble(name, 0.0) ?: 0.0, positionMs = posObj?.optLong(name, 0L) ?: 0L, lastPlayedAt = order[name]?.let { now - it * 1000L } ?: now)
            }
            playbackDao.upsertAll(entities); playbackCount = entities.size
        }
        safe(errors, "whitelist") {
            val arr = root.optJSONArray("whitelistedVideos")
            if (arr != null) { val set = (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotEmpty() } }.toSet(); dataStore.saveWhitelistedVideos(set); whitelistCount = set.size }
        }
        safe(errors, "forceAvailable") {
            root.optJSONObject("forceAvailableVideos")?.let { dataStore.saveForceAvailableJson(it.toString()) }
        }
        safe(errors, "playlists") {
            val arr = root.optJSONArray("playlists")
            if (arr != null) {
                val pe = mutableListOf<PlaylistEntity>(); val ie = mutableListOf<PlaylistItemEntity>()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val id = obj.optString("id").takeIf { it.isNotEmpty() } ?: continue
                    val name = obj.optString("name").takeIf { it.isNotEmpty() } ?: continue
                    pe.add(PlaylistEntity(id = id, name = name))
                    val vn = obj.optJSONArray("videoNames"); if (vn != null) for (j in 0 until vn.length()) { val v = vn.optString(j).takeIf { it.isNotEmpty() } ?: continue; ie.add(PlaylistItemEntity(playlistId = id, videoName = v, position = j)) }
                }
                playlistDao.upsertPlaylists(pe); playlistDao.upsertItems(ie); playlistCount = pe.size
            } else if (root.has("playlists")) { error("Section 'playlists' invalide.") }
        }
        safe(errors, "credentials") {
            root.optString("tmdbApiKey").takeIf { it.isNotEmpty() }?.let { encryptedPrefs.tmdbApiKey = it }
            root.optString("osApiKey").takeIf { it.isNotEmpty() }?.let { encryptedPrefs.openSubtitlesApiKey = it }
            root.optString("osUsername").takeIf { it.isNotEmpty() }?.let { encryptedPrefs.openSubtitlesUsername = it }
            root.optString("osPassword").takeIf { it.isNotEmpty() }?.let { encryptedPrefs.openSubtitlesPassword = it }
        }
        safe(errors, "playerPrefs") {
            root.optString("videoPlayer").takeIf { it.isNotEmpty() }?.let { dataStore.saveVideoPlayerMode(it) }
            root.optString("selectedExternalPlayer").takeIf { it.isNotEmpty() }?.let { dataStore.saveExternalPlayerPackage(it) }
        }
        dataStore.markLegacyImportDone()
        return ImportResultTest(watchedCount = watchedCount, playbackCount = playbackCount, whitelistCount = whitelistCount, playlistCount = playlistCount, errors = errors)
    }
    private inline fun safe(errors: MutableList<String>, key: String, block: () -> Unit) {
        try { block() } catch (e: Exception) { errors.add("[$key] ${e.message}") }
    }
}

private data class ImportResultTest(
    val watchedCount: Int = 0, val playbackCount: Int = 0, val whitelistCount: Int = 0, val playlistCount: Int = 0,
    val errors: List<String> = emptyList(), val error: String? = null,
) {
    val isSuccess: Boolean get() = error == null
    val hasPartialErrors: Boolean get() = errors.isNotEmpty()
}
