package com.localstream.app.data.repository

import com.localstream.app.data.local.PreferencesDataSource
import com.localstream.app.data.scanner.MediaScanner
import com.localstream.app.domain.HeroSelector
import com.localstream.app.domain.VideoFilterSorter
import com.localstream.app.domain.model.FilterSortOptions
import com.localstream.app.domain.model.MovieCollection
import com.localstream.app.domain.model.PlaylistInfo
import com.localstream.app.domain.model.VideoItem
import java.util.UUID

/**
 * Repository principal coordinateur du scan, du regroupement, du filtrage et des états utilisateur.
 */
class VideoRepository(
    private val mediaScanner: MediaScanner,
    private val preferencesDataSource: PreferencesDataSource
) {
    private var rawVideos: List<VideoItem> = emptyList()
    private var groupedVideos: List<VideoItem> = emptyList()

    private val watchedMap: MutableMap<String, Boolean> =
        preferencesDataSource.getWatchedVideos().toMutableMap()

    private val progressMap: MutableMap<String, Double> =
        preferencesDataSource.getWatchProgress().toMutableMap()

    private val whitelistSet: MutableSet<String> =
        preferencesDataSource.getWhitelistedVideos().toMutableSet()

    private val playlistList: MutableList<PlaylistInfo> =
        preferencesDataSource.getPlaylists().toMutableList()

    fun scanAndLoad(
        movieCollections: Map<String, MovieCollection> = emptyMap(),
        releaseDates: Map<String, String> = emptyMap()
    ): List<VideoItem> {
        rawVideos = mediaScanner.scanVideoFiles()
        groupedVideos = mediaScanner.scanAndGroup(
            whitelistedVideos = whitelistSet,
            movieCollections = movieCollections,
            releaseDates = releaseDates
        )
        return groupedVideos
    }

    fun getGroupedVideos(): List<VideoItem> = groupedVideos
    fun getRawVideos(): List<VideoItem> = rawVideos

    fun getWatchedVideos(): Map<String, Boolean> = watchedMap.toMap()
    fun getWatchProgress(): Map<String, Double> = progressMap.toMap()
    fun getWhitelistedVideos(): Set<String> = whitelistSet.toSet()
    fun getPlaylists(): List<PlaylistInfo> = playlistList.toList()

    fun toggleWatched(videoName: String): Boolean {
        val current = watchedMap[videoName] ?: false
        val next = !current
        if (next) {
            watchedMap[videoName] = true
        } else {
            watchedMap.remove(videoName)
        }
        preferencesDataSource.saveWatchedVideos(watchedMap)
        return next
    }

    fun setWatched(videoName: String, watched: Boolean) {
        if (watched) {
            watchedMap[videoName] = true
        } else {
            watchedMap.remove(videoName)
        }
        preferencesDataSource.saveWatchedVideos(watchedMap)
    }

    fun markSeriesWatched(series: VideoItem, watched: Boolean) {
        val eps = series.episodes.orEmpty()
        eps.forEach { ep ->
            if (watched) {
                watchedMap[ep.name] = true
            } else {
                watchedMap.remove(ep.name)
            }
        }
        preferencesDataSource.saveWatchedVideos(watchedMap)
    }

    fun updateWatchProgress(videoName: String, progressPercent: Double) {
        if (progressPercent <= 0.0) {
            progressMap.remove(videoName)
        } else {
            progressMap[videoName] = progressPercent
        }
        preferencesDataSource.saveWatchProgress(progressMap)
    }

    fun clearWatchProgress(videoName: String) {
        progressMap.remove(videoName)
        preferencesDataSource.saveWatchProgress(progressMap)
    }

    fun toggleWhitelist(videoName: String): Boolean {
        val isWhitelisted = if (whitelistSet.contains(videoName)) {
            whitelistSet.remove(videoName)
            false
        } else {
            whitelistSet.add(videoName)
            true
        }
        preferencesDataSource.saveWhitelistedVideos(whitelistSet)
        return isWhitelisted
    }

    fun createPlaylist(name: String): PlaylistInfo {
        val newPlaylist = PlaylistInfo(
            id = UUID.randomUUID().toString(),
            name = name,
            videoNames = emptyList()
        )
        playlistList.add(newPlaylist)
        preferencesDataSource.savePlaylists(playlistList)
        return newPlaylist
    }

    fun deletePlaylist(playlistId: String) {
        playlistList.removeAll { it.id == playlistId }
        preferencesDataSource.savePlaylists(playlistList)
    }

    fun addToPlaylist(playlistId: String, videoName: String) {
        val idx = playlistList.indexOfFirst { it.id == playlistId }
        if (idx >= 0) {
            val current = playlistList[idx]
            if (!current.videoNames.contains(videoName)) {
                playlistList[idx] = current.copy(videoNames = current.videoNames + videoName)
                preferencesDataSource.savePlaylists(playlistList)
            }
        }
    }

    fun removeFromPlaylist(playlistId: String, videoName: String) {
        val idx = playlistList.indexOfFirst { it.id == playlistId }
        if (idx >= 0) {
            val current = playlistList[idx]
            playlistList[idx] = current.copy(videoNames = current.videoNames - videoName)
            preferencesDataSource.savePlaylists(playlistList)
        }
    }

    fun getFilteredAndSortedVideos(opts: FilterSortOptions): List<VideoItem> {
        val optsWithWatched = opts.copy(watchedVideos = watchedMap)
        return VideoFilterSorter.filterAndSortVideos(groupedVideos, optsWithWatched)
    }

    fun getHeroCandidates(): List<VideoItem> {
        return HeroSelector.getHeroCandidates(groupedVideos, watchedMap, progressMap)
    }
}
