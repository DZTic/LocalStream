package com.localstream.app.domain

import com.localstream.app.domain.model.VideoItem

object HeroSelector {
    fun isFullyWatched(
        v: VideoItem,
        watchedVideos: Map<String, Boolean>
    ): Boolean {
        return if (v.isSeriesGroup) {
            !v.episodes.isNullOrEmpty() && v.episodes.all { watchedVideos[it.name] == true }
        } else {
            watchedVideos[v.name] == true
        }
    }

    private fun isInProgress(v: VideoItem, watchProgress: Map<String, Double>): Boolean {
        val p = watchProgress[v.name] ?: 0.0
        return p > 0.0 && p < 95.0
    }

    fun getHeroCandidates(
        groupedVideos: List<VideoItem>,
        watchedVideos: Map<String, Boolean>,
        watchProgress: Map<String, Double> = emptyMap()
    ): List<VideoItem> {
        return groupedVideos
            .filter { !isFullyWatched(it, watchedVideos) }
            .sortedWith { a, b ->
                val aProg = isInProgress(a, watchProgress)
                val bProg = isInProgress(b, watchProgress)
                if (aProg != bProg) {
                    if (aProg) -1 else 1
                } else {
                    b.lastModified.compareTo(a.lastModified)
                }
            }
    }
}

