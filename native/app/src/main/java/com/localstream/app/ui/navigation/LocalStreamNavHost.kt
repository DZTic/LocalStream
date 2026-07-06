package com.localstream.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.localstream.app.ui.components.LocalStreamBottomBar
import com.localstream.app.ui.screens.DetailsScreen
import com.localstream.app.ui.screens.HistoryScreen
import com.localstream.app.ui.screens.HomeScreen
import com.localstream.app.ui.screens.LibraryScreen
import com.localstream.app.ui.screens.PlaylistsScreen
import com.localstream.app.ui.screens.SearchScreen
import com.localstream.app.ui.screens.SettingsScreen

/**
 * Point d'entrée de l'UI : un [Scaffold] avec barre de navigation basse et le
 * [NavHost] contenant tous les écrans placeholder du socle.
 */
@Composable
fun LocalStreamApp(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            // La barre basse n'apparaît que sur les destinations de premier niveau.
            val isTopLevel = TopLevelDestination.entries.any { it.route == currentRoute }
            if (isTopLevel) {
                LocalStreamBottomBar(
                    currentRoute = currentRoute,
                    onNavigate = { destination ->
                        navController.navigate(destination.route) {
                            // Onglets : un seul écran par onglet dans la pile, état restauré.
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onOpenSearch = { navController.navigate(Routes.SEARCH) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpenDetails = { id -> navController.navigate(Routes.details(id)) },
                )
            }
            composable(Routes.LIBRARY) { LibraryScreen() }
            composable(Routes.PLAYLISTS) { PlaylistsScreen() }
            composable(Routes.HISTORY) { HistoryScreen() }
            composable(Routes.SEARCH) { SearchScreen() }
            composable(Routes.SETTINGS) { SettingsScreen() }
            composable(
                route = Routes.DETAILS,
                arguments = listOf(navArgument(Routes.ARG_ID) { type = NavType.StringType }),
            ) { entry ->
                DetailsScreen(id = entry.arguments?.getString(Routes.ARG_ID).orEmpty())
            }
        }
    }
}
