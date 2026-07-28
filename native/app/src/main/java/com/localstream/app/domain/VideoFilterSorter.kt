package com.localstream.app.domain

import com.localstream.app.domain.model.FilterSortOptions
import com.localstream.app.domain.model.ResolutionFilter
import com.localstream.app.domain.model.SortBy
import com.localstream.app.domain.model.VideoItem

object VideoFilterSorter {
    fun filterAndSortVideos(
        videos: List<VideoItem>,
        opts: FilterSortOptions
    ): List<VideoItem> {
        val filteredByGenre = filterByGenre(videos, opts)
        val filteredByRes = filterByResolution(filteredByGenre, opts)
        return sortVideos(filteredByRes, opts)
    }

    private fun filterByGenre(videos: List<VideoItem>, opts: FilterSortOptions): List<VideoItem> {
        val g = opts.filterGenre ?: return videos
        return videos.filter { v ->
            val lookupKey = if (v.isSeriesGroup) v.seriesName ?: "" else v.name
            opts.videoGenres[lookupKey]?.contains(g) == true
        }
    }

    private fun filterByResolution(videos: List<VideoItem>, opts: FilterSortOptions): List<VideoItem> {
        if (opts.filterResolution == ResolutionFilter.ALL) return videos
        return videos.filter { v ->
            val firstName = if (v.isSeriesGroup) v.episodes?.firstOrNull()?.name ?: v.name else v.name
            val n = firstName.lowercase()
            when (opts.filterResolution) {
                ResolutionFilter.FOUR_K -> n.contains("2160p") || n.contains("4k")
                ResolutionFilter.TWO_K -> n.contains("1440p")
                ResolutionFilter.ONE_THOUSAND_EIGHTY_P -> n.contains("1080p")
                ResolutionFilter.SEVEN_HUNDRED_TWENTY_P -> n.contains("720p")
                ResolutionFilter.SD -> !Regex("1080p|720p|2160p|4k", RegexOption.IGNORE_CASE).containsMatchIn(n)
                ResolutionFilter.ALL -> true
            }
        }
    }

    private fun sortVideos(videos: List<VideoItem>, opts: FilterSortOptions): List<VideoItem> {
        return videos.sortedWith { a, b ->
            val aWatched = isItemWatched(a, opts.watchedVideos)
            val bWatched = isItemWatched(b, opts.watchedVideos)
            if (aWatched != bWatched) {
                if (aWatched) 1 else -1
            } else {
                compareItemsByCriteria(a, b, opts)
            }
        }
    }

    private fun isItemWatched(v: VideoItem, watchedVideos: Map<String, Boolean>): Boolean {
        return if (v.isSeriesGroup) {
            v.episodes.orEmpty().all { watchedVideos[it.name] == true }
        } else {
            watchedVideos[v.name] == true
        }
    }

    private fun compareItemsByCriteria(a: VideoItem, b: VideoItem, opts: FilterSortOptions): Int {
        return when (opts.sortBy) {
            SortBy.ALPHA -> compareAlpha(a, b)
            SortBy.DATE -> compareDate(a, b, opts.releaseDates)
            SortBy.SIZE -> compareSize(a, b)
            SortBy.DURATION -> compareDuration(a, b, opts.videoDurations)
        }
    }

    private fun compareAlpha(a: VideoItem, b: VideoItem): Int {
        val nameA = if (a.isSeriesGroup) a.seriesName ?: "" else a.name
        val nameB = if (b.isSeriesGroup) b.seriesName ?: "" else b.name
        return nameA.compareTo(nameB)
    }

    private fun compareDate(a: VideoItem, b: VideoItem, releaseDates: Map<String, String>): Int {
        val lookupA = if (a.isSeriesGroup) a.seriesName ?: "" else a.name
        val lookupB = if (b.isSeriesGroup) b.seriesName ?: "" else b.name
        val dateA = releaseDates[lookupA] ?: a.lastModified.toString()
        val dateB = releaseDates[lookupB] ?: b.lastModified.toString()
        return dateB.compareTo(dateA)
    }

    private fun compareSize(a: VideoItem, b: VideoItem): Int {
        val sizeA = if (a.isSeriesGroup) a.episodes.orEmpty().sumOf { it.size } else a.size
        val sizeB = if (b.isSeriesGroup) b.episodes.orEmpty().sumOf { it.size } else b.size
        return sizeB.compareTo(sizeA)
    }

    private fun compareDuration(a: VideoItem, b: VideoItem, videoDurations: Map<String, Long>): Int {
        val durA = if (a.isSeriesGroup) a.episodes.orEmpty().sumOf { videoDurations[it.name] ?: 0L } else videoDurations[a.name] ?: 0L
        val durB = if (b.isSeriesGroup) b.episodes.orEmpty().sumOf { videoDurations[it.name] ?: 0L } else videoDurations[b.name] ?: 0L
        return durB.compareTo(durA)
    }
}

