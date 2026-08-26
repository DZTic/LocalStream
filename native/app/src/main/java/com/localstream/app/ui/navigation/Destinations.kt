package com.localstream.app.ui.navigation

import java.net.URLEncoder
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import com.localstream.app.ui.theme.AppIcons
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Routes de navigation de l'application (Navigation Compose).
 * Reprend la cartographie de la Phase 0 : home, search, library, playlists,
 * history, details/{id}, player/{id}, settings.
 */
object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val LIBRARY = "library"
    const val PLAYLISTS = "playlists"
    const val HISTORY = "history"
    const val DETAILS = "details/{id}"
    const val PLAYER = "player/{id}"
    const val SETTINGS = "settings"

    /** Construit la route Détails pour un identifiant donné. */
    fun details(id: String): String = "details/${encodeRouteParam(id)}"

    /** Construit la route Player pour un identifiant vidéo donné. */
    fun player(id: String): String = "player/${encodeRouteParam(id)}"

    /** Argument attendu par les routes [DETAILS] et [PLAYER]. */
    const val ARG_ID = "id"

    private fun encodeRouteParam(param: String): String =
        runCatching { URLEncoder.encode(param, "UTF-8").replace("+", "%20") }.getOrDefault(param)
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
    LIBRARY(Routes.LIBRARY, AppIcons.Movie, com.localstream.app.R.string.tab_library),
    PLAYLISTS(Routes.PLAYLISTS, AppIcons.PlaylistPlay, com.localstream.app.R.string.tab_playlists),
    HISTORY(Routes.HISTORY, AppIcons.History, com.localstream.app.R.string.tab_history),
}
