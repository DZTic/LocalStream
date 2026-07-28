package com.localstream.app.domain

import com.localstream.app.domain.model.MovieCollection
import com.localstream.app.domain.model.VideoItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupingTest {

    private fun v(name: String, path: String = name): VideoItem = VideoItem(
        url = "blob:x",
        name = name,
        type = "video/mp4",
        path = path
    )

    @Test
    fun groupVideos_regroupsSeriesEpisodesSortedBySeasonAndEpisode() {
        val videos = listOf(
            v("Show.S01E02.mkv"),
            v("Show.S01E01.mkv"),
            v("Show.S02E01.mkv")
        )
        val res = VideoGrouper.groupVideos(videos, emptyMap(), emptyMap(), emptySet())
        assertEquals(1, res.size)
        assertTrue(res[0].isSeriesGroup)
        assertEquals(
            listOf("Show.S01E01.mkv", "Show.S01E02.mkv", "Show.S02E01.mkv"),
            res[0].episodes?.map { it.name }
        )
    }

    @Test
    fun groupVideos_excludesUnwhitelistedPersonalVideos() {
        val res = VideoGrouper.groupVideos(listOf(v("VID_20240315_143022.mp4")), emptyMap(), emptyMap(), emptySet())
        assertEquals(0, res.size)
    }

    @Test
    fun groupVideos_reintegratesWhitelistedPersonalVideo() {
        val name = "VID_20240315_143022.mp4"
        val res = VideoGrouper.groupVideos(listOf(v(name)), emptyMap(), emptyMap(), setOf(name))
        assertEquals(1, res.size)
    }

    @Test
    fun groupVideos_groupsMultipleCollectionMoviesIntoSaga() {
        val col = MovieCollection("1", "Saga X")
        val collections = mapOf("FilmA.mkv" to col, "FilmB.mkv" to col)
        val res = VideoGrouper.groupVideos(
            listOf(v("FilmA.mkv"), v("FilmB.mkv")),
            collections,
            emptyMap(),
            emptySet()
        )
        assertEquals(1, res.size)
        assertEquals("Saga X", res[0].seriesName)
        assertEquals(2, res[0].episodes?.size)
    }

    @Test
    fun groupVideos_keepsSingleCollectionMovieAsStandalone() {
        val col = MovieCollection("1", "Saga X")
        val res = VideoGrouper.groupVideos(
            listOf(v("FilmA.mkv")),
            mapOf("FilmA.mkv" to col),
            emptyMap(),
            emptySet()
        )
        assertEquals(1, res.size)
        assertFalse(res[0].isSeriesGroup)
    }
}

