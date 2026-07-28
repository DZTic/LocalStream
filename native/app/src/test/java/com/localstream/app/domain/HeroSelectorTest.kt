package com.localstream.app.domain

import com.localstream.app.domain.model.VideoItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeroSelectorTest {

    private fun v(name: String, lastModified: Long = 0L, extraEpisodes: List<VideoItem>? = null): VideoItem = VideoItem(
        url = "blob:x",
        name = name,
        type = "video/mp4",
        path = name,
        lastModified = lastModified,
        isSeriesGroup = extraEpisodes != null,
        episodes = extraEpisodes
    )

    @Test
    fun isFullyWatched_tracksMovieAndSeriesState() {
        assertTrue(HeroSelector.isFullyWatched(v("Film.mkv"), mapOf("Film.mkv" to true)))
        assertFalse(HeroSelector.isFullyWatched(v("Film.mkv"), emptyMap()))

        val series = v("S", extraEpisodes = listOf(v("S.E1.mkv"), v("S.E2.mkv")))
        assertFalse(HeroSelector.isFullyWatched(series, mapOf("S.E1.mkv" to true)))
        assertTrue(HeroSelector.isFullyWatched(series, mapOf("S.E1.mkv" to true, "S.E2.mkv" to true)))
    }

    @Test
    fun getHeroCandidates_excludesFullyWatched() {
        val res = HeroSelector.getHeroCandidates(
            listOf(v("Vu.mkv"), v("NonVu.mkv")),
            mapOf("Vu.mkv" to true)
        )
        assertEquals(listOf("NonVu.mkv"), res.map { it.name })
    }

    @Test
    fun getHeroCandidates_returnsAllUnwatchedCandidatesByRecency() {
        val res = HeroSelector.getHeroCandidates(
            listOf(v("A.mkv", 1L), v("B.mkv", 2L), v("C.mkv", 3L)),
            emptyMap()
        )
        assertEquals(3, res.size)
        assertEquals("C.mkv", res[0].name)
    }

    @Test
    fun getHeroCandidates_prioritizesInProgressBeforeUnstarted() {
        val res = HeroSelector.getHeroCandidates(
            listOf(v("Recent.mkv", 10L), v("EnCours.mkv", 1L)),
            emptyMap(),
            mapOf("EnCours.mkv" to 40.0)
        )
        assertEquals("EnCours.mkv", res[0].name)
    }

    @Test
    fun getHeroCandidates_ignoresFinishedProgress() {
        val res = HeroSelector.getHeroCandidates(
            listOf(v("Presque.mkv", 1L), v("Recent.mkv", 10L)),
            emptyMap(),
            mapOf("Presque.mkv" to 98.0)
        )
        assertEquals("Recent.mkv", res[0].name)
    }

    @Test
    fun getHeroCandidates_returnsEmptyIfAllWatched() {
        val res = HeroSelector.getHeroCandidates(listOf(v("A.mkv")), mapOf("A.mkv" to true))
        assertEquals(0, res.size)
    }
}

