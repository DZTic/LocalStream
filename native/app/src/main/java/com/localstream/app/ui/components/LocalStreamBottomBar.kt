package com.localstream.app.ui.components

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.localstream.app.ui.navigation.TopLevelDestination
import com.localstream.app.ui.theme.Red500
import com.localstream.app.ui.theme.Zinc500

/**
 * Barre de navigation basse (4 onglets), équivalent Compose de `BottomNav.tsx`.
 * L'onglet actif est teinté en rouge, les autres en zinc.
 */
@Composable
fun LocalStreamBottomBar(
    currentRoute: String?,
    onNavigate: (TopLevelDestination) -> Unit,
) {
    NavigationBar {
        TopLevelDestination.entries.forEach { destination ->
            val selected = currentRoute == destination.route
            val label = stringResource(destination.labelRes)
            NavigationBarItem(
                selected = selected,
                onClick = { onNavigate(destination) },
                icon = { Icon(destination.icon, contentDescription = label) },
                label = { Text(label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Red500,
                    selectedTextColor = Red500,
                    unselectedIconColor = Zinc500,
                    unselectedTextColor = Zinc500,
                ),
            )
        }
    }
}
