package com.localstream.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Routes de navigation de l'application (Navigation Compose).
 * Reprend la cartographie de la Phase 0 : home, search, library, playlists,
 * history, details/{id}, settings.
 */
object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val LIBRARY = "library"
    const val PLAYLISTS = "playlists"
    const val HISTORY = "history"
    const val DETAILS = "details/{id}"
    const val SETTINGS = "settings"

    /** Construit la route Détails pour un identifiant donné. */
    fun details(id: String): String = "details/$id"

    /** Argument attendu par la route [DETAILS]. */
    const val ARG_ID = "id"
}

/**
 * Onglets de la barre de navigation basse.
 * Reproduit `BottomNav.tsx` : Accueil, Bibliothèque, Listes, Historique.
 */
enum class TopLevelDestination(
    val route: String,
    val icon: ImageVector,
    val labelRes: Int,
) {
    HOME(Routes.HOME, Icons.Filled.Home, com.localstream.app.R.string.tab_home),
    LIBRARY(Routes.LIBRARY, Icons.Filled.Movie, com.localstream.app.R.string.tab_library),
    PLAYLISTS(Routes.PLAYLISTS, Icons.AutoMirrored.Filled.PlaylistPlay, com.localstream.app.R.string.tab_playlists),
    HISTORY(Routes.HISTORY, Icons.Filled.History, com.localstream.app.R.string.tab_history),
}
