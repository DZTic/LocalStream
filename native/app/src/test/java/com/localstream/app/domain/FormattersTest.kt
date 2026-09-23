package com.localstream.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FormattersTest {

    @Test
    fun getCleanTitle_removesExtensionYearQualityAndSeriesMarkers() {
        assertEquals("Inception", TitleCleaner.getCleanTitle("Inception.2010.1080p.BluRay.x264.mkv"))
        assertEquals("Breaking Bad", TitleCleaner.getCleanTitle("Breaking.Bad.S01E01.720p.mkv"))
        assertEquals("The Office", TitleCleaner.getCleanTitle("The Office 1x05.mp4"))
        assertEquals("Running Man", TitleCleaner.getCleanTitle("Running Man (2025).mp4"))
    }

    @Test
    fun extractYear_correctlyExtractsYearFromFilename() {
        assertEquals(2025, TitleCleaner.extractYear("Running Man (2025).mp4"))
        assertEquals(2010, TitleCleaner.extractYear("Inception.2010.1080p.mkv"))
        assertEquals(1999, TitleCleaner.extractYear("Fight.Club.1999.mp4"))
        assertNull(TitleCleaner.extractYear("Breaking.Bad.S01E01.mp4"))
    }

    @Test
    fun isInPersonalFolder_checksOnlyThePath() {
        assertTrue(Formatters.isInPersonalFolder("/storage/emulated/0/DCIM/Camera/clip.mp4"))
        assertTrue(
            Formatters.isInPersonalFolder(
                "/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Video/VID-1.mp4",
            ),
        )
        // Nom typique de caméra mais dossier de films : seul isPersonalVideo le détecte.
        assertFalse(Formatters.isInPersonalFolder("/storage/emulated/0/Movies/VID_20240315_143022.mp4"))
        assertFalse(Formatters.isInPersonalFolder("/storage/emulated/0/Download/Show/Saison 1/Show.S01E01.mkv"))
    }

    @Test
    fun isPersonalVideo_detectsCameraAndAppMedia() {
        assertTrue(Formatters.isPersonalVideo("VID_20240315_143022.mp4", "/Movies/VID_20240315_143022.mp4"))
        assertTrue(Formatters.isPersonalVideo("clip.mp4", "/storage/emulated/0/DCIM/Camera/clip.mp4"))
        assertTrue(Formatters.isPersonalVideo("movie.mp4", "/WhatsApp/Media/movie.mp4"))
        assertFalse(Formatters.isPersonalVideo("Inception.2010.1080p.mkv", "/Movies/Inception.2010.1080p.mkv"))
        assertFalse(Formatters.isPersonalVideo("The.Matrix.1999.mkv", "/storage/emulated/0/Movies/The.Matrix.1999.mkv"))
        assertFalse(Formatters.isPersonalVideo("Fight.Club.1999.mkv", "/storage/emulated/0/Movies/Fight.Club.1999.mkv"))
    }

    @Test
    fun getResolution_recognizesFormats() {
        assertEquals("4K", Formatters.getResolution("Film.2160p.mkv"))
        assertEquals("1080p", Formatters.getResolution("Film.1080p.mkv"))
        assertEquals("720p", Formatters.getResolution("Film.720p.mkv"))
        assertEquals("", Formatters.getResolution("Film.mkv"))
    }

    @Test
    fun formatSize_formatsBytes() {
        assertEquals("0 B", Formatters.formatSize(0L))
        assertEquals("1 KB", Formatters.formatSize(1024L))
        assertEquals("1 GB", Formatters.formatSize(1024L * 1024L * 1024L))
    }

    @Test
    fun formatDuration_formatsHoursAndMinutes() {
        assertEquals("Inconnue", Formatters.formatDuration(0L))
        assertEquals("1m", Formatters.formatDuration(90L))
        assertEquals("1h 1m", Formatters.formatDuration(3660L))
    }

    @Test
    fun tmdbUrls_buildsCorrectImageUrls() {
        assertEquals("https://image.tmdb.org/t/p/w342/abc.jpg", TmdbUrls.posterUrl("/abc.jpg"))
        assertEquals("https://image.tmdb.org/t/p/w1280/abc.jpg", TmdbUrls.backdropUrl("/abc.jpg"))
        assertEquals("https://image.tmdb.org/t/p/w300/abc.jpg", TmdbUrls.stillUrl("/abc.jpg"))
        assertNull(TmdbUrls.posterUrl(null))
    }
}
