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

    private val HD_OR_4K_REGEX = Regex("1080p|720p|2160p|4k", RegexOption.IGNORE_CASE)

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
                ResolutionFilter.SD -> !HD_OR_4K_REGEX.containsMatchIn(n)
                ResolutionFilter.ALL -> true
            }
        }
    }

    private fun sortVideos(videos: List<VideoItem>, opts: FilterSortOptions): List<VideoItem> {
        return when (opts.sortBy) {
            SortBy.ALPHA -> sortByCriterion(videos, opts, naturalOrder<String>()) {
                if (it.isSeriesGroup) it.seriesName.orEmpty() else it.name
            }
            SortBy.DATE -> sortByCriterion(videos, opts, reverseOrder<String>()) {
                val lookup = if (it.isSeriesGroup) it.seriesName.orEmpty() else it.name
                opts.releaseDates[lookup] ?: it.lastModified.toString()
            }
            SortBy.SIZE -> sortByCriterion(videos, opts, reverseOrder<Long>()) {
                if (it.isSeriesGroup) it.episodes.orEmpty().sumOf { ep -> ep.size } else it.size
            }
            SortBy.DURATION -> sortByCriterion(videos, opts, reverseOrder<Long>()) {
                if (it.isSeriesGroup) {
                    it.episodes.orEmpty().sumOf { ep -> opts.videoDurations[ep.name] ?: 0L }
                } else {
                    opts.videoDurations[it.name] ?: 0L
                }
            }
        }
    }

    private class SortEntry<T>(val video: VideoItem, val watched: Boolean, val criterion: T)

    private fun <T> sortByCriterion(
        videos: List<VideoItem>,
        opts: FilterSortOptions,
        criterionComparator: Comparator<T>,
        criterion: (VideoItem) -> T,
    ): List<VideoItem> {
        // Decorate once: comparisons must never hash VideoItem (and all its episodes).
        val entries = videos.map {
            SortEntry(it, isItemWatched(it, opts.watchedVideos), criterion(it))
        }
        val comparator = compareBy<SortEntry<T>> { it.watched }
            .thenComparator { a, b -> criterionComparator.compare(a.criterion, b.criterion) }
        // The stable sort retains input order for ties, including duplicate videos.
        return entries.sortedWith(comparator).map { it.video }
    }

    private fun isItemWatched(v: VideoItem, watchedVideos: Map<String, Boolean>): Boolean {
        return if (v.isSeriesGroup) {
            v.episodes.orEmpty().all { watchedVideos[it.name] == true }
        } else {
            watchedVideos[v.name] == true
        }
    }
}
