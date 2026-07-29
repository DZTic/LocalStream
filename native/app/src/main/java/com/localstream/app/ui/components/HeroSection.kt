package com.localstream.app.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.localstream.app.domain.TitleCleaner
import com.localstream.app.domain.VideoUiSelectors
import com.localstream.app.domain.model.TmdbMetadata
import com.localstream.app.domain.model.VideoItem
import com.localstream.app.ui.theme.White
import com.localstream.app.ui.theme.Zinc300
import com.localstream.app.ui.theme.Zinc500
import com.localstream.app.ui.theme.Zinc800
import kotlinx.coroutines.delay

/** Rotation du hero toutes les 10 s (comme le web). */
private const val HERO_ROTATION_MS = 10_000L
private const val HERO_HEIGHT_FRACTION = 0.62f
private const val HERO_FADE_MS = 700

/**
 * Hero plein écran de l'accueil (réf. `HomeScreen.tsx` + `hero.ts`) : backdrop
 * TMDB, dégradés noirs (bas + gauche), titre nettoyé, synopsis, boutons
 * "Lecture" et "Plus d'infos".
 *
 * La rotation automatique est interne au composable : son état est local, donc
 * le tick des 10 s ne recompose ni les rows ni le reste de l'écran.
 */
@Composable
fun HeroSection(
    candidates: List<VideoItem>,
    metadata: Map<String, TmdbMetadata>,
    onPlay: (VideoItem) -> Unit,
    onOpenDetails: (VideoItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (candidates.isEmpty()) return

    var heroIndex by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(candidates.size) {
        if (candidates.size <= 1) return@LaunchedEffect
        while (true) {
            delay(HERO_ROTATION_MS)
            heroIndex += 1
        }
    }

    val hero = candidates[heroIndex % candidates.size]
    val heroHeight = LocalConfiguration.current.screenHeightDp.dp * HERO_HEIGHT_FRACTION

    Crossfade(
        targetState = hero,
        animationSpec = tween(durationMillis = HERO_FADE_MS),
        modifier = modifier
            .fillMaxWidth()
            .height(heroHeight),
        label = "hero-crossfade",
    ) { current ->
        HeroContent(
            hero = current,
            metadata = metadata[VideoUiSelectors.metadataKey(current)],
            onPlay = { onPlay(current) },
            onOpenDetails = { onOpenDetails(current) },
        )
    }
}

@Composable
private fun HeroContent(
    hero: VideoItem,
    metadata: TmdbMetadata?,
    onPlay: () -> Unit,
    onOpenDetails: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        val imageUrl = metadata?.backdropUrl() ?: metadata?.posterUrl()
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = hero.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Zinc800),
            )
        }

        // Dégradés noirs : bas (vertical) + gauche (horizontal), comme le web.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color.Black, Color.Black.copy(alpha = 0.6f), Color.Transparent),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, end = 16.dp, bottom = 48.dp)
                .widthIn(max = 560.dp),
        ) {
            Text(
                text = TitleCleaner.getCleanTitle(hero.name),
                color = White,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            metadata?.overview?.takeIf { it.isNotBlank() }?.let { overview ->
                Text(
                    text = overview,
                    color = Zinc300,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 16.dp),
            ) {
                Button(
                    onClick = onPlay,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = White,
                        contentColor = Color.Black,
                    ),
                    shape = RoundedCornerShape(4.dp),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Text(
                        text = "Lecture",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
                Button(
                    onClick = onOpenDetails,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Zinc500.copy(alpha = 0.7f),
                        contentColor = White,
                    ),
                    shape = RoundedCornerShape(4.dp),
                ) {
                    Icon(Icons.Filled.Info, contentDescription = null)
                    Text(
                        text = "Plus d'infos",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
        }
    }
}
