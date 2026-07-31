package com.localstream.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.localstream.app.ui.navigation.LocalStreamApp
import com.localstream.app.ui.theme.LocalStreamTheme

/**
 * Activité unique de l'app native. Héberge toute l'UI Compose sous [LocalStreamTheme].
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            LocalStreamTheme {
                LocalStreamApp()
            }
        }
    }
}
