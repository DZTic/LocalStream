package com.localstream.app.data.repository

import com.localstream.app.data.scanner.MediaScanner
import com.localstream.app.domain.HeroSelector
import com.localstream.app.domain.VideoGrouper
import com.localstream.app.domain.VideoFilterSorter
import com.localstream.app.domain.model.FilterSortOptions
import com.localstream.app.domain.model.MovieCollection
import com.localstream.app.domain.model.VideoItem

/**
 * Repository principal coordinateur du scan, du regroupement et du filtrage.
 *
 * Phase 4 : les \u00e9tats utilisateur (vus, progression, playlists) sont d\u00e9sormais
 * g\u00e9r\u00e9s par [WatchStateRepository] et [PlaylistRepository].
 * Ce repository se concentre sur les vid\u00e9os et la logique de filtrage.
 */
class VideoRepository(
    private val mediaScanner: MediaScanner,
) {
    private var rawVideos: List<VideoItem> = emptyList()
    private var groupedVideos: List<VideoItem> = emptyList()

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
        return groupedVideos
    }

    fun getGroupedVideos(): List<VideoItem> = groupedVideos
    fun getRawVideos(): List<VideoItem> = rawVideos

    /**
     * Regroupe les vidéos déjà scannées (Phase 7) : re-applique [VideoGrouper] sur
     * le dernier scan brut avec les collections TMDB / dates de sortie fraîchement
     * récupérées, sans relancer une requête MediaStore. Permet le regroupement en
     * sagas après enrichissement des métadonnées.
     */
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
