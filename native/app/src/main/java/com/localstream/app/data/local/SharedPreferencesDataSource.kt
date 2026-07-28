package com.localstream.app.data.local

import android.content.Context
import android.content.SharedPreferences
import com.localstream.app.domain.model.PlaylistInfo
import org.json.JSONArray
import org.json.JSONObject

/**
 * Implémentation Android basée sur [SharedPreferences] et sérialisation JSON via org.json.
 */
class SharedPreferencesDataSource(
    context: Context,
    prefName: String = "localstream_prefs"
) : PreferencesDataSource {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(prefName, Context.MODE_PRIVATE)

    override fun getWatchedVideos(): Map<String, Boolean> {
        val jsonStr = prefs.getString(KEY_WATCHED, "") ?: ""
        if (jsonStr.isBlank()) return emptyMap()
        val map = mutableMapOf<String, Boolean>()
        val json = JSONObject(jsonStr)
        val keys = json.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            map[k] = json.optBoolean(k, false)
        }
        return map
    }

    override fun saveWatchedVideos(watched: Map<String, Boolean>) {
        val json = JSONObject()
        watched.forEach { (k, v) -> json.put(k, v) }
        prefs.edit().putString(KEY_WATCHED, json.toString()).apply()
    }

    override fun getWatchProgress(): Map<String, Double> {
        val jsonStr = prefs.getString(KEY_PROGRESS, "") ?: ""
        if (jsonStr.isBlank()) return emptyMap()
        val map = mutableMapOf<String, Double>()
        val json = JSONObject(jsonStr)
        val keys = json.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            map[k] = json.optDouble(k, 0.0)
        }
        return map
    }

    override fun saveWatchProgress(progress: Map<String, Double>) {
        val json = JSONObject()
        progress.forEach { (k, v) -> json.put(k, v) }
        prefs.edit().putString(KEY_PROGRESS, json.toString()).apply()
    }

    override fun getWhitelistedVideos(): Set<String> {
        val jsonStr = prefs.getString(KEY_WHITELIST, "") ?: ""
        if (jsonStr.isBlank()) return emptySet()
        val set = mutableSetOf<String>()
        val array = JSONArray(jsonStr)
        for (i in 0 until array.length()) {
            set.add(array.getString(i))
        }
        return set
    }

    override fun saveWhitelistedVideos(whitelist: Set<String>) {
        val array = JSONArray()
        whitelist.forEach { array.put(it) }
        prefs.edit().putString(KEY_WHITELIST, array.toString()).apply()
    }

    override fun getPlaylists(): List<PlaylistInfo> {
        val jsonStr = prefs.getString(KEY_PLAYLISTS, "") ?: ""
        if (jsonStr.isBlank()) return emptyList()
        val list = mutableListOf<PlaylistInfo>()
        val array = JSONArray(jsonStr)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val id = obj.getString("id")
            val name = obj.getString("name")
            val itemsArr = obj.optJSONArray("videoNames")
            val names = mutableListOf<String>()
            if (itemsArr != null) {
                for (j in 0 until itemsArr.length()) {
                    names.add(itemsArr.getString(j))
                }
            }
            list.add(PlaylistInfo(id = id, name = name, videoNames = names))
        }
        return list
    }

    override fun savePlaylists(playlists: List<PlaylistInfo>) {
        val array = JSONArray()
        for (p in playlists) {
            val obj = JSONObject()
            obj.put("id", p.id)
            obj.put("name", p.name)
            val itemsArr = JSONArray()
            p.videoNames.forEach { itemsArr.put(it) }
            obj.put("videoNames", itemsArr)
            array.put(obj)
        }
        prefs.edit().putString(KEY_PLAYLISTS, array.toString()).apply()
    }

    override fun getTmdbApiKey(): String = prefs.getString(KEY_TMDB_API_KEY, "") ?: ""
    override fun saveTmdbApiKey(key: String) {
        prefs.edit().putString(KEY_TMDB_API_KEY, key).apply()
    }

    override fun getOpenSubtitlesApiKey(): String = prefs.getString(KEY_OPENSUB_API_KEY, "") ?: ""
    override fun saveOpenSubtitlesApiKey(key: String) {
        prefs.edit().putString(KEY_OPENSUB_API_KEY, key).apply()
    }

    override fun getOpenSubtitlesUsername(): String = prefs.getString(KEY_OPENSUB_USER, "") ?: ""
    override fun saveOpenSubtitlesUsername(username: String) {
        prefs.edit().putString(KEY_OPENSUB_USER, username).apply()
    }

    override fun getOpenSubtitlesPassword(): String = prefs.getString(KEY_OPENSUB_PASS, "") ?: ""
    override fun saveOpenSubtitlesPassword(password: String) {
        prefs.edit().putString(KEY_OPENSUB_PASS, password).apply()
    }

    override fun getExternalPlayerPackage(): String = prefs.getString(KEY_EXT_PLAYER, "") ?: ""
    override fun saveExternalPlayerPackage(packageName: String) {
        prefs.edit().putString(KEY_EXT_PLAYER, packageName).apply()
    }

    override fun getTmdbCacheMap(key: String): Map<String, String> {
        val jsonStr = prefs.getString(KEY_TMDB_CACHE_PREFIX + key, "") ?: ""
        if (jsonStr.isBlank()) return emptyMap()
        val map = mutableMapOf<String, String>()
        val json = JSONObject(jsonStr)
        val keys = json.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            map[k] = json.getString(k)
        }
        return map
    }

    override fun saveTmdbCacheMap(key: String, map: Map<String, String>) {
        val json = JSONObject()
        map.forEach { (k, v) -> json.put(k, v) }
        prefs.edit().putString(KEY_TMDB_CACHE_PREFIX + key, json.toString()).apply()
    }

    override fun clearTmdbCache() {
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith(KEY_TMDB_CACHE_PREFIX) }.forEach { editor.remove(it) }
        editor.apply()
    }

    companion object {
        private const val KEY_WATCHED = "watched_videos"
        private const val KEY_PROGRESS = "watch_progress"
        private const val KEY_WHITELIST = "whitelisted_videos"
        private const val KEY_PLAYLISTS = "user_playlists"
        private const val KEY_TMDB_API_KEY = "tmdb_api_key"
        private const val KEY_OPENSUB_API_KEY = "opensub_api_key"
        private const val KEY_OPENSUB_USER = "opensub_username"
        private const val KEY_OPENSUB_PASS = "opensub_password"
        private const val KEY_EXT_PLAYER = "external_player_package"
        private const val KEY_TMDB_CACHE_PREFIX = "tmdb_cache_"
    }
}
