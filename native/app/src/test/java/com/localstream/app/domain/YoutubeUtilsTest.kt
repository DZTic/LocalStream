package com.localstream.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YoutubeUtilsTest {

    @Test
    fun `extractVideoId correctly identifies standard youtube urls and ids`() {
        assertEquals("dQw4w9WgXcQ", YoutubeUtils.extractVideoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", YoutubeUtils.extractVideoId("https://youtu.be/dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", YoutubeUtils.extractVideoId("https://www.youtube.com/embed/dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", YoutubeUtils.extractVideoId("https://www.youtube.com/shorts/dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", YoutubeUtils.extractVideoId("dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", YoutubeUtils.extractVideoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ&feature=shared"))
    }

    @Test
    fun `extractVideoId returns null for non-youtube inputs`() {
        assertNull(YoutubeUtils.extractVideoId("https://example.com/video.mp4"))
        assertNull(YoutubeUtils.extractVideoId("Inception.2010.mkv"))
        assertNull(YoutubeUtils.extractVideoId(""))
        assertNull(YoutubeUtils.extractVideoId(null))
    }

    @Test
    fun `isYoutubeUrlOrId verifies valid youtube sources`() {
        assertTrue(YoutubeUtils.isYoutubeUrlOrId("https://youtu.be/dQw4w9WgXcQ"))
        assertTrue(YoutubeUtils.isYoutubeUrlOrId("dQw4w9WgXcQ"))
        assertFalse(YoutubeUtils.isYoutubeUrlOrId("not_a_youtube_url"))
    }

    @Test
    fun `buildYoutubeUrl and buildWatchStateKey format correctly`() {
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", YoutubeUtils.buildYoutubeUrl("dQw4w9WgXcQ"))
        assertEquals("YouTube (dQw4w9WgXcQ)", YoutubeUtils.buildWatchStateKey("dQw4w9WgXcQ"))
    }
}
