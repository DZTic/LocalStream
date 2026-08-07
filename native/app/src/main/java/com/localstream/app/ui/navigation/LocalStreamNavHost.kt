package com.localstream.app.ui.navigation

import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.localstream.app.LocalStreamApplication
import com.localstream.app.domain.model.VideoItem
import com.localstream.app.ui.components.LocalStreamBottomBar
import com.localstream.app.ui.components.YouTubeUrlDialog
import com.localstream.app.ui.home.HomeViewModel
import com.localstream.app.ui.library.LibraryViewModel
import com.localstream.app.ui.permission.StoragePermissions
import com.localstream.app.ui.screens.DetailsScreen
import com.localstream.app.ui.screens.HistoryScreen
import com.localstream.app.ui.screens.HomeScreen
import com.localstream.app.ui.screens.LibraryScreen
import com.localstream.app.ui.screens.PermissionScreen
import com.localstream.app.ui.screens.PlayerScreen
import com.localstream.app.ui.screens.PlaylistsScreen
import com.localstream.app.ui.screens.SearchScreen
import com.localstream.app.ui.screens.SettingsScreen
import com.localstream.app.ui.theme.Black

/**
 * Point d'entrée de l'UI (Phase 9) : gate de permission stockage (Phase 3),
 * [Scaffold] avec barre de navigation basse, et [NavHost] reliant les écrans.
 */
@Suppress("FunctionNaming", "LongMethod", "CyclomaticComplexMethod")
@Composable
fun LocalStreamApp(navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current
    val container = (context.applicationContext as LocalStreamApplication).container

    // ViewModels partagés (scope activité) : bibliothèque + dérivation accueil.
    val libraryViewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.factory(container))
    val homeViewModel: HomeViewModel = viewModel(factory = HomeViewModel.factory(libraryViewModel))

    // Permission stockage : re-vérifiée à chaque retour au premier plan.
    var showYouTubeDialog by remember { mutableStateOf(false) }
    var storageGranted by remember { mutableStateOf(StoragePermissions.hasStoragePermission(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        storageGranted = StoragePermissions.hasStoragePermission(context)
    }

    LaunchedEffect(storageGranted) {
        if (storageGranted) libraryViewModel.refreshLibrary()
    }

    if (!storageGranted) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            PermissionScreen(onPermissionGranted = { storageGranted = true })
        }
        return
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    /** Clic logo : retour accueil + réinitialisation filtres/recherche. */
    val resetToHome: () -> Unit = {
        libraryViewModel.resetFilters()
        navController.navigate(Routes.HOME) {
            popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
            launchSingleTop = true
        }
    }
    val openSearch: () -> Unit = { navController.navigate(Routes.SEARCH) }
    val openSettings: () -> Unit = { navController.navigate(Routes.SETTINGS) }
    val openDetails: (VideoItem) -> Unit = { video ->
        navController.navigate(Routes.details(video.name))
    }
    val openDetailsByName: (String) -> Unit = { name ->
        navController.navigate(Routes.details(name))
    }
    val openPlayer: (VideoItem) -> Unit = { video ->
        navController.navigate(Routes.player(video.name))
    }
    val openPlayerByName: (String) -> Unit = { name ->
        navController.navigate(Routes.player(name))
    }

    if (showYouTubeDialog) {
        YouTubeUrlDialog(
            onDismiss = { showYouTubeDialog = false },
            onPlayYouTube = { url ->
                showYouTubeDialog = false
                openPlayerByName(url)
            },
        )
    }

    Scaffold(
        containerColor = Black,
        bottomBar = {
            // La barre basse n'apparaît que sur les destinations de premier niveau (masquée pendant la lecture).
            val isTopLevel = TopLevelDestination.entries.any { it.route == currentRoute }
            if (isTopLevel) {
                LocalStreamBottomBar(
                    currentRoute = currentRoute,
                    onNavigate = { destination ->
                        navController.navigate(destination.route) {
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
            modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding()),
        ) {
            composable(Routes.HOME) {
                val uiState by homeViewModel.uiState.collectAsStateWithLifecycle()
                HomeScreen(
                    uiState = uiState,
                    onPlay = openPlayer,
                    onOpenDetails = openDetails,
                    onResetProgress = libraryViewModel::resetProgress,
                    onDismissTmdbBanner = libraryViewModel::dismissTmdbBanner,
                    onConfigureTmdb = openSettings,
                    onLogoClick = resetToHome,
                    onSearchClick = openSearch,
                    onSettingsClick = openSettings,
                    onYouTubeClick = { showYouTubeDialog = true },
                )
            }
            composable(Routes.LIBRARY) {
                val uiState by libraryViewModel.uiState.collectAsStateWithLifecycle()
                LibraryScreen(
                    uiState = uiState,
                    onSortBy = libraryViewModel::setSortBy,
                    onFilterGenre = libraryViewModel::setFilterGenre,
                    onFilterResolution = libraryViewModel::setFilterResolution,
                    onOpenDetails = openDetails,
                    onLogoClick = resetToHome,
                    onSearchClick = openSearch,
                    onSettingsClick = openSettings,
                    onYouTubeClick = { showYouTubeDialog = true },
                )
            }
            composable(Routes.SEARCH) {
                val uiState by libraryViewModel.uiState.collectAsStateWithLifecycle()
                val query by libraryViewModel.searchQuery.collectAsStateWithLifecycle()
                SearchScreen(
                    query = query,
                    results = uiState.searchResults,
                    metadata = uiState.metadata,
                    watched = uiState.watched,
                    progress = uiState.progress,
                    onQueryChange = libraryViewModel::onSearchChange,
                    onOpenDetails = openDetails,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.PLAYLISTS) {
                PlaylistsScreen(
                    onLogoClick = resetToHome,
                    onSearchClick = openSearch,
                    onSettingsClick = openSettings,
                    onYouTubeClick = { showYouTubeDialog = true },
                    onOpenDetails = openDetailsByName,
                )
            }
            composable(Routes.HISTORY) {
                HistoryScreen(
                    onLogoClick = resetToHome,
                    onSearchClick = openSearch,
                    onSettingsClick = openSettings,
                    onYouTubeClick = { showYouTubeDialog = true },
                    onOpenDetails = openDetailsByName,
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onLogoClick = resetToHome,
                    onSearchClick = openSearch,
                    onSettingsClick = { /* deja sur les parametres, pas de navigation */ },
                    onYouTubeClick = { showYouTubeDialog = true },
                )
            }
            composable(
                route = Routes.DETAILS,
                arguments = listOf(navArgument(Routes.ARG_ID) { type = NavType.StringType }),
            ) { entry ->
                val argId = entry.arguments?.getString(Routes.ARG_ID).orEmpty()
                DetailsScreen(
                    id = Uri.decode(argId),
                    onBack = { navController.popBackStack() },
                    onPlayVideo = openPlayerByName,
                )
            }
            composable(
                route = Routes.PLAYER,
                arguments = listOf(navArgument(Routes.ARG_ID) { type = NavType.StringType }),
            ) { entry ->
                val argId = entry.arguments?.getString(Routes.ARG_ID).orEmpty()
                PlayerScreen(
                    videoName = Uri.decode(argId),
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
