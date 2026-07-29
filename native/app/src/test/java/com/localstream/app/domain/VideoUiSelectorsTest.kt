package com.localstream.app.domain

import com.localstream.app.domain.model.VideoItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de [VideoUiSelectors] : parité avec la logique d'affichage de
 * `VideoRow.tsx` / `VideoCard.tsx` (vu, progression, clés de métadonnées).
 */
class VideoUiSelectorsTest {

    private val ep1 = VideoItem(url = "u1", name = "Show S01E01.mkv")
    private val ep2 = VideoItem(url = "u2", name = "Show S01E02.mkv")
    private val ep3 = VideoItem(url = "u3", name = "Show S01E03.mkv")
    private val show = VideoItem(
        url = "u1",
        name = "Show",
        isSeriesGroup = true,
        isTvSeries = true,
        episodes = listOf(ep1, ep2, ep3),
        seriesName = "Show",
    )
    private val film = VideoItem(url = "u4", name = "Film.2024.1080p.mkv")

    @Test
    fun `vu - groupe vu seulement si tous les épisodes sont vus`() {
        assertFalse(VideoUiSelectors.isWatched(show, mapOf("Show S01E01.mkv" to true, "Show S01E02.mkv" to true)))
        assertTrue(
            VideoUiSelectors.isWatched(
                show,
                mapOf("Show S01E01.mkv" to true, "Show S01E02.mkv" to true, "Show S01E03.mkv" to true),
            ),
        )
        assertTrue(VideoUiSelectors.isWatched(film, mapOf("Film.2024.1080p.mkv" to true)))
        assertFalse(VideoUiSelectors.isWatched(film, emptyMap()))
    }

    @Test
    fun `progression - groupe = premier épisode en cours, sinon celle du nom`() {
        val progress = mapOf(
            "Show S01E01.mkv" to 100.0,
            "Show S01E02.mkv" to 42.0,
            "Show S01E03.mkv" to 0.0,
        )
        assertEquals(42.0, VideoUiSelectors.progressOf(show, progress), 0.001)
        assertEquals(0.0, VideoUiSelectors.progressOf(show, emptyMap()), 0.001)
        assertEquals(12.5, VideoUiSelectors.progressOf(film, mapOf("Film.2024.1080p.mkv" to 12.5)), 0.001)
    }

    @Test
    fun `clé métadonnées = nom de série pour un groupe, nom du fichier sinon`() {
        assertEquals("Show", VideoUiSelectors.metadataKey(show))
        assertEquals("Film.2024.1080p.mkv", VideoUiSelectors.metadataKey(film))
    }

    @Test
    fun `titre affiché = nom de série, sinon titre nettoyé du fichier`() {
        assertEquals("Show", VideoUiSelectors.displayTitle(show))
        assertEquals("Film", VideoUiSelectors.displayTitle(film))
    }

    @Test
    fun `recherche - insensible à la casse, vide si requête blanche`() {
        val videos = listOf(show, film)
        assertEquals(listOf(show), VideoUiSelectors.filterByQuery(videos, "show"))
        assertEquals(listOf(show), VideoUiSelectors.filterByQuery(videos, "SHOW"))
        assertEquals(listOf(film), VideoUiSelectors.filterByQuery(videos, "film.2024"))
        assertTrue(VideoUiSelectors.filterByQuery(videos, "   ").isEmpty())
        assertTrue(VideoUiSelectors.filterByQuery(videos, "inconnu").isEmpty())
    }
}
