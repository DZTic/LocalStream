package com.localstream.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localstream.app.R
import com.localstream.app.ui.theme.Red600
import com.localstream.app.ui.theme.White

private val TopBarGradient = Brush.verticalGradient(
    colors = listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent),
)

/**
 * Barre supérieure (équivalent Compose de `AppHeader.tsx`) : logo "LOCALSTREAM"
 * rouge (retour accueil + reset des filtres), indicateur de chargement TMDB,
 * recherche, réglages.
 *
 * [solid] : fond noir opaque (écrans Recherche/Bibliothèque).
 * [backgroundAlphaProvider] : lambda retournant l'opacité du fond noir lue uniquement
 * en phase de dessin GPU (graphicsLayer), sans déclencher de recomposition Compose au scroll.
 */
@Composable
fun TopBar(
    solid: Boolean,
    showSearch: Boolean,
    isFetchingMetadata: Boolean,
    onLogoClick: () -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundAlphaProvider: () -> Float,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        // Dégradé de base sous le hero quand la top bar n'est pas totalement opaque (phase de dessin)
        if (!solid) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        alpha = (1f - backgroundAlphaProvider()).coerceIn(0f, 1f)
                    }
                    .background(TopBarGradient),
            )
        }

        // Fond noir opaque ou progressif via graphicsLayer (évite de réallouer / recomposer le contenu)
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    alpha = if (solid) 1f else backgroundAlphaProvider().coerceIn(0f, 1f)
                }
                .background(Color.Black),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "LOCALSTREAM",
                color = Red600,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.5).sp,
                modifier = Modifier.clickable(onClick = onLogoClick),
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isFetchingMetadata) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(horizontal = 11.dp),
                        color = Red600,
                    )
                }
                if (showSearch) {
                    IconButton(onClick = onSearchClick) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "Rechercher",
                            tint = White,
                        )
                    }
                }
                IconButton(onClick = onSettingsClick) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = stringResource(R.string.settings_title),
                        tint = White,
                    )
                }
            }
        }
    }
}

/** Surcharge de compatibilité recevant un Float statique. */
@Composable
fun TopBar(
    solid: Boolean,
    showSearch: Boolean,
    isFetchingMetadata: Boolean,
    onLogoClick: () -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundAlpha: Float = if (solid) 1f else 0f,
) {
    TopBar(
        solid = solid,
        showSearch = showSearch,
        isFetchingMetadata = isFetchingMetadata,
        onLogoClick = onLogoClick,
        onSearchClick = onSearchClick,
        onSettingsClick = onSettingsClick,
        modifier = modifier,
        backgroundAlphaProvider = { backgroundAlpha },
    )
}
