package com.localstream.app.ui.components

import com.localstream.app.domain.model.TmdbMetadata
import com.localstream.app.domain.model.VideoItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HeroSectionTest {

    private fun video(name: String): VideoItem = VideoItem(
        url = "blob:$name",
        name = name,
        type = "video/mp4",
        path = "/storage/emulated/0/Movies/$name",
    )

    private fun metadata(backdropPath: String? = null, posterPath: String? = null): TmdbMetadata = TmdbMetadata(
        queryKey = "test",
        title = "Test",
        backdropPath = backdropPath,
        posterPath = posterPath,
    )

    @Test
    fun resolveCurrentBackdropUrl_prefersBackdropOverPoster() {
        val hero = video("Movie.mkv")
        val meta = mapOf("Movie.mkv" to metadata(backdropPath = "/backdrop.jpg", posterPath = "/poster.jpg"))

        val url = resolveCurrentBackdropUrl(hero, meta)
        assertEquals("https://image.tmdb.org/t/p/w1280/backdrop.jpg", url)
    }

    @Test
    fun resolveCurrentBackdropUrl_fallsBackToPosterWhenNoBackdrop() {
        val hero = video("Movie.mkv")
        val meta = mapOf("Movie.mkv" to metadata(backdropPath = null, posterPath = "/poster.jpg"))

        val url = resolveCurrentBackdropUrl(hero, meta)
        assertEquals("https://image.tmdb.org/t/p/w342/poster.jpg", url)
    }

    @Test
    fun resolveCurrentBackdropUrl_returnsNullWhenNoMetadata() {
        val hero = video("Unknown.mkv")
        val url = resolveCurrentBackdropUrl(hero, emptyMap())
        assertNull(url)
    }

    @Test
    fun resolveNextBackdropUrl_returnsNullWhenSingleOrEmptyCandidates() {
        assertNull(resolveNextBackdropUrl(emptyList(), emptyMap(), 0))
        assertNull(resolveNextBackdropUrl(listOf(video("OnlyOne.mkv")), emptyMap(), 0))
    }

    @Test
    fun resolveNextBackdropUrl_resolvesNextCandidateAndWrapsAround() {
        val v1 = video("Film1.mkv")
        val v2 = video("Film2.mkv")
        val v3 = video("Film3.mkv")
        val candidates = listOf(v1, v2, v3)

        val meta = mapOf(
            "Film1.mkv" to metadata(backdropPath = "/b1.jpg"),
            "Film2.mkv" to metadata(backdropPath = "/b2.jpg"),
            "Film3.mkv" to metadata(backdropPath = "/b3.jpg"),
        )

        // À l'index 0, le prochain est l'index 1 (Film2)
        assertEquals("https://image.tmdb.org/t/p/w1280/b2.jpg", resolveNextBackdropUrl(candidates, meta, 0))

        // À l'index 1, le prochain est l'index 2 (Film3)
        assertEquals("https://image.tmdb.org/t/p/w1280/b3.jpg", resolveNextBackdropUrl(candidates, meta, 1))

        // À l'index 2, le prochain boucle vers l'index 0 (Film1)
        assertEquals("https://image.tmdb.org/t/p/w1280/b1.jpg", resolveNextBackdropUrl(candidates, meta, 2))
    }
}
