package com.localstream.app.domain.model

data class SeriesInfo(
    val seriesName: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
)

data class SubtitleEntry(
    val name: String,
    val folder: String,
    val uri: String,
)

