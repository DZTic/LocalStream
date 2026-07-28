package com.localstream.app.domain.model

data class SubtitleInfo(
    val id: String,
    val language: String,
    val filename: String,
    val url: String? = null,
)

