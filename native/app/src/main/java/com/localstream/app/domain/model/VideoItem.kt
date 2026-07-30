package com.localstream.app.domain.model

data class VideoItem(
    val url: String = "",
    val name: String,
    val type: String = "video/mp4",
    val path: String = "",
    val size: Long = 0L,
    val lastModified: Long = 0L,
    /** Dur\u00e9e en secondes. 0 si inconnue (compatibilit\u00e9 r\u00e9trograde). */
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
    /** ID MediaStore : null si inconnu. Sert \u00e0 fiabiliser la cl\u00e9 d'identit\u00e9 \u00e0 terme. */
    val mediaStoreId: Long? = null,
)
