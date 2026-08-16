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
        val watchedCache = videos.associateWith { isItemWatched(it, opts.watchedVideos) }

        return when (opts.sortBy) {
            SortBy.ALPHA -> {
                val alphaCache = videos.associateWith { if (it.isSeriesGroup) it.seriesName.orEmpty() else it.name }
                videos.sortedWith { a, b ->
                    val aWatched = watchedCache[a] ?: false
                    val bWatched = watchedCache[b] ?: false
                    if (aWatched != bWatched) {
                        if (aWatched) 1 else -1
                    } else {
                        (alphaCache[a].orEmpty()).compareTo(alphaCache[b].orEmpty())
                    }
                }
            }
            SortBy.DATE -> {
                val dateCache = videos.associateWith {
                    val lookup = if (it.isSeriesGroup) it.seriesName.orEmpty() else it.name
                    opts.releaseDates[lookup] ?: it.lastModified.toString()
                }
                videos.sortedWith { a, b ->
                    val aWatched = watchedCache[a] ?: false
                    val bWatched = watchedCache[b] ?: false
                    if (aWatched != bWatched) {
                        if (aWatched) 1 else -1
                    } else {
                        (dateCache[b].orEmpty()).compareTo(dateCache[a].orEmpty())
                    }
                }
            }
            SortBy.SIZE -> {
                val sizeCache = videos.associateWith {
                    if (it.isSeriesGroup) it.episodes.orEmpty().sumOf { ep -> ep.size } else it.size
                }
                videos.sortedWith { a, b ->
                    val aWatched = watchedCache[a] ?: false
                    val bWatched = watchedCache[b] ?: false
                    if (aWatched != bWatched) {
                        if (aWatched) 1 else -1
                    } else {
                        (sizeCache[b] ?: 0L).compareTo(sizeCache[a] ?: 0L)
                    }
                }
            }
            SortBy.DURATION -> {
                val durationCache = videos.associateWith {
                    if (it.isSeriesGroup) {
                        it.episodes.orEmpty().sumOf { ep -> opts.videoDurations[ep.name] ?: 0L }
                    } else {
                        opts.videoDurations[it.name] ?: 0L
                    }
                }
                videos.sortedWith { a, b ->
                    val aWatched = watchedCache[a] ?: false
                    val bWatched = watchedCache[b] ?: false
                    if (aWatched != bWatched) {
                        if (aWatched) 1 else -1
                    } else {
                        (durationCache[b] ?: 0L).compareTo(durationCache[a] ?: 0L)
                    }
                }
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
}

