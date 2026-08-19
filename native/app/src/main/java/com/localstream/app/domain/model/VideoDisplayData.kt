package com.localstream.app.domain.model

import androidx.compose.runtime.Immutable

/**
 * Regroupe les données dynamiques d'affichage vidéo (métadonnées TMDB, visionnage, progression)
 * dans une structure @Immutable pour garantir la stabilité Compose et permettre le skipping
 * des composants VideoGrid, VideoRow et VideoCard.
 */
@Immutable
data class VideoDisplayData(
    val metadata: Map<String, TmdbMetadata> = emptyMap(),
    val watched: Map<String, Boolean> = emptyMap(),
    val progress: Map<String, Double> = emptyMap(),
)
