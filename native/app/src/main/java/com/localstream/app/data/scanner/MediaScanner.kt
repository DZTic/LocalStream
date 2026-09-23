package com.localstream.app.data.scanner

import com.localstream.app.domain.model.MovieCollection
import com.localstream.app.domain.model.SubtitleEntry
import com.localstream.app.domain.model.VideoItem

/**
 * Interface d'abstraction pour le scan de fichiers multim\u00e9dias locaux (vid\u00e9os + sous-titres).
 */
interface MediaScanner {
    fun scanVideoFiles(): List<VideoItem>

    /** Emits immutable batches on the calling thread as files become available. */
    fun scanVideoFiles(onBatchScanned: (List<VideoItem>) -> Unit): List<VideoItem> =
        scanVideoFiles().also { if (it.isNotEmpty()) onBatchScanned(it) }

    fun scanSubtitleFiles(): List<SubtitleEntry>
    fun scanAndGroup(
        whitelistedVideos: Set<String> = emptySet(),
        movieCollections: Map<String, MovieCollection> = emptyMap(),
        releaseDates: Map<String, String> = emptyMap(),
        rawVideos: List<VideoItem>? = null,
    ): List<VideoItem>

    /**
     * Associe les sous-titres locaux aux vidéos. Séparé de [scanAndGroup] car coûteux
     * (requête MediaStore.Files) : exécuté après la publication du catalogue.
     */
    fun matchSubtitles(videos: List<VideoItem>): List<VideoItem> = videos

    /** Invalide le cache des sous-titres (appelé lors d'un refresh forcé). */
    fun clearSubtitleCache() {}
}
