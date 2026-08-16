package com.localstream.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localstream.app.domain.model.VideoItem
import com.localstream.app.ui.components.HeroSection
import com.localstream.app.ui.components.TopBar
import com.localstream.app.ui.components.VideoRow
import com.localstream.app.ui.home.HomeUiState
import com.localstream.app.ui.theme.Red600
import com.localstream.app.ui.theme.White
import com.localstream.app.ui.theme.Zinc500
import com.localstream.app.ui.theme.Zinc900

/** Seuil de scroll (px) au-delà duquel la TopBar devient opaque. */
private const val TOPBAR_SOLID_OFFSET_PX = 80

/**
 * Écran d'accueil (Phase 7, réf. `HomeScreen.tsx`) : hero rotatif puis rows
 * "Continuer la lecture", "Nouveautés", "Recommandations", "Séries", "Films",
 * "De A à Z".
 */
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onPlay: (VideoItem) -> Unit,
    onOpenDetails: (VideoItem) -> Unit,
    onResetProgress: (String) -> Unit,
    onDismissTmdbBanner: () -> Unit,
    onConfigureTmdb: () -> Unit,
    onLogoClick: () -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onYouTubeClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val topBarSolid by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 ||
                listState.firstVisibleItemScrollOffset > TOPBAR_SOLID_OFFSET_PX
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            uiState.isLoading -> LoadingContent(Modifier.align(Alignment.Center))
            !uiState.hasContent -> EmptyLibraryContent(Modifier.align(Alignment.Center))
            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "hero", contentType = "hero") {
                    HeroSection(
                        candidates = uiState.heroCandidates,
                        metadata = uiState.metadata,
                        onPlay = onPlay,
                        onOpenDetails = onOpenDetails,
                    )
                }
                if (uiState.showTmdbBanner) {
                    item(key = "tmdb_banner", contentType = "banner") {
                        TmdbBanner(
                            onConfigure = onConfigureTmdb,
                            onDismiss = onDismissTmdbBanner,
                            modifier = Modifier.offset(y = (-24).dp),
                        )
                    }
                }
                if (uiState.continueWatching.isNotEmpty()) {
                    item(key = "row_continue_watching", contentType = "row") {
                        HomeRow(
                            title = "Continuer la lecture",
                            items = uiState.continueWatching,
                            uiState = uiState,
                            showResetProgress = true,
                            onOpenDetails = onOpenDetails,
                            onResetProgress = onResetProgress,
                            modifier = Modifier.offset(y = (-24).dp),
                        )
                    }
                }
                item(key = "row_recent", contentType = "row") {
                    HomeRow(
                        title = "Nouveautés",
                        items = uiState.recentAdditions,
                        uiState = uiState,
                        showResetProgress = false,
                        onOpenDetails = onOpenDetails,
                        onResetProgress = onResetProgress,
                        modifier = Modifier.offset(y = (-24).dp),
                    )
                }
                item(key = "row_recommendations", contentType = "row") {
                    HomeRow(
                        title = "Recommandations",
                        items = uiState.recommendations,
                        uiState = uiState,
                        showResetProgress = false,
                        onOpenDetails = onOpenDetails,
                        onResetProgress = onResetProgress,
                        modifier = Modifier.offset(y = (-24).dp),
                    )
                }
                item(key = "row_series", contentType = "row") {
                    HomeRow(
                        title = "Séries",
                        items = uiState.series,
                        uiState = uiState,
                        showResetProgress = false,
                        onOpenDetails = onOpenDetails,
                        onResetProgress = onResetProgress,
                        modifier = Modifier.offset(y = (-24).dp),
                    )
                }
                item(key = "row_movies", contentType = "row") {
                    HomeRow(
                        title = "Films",
                        items = uiState.movies,
                        uiState = uiState,
                        showResetProgress = false,
                        onOpenDetails = onOpenDetails,
                        onResetProgress = onResetProgress,
                        modifier = Modifier.offset(y = (-24).dp),
                    )
                }
                item(key = "row_alphabetical", contentType = "row") {
                    HomeRow(
                        title = "De A à Z",
                        items = uiState.alphabetical,
                        uiState = uiState,
                        showResetProgress = false,
                        onOpenDetails = onOpenDetails,
                        onResetProgress = onResetProgress,
                        modifier = Modifier.offset(y = (-24).dp),
                    )
                }
                item(key = "bottom_spacer", contentType = "spacer") {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }

        TopBar(
            solid = topBarSolid || !uiState.hasContent,
            showSearch = uiState.hasContent,
            isFetchingMetadata = uiState.isFetchingMetadata,
            onLogoClick = onLogoClick,
            onSearchClick = onSearchClick,
            onSettingsClick = onSettingsClick,
            onYouTubeClick = onYouTubeClick,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

@Composable
private fun HomeRow(
    title: String,
    items: List<VideoItem>,
    uiState: HomeUiState,
    showResetProgress: Boolean,
    onOpenDetails: (VideoItem) -> Unit,
    onResetProgress: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    VideoRow(
        title = title,
        items = items,
        metadata = uiState.metadata,
        watched = uiState.watched,
        progress = uiState.progress,
        showResetProgress = showResetProgress,
        onOpenDetails = onOpenDetails,
        onResetProgress = onResetProgress,
        modifier = modifier,
    )
}

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    CircularProgressIndicator(color = Red600, modifier = modifier)
}

/** Bibliothèque vide après scan (équivalent de l'écran d'accueil sans vidéos du web). */
@Composable
private fun EmptyLibraryContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Vos films et séries.",
            color = White,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Aucune vidéo trouvée sur l'appareil. Ajoutez des vidéos dans vos dossiers (Movies, Download…) puis relancez l'application.",
            color = Zinc500,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

/** Bannière d'onboarding TMDB (réf. `HomeScreen.tsx`) : guide vers les réglages. */
@Composable
private fun TmdbBanner(
    onConfigure: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp)
            .background(Zinc900, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = Icons.Outlined.Image,
            contentDescription = null,
            tint = Red600,
            modifier = Modifier.padding(top = 2.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Text(
                text = "Ajoutez les affiches et synopsis",
                color = White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            )
            Text(
                text = "Configurez une clé API TMDB (gratuite) pour récupérer automatiquement les affiches, résumés et regroupements en sagas.",
                color = Zinc500,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            Button(
                onClick = onConfigure,
                colors = ButtonDefaults.buttonColors(containerColor = Red600, contentColor = White),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(text = "Configurer TMDB", fontWeight = FontWeight.Black, fontSize = 12.sp)
            }
        }
        IconButton(onClick = onDismiss) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Masquer cette bannière",
                tint = Zinc500,
            )
        }
    }
}
