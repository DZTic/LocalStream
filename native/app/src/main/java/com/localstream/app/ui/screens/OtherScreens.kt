package com.localstream.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Écrans placeholder du socle. Ils réutilisent [PlaceholderScreen] et seront
 * remplacés par les implémentations réelles au fil des phases de migration.
 */

@Composable
fun LibraryScreen(modifier: Modifier = Modifier) =
    PlaceholderScreen(title = "Bibliothèque", modifier = modifier)

@Composable
fun PlaylistsScreen(modifier: Modifier = Modifier) =
    PlaceholderScreen(title = "Listes", modifier = modifier)

@Composable
fun HistoryScreen(modifier: Modifier = Modifier) =
    PlaceholderScreen(title = "Historique", modifier = modifier)

@Composable
fun SearchScreen(modifier: Modifier = Modifier) =
    PlaceholderScreen(title = "Recherche", modifier = modifier)

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
