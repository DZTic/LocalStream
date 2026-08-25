package com.localstream.app.domain

import java.util.Locale
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow

object TitleCleaner {
    private val YEAR_IN_PAREN_REGEX = Regex("[\\(\\[]((?:19|20)\\d{2})[\\)\\]]")
    private val YEAR_REGEX = Regex("(?:^|[\\s\\._\\-\\(\\[])((?:19|20)\\d{2})(?=[\\s\\._\\-\\)\\]]|$)")
    private val EXTENSION_REGEX = Regex("\\.[^/.]+$")
    private val SEASON_EPISODE_CLEAN_REGEX = Regex("[sS]\\d+(\\s*)?([eE]\\d+)?|(\\d+)(\\s*)?x(\\d+).*", RegexOption.IGNORE_CASE)
    private val YEAR_SUFFIX_REGEX = Regex("(?<=\\w|\\s|\\.|\\-|_|\\()\\s*[\\(\\.\\[\\-_]?(19|20)\\d{2}.*")
    private val SEPARATORS_REGEX = Regex("[\\.\\-_]")
    private val TAGS_REGEX = Regex("1080p|720p|2160p|4k|bluray|webrip|hdtv|x264|x265|hevc|vostfr|french|truefrench", RegexOption.IGNORE_CASE)
    private val OPEN_BRACKET_END_REGEX = Regex("[\\(\\[\\{]\\s*$")
    private val TRAILING_SYMBOLS_REGEX = Regex("[\\s\\-\\.\\(\\)\\[\\]\\{\\}]+$")

    @Suppress("ReturnCount")
    fun extractYear(filename: String): Int? {
        val nameWithoutExt = filename.replace(EXTENSION_REGEX, "")
        val parenMatch = YEAR_IN_PAREN_REGEX.find(nameWithoutExt)
        if (parenMatch != null) {
            return parenMatch.groupValues[1].toIntOrNull()
        }
        val matches = YEAR_REGEX.findAll(nameWithoutExt).toList()
        return matches.lastOrNull()?.groupValues?.get(1)?.toIntOrNull()
    }

    fun getCleanTitle(filename: String): String {
        var title = filename.replace(EXTENSION_REGEX, "")
        title = title.replace(SEASON_EPISODE_CLEAN_REGEX, "")
        title = title.replace(YEAR_SUFFIX_REGEX, "")
        title = title.replace(SEPARATORS_REGEX, " ")
        title = title.replace(TAGS_REGEX, "")
        return title.trim()
            .replace(OPEN_BRACKET_END_REGEX, "")
            .replace(TRAILING_SYMBOLS_REGEX, "")
            .trim()
    }
}

object Formatters {
    private val TRAILING_ZEROS_REGEX = Regex("\\.?0+$")

    private val suspectPaths = listOf(
        "/dcim/", "/camera/", "/whatsapp/", "/snapchat/", "/instagram/",
        "/telegram/", "/signal/", "/viber/", "/messenger/", "/tiktok/",
        "/recordings/", "/screenrecord", "/screen_record", "/voicememos/"
    )

    private val personalPatterns = listOf(
        Regex("^vid_\\d{8}_\\d{6}"),
        Regex("^\\d{8}_\\d{6}"),
        Regex("^\\d{4}-\\d{2}-\\d{2}-\\d{2}-\\d{2}-\\d{2}"),
        Regex("^(img|mov|dsc|dscn|dscf|mvc|sdc|vlc)_?\\d{4,}", RegexOption.IGNORE_CASE),
        Regex("^(c|m2u|avchd|mts|m2ts)\\d{4,}", RegexOption.IGNORE_CASE),
        Regex("^(gh|gx|gopr|gp)\\d{4,}", RegexOption.IGNORE_CASE),
        Regex("^dji_\\d{4}", RegexOption.IGNORE_CASE),
        Regex("^whatsapp.*(video|vidéo|audio)", RegexOption.IGNORE_CASE),
        Regex("^snapchat-\\d+"),
        Regex("^\\d+\\.(mp4|mkv|avi|mov|webm)$", RegexOption.IGNORE_CASE),
        Regex("^\\d{4}[-_.\\s]\\d{2}[-_.\\s]\\d{2}[\\s_-]\\d{2}[.:_]\\d{2}"),
        Regex("^screen.?record", RegexOption.IGNORE_CASE),
        Regex("^\\d{14}\\.(mp4|avi|mkv)$", RegexOption.IGNORE_CASE)
    )

    fun formatSize(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val k = 1024.0
        val sizes = arrayOf("B", "KB", "MB", "GB", "TB")
        val i = floor(ln(bytes.toDouble()) / ln(k)).toInt()
        val value = bytes / k.pow(i.toDouble())
        val formatted = String.format(Locale.US, "%.2f", value).replace(TRAILING_ZEROS_REGEX, "")
        return "$formatted ${sizes[i]}"
    }

    fun formatDuration(seconds: Long): String {
        if (seconds <= 0L) return "Inconnue"
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }

    fun getResolution(name: String): String = when {
        name.contains("2160p", ignoreCase = true) ||
            name.contains("4k", ignoreCase = true) ||
            name.contains("uhd", ignoreCase = true) -> "4K"
        name.contains("1440p", ignoreCase = true) -> "2K"
        name.contains("1080p", ignoreCase = true) ||
            name.contains("fhd", ignoreCase = true) -> "1080p"
        name.contains("720p", ignoreCase = true) ||
            name.contains("hd", ignoreCase = true) -> "720p"
        name.contains("480p", ignoreCase = true) ||
            name.contains("sd", ignoreCase = true) -> "SD"
        else -> ""
    }

    fun isPersonalVideo(name: String, path: String): Boolean {
        val n = name.lowercase()
        val p = path.lowercase().replace('\\', '/')
        return suspectPaths.any { p.contains(it) } || personalPatterns.any { it.containsMatchIn(n) }
    }
}

object TmdbUrls {
    fun posterUrl(path: String?): String? = path?.let {
        if (it.startsWith("http")) it else "https://image.tmdb.org/t/p/w342$it"
    }
    fun backdropUrl(path: String?): String? = path?.let {
        if (it.startsWith("http")) it else "https://image.tmdb.org/t/p/w1280$it"
    }
    fun stillUrl(path: String?): String? = path?.let {
        if (it.startsWith("http")) it else "https://image.tmdb.org/t/p/w300$it"
    }
}
