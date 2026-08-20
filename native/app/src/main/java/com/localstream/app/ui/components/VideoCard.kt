package com.localstream.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.localstream.app.domain.Formatters
import com.localstream.app.domain.VideoUiSelectors
import com.localstream.app.domain.model.VideoItem
import com.localstream.app.ui.theme.Red600
import com.localstream.app.ui.theme.Zinc500
import com.localstream.app.ui.theme.Zinc800

private val WatchedGreen = Color(0xFF16A34A) // green-600 Tailwind
private val ProgressTrack = Color(0xFF52525B) // zinc-600 Tailwind
private val CardShape = RoundedCornerShape(6.dp)
private val BadgeShape = RoundedCornerShape(4.dp)

/**
 * Carte d'affiche vidéo (équivalent Compose de `VideoCard.tsx`) :
 * vignette 2/3, badge résolution, badge Série/Saga, coche verte "vu",
 * barre de progression rouge, bouton reset de progression optionnel.
 *
 * Les contenus vus sont atténués (opacité + désaturation), comme sur le web.
 */
@Suppress("LongParameterList", "LongMethod", "FunctionNaming")
@Composable
fun VideoCard(
    video: VideoItem,
    posterUrl: String?,
    isWatched: Boolean,
    progress: Double,
    showResetProgress: Boolean,
    onClick: () -> Unit,
    onResetProgress: () -> Unit,
    modifier: Modifier = Modifier,
    episodeLabel: String? = null,
) {
    val title = remember(video.name, video.seriesName, video.isSeriesGroup, video.cleanTitle) {
        VideoUiSelectors.displayTitle(video)
    }
    val resolution = remember(video.name) {
        Formatters.getResolution(video.name)
    }

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(CardShape)
                .background(Zinc800)
                .clickable(onClick = onClick),
        ) {
            PosterImage(
                posterUrl = posterUrl,
                title = title,
                isWatched = isWatched,
            )

            // Badge type (Série / Saga) — coin haut gauche.
            if (video.isSeriesGroup) {
                val badgeText = if (video.isTvSeries) "Série" else "Saga"
                Badge(
                    text = badgeText,
                    background = Red600,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                )
            }

            // Coche "vu" — coin haut droit.
            if (isWatched) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(18.dp)
                        .background(WatchedGreen, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Vu",
                        tint = Color.White,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }

            // Badge résolution — coin bas gauche.
            if (resolution.isNotEmpty()) {
                Badge(
                    text = resolution,
                    background = Color.Black.copy(alpha = 0.7f),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp),
                )
            }

            // Bouton reset progression — coin bas droit (row "Continuer la lecture").
            if (showResetProgress && progress > 0.0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .clickable(onClick = onResetProgress),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Replay,
                        contentDescription = "Reprendre à zéro",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            // Barre de progression rouge — bas de la vignette.
            if (progress > 0.0 && progress < 100.0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(ProgressTrack),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth((progress / 100.0).toFloat().coerceIn(0f, 1f))
                            .height(4.dp)
                            .background(Red600),
                    )
                }
            }
        }

        // Titre sous la vignette.
        Text(
            text = title,
            color = Zinc500,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp, top = 6.dp),
        )

        if (video.isSeriesGroup && !episodeLabel.isNullOrEmpty()) {
            Text(
                text = episodeLabel,
                color = if (episodeLabel.startsWith("En cours")) Red600 else Zinc500,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp),
            )
        }
    }
}

/** Affiche Coil, ou placeholder zinc-800 avec le titre nettoyé (comme le web). */
@Suppress("FunctionNaming")
@Composable
private fun PosterImage(
    posterUrl: String?,
    title: String,
    isWatched: Boolean,
) {
    if (posterUrl != null) {
        val context = LocalContext.current
        val imageRequest = remember(posterUrl) {
            ImageRequest.Builder(context)
                .data(posterUrl)
                .size(width = 300, height = 450)
                .precision(Precision.INEXACT)
                .memoryCacheKey(posterUrl)
                .diskCacheKey(posterUrl)
                .crossfade(false)
                .build()
        }
        AsyncImage(
            model = imageRequest,
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = if (isWatched) 0.5f else 1f },
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = if (isWatched) 0.5f else 1f }
                .padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = title,
                color = Zinc500,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun Badge(
    text: String,
    background: Color,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text.uppercase(),
        color = Color.White,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .background(background, BadgeShape)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
