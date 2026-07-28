package com.localstream.app.domain

import com.localstream.app.domain.model.FilterSortOptions
import com.localstream.app.domain.model.ResolutionFilter
import com.localstream.app.domain.model.SortBy
import com.localstream.app.domain.model.VideoItem
import org.junit.Assert.assertEquals
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
}

