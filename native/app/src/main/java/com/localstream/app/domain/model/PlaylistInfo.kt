package com.localstream.app.domain.model

data class PlaylistInfo(
    val id: String,
    val name: String,
    val videoNames: List<String> = emptyList(),
)

