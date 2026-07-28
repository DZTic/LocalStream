package com.localstream.app.domain

import com.localstream.app.domain.model.FilterSortOptions
import com.localstream.app.domain.model.SortBy
import com.localstream.app.domain.model.VideoItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EdgeCasesTest {

    private fun v(name: String, size: Long = 0L, lastModified: Long = 0L, episodes: List<VideoItem>? = null): VideoItem = VideoItem(
        url = "blob:x",
        name = name,
        type = "video/mp4",
        path = name,
        size = size,
        lastModified = lastModified,
        isSeriesGroup = episodes != null,
        seriesName = if (episodes != null) name else null,
        episodes = episodes
    )

    @Test
    fun groupVideos_sortsMixedMultiSeasonSeries() {
        val videos = listOf(
            v("Dark.S02E01.mkv"),
            v("Dark.S01E02.mkv"),
            v("Dark.S01E01.mkv"),
            v("Dark.S03E01.mkv"),
            v("Dark.S02E02.mkv")
        )
        val res = VideoGrouper.groupVideos(videos, emptyMap(), emptyMap(), emptySet())
        assertEquals(1, res.size)
        assertEquals(
            listOf("1x1", "1x2", "2x1", "2x2", "3x1"),
            res[0].episodes?.map { "${it.season}x${it.episode}" }
        )
    }

    @Test
    fun groupVideos_parsesSeasonAndEpisodeFromNxMFormat() {
        val res = VideoGrouper.groupVideos(listOf(v("Friends 1x05.mkv"), v("Friends 1x02.mkv")), emptyMap(), emptyMap(), emptySet())
        assertEquals(listOf(2, 5), res[0].episodes?.map { it.episode })
        assertTrue(res[0].episodes.orEmpty().all { it.season == 1 })
    }

    @Test
    fun groupVideos_keepsDistinctSeriesSeparate() {
        val res = VideoGrouper.groupVideos(listOf(v("Show.A.S01E01.mkv"), v("Show.B.S01E01.mkv")), emptyMap(), emptyMap(), emptySet())
        assertEquals(2, res.size)
    }

    private val baseOpts = FilterSortOptions(
        sortBy = SortBy.ALPHA,
        filterGenre = null,
        releaseDates = emptyMap(),
        videoGenres = emptyMap(),
        videoDurations = emptyMap(),
        watchedVideos = emptyMap()
    )

    @Test
    fun filterAndSortVideos_sortsBySizeMissingSizesLast() {
        val res = VideoFilterSorter.filterAndSortVideos(
            listOf(v("Sans.mkv"), v("Gros.mkv", size = 5000L), v("Petit.mkv", size = 100L)),
            baseOpts.copy(sortBy = SortBy.SIZE)
        )
        assertEquals(listOf("Gros.mkv", "Petit.mkv", "Sans.mkv"), res.map { it.name })
    }

    @Test
    fun filterAndSortVideos_sortsByDurationMissingDurationsLast() {
        val res = VideoFilterSorter.filterAndSortVideos(
            listOf(v("Sans.mkv"), v("Long.mkv"), v("Court.mkv")),
            baseOpts.copy(sortBy = SortBy.DURATION, videoDurations = mapOf("Long.mkv" to 7200L, "Court.mkv" to 1200L))
        )
        assertEquals(listOf("Long.mkv", "Court.mkv", "Sans.mkv"), res.map { it.name })
    }

    @Test
    fun filterAndSortVideos_sortsGroupSizeBySumOfEpisodes() {
        val serie = v(
            "S",
            episodes = listOf(v("S.S01E01.mkv", size = 3000L), v("S.S01E02.mkv", size = 3000L))
        )
        val res = VideoFilterSorter.filterAndSortVideos(
            listOf(v("Film.mkv", size = 5000L), serie),
            baseOpts.copy(sortBy = SortBy.SIZE)
        )
        assertEquals("S", res[0].name)
    }

    @Test
    fun filterAndSortVideos_filtersByGenreMap() {
        val res = VideoFilterSorter.filterAndSortVideos(
            listOf(v("Action.mkv"), v("Drame.mkv")),
            baseOpts.copy(filterGenre = 28, videoGenres = mapOf("Action.mkv" to listOf(28), "Drame.mkv" to listOf(18)))
        )
        assertEquals(listOf("Action.mkv"), res.map { it.name })
    }

    @Test
    fun filterAndSortVideos_relegatesWatchedContentToEnd() {
        val res = VideoFilterSorter.filterAndSortVideos(
            listOf(v("Avu.mkv"), v("Bnonvu.mkv")),
            baseOpts.copy(watchedVideos = mapOf("Avu.mkv" to true))
        )
        assertEquals(listOf("Bnonvu.mkv", "Avu.mkv"), res.map { it.name })
    }
}

