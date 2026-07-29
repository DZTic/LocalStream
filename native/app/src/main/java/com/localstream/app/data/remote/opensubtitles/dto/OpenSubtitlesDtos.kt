package com.localstream.app.data.remote.opensubtitles.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** DTOs de l'API OpenSubtitles v1 (https://api.opensubtitles.com/api/v1/). */

@Serializable
data class OsLoginRequest(
    val username: String,
    val password: String,
)

@Serializable
data class OsLoginResponse(
    val token: String? = null,
)

@Serializable
data class OsSearchResponse(
    val data: List<OsSubtitleDto> = emptyList(),
)

@Serializable
data class OsSubtitleDto(
    val attributes: OsSubtitleAttributesDto? = null,
)

@Serializable
data class OsSubtitleAttributesDto(
    val language: String = "",
    val files: List<OsSubtitleFileDto> = emptyList(),
)

@Serializable
data class OsSubtitleFileDto(
    @SerialName("file_id") val fileId: Long = 0,
    @SerialName("file_name") val fileName: String = "",
)

@Serializable
data class OsDownloadRequest(
    @SerialName("file_id") val fileId: Long,
)

@Serializable
data class OsDownloadResponse(
    val link: String? = null,
)
