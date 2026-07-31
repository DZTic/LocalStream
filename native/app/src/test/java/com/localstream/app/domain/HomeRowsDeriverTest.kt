package com.localstream.app.domain

import com.localstream.app.domain.model.VideoItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de [HomeRowsDeriver] : ordre et contenu des rows de l'accueil.
 */
class HomeRowsDeriverTest {

    private fun movie(
        name: String,
        path: String = "/storage/emulated/0/Movies/$name",
        lastModified: Long = 0L,
    ) = VideoItem(
        url = "content://media/$name",
        name = name,
        path = path,
        lastModified = lastModified,
    )

    private fun series(name: String, episodes: List<VideoItem>) = VideoItem(
        url = episodes.first().url,
        name = name,
        path = episodes.first().path,
        isSeriesGroup = true,
        isTvSeries = true,
        episodes = episodes,
        seriesName = name,
    )

    // grouped : ordre du scan MediaStore (plus récent d'abord)
    private val ep1 = VideoItem(url = "u1", name = "Show S01E01.mkv", path = "/storage/emulated/0/Series/Show/Show S01E01.mkv")
    private val ep2 = VideoItem(url = "u2", name = "Show S01E02.mkv", path = "/storage/emulated/0/Series/Show/Show S01E02.mkv")
    private val show = series("Show", listOf(ep1, ep2))
    private val filmA = movie("Alpha.2020.1080p.mkv", lastModified = 300)
    private val filmB = movie("Beta.2021.720p.mkv", path = "/storage/emulated/0/Download/Beta.2021.720p.mkv", lastModified = 200)
    private val filmC = movie("Charlie.mp4", path = "/storage/emulated/0/DCIM/Camera/Charlie.mp4", lastModified = 100)

    private val grouped = listOf(filmA, show, filmB, filmC) // récent → ancien

    @Test
    fun `nouveautés = ordre scan inversé, limité à 15`() {
        val rows = HomeRowsDeriver.derive(grouped, grouped, emptyMap(), emptyMap())
        assertEquals(listOf(filmC, filmB, show, filmA), rows.recentAdditions)
    }

    @Test
    fun `recommandations = 15 premiers de l'ordre du scan`() {
        val rows = HomeRowsDeriver.derive(grouped, grouped, emptyMap(), emptyMap())
        assertEquals(grouped, rows.recommendations)
    }

    @Test
    fun `séries et films sont partitionnés comme le web`() {
        val rows = HomeRowsDeriver.derive(grouped, grouped, emptyMap(), emptyMap())
        assertEquals(listOf(show), rows.series)
        assertEquals(listOf(filmA, filmB, filmC), rows.movies)
    }

    @Test
    fun `les contenus déjà vus sont masqués de l'accueil`() {
        val watched = mapOf(filmA.name to true)
        val rows = HomeRowsDeriver.derive(grouped, grouped, watched, emptyMap())
        assertEquals(listOf(filmB, filmC), rows.movies)
    }

    @Test
    fun `continuer la lecture = progression entre 0 et 95 exclus, sur la liste filtrée`() {
        val progress = mapOf(
            filmA.name to 50.0,
            filmB.name to 95.0,   // exclu : >= 95
            filmC.name to 0.0,    // exclu : 0
            ep1.name to 10.0,
        )
        val rows = HomeRowsDeriver.derive(grouped, grouped, emptyMap(), progress)
        assertEquals(listOf(filmA, show), rows.continueWatching)
    }

    @Test
    fun `de A à Z = tri insensible à la casse sur la liste filtrée`() {
        val unsorted = listOf(filmC, filmA, filmB)
        val rows = HomeRowsDeriver.derive(grouped, unsorted, emptyMap(), emptyMap())
        assertEquals(listOf(filmA, filmB, filmC), rows.alphabetical)
    }

    @Test
    fun `hero = candidats HeroSelector, repli sur le premier filtré sinon`() {
        // Tout vu → HeroSelector vide → repli sur le premier de la liste filtrée.
        val allWatched = mapOf(
            filmA.name to true,
            filmB.name to true,
            filmC.name to true,
            ep1.name to true,
            ep2.name to true,
        )
        val rows = HomeRowsDeriver.derive(grouped, grouped, allWatched, emptyMap())
        assertEquals(listOf(filmA), rows.heroCandidates)
    }

    @Test
    fun `hero privilégie les contenus en cours de lecture`() {
        val progress = mapOf(filmC.name to 40.0)
        val rows = HomeRowsDeriver.derive(grouped, grouped, emptyMap(), progress)
        assertEquals(filmC, rows.heroCandidates.first())
    }
}
