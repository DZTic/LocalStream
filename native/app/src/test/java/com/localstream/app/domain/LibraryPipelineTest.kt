package com.localstream.app.domain

import com.localstream.app.domain.model.FilterSortOptions
import com.localstream.app.domain.model.MovieCollection
import com.localstream.app.domain.model.ResolutionFilter
import com.localstream.app.domain.model.SortBy
import com.localstream.app.domain.model.VideoItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryPipelineTest {

    private fun v(
        name: String,
        path: String = name,
        size: Long = 1000L,
        lastModified: Long = 100L
    ): VideoItem {
        val seriesInfo = VideoNameParser.parseSeriesInfo(name, path)
        return VideoItem(
            url = "file://$path",
            name = name,
            path = path,
            size = size,
            lastModified = lastModified,
            seriesName = seriesInfo.seriesName,
            season = seriesInfo.season,
            episode = seriesInfo.episode
        )
    }

    @Test
    fun testFullPipeline_parseGroupFilterSortOn30RealisticFiles() {
        val dataset = createTestDataset()

        val grouped = VideoGrouper.groupVideos(
            dataset.files,
            dataset.collections,
            dataset.releaseDates,
            dataset.whitelisted
        )
        assertGroupedResults(grouped)

        val sorted4k = VideoFilterSorter.filterAndSortVideos(
            grouped,
            FilterSortOptions(
                sortBy = SortBy.SIZE,
                filterResolution = ResolutionFilter.FOUR_K,
                releaseDates = dataset.releaseDates
            )
        )
        assertEquals(
            listOf("Dune Collection", "Oppenheimer.2023.2160p.mkv", "Interstellar.2014.2160p.UHD.mkv", "Stranger Things"),
            sorted4k.map { it.name }
        )
    }

    private fun assertGroupedResults(grouped: List<VideoItem>) {
        assertTrue(grouped.any { it.name == "VID_20240316_101010.mp4" })
        assertFalse(grouped.any { it.name == "VID_20240315_143022.mp4" })

        val bb = grouped.find { it.seriesName == "Breaking Bad" }
        assertTrue(bb != null && bb.isSeriesGroup && bb.isTvSeries && bb.episodes?.size == 3)

        val office = grouped.find { it.seriesName == "The Office" }
        assertTrue(office != null && office.isSeriesGroup && office.isTvSeries && office.episodes?.size == 2)

        val dkSaga = grouped.find { it.seriesName == "The Dark Knight Collection" }
        assertTrue(dkSaga != null && dkSaga.isSeriesGroup && !dkSaga.isTvSeries && dkSaga.episodes?.size == 3)

        val duneSaga = grouped.find { it.seriesName == "Dune Collection" }
        assertTrue(duneSaga != null && duneSaga.isSeriesGroup && !duneSaga.isTvSeries && duneSaga.episodes?.size == 2)
    }

    private fun createTestDataset(): Dataset {
        val rawFiles = listOf(
            v("Inception.2010.1080p.BluRay.mkv", size = 2000L, lastModified = 500L),
            v("Interstellar.2014.2160p.UHD.mkv", size = 8000L, lastModified = 600L),
            v("The.Dark.Knight.2008.1080p.mkv", size = 2500L, lastModified = 300L),
            v("The.Dark.Knight.Rises.2012.1080p.mkv", size = 2700L, lastModified = 400L),
            v("Batman.Begins.2005.720p.mkv", size = 1500L, lastModified = 200L),
            v("Matrix.1999.1080p.mkv", size = 2200L, lastModified = 100L),
            v("Avatar.2009.1080p.mkv", size = 3000L, lastModified = 250L),
            v("Gladiator.2000.1080p.mkv", size = 2100L, lastModified = 150L),
            v("Pulp.Fiction.1994.720p.mkv", size = 1400L, lastModified = 120L),
            v("Fight.Club.1999.1080p.mkv", size = 2300L, lastModified = 180L),
            v("Breaking.Bad.S01E01.720p.mkv", size = 500L, lastModified = 1000L),
            v("Breaking.Bad.S01E02.720p.mkv", size = 520L, lastModified = 1001L),
            v("Breaking.Bad.S02E01.1080p.mkv", size = 900L, lastModified = 1002L),
            v("Game.of.Thrones.S01E01.1080p.mkv", size = 1100L, lastModified = 900L),
            v("Game.of.Thrones.S01E02.1080p.mkv", size = 1150L, lastModified = 901L),
            v("Stranger.Things.S04E01.2160p.mkv", size = 3500L, lastModified = 1200L),
            v("Stranger.Things.S04E02.2160p.mkv", size = 3600L, lastModified = 1201L),
            v("The Office 1x01.mp4", path = "Shows/The Office/Season 1/The Office 1x01.mp4", size = 300L, lastModified = 700L),
            v("The Office 1x02.mp4", path = "Shows/The Office/Season 1/The Office 1x02.mp4", size = 310L, lastModified = 701L),
            v("01.mp4", path = "Shows/Chernobyl/Saison 1/01.mp4", size = 800L, lastModified = 800L),
            v("02.mp4", path = "Shows/Chernobyl/Saison 1/02.mp4", size = 820L, lastModified = 801L),
            v("VID_20240315_143022.mp4", path = "/Movies/VID_20240315_143022.mp4"),
            v("20240315_143022.mp4", path = "/storage/emulated/0/DCIM/Camera/20240315_143022.mp4"),
            v("whatsapp_video.mp4", path = "/WhatsApp/Media/whatsapp_video.mp4"),
            v("screenrecord_2024.mp4", path = "/Movies/screenrecord_2024.mp4"),
            v("VID_20240316_101010.mp4", path = "/Movies/VID_20240316_101010.mp4"),
            v("Oppenheimer.2023.2160p.mkv", size = 9500L, lastModified = 1500L),
            v("Dune.2021.2160p.mkv", size = 8500L, lastModified = 1300L),
            v("Dune.Part.Two.2024.2160p.mkv", size = 9000L, lastModified = 1600L),
            v("Whiplash.2014.1080p.mkv", size = 1900L, lastModified = 450L)
        )

        val dkSaga = MovieCollection("100", "The Dark Knight Collection")
        val duneSaga = MovieCollection("101", "Dune Collection")
        val collections = mapOf(
            "Batman.Begins.2005.720p.mkv" to dkSaga,
            "The.Dark.Knight.2008.1080p.mkv" to dkSaga,
            "The.Dark.Knight.Rises.2012.1080p.mkv" to dkSaga,
            "Dune.2021.2160p.mkv" to duneSaga,
            "Dune.Part.Two.2024.2160p.mkv" to duneSaga
        )

        val releaseDates = mapOf(
            "Batman.Begins.2005.720p.mkv" to "2005-06-15",
            "The.Dark.Knight.2008.1080p.mkv" to "2008-07-18",
            "The.Dark.Knight.Rises.2012.1080p.mkv" to "2012-07-20",
            "Dune.2021.2160p.mkv" to "2021-09-15",
            "Dune.Part.Two.2024.2160p.mkv" to "2024-03-01"
        )

        return Dataset(rawFiles, collections, releaseDates, setOf("VID_20240316_101010.mp4"))
    }

    private data class Dataset(
        val files: List<VideoItem>,
        val collections: Map<String, MovieCollection>,
        val releaseDates: Map<String, String>,
        val whitelisted: Set<String>
    )
}

