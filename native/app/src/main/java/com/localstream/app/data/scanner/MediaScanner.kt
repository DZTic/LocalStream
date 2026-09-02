package com.localstream.app.data.scanner

import com.localstream.app.domain.model.MovieCollection
import com.localstream.app.domain.model.SubtitleEntry
import com.localstream.app.domain.model.VideoItem

/**
 * Interface d'abstraction pour le scan de fichiers multim\u00e9dias locaux (vid\u00e9os + sous-titres).
 */
interface MediaScanner {
    fun scanVideoFiles(): List<VideoItem>
    fun scanSubtitleFiles(): List<SubtitleEntry>
    fun scanAndGroup(
        whitelistedVideos: Set<String> = emptySet(),
        movieCollections: Map<String, MovieCollection> = emptyMap(),
        releaseDates: Map<String, String> = emptyMap(),
        rawVideos: List<VideoItem>? = null,
    ): List<VideoItem>

    /** Invalide le cache des sous-titres (appelé lors d'un refresh forcé). */
    fun clearSubtitleCache() {}
}
