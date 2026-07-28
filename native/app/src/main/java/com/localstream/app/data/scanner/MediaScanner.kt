package com.localstream.app.data.scanner

import com.localstream.app.domain.model.MovieCollection
import com.localstream.app.domain.model.SubtitleEntry
import com.localstream.app.domain.model.VideoItem

/**
 * Interface d'abstraction pour le scan de fichiers multimédias locaux (vidéos + sous-titres).
 */
interface MediaScanner {
    fun scanVideoFiles(): List<VideoItem>
    fun scanSubtitleFiles(): List<SubtitleEntry>
    fun scanAndGroup(
        whitelistedVideos: Set<String> = emptySet(),
        movieCollections: Map<String, MovieCollection> = emptyMap(),
        releaseDates: Map<String, String> = emptyMap()
    ): List<VideoItem>
}
