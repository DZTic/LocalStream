package com.localstream.app.data.scanner

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import com.localstream.app.domain.Formatters
import com.localstream.app.domain.TitleCleaner
import com.localstream.app.domain.VideoGrouper
import com.localstream.app.domain.VideoNameParser
import com.localstream.app.domain.model.MovieCollection
import com.localstream.app.domain.model.SubtitleEntry
import com.localstream.app.domain.model.VideoItem
import java.io.File

/**
 * Implémentation du scanner basée sur le [MediaStore] Android avec repli sur le système de fichiers.
 */
class MediaStoreScanner(
    private val context: Context? = null,
    private val customDirectories: List<File> = emptyList()
) : MediaScanner {

    private var cachedSubtitleIndex: Map<String, SubtitleEntry>? = null

    override fun clearSubtitleCache() {
        cachedSubtitleIndex = null
    }

    /**
     * Retourne l\'index des sous-titres en cache ou le construit au premier appel.
     */
    fun getOrBuildSubtitleIndex(targetDirectories: Set<String> = emptySet()): Map<String, SubtitleEntry> {
        val existing = cachedSubtitleIndex
        if (existing != null) return existing
        val subtitles = scanSubtitleFiles(targetDirectories)
        val index = VideoNameParser.buildSubtitleIndex(subtitles)
        cachedSubtitleIndex = index
        return index
    }

    override fun scanVideoFiles(): List<VideoItem> {
        return scanVideoFilesPaged(pageSize = DEFAULT_PAGE_SIZE)
    }

    override fun scanVideoFiles(onBatchScanned: (List<VideoItem>) -> Unit): List<VideoItem> =
        scanVideoFilesPaged(onBatchScanned = onBatchScanned)

    fun scanVideoFilesPaged(
        pageSize: Int = DEFAULT_PAGE_SIZE,
        onBatchScanned: ((List<VideoItem>) -> Unit)? = null,
    ): List<VideoItem> {
        require(pageSize > 0)
        val resolver = context?.contentResolver ?: return scanVideoFilesFromFileSystem(pageSize, onBatchScanned)
        val videos = mutableListOf<VideoItem>()

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.MIME_TYPE,
        )

        var offset = 0
        var hasMore = true

        while (hasMore) {
            val (batch, rowCount) = fetchVideoBatch(resolver, projection, pageSize, offset)
            if (batch.isNotEmpty()) {
                videos.addAll(batch)
                onBatchScanned?.invoke(batch)
            }
            if (rowCount < pageSize || rowCount == 0) {
                hasMore = false
            } else {
                offset += pageSize
            }
        }

        return if (videos.isNotEmpty()) videos else scanVideoFilesFromFileSystem(pageSize, onBatchScanned)
    }

    private fun fetchVideoBatch(
        resolver: android.content.ContentResolver,
        projection: Array<String>,
        pageSize: Int,
        offset: Int,
    ): Pair<List<VideoItem>, Int> {
        val batch = mutableListOf<VideoItem>()
        var rowsInBatch = 0

        val cursor = queryVideoPage(resolver, projection, pageSize, offset)

        cursor?.use { c ->
            val indices = VideoCursorIndices(
                id = c.getColumnIndex(MediaStore.Video.Media._ID),
                data = c.getColumnIndex(MediaStore.Video.Media.DATA),
                name = c.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME),
                size = c.getColumnIndex(MediaStore.Video.Media.SIZE),
                dur = c.getColumnIndex(MediaStore.Video.Media.DURATION),
                date = c.getColumnIndex(MediaStore.Video.Media.DATE_MODIFIED),
                mime = c.getColumnIndex(MediaStore.Video.Media.MIME_TYPE),
            )
            while (c.moveToNext()) {
                rowsInBatch++
                parseVideoFromCursor(c, indices)?.let { batch.add(withParsedName(it)) }
            }
        }

        return Pair(batch, rowsInBatch)
    }

    /**
     * Requête paginée (tri + LIMIT/OFFSET) sur MediaStore.
     *
     * Depuis Android 10/11, `MediaProvider` valide strictement la grammaire SQL du
     * paramètre `sortOrder` et rejette tout token comme `LIMIT`/`OFFSET` injecté
     * manuellement dans la chaîne (`IllegalArgumentException: Invalid token LIMIT`).
     * À partir de l'API 30, on utilise donc l'API `Bundle` (`QUERY_ARG_LIMIT` /
     * `QUERY_ARG_OFFSET`), seule méthode officiellement supportée pour paginer une
     * requête MediaStore. En dessous de l'API 30, la pagination native n'est pas
     * fiable : on trie sans LIMIT et on charge tout en un seul appel (l'appelant
     * détecte la fin via `rowCount < pageSize`, donc `hasMore` repasse à `false`
     * après ce premier lot).
     */
    private fun queryVideoPage(
        resolver: android.content.ContentResolver,
        projection: Array<String>,
        pageSize: Int,
        offset: Int,
    ): Cursor? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val queryArgs = Bundle().apply {
                putString(ContentResolver.QUERY_ARG_SQL_SELECTION, VIDEO_SELECTION)
                putStringArray(
                    ContentResolver.QUERY_ARG_SORT_COLUMNS,
                    arrayOf(MediaStore.Video.Media.DATE_MODIFIED),
                )
                putInt(
                    ContentResolver.QUERY_ARG_SORT_DIRECTION,
                    ContentResolver.QUERY_SORT_DIRECTION_DESCENDING,
                )
                putInt(ContentResolver.QUERY_ARG_LIMIT, pageSize)
                putInt(ContentResolver.QUERY_ARG_OFFSET, offset)
            }
            resolver.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, projection, queryArgs, null)
        } else {
            if (offset > 0) return null
            val sortOrder = "${MediaStore.Video.Media.DATE_MODIFIED} DESC"
            resolver.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, projection, VIDEO_SELECTION, null, sortOrder)
        }
    }

    private fun parseVideoFromCursor(c: Cursor, idx: VideoCursorIndices): VideoItem? {
        val id = if (idx.id >= 0) c.getLong(idx.id) else 0L
        val path = if (idx.data >= 0) c.getString(idx.data) ?: "" else ""
        val name = if (idx.name >= 0) c.getString(idx.name) ?: File(path).name else File(path).name
        val size = if (idx.size >= 0) c.getLong(idx.size) else 0L
        val durationMs = if (idx.dur >= 0) c.getLong(idx.dur) else 0L
        val lastModifiedSec = if (idx.date >= 0) c.getLong(idx.date) else 0L
        val mimeType = if (idx.mime >= 0) c.getString(idx.mime) ?: "video/mp4" else "video/mp4"

        if (name.isEmpty() || !VideoNameParser.VIDEO_EXT_REGEX.containsMatchIn(name)) return null

        return VideoItem(
            url = "content://media/external/video/media/$id",
            name = name,
            type = mimeType,
            size = size,
            path = path,
            lastModified = if (lastModifiedSec > 0) lastModifiedSec * 1000L else System.currentTimeMillis(),
            duration = durationMs / 1000L,
        )
    }

    /**
     * Analyse regex du nom (série, titre, résolution, année). Ignorée pour les vidéos perso
     * (caméra, messageries…) : VideoGrouper les écarte, sauf whitelist où il complète ces champs.
     */
    private fun withParsedName(video: VideoItem): VideoItem {
        if (Formatters.isInPersonalFolder(video.path)) return video
        val seriesInfo = VideoNameParser.parseSeriesInfo(video.name, video.path)
        return video.copy(
            seriesName = seriesInfo.seriesName,
            season = seriesInfo.season,
            episode = seriesInfo.episode,
            cleanTitle = TitleCleaner.getCleanTitle(video.name),
            resolution = Formatters.getResolution(video.name),
            year = TitleCleaner.extractYear(video.name),
        )
    }

    override fun scanSubtitleFiles(): List<SubtitleEntry> = scanSubtitleFiles(emptySet())

    fun scanSubtitleFiles(targetDirectories: Set<String> = emptySet()): List<SubtitleEntry> {
        val resolver = context?.contentResolver ?: return scanSubtitleFilesFromFileSystem()
        val subtitles = mutableListOf<SubtitleEntry>()

        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
        )

        val extCondition = "(${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.srt' OR " +
            "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.vtt' OR " +
            "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.ass' OR " +
            "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.ssa')"

        // Depuis l'API 30, MediaStore indexe les .srt/.vtt en MEDIA_TYPE_SUBTITLE (5), plus en NONE (0).
        // Un IN reste indexable (un NOT IN mesuré ~5x plus lent sur appareil).
        val baseCondition = "(${MediaStore.Files.FileColumns.MEDIA_TYPE} IN " +
            "(${MediaStore.Files.FileColumns.MEDIA_TYPE_NONE}, $MEDIA_TYPE_SUBTITLE) " +
            "OR ${MediaStore.Files.FileColumns.MEDIA_TYPE} IS NULL) AND " +
            "(${MediaStore.Files.FileColumns.SIZE} > 0) AND $extCondition"

        val (selection, selectionArgs) = if (targetDirectories.isNotEmpty() && targetDirectories.size <= MAX_SQL_DIR_ARGS) {
            val dirConditions = targetDirectories.joinToString(" OR ") { "${MediaStore.Files.FileColumns.DATA} LIKE ?" }
            val args = targetDirectories.map { "$it%" }.toTypedArray()
            "($baseCondition) AND ($dirConditions)" to args
        } else {
            baseCondition to null
        }

        val cursor = resolver.query(
            MediaStore.Files.getContentUri("external"),
            projection,
            selection,
            selectionArgs,
            null,
        )

        cursor?.use { c ->
            val idCol = c.getColumnIndex(MediaStore.Files.FileColumns._ID)
            val dataCol = c.getColumnIndex(MediaStore.Files.FileColumns.DATA)
            val nameCol = c.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME)

            while (c.moveToNext()) {
                val id = if (idCol >= 0) c.getLong(idCol) else 0L
                val path = if (dataCol >= 0) c.getString(dataCol) ?: "" else ""
                val name = if (nameCol >= 0) c.getString(nameCol) ?: File(path).name else File(path).name
                val folder = VideoNameParser.parentFolder(path)

                if (name.isNotEmpty()) {
                    subtitles.add(SubtitleEntry(name = name, folder = folder, uri = "content://media/external/file/$id"))
                }
            }
        }

        // Pas de repli sur `user.home` côté Android : cette propriété ne désigne pas le stockage partagé.
        return if (subtitles.isNotEmpty() || customDirectories.isEmpty()) subtitles else scanSubtitleFilesFromFileSystem()
    }

    override fun scanAndGroup(
        whitelistedVideos: Set<String>,
        movieCollections: Map<String, MovieCollection>,
        releaseDates: Map<String, String>,
        rawVideos: List<VideoItem>?,
    ): List<VideoItem> {
        // Les sous-titres sont associés à part (matchSubtitles) : la requête MediaStore.Files
        // ne doit pas retarder la publication du catalogue.
        return VideoGrouper.groupVideos(
            videos = rawVideos ?: scanVideoFiles(),
            movieCollections = movieCollections,
            releaseDates = releaseDates,
            whitelistedVideos = whitelistedVideos
        )
    }

    override fun matchSubtitles(videos: List<VideoItem>): List<VideoItem> {
        val targetDirs = videos.mapNotNull {
            val folder = File(it.path).parent
            if (!folder.isNullOrBlank()) folder else null
        }.toSet()
        val subIndex = getOrBuildSubtitleIndex(targetDirs)
        if (subIndex.isEmpty()) return videos

        return videos.map { video ->
            val folder = VideoNameParser.parentFolder(video.path)
            val matchedSub = VideoNameParser.matchSubtitle(subIndex, video.name, folder)
            if (matchedSub != null) {
                video.copy(subtitleNativePath = matchedSub.uri)
            } else {
                video
            }
        }
    }

    private fun scanVideoFilesFromFileSystem(
        pageSize: Int,
        onBatchScanned: ((List<VideoItem>) -> Unit)?,
    ): List<VideoItem> {
        val dirsToScan = customDirectories.ifEmpty { defaultDirsToScan() }
        return dirsToScan.asSequence().filter { it.isDirectory }.flatMap { dir ->
            dir.walkTopDown().maxDepth(MAX_SCAN_DEPTH)
                .filter { it.isFile && it.extension.lowercase() in VIDEO_EXTENSIONS }
                .map { file ->
                    val seriesInfo = VideoNameParser.parseSeriesInfo(file.name, file.absolutePath)
                    val extractedYear = TitleCleaner.extractYear(file.name)
                    val cleanTitle = TitleCleaner.getCleanTitle(file.name)
                    val resolution = Formatters.getResolution(file.name)
                    VideoItem(
                        url = "file://${file.absolutePath}",
                        name = file.name,
                        type = "video/${file.extension}",
                        size = file.length(),
                        path = file.absolutePath,
                        lastModified = file.lastModified(),
                        seriesName = seriesInfo.seriesName,
                        season = seriesInfo.season,
                        episode = seriesInfo.episode,
                        cleanTitle = cleanTitle,
                        resolution = resolution,
                        year = extractedYear
                    )
                }
        }.chunked(pageSize).onEach { onBatchScanned?.invoke(it) }.flatten().toList()
    }

    private fun scanSubtitleFilesFromFileSystem(): List<SubtitleEntry> {
        val dirsToScan = customDirectories.ifEmpty { defaultDirsToScan() }
        return dirsToScan.filter { it.isDirectory }.flatMap { dir ->
            dir.walkTopDown().maxDepth(MAX_SCAN_DEPTH)
                .filter { it.isFile && it.extension.lowercase() in SUBTITLE_EXTENSIONS }
                .map { file ->
                    val folder = VideoNameParser.parentFolder(file.absolutePath)
                    SubtitleEntry(
                        name = file.name,
                        folder = folder,
                        uri = "file://${file.absolutePath}"
                    )
                }.toList()
        }
    }

    private fun defaultDirsToScan(): List<File> {
        val userHome = System.getProperty("user.home") ?: return emptyList()
        return listOf("Movies", "Download", "Downloads", "Documents").map {
            File(userHome, it)
        }
    }

    companion object {
        const val DEFAULT_PAGE_SIZE = 100
        private const val MAX_SCAN_DEPTH = 5
        private const val MIN_VIDEO_DURATION_MS = 1000L
        private const val MAX_SQL_DIR_ARGS = 50

        /** `MediaStore.Files.FileColumns.MEDIA_TYPE_SUBTITLE`, défini à partir de l'API 30. */
        private const val MEDIA_TYPE_SUBTITLE = 5
        private const val VIDEO_SELECTION =
            "${MediaStore.Video.Media.SIZE} > 0 AND (${MediaStore.Video.Media.DURATION} IS NULL OR ${MediaStore.Video.Media.DURATION} > $MIN_VIDEO_DURATION_MS)"
        private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "webm", "avi", "mov")
        private val SUBTITLE_EXTENSIONS = setOf("srt", "vtt", "ass", "ssa")
    }
}

private data class VideoCursorIndices(
    val id: Int,
    val data: Int,
    val name: Int,
    val size: Int,
    val dur: Int,
    val date: Int,
    val mime: Int
)
