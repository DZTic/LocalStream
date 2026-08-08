package com.localstream.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.localstream.app.domain.model.TmdbMetadata
import com.localstream.app.domain.model.VideoItem
import com.localstream.app.ui.components.VideoGrid
import com.localstream.app.ui.theme.Red600
import com.localstream.app.ui.theme.White
import com.localstream.app.ui.theme.Zinc500
import com.localstream.app.ui.theme.Zinc800
import com.localstream.app.ui.theme.Zinc900

/**
 * Écran de recherche (Phase 7, réf. `SearchScreen.tsx`) : champ dans la barre
 * supérieure (focus automatique), filtrage insensible à la casse appliqué par
 * le ViewModel sur les groupes filtrés/triés, grille adaptative, état vide.
 */
@Composable
fun SearchScreen(
    query: String,
    results: List<VideoItem>,
    metadata: Map<String, TmdbMetadata>,
    watched: Map<String, Boolean>,
    progress: Map<String, Double>,
    onQueryChange: (String) -> Unit,
    onOpenDetails: (VideoItem) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        SearchTopBar(
            query = query,
            onQueryChange = onQueryChange,
            onBack = onBack,
        )

        val trimmedQuery = query.trim()
        val isUrl = trimmedQuery.startsWith("http://") || trimmedQuery.startsWith("https://") ||
                trimmedQuery.contains("youtube.com") || trimmedQuery.contains("youtu.be")

        if (isUrl) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Zinc900),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clickable {
                        onOpenDetails(VideoItem(name = trimmedQuery, url = trimmedQuery))
                    },
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = "Lancer",
                        tint = Red600,
                        modifier = Modifier.size(32.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (trimmedQuery.contains("youtube") || trimmedQuery.contains("youtu.be")) "Lancer la vidéo YouTube" else "Lancer le flux vidéo",
                            color = White,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = trimmedQuery,
                            color = Zinc500,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        if (query.isNotBlank()) {
            Text(
                text = "Résultats pour \"$query\"",
                color = White,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }

        if (query.isBlank()) {
            EmptySearchHint()
        } else if (results.isEmpty()) {
            Text(
                text = "Aucun résultat trouvé pour \"$query\"",
                color = Zinc500,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .padding(32.dp)
                    .align(Alignment.CenterHorizontally),
            )
        } else {
            VideoGrid(
                videos = results,
                metadata = metadata,
                watched = watched,
                progress = progress,
                onOpenDetails = onOpenDetails,
            )
        }
    }
}

@Composable
private fun SearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Retour",
                tint = White,
            )
        }
        TextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("Rechercher un titre...", color = Zinc500) },
            leadingIcon = {
                Icon(Icons.Filled.Search, contentDescription = null, tint = White)
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Filled.Close, contentDescription = "Effacer", tint = White)
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            shape = CircleShape,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Zinc900,
                unfocusedContainerColor = Zinc800,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = White,
                focusedTextColor = White,
                unfocusedTextColor = White,
            ),
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
                .focusRequester(focusRequester),
        )
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

/** Invitation à saisir quand la recherche est vide. */
@Composable
private fun EmptySearchHint() {
    Text(
        text = "Recherchez un film ou une série par son titre.",
        color = Zinc500,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(32.dp),
    )
}
