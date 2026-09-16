package com.localstream.app.domain

import com.localstream.app.domain.model.FilterSortOptions
import com.localstream.app.domain.model.ResolutionFilter
import com.localstream.app.domain.model.SortBy
import com.localstream.app.domain.model.VideoItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class SortingTest {

    private fun v(name: String): VideoItem = VideoItem(
        url = "blob:x",
        name = name,
        type = "video/mp4",
        path = name
    )

    private val baseOpts = FilterSortOptions(
        sortBy = SortBy.ALPHA,
        filterGenre = null,
        filterResolution = ResolutionFilter.ALL,
        releaseDates = emptyMap(),
        videoGenres = emptyMap(),
        videoDurations = emptyMap(),
        watchedVideos = emptyMap()
    )

    @Test
    fun filterAndSortVideos_sortsAlphabetically() {
        val res = VideoFilterSorter.filterAndSortVideos(listOf(v("Zebra.mkv"), v("Alpha.mkv")), baseOpts)
        assertEquals(listOf("Alpha.mkv", "Zebra.mkv"), res.map { it.name })
    }

    @Test
    fun filterAndSortVideos_relegatesWatchedVideosToEnd() {
        val opts = baseOpts.copy(watchedVideos = mapOf("Alpha.mkv" to true))
        val res = VideoFilterSorter.filterAndSortVideos(listOf(v("Alpha.mkv"), v("Beta.mkv")), opts)
        assertEquals(listOf("Beta.mkv", "Alpha.mkv"), res.map { it.name })
    }

    @Test
    fun filterAndSortVideos_filtersByResolution() {
        val opts = baseOpts.copy(filterResolution = ResolutionFilter.ONE_THOUSAND_EIGHTY_P)
        val res = VideoFilterSorter.filterAndSortVideos(listOf(v("Film.1080p.mkv"), v("Film.720p.mkv")), opts)
        assertEquals(1, res.size)
        assertEquals("Film.1080p.mkv", res[0].name)
    }

    @Test
    fun filterAndSortVideos_filtersByPrecalculatedResolution() {
        val opts = baseOpts.copy(filterResolution = ResolutionFilter.FOUR_K)
        val item4k = v("CustomMovie.mkv").copy(resolution = "4K")
        val item1080 = v("CustomMovie2.mkv").copy(resolution = "1080p")
        val res = VideoFilterSorter.filterAndSortVideos(listOf(item4k, item1080), opts)
        assertEquals(1, res.size)
        assertEquals("CustomMovie.mkv", res[0].name)
    }

    @Test
    fun filterAndSortVideos_sortsByDateDescending() {
        val opts = baseOpts.copy(
            sortBy = SortBy.DATE,
            releaseDates = mapOf("Old.mkv" to "2010-01-01", "New.mkv" to "2024-05-01"),
        )
        val res = VideoFilterSorter.filterAndSortVideos(listOf(v("Old.mkv"), v("New.mkv")), opts)
        assertEquals(listOf("New.mkv", "Old.mkv"), res.map { it.name })
    }

    @Test
    fun filterAndSortVideos_sortsBySizeDescending() {
        val opts = baseOpts.copy(sortBy = SortBy.SIZE)
        val small = v("Small.mkv").copy(size = 100L)
        val large = v("Large.mkv").copy(size = 5000L)
        val res = VideoFilterSorter.filterAndSortVideos(listOf(small, large), opts)
        assertEquals(listOf("Large.mkv", "Small.mkv"), res.map { it.name })
    }

    @Test
    fun filterAndSortVideos_sortsByDurationDescending() {
        val opts = baseOpts.copy(
            sortBy = SortBy.DURATION,
            videoDurations = mapOf("Short.mkv" to 60L, "Long.mkv" to 3600L),
        )
        val res = VideoFilterSorter.filterAndSortVideos(listOf(v("Short.mkv"), v("Long.mkv")), opts)
        assertEquals(listOf("Long.mkv", "Short.mkv"), res.map { it.name })
    }

    @Test
    fun filterAndSortVideos_preservesMixedLibraryOrderForEveryCriterion() {
        val episodes = listOf(v("episode1").copy(size = 300L), v("episode2").copy(size = 400L))
        val series = v("group").copy(
            isSeriesGroup = true, seriesName = "Beta", episodes = episodes, size = 1L,
        )
        val watchedSeries = series.copy(name = "watchedGroup", seriesName = "Aardvark", episodes = episodes.take(1))
        val movie = v("Alpha").copy(size = 500L, lastModified = 9L)
        val fallbackMovie = v("Zulu").copy(size = 50L, lastModified = 10L)
        val watchedMovie = v("A").copy(size = 1000L)
        val library = listOf(watchedMovie, fallbackMovie, series, movie, watchedSeries)
        val opts = baseOpts.copy(
            releaseDates = mapOf("Beta" to "2024-01-01", "Aardvark" to "2020-01-01", "A" to "2025-01-01"),
            videoDurations = mapOf("episode1" to 20L, "episode2" to 40L, "Alpha" to 100L, "A" to 1L),
            watchedVideos = mapOf("episode1" to true, "episode2" to false, "A" to true, "group" to true),
        )
        val expected = mapOf(
            SortBy.ALPHA to listOf("Alpha", "group", "Zulu", "A", "watchedGroup"),
            // Preserve the existing lexicographic comparison, including timestamp fallbacks.
            SortBy.DATE to listOf("Alpha", "group", "Zulu", "A", "watchedGroup"),
            SortBy.SIZE to listOf("group", "Alpha", "Zulu", "A", "watchedGroup"),
            SortBy.DURATION to listOf("Alpha", "group", "Zulu", "watchedGroup", "A"),
        )
        expected.forEach { (criterion, names) ->
            val sorted = VideoFilterSorter.filterAndSortVideos(library, opts.copy(sortBy = criterion))
            assertEquals(criterion.name, names, sorted.map { it.name })
        }
    }

    @Test
    fun filterAndSortVideos_preservesTiesAndDuplicateInstances() {
        val movie = v("Same").copy(size = 10L, lastModified = 100L)
        val duplicate = movie.copy()
        val series = v("group").copy(
            isSeriesGroup = true, seriesName = "Same", episodes = listOf(movie), lastModified = 100L,
        )
        val library = listOf(series, movie, duplicate, movie)
        SortBy.entries.forEach { criterion ->
            val sorted = VideoFilterSorter.filterAndSortVideos(library, baseOpts.copy(sortBy = criterion))
            assertEquals(library.size, sorted.size)
            library.indices.forEach { index -> assertSame(library[index], sorted[index]) }
        }
    }

    @Test
    fun filterAndSortVideos_preservesEmptyAndUnnamedGroups() {
        val emptyGroup = v("empty").copy(isSeriesGroup = true, episodes = emptyList())
        val nullGroup = v("null").copy(isSeriesGroup = true)
        val movie = v("Z")
        SortBy.entries.forEach { criterion ->
            val sorted = VideoFilterSorter.filterAndSortVideos(
                listOf(emptyGroup, movie, nullGroup), baseOpts.copy(sortBy = criterion),
            )
            assertEquals(listOf(movie, emptyGroup, nullGroup), sorted)
            assertEquals(emptyList<VideoItem>(), VideoFilterSorter.filterAndSortVideos(emptyList(), baseOpts.copy(sortBy = criterion)))
            assertSame(movie, VideoFilterSorter.filterAndSortVideos(listOf(movie), baseOpts.copy(sortBy = criterion)).single())
        }
    }

    @Test
    fun filterAndSortVideos_neverHashesEpisodes() {
        val episodes = object : AbstractList<VideoItem>() {
            override val size: Int = 120
            override fun get(index: Int): VideoItem = v("episode$index").copy(size = 10L)
            override fun hashCode(): Int = error("Sorting must not hash episode lists")
        }
        val series = v("series").copy(isSeriesGroup = true, seriesName = "Series", episodes = episodes)
        val library = listOf(series, v("Alpha"), v("Zulu"))
        SortBy.entries.forEach { criterion ->
            val sorted = VideoFilterSorter.filterAndSortVideos(library, baseOpts.copy(sortBy = criterion))
            assertEquals(library.size, sorted.size)
            assertSame(series, sorted.single { it.isSeriesGroup })
        }
    }
}
