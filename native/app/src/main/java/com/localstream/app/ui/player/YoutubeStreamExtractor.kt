package com.localstream.app.ui.player

import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest

object YoutubeStreamExtractor {
    fun extract(youtubeUrl: String): String {
        val request = YoutubeDLRequest(youtubeUrl).apply {
            addOption("-f", "best[acodec!=none][vcodec!=none]/best")
        }
        return YoutubeDL.getInstance().getInfo(request).url
            .takeIf { it.isNotBlank() }
            ?: error("yt-dlp n'a retourné aucun flux lisible")
    }
}
