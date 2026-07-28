package com.localstream.app.domain

import com.localstream.app.domain.model.SeriesInfo
import com.localstream.app.domain.model.SubtitleEntry

object VideoNameParser {
    val VIDEO_EXT_REGEX = Regex("\\.(mp4|mkv|webm|avi|mov)$", RegexOption.IGNORE_CASE)
    val SUBTITLE_EXT_REGEX = Regex("\\.(srt|vtt|ass|ssa)$", RegexOption.IGNORE_CASE)

    private val LANG_SUFFIX_REGEX = Regex(
        "\\.(fr|en|es|de|it|pt|nl|pl|ru|ja|zh|ko|ar|he|tr|sv|da|fi|nb|uk|cs|sk|hu|ro|hr|sr|bg|el|vi|th|hi|id|ms|fa)$",
        RegexOption.IGNORE_CASE
    )

    val SEASON_EPISODE_REGEX = Regex("[sS](\\d+)(\\s*)[eE](\\d+)|(\\d+)(\\s*)x(\\d+)")
    private val SEASON_FOLDER_REGEX = Regex("Saison\\s*(\\d+)|Season\\s*(\\d+)|S(\\d+)", RegexOption.IGNORE_CASE)

    fun videoBaseName(fileName: String): String =
        fileName.replace(Regex("\\.\\w+$"), "").lowercase()

    fun subtitleBaseName(fileName: String): String =
        fileName.replace(Regex("\\.\\w+$"), "").replace(LANG_SUFFIX_REGEX, "").lowercase()

    fun parentFolder(relativePath: String): String {
        val normalized = relativePath.replace('\\', '/').trimEnd('/')
        val idx = normalized.lastIndexOf('/')
        return if (idx == -1) "" else normalized.substring(0, idx)
    }

    fun parseSeriesInfo(fileName: String, fullPath: String? = null): SeriesInfo {
        val match = SEASON_EPISODE_REGEX.find(fileName)
        if (match != null) {
            val groupValues = match.groupValues
            val sStr = if (groupValues[1].isNotEmpty()) groupValues[1] else groupValues[4]
            val eStr = if (groupValues[3].isNotEmpty()) groupValues[3] else groupValues[6]
            var seriesName = fileName.substring(0, match.range.first)
                .replace(Regex("[\\.\\-_/\\\\\\[\\]\\(\\)]"), " ")
                .trim()
                .replace(Regex("[\\s\\-]+$"), "")
            if (seriesName.isEmpty()) seriesName = "Série Inconnue"
            return SeriesInfo(seriesName = seriesName, season = sStr.toIntOrNull(), episode = eStr.toIntOrNull())
        }
        return parseFolderSeriesInfo(fileName, fullPath)
    }

    private fun parseFolderSeriesInfo(fileName: String, fullPath: String?): SeriesInfo {
        val pathParts = (fullPath ?: "").split('/').filter { it.isNotEmpty() }
        if (pathParts.size >= 2) {
            val lastFolder = pathParts[pathParts.size - 2]
            val sMatch = SEASON_FOLDER_REGEX.find(lastFolder)
            if (sMatch != null) {
                val sVal = sMatch.groupValues[1].ifEmpty {
                    sMatch.groupValues[2].ifEmpty { sMatch.groupValues[3] }
                }
                val seriesName = if (pathParts.size >= 3) pathParts[pathParts.size - 3] else "Série Inconnue"
                val epMatch = Regex("^(\\d+)").find(fileName)
                return SeriesInfo(
                    seriesName = seriesName,
                    season = sVal.toIntOrNull(),
                    episode = epMatch?.groupValues?.get(1)?.toIntOrNull()
                )
            }
        }
        return SeriesInfo()
    }

    fun buildSubtitleIndex(subtitles: List<SubtitleEntry>): Map<String, SubtitleEntry> {
        val index = mutableMapOf<String, SubtitleEntry>()
        for (sub in subtitles) {
            if (!SUBTITLE_EXT_REGEX.containsMatchIn(sub.name)) continue
            val key = "${sub.folder}/${subtitleBaseName(sub.name)}"
            index[key] = sub
        }
        return index
    }

    fun matchSubtitle(
        index: Map<String, SubtitleEntry>,
        videoName: String,
        videoFolder: String
    ): SubtitleEntry? {
        val key = "$videoFolder/${videoBaseName(videoName)}"
        return index[key]
    }
}

