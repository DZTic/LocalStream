package com.localstream.app.domain.model

data class VideoItem(
    val url: String = "",
    val name: String,
    val type: String = "video/mp4",
    val path: String = "",
    val size: Long = 0L,
    val lastModified: Long = 0L,
    val nativeUri: String? = null,
    val subtitleNativePath: String? = null,
    val subtitleUrl: String? = null,
    val seriesName: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val isSeriesGroup: Boolean = false,
    val isTvSeries: Boolean = false,
    val episodes: List<VideoItem>? = null,
    val cleanTitle: String? = null,
)

