package com.localstream.app.data.repository

import com.localstream.app.data.scanner.MediaScanner
import com.localstream.app.domain.HeroSelector
import com.localstream.app.domain.VideoFilterSorter
import com.localstream.app.domain.VideoGrouper
import com.localstream.app.domain.model.FilterSortOptions
import com.localstream.app.domain.model.MovieCollection
import com.localstream.app.domain.model.VideoItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Repository principal coordinateur du scan, du regroupement et du filtrage.
 */
class VideoRepository(
    private val mediaScanner: MediaScanner,
) {
    private var rawVideos: List<VideoItem> = emptyList()
    private var groupedVideos: List<VideoItem> = emptyList()

    private val _videosFlow = MutableStateFlow<List<VideoItem>>(emptyList())
    val observeVideos: Flow<List<VideoItem>> = _videosFlow.asStateFlow()

    fun scanAndLoad(
        movieCollections: Map<String, MovieCollection> = emptyMap(),
        releaseDates: Map<String, String> = emptyMap(),
        whitelistedVideos: Set<String> = emptySet(),
    ): List<VideoItem> {
        rawVideos = mediaScanner.scanVideoFiles()
        groupedVideos = mediaScanner.scanAndGroup(
            whitelistedVideos = whitelistedVideos,
            movieCollections = movieCollections,
            releaseDates = releaseDates,
        )
        _videosFlow.value = groupedVideos
        return groupedVideos
    }

    fun getGroupedVideos(): List<VideoItem> = groupedVideos
    fun getRawVideos(): List<VideoItem> = rawVideos

    fun refreshVideos() {
        _videosFlow.value = groupedVideos
    }

    fun regroup(
        movieCollections: Map<String, MovieCollection> = emptyMap(),
        releaseDates: Map<String, String> = emptyMap(),
        whitelistedVideos: Set<String> = emptySet(),
    ): List<VideoItem> {
        groupedVideos = VideoGrouper.groupVideos(
            videos = rawVideos,
            movieCollections = movieCollections,
            releaseDates = releaseDates,
            whitelistedVideos = whitelistedVideos,
        )
        _videosFlow.value = groupedVideos
        return groupedVideos
    }

    fun getFilteredAndSortedVideos(
        opts: FilterSortOptions,
        watchedMap: Map<String, Boolean> = emptyMap(),
    ): List<VideoItem> {
        val optsWithWatched = opts.copy(watchedVideos = watchedMap)
        return VideoFilterSorter.filterAndSortVideos(groupedVideos, optsWithWatched)
    }

    fun getHeroCandidates(
        watchedMap: Map<String, Boolean> = emptyMap(),
        progressMap: Map<String, Double> = emptyMap(),
    ): List<VideoItem> = HeroSelector.getHeroCandidates(groupedVideos, watchedMap, progressMap)
}
