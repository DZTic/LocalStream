package com.localstream.app.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class PlaylistInfo(
    val id: String,
    val name: String,
    val videoNames: List<String> = emptyList(),
)

