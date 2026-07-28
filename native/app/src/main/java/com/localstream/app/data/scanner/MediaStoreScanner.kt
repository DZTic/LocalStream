package com.localstream.app.data.scanner

import android.content.Context
import android.database.Cursor
import android.provider.MediaStore
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

    override fun scanVideoFiles(): List<VideoItem> {
        val resolver = context?.contentResolver ?: return scanVideoFilesFromFileSystem()
        val videos = mutableListOf<VideoItem>()

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.MIME_TYPE
        )

        val cursor = resolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            "${MediaStore.Video.Media.DATE_MODIFIED} DESC"
        )

        cursor?.use { c ->
            val indices = VideoCursorIndices(
                id = c.getColumnIndex(MediaStore.Video.Media._ID),
                data = c.getColumnIndex(MediaStore.Video.Media.DATA),
                name = c.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME),
                size = c.getColumnIndex(MediaStore.Video.Media.SIZE),
                dur = c.getColumnIndex(MediaStore.Video.Media.DURATION),
                date = c.getColumnIndex(MediaStore.Video.Media.DATE_MODIFIED),
                mime = c.getColumnIndex(MediaStore.Video.Media.MIME_TYPE)
            )
            while (c.moveToNext()) {
                parseVideoFromCursor(c, indices)?.let { videos.add(it) }
            }
        }

        return if (videos.isNotEmpty()) videos else scanVideoFilesFromFileSystem()
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

        val contentUri = "content://media/external/video/media/$id"
        val seriesInfo = VideoNameParser.parseSeriesInfo(name, path)

        return VideoItem(
            url = contentUri,
            name = name,
            type = mimeType,
            size = size,
            path = path,
            lastModified = if (lastModifiedSec > 0) lastModifiedSec * 1000L else System.currentTimeMillis(),
            duration = durationMs / 1000L,
            seriesName = seriesInfo.seriesName,
            season = seriesInfo.season,
            episode = seriesInfo.episode
        )
    }

    override fun scanSubtitleFiles(): List<SubtitleEntry> {
        val resolver = context?.contentResolver ?: return scanSubtitleFilesFromFileSystem()
        val subtitles = mutableListOf<SubtitleEntry>()

        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.DISPLAY_NAME
        )

        val selection = "${MediaStore.Files.FileColumns.DATA} LIKE '%.srt' OR ${MediaStore.Files.FileColumns.DATA} LIKE '%.vtt'"

        val cursor = resolver.query(
            MediaStore.Files.getContentUri("external"),
            projection,
            selection,
            null,
            null
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

        return if (subtitles.isNotEmpty()) subtitles else scanSubtitleFilesFromFileSystem()
    }

    override fun scanAndGroup(
        whitelistedVideos: Set<String>,
        movieCollections: Map<String, MovieCollection>,
        releaseDates: Map<String, String>
    ): List<VideoItem> {
        val rawVideos = scanVideoFiles()
        val subtitles = scanSubtitleFiles()
        val subIndex = VideoNameParser.buildSubtitleIndex(subtitles)

        val videosWithSubtitles = rawVideos.map { video ->
            val folder = VideoNameParser.parentFolder(video.path)
            val matchedSub = VideoNameParser.matchSubtitle(subIndex, video.name, folder)
            if (matchedSub != null) {
                video.copy(subtitleNativePath = matchedSub.uri)
            } else {
                video
            }
        }

        return VideoGrouper.groupVideos(
            videos = videosWithSubtitles,
            movieCollections = movieCollections,
            releaseDates = releaseDates,
            whitelistedVideos = whitelistedVideos
        )
    }

    private fun scanVideoFilesFromFileSystem(): List<VideoItem> {
        val dirsToScan = customDirectories.ifEmpty { defaultDirsToScan() }
        return dirsToScan.filter { it.isDirectory }.flatMap { dir ->
            dir.walkTopDown().maxDepth(MAX_SCAN_DEPTH)
                .filter { it.isFile && VideoNameParser.VIDEO_EXT_REGEX.containsMatchIn(it.name) }
                .map { file ->
                    val seriesInfo = VideoNameParser.parseSeriesInfo(file.name, file.absolutePath)
                    VideoItem(
                        url = "file://${file.absolutePath}",
                        name = file.name,
                        type = "video/${file.extension}",
                        size = file.length(),
                        path = file.absolutePath,
                        lastModified = file.lastModified(),
                        seriesName = seriesInfo.seriesName,
                        season = seriesInfo.season,
                        episode = seriesInfo.episode
                    )
                }.toList()
        }
    }

    private fun scanSubtitleFilesFromFileSystem(): List<SubtitleEntry> {
        val dirsToScan = customDirectories.ifEmpty { defaultDirsToScan() }
        return dirsToScan.filter { it.isDirectory }.flatMap { dir ->
            dir.walkTopDown().maxDepth(MAX_SCAN_DEPTH)
                .filter { it.isFile && VideoNameParser.SUBTITLE_EXT_REGEX.containsMatchIn(it.name) }
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

    private companion object {
        private const val MAX_SCAN_DEPTH = 5
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
