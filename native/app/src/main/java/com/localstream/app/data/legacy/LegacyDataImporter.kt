package com.localstream.app.data.legacy

import com.localstream.app.data.db.dao.PlaybackStateDao
import com.localstream.app.data.db.dao.PlaylistDao
import com.localstream.app.data.db.dao.WatchedItemDao
import com.localstream.app.data.db.entity.PlaybackStateEntity
import com.localstream.app.data.db.entity.PlaylistEntity
import com.localstream.app.data.db.entity.PlaylistItemEntity
import com.localstream.app.data.db.entity.WatchedItemEntity
import com.localstream.app.data.local.EncryptedPreferencesManager
import com.localstream.app.data.local.UserPreferencesDataStore
import org.json.JSONArray
import org.json.JSONObject

/**
 * Importeur des donn\u00e9es localStorage provenant de l'app Capacitor existante.
 *
 * ## Format du fichier source
 * `localstream-backup.json` \u00e9crit par l'app Capacitor dans `Directory.Data` :
 * ```json
 * {
 *   "watchedVideos":        { "Film.mkv": true, ... },
 *   "watchProgress":        { "Film.mkv": 45.0, ... },
 *   "watchPositions":       { "Film.mkv": 120000, ... },
 *   "recentlyWatched":      ["Film.mkv", ...],
 *   "whitelistedVideos":    ["VID_001.mp4", ...],
 *   "forceAvailableVideos": { "VID_001.mp4": true, ... },
 *   "playlists":            [{ "id": "...", "name": "Favoris", "videoNames": [...] }, ...],
 *   "tmdbApiKey":           "...",
 *   "osApiKey":             "...",
 *   "osUsername":           "...",
 *   "osPassword":           "...",
 *   "videoPlayer":          "internal",
 *   "selectedExternalPlayer": "org.videolan.vlc"
 * }
 * ```
 *
 * ## Caract\u00e9ristiques
 * - **Transactionnel** : les \u00e9critures Room sont regroup\u00e9es par batch.
 * - **Idempotent** : un double import ne duplique pas les donn\u00e9es (REPLACE).
 * - **Tol\u00e9rant aux pannes** : un JSON corrompu ou partiel ne provoque pas de crash ;
 *   seules les cl\u00e9s valides sont import\u00e9es ([ImportResult] d\u00e9taille les \u00e9cueils).
 */
class LegacyDataImporter(
    private val watchedItemDao: WatchedItemDao,
    private val playbackStateDao: PlaybackStateDao,
    private val playlistDao: PlaylistDao,
    private val encryptedPrefs: EncryptedPreferencesManager,
    private val dataStore: UserPreferencesDataStore,
) {

    /**
     * Lance l'import depuis un JSON brut (contenu de `localstream-backup.json`).
     *
     * @param json Contenu brut du fichier de sauvegarde.
     * @return [ImportResult] d\u00e9taillant ce qui a \u00e9t\u00e9 import\u00e9 et les erreurs rencontr\u00e9es.
     */
    suspend fun import(json: String): ImportResult {
        if (json.isBlank()) return ImportResult(error = "Fichier vide ou absent.")

        val root: JSONObject = try {
            JSONObject(json)
        } catch (e: Exception) {
            return ImportResult(error = "JSON invalide : ${e.message}")
        }

        val errors = mutableListOf<String>()

        // -------- watchedVideos → watched_items --------
        var watchedCount = 0
        safeBlock(errors, "watchedVideos") {
            val obj = root.optJSONObject("watchedVideos")
            if (obj != null) {
                val entities = mutableListOf<WatchedItemEntity>()
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val name = keys.next()
                    val watched = obj.optBoolean(name, false)
                    entities.add(WatchedItemEntity(name = name, watched = watched))
                }
                watchedItemDao.upsertAll(entities)
                watchedCount = entities.size
            }
        }

        // -------- watchProgress + watchPositions + recentlyWatched → playback_state --------
        var playbackCount = 0
        safeBlock(errors, "watchProgress/watchPositions") {
            val progressObj = root.optJSONObject("watchProgress")
            val positionsObj = root.optJSONObject("watchPositions")

            val allNames = mutableSetOf<String>()
            progressObj?.keys()?.forEach { allNames.add(it) }
            positionsObj?.keys()?.forEach { allNames.add(it) }

            // R\u00e9cup\u00e9rer les timestamps depuis recentlyWatched pour ordonner l'historique
            val recentArr = root.optJSONArray("recentlyWatched")
            val recentOrder = mutableMapOf<String, Int>()
            if (recentArr != null) {
                for (i in 0 until recentArr.length()) {
                    val n = recentArr.optString(i)
                    if (n.isNotEmpty()) recentOrder[n] = i
                }
                allNames.addAll(recentOrder.keys)
            }

            val now = System.currentTimeMillis()
            val entities = allNames.map { name ->
                val pct = progressObj?.optDouble(name, 0.0) ?: 0.0
                val posMs = positionsObj?.optLong(name, 0L) ?: 0L
                // Simuler lastPlayedAt : plus r\u00e9cent = indice plus petit dans recentlyWatched
                val order = recentOrder[name] ?: Int.MAX_VALUE
                val lastPlayedAt = if (order != Int.MAX_VALUE) now - order * 1_000L else now
                PlaybackStateEntity(
                    name = name,
                    progressPct = pct,
                    positionMs = posMs,
                    lastPlayedAt = lastPlayedAt,
                )
            }
            playbackStateDao.upsertAll(entities)
            playbackCount = entities.size
        }

        // -------- whitelistedVideos → DataStore --------
        var whitelistCount = 0
        safeBlock(errors, "whitelistedVideos") {
            val arr = root.optJSONArray("whitelistedVideos")
            if (arr != null) {
                val set = (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotEmpty() } }.toSet()
                dataStore.saveWhitelistedVideos(set)
                whitelistCount = set.size
            }
        }

        // -------- forceAvailableVideos → DataStore (JSON string) --------
        safeBlock(errors, "forceAvailableVideos") {
            val obj = root.optJSONObject("forceAvailableVideos")
            if (obj != null) {
                dataStore.saveForceAvailableJson(obj.toString())
            }
        }

        // -------- playlists → playlist + playlist_item --------
        var playlistCount = 0
        safeBlock(errors, "playlists") {
            val arr = root.optJSONArray("playlists")
            if (arr != null) {
                val playlistEntities = mutableListOf<PlaylistEntity>()
                val itemEntities = mutableListOf<PlaylistItemEntity>()

                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val id = obj.optString("id").takeIf { it.isNotEmpty() } ?: continue
                    val name = obj.optString("name").takeIf { it.isNotEmpty() } ?: continue
                    playlistEntities.add(PlaylistEntity(id = id, name = name))

                    val videoNamesArr = obj.optJSONArray("videoNames")
                    if (videoNamesArr != null) {
                        for (j in 0 until videoNamesArr.length()) {
                            val vName = videoNamesArr.optString(j).takeIf { it.isNotEmpty() } ?: continue
                            itemEntities.add(PlaylistItemEntity(playlistId = id, videoName = vName, position = j))
                        }
                    }
                }
                playlistDao.upsertPlaylists(playlistEntities)
                playlistDao.upsertItems(itemEntities)
                playlistCount = playlistEntities.size
            }
        }

        // -------- Credentials → EncryptedSharedPreferences --------
        safeBlock(errors, "credentials") {
            root.optString("tmdbApiKey").takeIf { it.isNotEmpty() }
                ?.let { encryptedPrefs.tmdbApiKey = it }
            root.optString("osApiKey").takeIf { it.isNotEmpty() }
                ?.let { encryptedPrefs.openSubtitlesApiKey = it }
            root.optString("osUsername").takeIf { it.isNotEmpty() }
                ?.let { encryptedPrefs.openSubtitlesUsername = it }
            root.optString("osPassword").takeIf { it.isNotEmpty() }
                ?.let { encryptedPrefs.openSubtitlesPassword = it }
        }

        // -------- Pr\u00e9f\u00e9rences lecteur → DataStore --------
        safeBlock(errors, "playerPrefs") {
            root.optString("videoPlayer").takeIf { it.isNotEmpty() }
                ?.let { dataStore.saveVideoPlayerMode(it) }
            root.optString("selectedExternalPlayer").takeIf { it.isNotEmpty() }
                ?.let { dataStore.saveExternalPlayerPackage(it) }
        }

        // Marquer l'import comme effectu\u00e9 (idempotence)
        dataStore.markLegacyImportDone()

        return ImportResult(
            watchedCount = watchedCount,
            playbackCount = playbackCount,
            whitelistCount = whitelistCount,
            playlistCount = playlistCount,
            errors = errors,
        )
    }

    private inline fun safeBlock(errors: MutableList<String>, key: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            errors.add("[$key] ${e.message ?: e.javaClass.simpleName}")
        }
    }
}

/**
 * R\u00e9sultat d\u00e9taill\u00e9 de l'import. [error] est non-null uniquement si le fichier est illisible.
 */
data class ImportResult(
    val watchedCount: Int = 0,
    val playbackCount: Int = 0,
    val whitelistCount: Int = 0,
    val playlistCount: Int = 0,
    val errors: List<String> = emptyList(),
    val error: String? = null,
) {
    val isSuccess: Boolean get() = error == null
    val hasPartialErrors: Boolean get() = errors.isNotEmpty()
}
