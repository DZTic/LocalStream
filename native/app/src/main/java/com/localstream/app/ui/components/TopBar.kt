package com.localstream.app.ui.components

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localstream.app.R
import com.localstream.app.ui.theme.Red600
import com.localstream.app.ui.theme.White

/**
 * Barre sup?rieure (?quivalent Compose de `AppHeader.tsx`) : logo "LOCALSTREAM"
 * rouge (retour accueil + reset des filtres), indicateur de chargement TMDB,
 * recherche, r?glages.
 *
 * [solid] : fond noir opaque (?crans Recherche/Biblioth?que) ; sinon d?grad?
 * noir ? transparent au-dessus du hero, qui devient opaque au scroll (g?r? par
 * l'appelant via [solid] d?riv? du LazyListState).
 */
@Composable
fun TopBar(
    solid: Boolean,
    showSearch: Boolean,
    isFetchingMetadata: Boolean,
    onLogoClick: () -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onYouTubeClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val background = if (solid) {
        Modifier.background(Color.Black)
    } else {
        Modifier.background(
            Brush.verticalGradient(
                colors = listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent),
            ),
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(background)
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
                SpinningRefreshIcon()
            }
            if (onYouTubeClick != null) {
                IconButton(onClick = onYouTubeClick) {
                    Icon(
                        imageVector = Icons.Filled.PlayCircle,
                        contentDescription = "Vid?o YouTube",
                        tint = Red600,
                    )
                }
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

/** Indicateur de r?cup?ration TMDB en cours (RefreshCw anim? du web). */
@Composable
private fun SpinningRefreshIcon() {
    val transition = rememberInfiniteTransition(label = "tmdb-refresh")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(durationMillis = 1000)),
        label = "rotation",
    )
    Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
        Icon(
            imageVector = Icons.Filled.Refresh,
            contentDescription = "R?cup?ration des m?tadonn?es TMDB en cours",
            tint = Red600,
            modifier = Modifier
                .size(18.dp)
                .rotate(angle),
        )
    }
}
