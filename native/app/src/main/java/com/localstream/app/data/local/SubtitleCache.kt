package com.localstream.app.data.local

import java.io.File

/**
 * Cache disque des sous-titres téléchargés : `cacheDir/subtitles/{file_id}.srt`.
 *
 * Nettoyage LRU : au-delà de [MAX_SIZE_BYTES] (50 Mo), les fichiers les moins
 * récemment utilisés sont supprimés en premier.
 */
class SubtitleCache(
    private val cacheDir: File,
    private val maxSizeBytes: Long = MAX_SIZE_BYTES,
) {

    private val subtitlesDir: File get() = File(cacheDir, SUBTITLES_DIR)

    fun get(fileId: String): File? {
        val file = File(subtitlesDir, fileName(fileId))
        return if (file.isFile) file else null
    }

    /** Écrit le .srt sur disque puis déclenche le nettoyage LRU. */
    fun store(fileId: String, bytes: ByteArray): File {
        val dir = subtitlesDir.apply { mkdirs() }
        val file = File(dir, fileName(fileId))
        file.writeBytes(bytes)
        file.setLastModified(System.currentTimeMillis())
        evictIfNeeded()
        return file
    }

    fun clear() {
        subtitlesDir.deleteRecursively()
    }

    private fun evictIfNeeded() {
        val files = subtitlesDir.listFiles()?.filter { it.isFile } ?: return
        var total = files.sumOf { it.length() }
        if (total <= maxSizeBytes) return
        for (file in files.sortedBy { it.lastModified() }) {
            if (total <= maxSizeBytes) break
            val size = file.length()
            if (file.delete()) total -= size
        }
    }

    private fun fileName(fileId: String) = "$fileId.srt"

    companion object {
        const val SUBTITLES_DIR = "subtitles"
        const val MAX_SIZE_BYTES = 50L * 1024 * 1024 // 50 Mo
    }
}
