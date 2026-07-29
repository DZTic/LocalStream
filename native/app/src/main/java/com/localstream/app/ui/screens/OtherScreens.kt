package com.localstream.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Écrans placeholder restants du socle. Ils seront remplacés par les
 * implémentations réelles lors des phases suivantes (Phase 8 : fiche détail,
 * épisodes, playlists, historique, paramètres).
 */

@Composable
fun PlaylistsScreen(modifier: Modifier = Modifier) =
    PlaceholderScreen(title = "Listes", modifier = modifier)

@Composable
fun HistoryScreen(modifier: Modifier = Modifier) =
    PlaceholderScreen(title = "Historique", modifier = modifier)

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) =
    PlaceholderScreen(title = "Paramètres", modifier = modifier)

@Composable
fun DetailsScreen(id: String, modifier: Modifier = Modifier) =
    PlaceholderScreen(
        title = "Détails",
        subtitle = "id = $id",
        modifier = modifier,
    )
