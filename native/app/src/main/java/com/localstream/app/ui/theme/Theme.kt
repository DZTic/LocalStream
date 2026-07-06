package com.localstream.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * Schéma de couleurs sombre unique de LocalStream (noir / zinc / rouge).
 */
private val LocalStreamColorScheme = darkColorScheme(
    primary = Red600,
    onPrimary = White,
    secondary = Red500,
    onSecondary = White,
    background = Black,
    onBackground = White,
    surface = Zinc900,
    onSurface = White,
    surfaceVariant = Zinc800,
    onSurfaceVariant = Zinc300,
    outline = Zinc500,
)

/**
 * Thème racine à envelopper autour de toute l'UI Compose.
 * Volontairement sans Dynamic Color : l'identité visuelle reste constante.
 */
@Composable
fun LocalStreamTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LocalStreamColorScheme,
        typography = Typography,
        content = content,
    )
}
