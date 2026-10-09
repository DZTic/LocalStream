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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import com.localstream.app.domain.TitleCleaner
import com.localstream.app.domain.VideoUiSelectors
import com.localstream.app.domain.model.TmdbMetadata
import com.localstream.app.domain.model.VideoItem
import com.localstream.app.ui.theme.White
import com.localstream.app.ui.theme.Zinc300
import com.localstream.app.ui.theme.Zinc500
import com.localstream.app.ui.theme.Zinc800
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/** Rotation du hero toutes les 10 s (comme le web). */
private const val HERO_ROTATION_MS = 10_000L
private const val HERO_HEIGHT_FRACTION = 0.62f
private const val HERO_FADE_MS = 700

private val HeroVerticalGradient = Brush.verticalGradient(
    colors = listOf(Color.Transparent, Color.Black),
)
private val HeroHorizontalGradient = Brush.horizontalGradient(
    colors = listOf(Color.Black, Color.Black.copy(alpha = 0.6f), Color.Transparent),
)

/**
 * Hero plein écran de l'accueil (réf. `HomeScreen.tsx` + `hero.ts`) : backdrop
 * TMDB, dégradés noirs (bas + gauche), titre nettoyé, synopsis, boutons
 * "Lecture" et "Plus d'infos".
 *
 * La rotation automatique est interne au composable : son état est local, donc
 * le tick des 10 s ne recompose ni les rows ni le reste de l'écran. Elle ne tourne
 * que lorsque l'écran est au premier plan (RESUMED) et attend la fin d'un éventuel
 * défilement ([isScrolling]).
 *
 * Optimisation rotation (#240, #246) :
 * - Préchargement anticipé du prochain backdrop en mémoire cache (Coil) pour éliminer le décodage bloquant.
 * - Sortie des calques statiques (dégradés, boutons d'action) du Crossfade pour alléger l'arbre de composition
 *   et diviser par deux le coût de mesure/layout par rotation.
 */
@Composable
fun HeroSection(
    candidates: List<VideoItem>,
    metadata: Map<String, TmdbMetadata>,
    onPlay: (VideoItem) -> Unit,
    onOpenDetails: (VideoItem) -> Unit,
    modifier: Modifier = Modifier,
    isScrolling: () -> Boolean = { false },
) {
    if (candidates.isEmpty()) return

    var heroIndex by rememberSaveable { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentIsScrolling by rememberUpdatedState(isScrolling)
    val currentOnPlay by rememberUpdatedState(onPlay)
    val currentOnOpenDetails by rememberUpdatedState(onOpenDetails)

    LaunchedEffect(candidates.size, lifecycleOwner) {
        if (candidates.size <= 1) return@LaunchedEffect
        // La composition reste active quand l'app passe en arrière-plan : sans ce garde-fou,
        // chaque tick recomposait et décodait un backdrop pour un écran invisible.
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(HERO_ROTATION_MS)
                // Le changement de hero coûte une image longue (~25 ms) : jamais pendant un scroll.
                snapshotFlow { currentIsScrolling() }.first { !it }
                heroIndex += 1
            }
        }
    }

    val hero = candidates[heroIndex % candidates.size]
    val configuration = LocalConfiguration.current
    val heroHeight = remember(configuration.screenHeightDp) {
        configuration.screenHeightDp.dp * HERO_HEIGHT_FRACTION
    }

    // Préchargement anticipé du prochain backdrop en cache mémoire Coil (#240, #246)
    val nextImageUrl = resolveNextBackdropUrl(candidates, metadata, heroIndex)
    val context = LocalContext.current
    LaunchedEffect(nextImageUrl) {
        if (nextImageUrl != null) {
            val prefetchRequest = ImageRequest.Builder(context)
                .data(nextImageUrl)
                .memoryCacheKey(nextImageUrl)
                .diskCacheKey(nextImageUrl)
                .allowHardware(true)
                .crossfade(false)
                .build()
            context.imageLoader.enqueue(prefetchRequest)
        }
    }

    val currentImageUrl = resolveCurrentBackdropUrl(hero, metadata)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(heroHeight),
    ) {
        // 1. Image de fond avec transition fluide
        Crossfade(
            targetState = currentImageUrl,
            animationSpec = tween(durationMillis = HERO_FADE_MS),
            modifier = Modifier.fillMaxSize(),
            label = "hero-backdrop-crossfade",
        ) { imageUrl ->
            if (imageUrl != null) {
                val imageRequest = remember(imageUrl) {
                    ImageRequest.Builder(context)
                        .data(imageUrl)
                        .memoryCacheKey(imageUrl)
                        .diskCacheKey(imageUrl)
                        .allowHardware(true)
                        .crossfade(false)
                        .build()
                }
                AsyncImage(
                    model = imageRequest,
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
        }

        // 2. Dégradés noirs : bas (vertical) + gauche (horizontal), comme le web.
        // Statiques et partagés : évite d'instancier et mesurer deux fois ces calques pendant le Crossfade.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(HeroVerticalGradient),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(HeroHorizontalGradient),
        )

        // 3. Titre, synopsis et boutons d'actions
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, end = 16.dp, bottom = 48.dp)
                .widthIn(max = 560.dp),
        ) {
            Crossfade(
                targetState = hero,
                animationSpec = tween(durationMillis = HERO_FADE_MS),
                label = "hero-text-crossfade",
            ) { currentHero ->
                val currentMeta = metadata[VideoUiSelectors.metadataKey(currentHero)]
                val title = remember(currentHero.name) { TitleCleaner.getCleanTitle(currentHero.name) }
                Column {
                    Text(
                        text = title,
                        color = White,
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )

                    currentMeta?.overview?.takeIf { it.isNotBlank() }?.let { overview ->
                        Text(
                            text = overview,
                            color = Zinc300,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }

            // Boutons d'action : statiques et hors du Crossfade pour ne pas instancier ni mesurer 4 boutons Material3 à chaque rotation.
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 16.dp),
            ) {
                Button(
                    onClick = { currentOnPlay(hero) },
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
                    onClick = { currentOnOpenDetails(hero) },
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

internal fun resolveNextBackdropUrl(
    candidates: List<VideoItem>,
    metadata: Map<String, TmdbMetadata>,
    currentIndex: Int,
): String? {
    if (candidates.size <= 1) return null
    val nextHero = candidates[(currentIndex + 1) % candidates.size]
    return resolveCurrentBackdropUrl(nextHero, metadata)
}

internal fun resolveCurrentBackdropUrl(
    hero: VideoItem,
    metadata: Map<String, TmdbMetadata>,
): String? {
    val meta = metadata[VideoUiSelectors.metadataKey(hero)]
    return meta?.backdropUrl() ?: meta?.posterUrl()
}
