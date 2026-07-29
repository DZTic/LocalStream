package com.localstream.app.domain

import java.util.Locale
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow

object TitleCleaner {
    fun getCleanTitle(filename: String): String {
        var title = filename.replace(Regex("\\.[^/.]+$"), "")
        title = title.replace(Regex("[sS]\\d+(\\s*)?([eE]\\d+)?|(\\d+)(\\s*)?x(\\d+).*", RegexOption.IGNORE_CASE), "")
        title = title.replace(Regex("(19|20)\\d{2}.*"), "")
        title = title.replace(Regex("[\\.\\-_]"), " ")
        title = title.replace(
            Regex("1080p|720p|2160p|4k|bluray|webrip|hdtv|x264|x265|hevc|vostfr|french|truefrench", RegexOption.IGNORE_CASE),
            ""
        )
        return title.trim()
            .replace(Regex("[\\(\\[\\{]\\s*$"), "")
            .replace(Regex("[\\s\\-\\.\\(\\)\\[\\]\\{\\}]+$"), "")
            .trim()
    }
}

object Formatters {
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
        val formatted = String.format(Locale.US, "%.2f", value).replace(Regex("\\.?0+$"), "")
        return "$formatted ${sizes[i]}"
    }

    fun formatDuration(seconds: Long): String {
        if (seconds <= 0L) return "Inconnue"
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }

    fun getResolution(name: String): String {
        val n = name.lowercase()
        return when {
            n.contains("2160p") || n.contains("4k") || n.contains("uhd") -> "4K"
            n.contains("1440p") -> "2K"
            n.contains("1080p") || n.contains("fhd") -> "1080p"
            n.contains("720p") || n.contains("hd") -> "720p"
            n.contains("480p") || n.contains("sd") -> "SD"
            else -> ""
        }
    }

    fun isPersonalVideo(name: String, path: String): Boolean {
        val n = name.lowercase()
        val p = path.lowercase().replace('\\', '/')
        return suspectPaths.any { p.contains(it) } || personalPatterns.any { it.containsMatchIn(n) }
    }
}

object TmdbUrls {
    fun posterUrl(path: String?): String? = path?.let {
        if (it.startsWith("http")) it else "https://image.tmdb.org/t/p/w500$it"
    }
    fun backdropUrl(path: String?): String? = path?.let {
        if (it.startsWith("http")) it else "https://image.tmdb.org/t/p/w1280$it"
    }
    fun stillUrl(path: String?): String? = path?.let {
        if (it.startsWith("http")) it else "https://image.tmdb.org/t/p/w300$it"
    }
}
