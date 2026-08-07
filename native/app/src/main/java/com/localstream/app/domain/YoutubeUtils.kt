package com.localstream.app.domain

object YoutubeUtils {
    private val YOUTUBE_ID_REGEX = Regex("^[a-zA-Z0-9_-]{11}$")
    private val YOUTUBE_URL_REGEX = Regex(
        """(?:youtube\.com\/(?:[^\/]+\/.+\/|(?:v|e(?:mbed)?|shorts)\/|.*[?&]v=)|youtu\.be\/)([a-zA-Z0-9_-]{11})""",
        RegexOption.IGNORE_CASE
    )

    fun extractVideoId(input: String?): String? {
        val trimmed = input?.trim().orEmpty()
        return when {
            trimmed.isEmpty() -> null
            YOUTUBE_ID_REGEX.matches(trimmed) -> trimmed
            else -> YOUTUBE_URL_REGEX.find(trimmed)?.groupValues?.get(1)
        }
    }

    fun isYoutubeUrlOrId(input: String?): Boolean = extractVideoId(input) != null

    fun buildYoutubeUrl(videoId: String): String = "https://www.youtube.com/watch?v=$videoId"

    fun buildWatchStateKey(videoId: String): String = "YouTube ($videoId)"
}
