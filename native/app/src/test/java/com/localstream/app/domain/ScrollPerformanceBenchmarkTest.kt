package com.localstream.app.domain

import com.localstream.app.domain.model.FilterSortOptions
import com.localstream.app.domain.model.ResolutionFilter
import com.localstream.app.domain.model.SortBy
import com.localstream.app.domain.model.TmdbMetadata
import com.localstream.app.domain.model.VideoDisplayData
import com.localstream.app.domain.model.VideoItem
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.system.measureTimeMillis

class ScrollPerformanceBenchmarkTest {

    private fun generateMockLibrary(count: Int): List<VideoItem> {
        return (1..count).map { i ->
            if (i % 3 == 0) {
                // Series group with 12 episodes
                val episodes = (1..12).map { ep ->
                    VideoItem(
                        url = "content://media/$i/$ep",
                        name = "Super.Series.Name.2023.S01E${ep.toString().padStart(2, '0')}.1080p.mkv",
                        season = 1,
                        episode = ep,
                        size = 1_500_000_000L,
                        lastModified = 1700000000000L + (i * 1000L),
                        duration = 2700L,
                    )
                }
                VideoItem(
                    url = "content://media/$i/1",
                    name = "Super Series Name $i",
                    type = "series",
                    isSeriesGroup = true,
                    isTvSeries = true,
                    episodes = episodes,
                    seriesName = "Super Series Name $i",
                    size = 18_000_000_000L,
                    lastModified = 1700000000000L + (i * 1000L),
                    duration = 2700L * 12,
                )
            } else {
                VideoItem(
                    url = "content://media/$i",
                    name = "Epic.Movie.Title.Number.$i.2024.MULTi.2160p.HDR.x265.mkv",
                    size = 8_000_000_000L,
                    lastModified = 1700000000000L + (i * 1000L),
                    duration = 7200L,
                )
            }
        }
    }

    @Test
    fun `benchmark title cleaning and resolution extraction on large library`() {
        val library = generateMockLibrary(1000)

        // Warmup
        library.take(100).forEach {
            TitleCleaner.getCleanTitle(it.name)
            Formatters.getResolution(it.name)
        }

        val titleTime = measureTimeMillis {
            library.forEach {
                TitleCleaner.getCleanTitle(it.name)
            }
        }

        val resolutionTime = measureTimeMillis {
            library.forEach {
                Formatters.getResolution(it.name)
            }
        }

        println("BENCHMARK - 1000 TitleCleaner.getCleanTitle: ${titleTime}ms")
        println("BENCHMARK - 1000 Formatters.getResolution: ${resolutionTime}ms")
        assertTrue("Title cleaning took too long", titleTime < 5000)
    }

    @Test
    fun `benchmark VideoUiSelectors on scrolling simulation`() {
        val library = generateMockLibrary(500)
        val watched = library.filterIndexed { index, _ -> index % 4 == 0 }
            .flatMap { if (it.isSeriesGroup) it.episodes.orEmpty().map { ep -> ep.name } else listOf(it.name) }
            .associateWith { true }

        val progress = library.filterIndexed { index, _ -> index % 5 == 0 }
            .flatMap { if (it.isSeriesGroup) it.episodes.orEmpty().map { ep -> ep.name } else listOf(it.name) }
            .associateWith { 45.0 }

        val metadata = library.associate {
            it.name to TmdbMetadata(
                queryKey = it.name,
                title = "Metadata Title",
                posterPath = "/poster_${it.name}.jpg",
                releaseDate = "2024-01-01",
            )
        }

        val displayData = VideoDisplayData(
            metadata = metadata,
            watched = watched,
            progress = progress,
        )

        // Warmup
        library.take(50).forEach {
            VideoUiSelectors.posterUrl(it, displayData)
            VideoUiSelectors.isWatched(it, displayData)
            VideoUiSelectors.progressOf(it, displayData)
            VideoUiSelectors.activeEpisodeLabel(it, displayData)
            VideoUiSelectors.displayTitle(it)
        }

        // Simulate 20 scroll passes over all visible cards
        var checksum = 0
        val scrollTime = measureTimeMillis {
            repeat(20) {
                library.forEach { video ->
                    val poster = VideoUiSelectors.posterUrl(video, displayData)
                    val isW = VideoUiSelectors.isWatched(video, displayData)
                    val prog = VideoUiSelectors.progressOf(video, displayData)
                    val label = VideoUiSelectors.activeEpisodeLabel(video, displayData)
                    val title = VideoUiSelectors.displayTitle(video)
                    val res = Formatters.getResolution(video.name)

                    if (isW) checksum++
                    if (prog > 0.0) checksum++
                    if (label != null) checksum += label.length
                    checksum += title.length + res.length + (poster?.length ?: 0)
                }
            }
        }

        assertTrue("Checksum should be computed", checksum > 0)

        println("BENCHMARK - 20 passes over 500 items (10,000 card evaluations): ${scrollTime}ms")
        assertTrue("Scrolling card evaluations took too long", scrollTime < 5000)
    }

    @Test
    fun `benchmark HomeRowsDeriver and VideoFilterSorter on large library`() {
        val library = generateMockLibrary(1000)
        val watched = library.take(200).associate { it.name to true }
        val progress = library.take(100).associate { it.name to 30.0 }

        val filterSortTime = measureTimeMillis {
            VideoFilterSorter.filterAndSortVideos(
                library,
                FilterSortOptions(
                    sortBy = SortBy.ALPHA,
                    filterGenre = null,
                    filterResolution = ResolutionFilter.ALL,
                    releaseDates = emptyMap(),
                    videoGenres = emptyMap(),
                    videoDurations = emptyMap(),
                    watchedVideos = watched,
                ),
            )
        }

        val filtered = VideoFilterSorter.filterAndSortVideos(
            library,
            FilterSortOptions(
                sortBy = SortBy.ALPHA,
                filterGenre = null,
                filterResolution = ResolutionFilter.ALL,
                releaseDates = emptyMap(),
                videoGenres = emptyMap(),
                videoDurations = emptyMap(),
                watchedVideos = watched,
            ),
        )

        val homeRowsTime = measureTimeMillis {
            HomeRowsDeriver.derive(
                grouped = library,
                filteredSorted = filtered,
                watched = watched,
                progress = progress,
            )
        }

        println("BENCHMARK - Filter and Sort 1000 items: ${filterSortTime}ms")
        println("BENCHMARK - HomeRowsDeriver derive 1000 items: ${homeRowsTime}ms")
    }
}
