package com.localstream.app.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class VideoItem(
    val url: String = "",
    val name: String,
    val type: String = "video/mp4",
    val path: String = "",
    val size: Long = 0L,
    val lastModified: Long = 0L,
    /** Durée en secondes. 0 si inconnue (compatibilité rétrograde). */
    val duration: Long = 0L,
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
    val resolution: String = "",
    val year: Int? = null,
    /** ID MediaStore : null si inconnu. Sert à fiabiliser la clé d'identité à terme. */
    val mediaStoreId: Long? = null,
)
