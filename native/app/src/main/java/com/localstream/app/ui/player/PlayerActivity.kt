package com.localstream.app.ui.player

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.localstream.app.ui.theme.LocalStreamTheme

/**
 * Activité dédiée à la lecture vidéo en plein écran paysage (sensorLandscape).
 *
 * Évite l'IPC synchrone [Activity.setRequestedOrientation] (~80 ms)
 * au démarrage et à la sortie du lecteur, ainsi que la reconfiguration
 * de [com.localstream.app.MainActivity].
 */
class PlayerActivity : ComponentActivity() {

    private var currentVideoName by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        currentVideoName = intent.getStringExtra(EXTRA_VIDEO_NAME).orEmpty()
        setContent {
            LocalStreamTheme {
                key(currentVideoName) {
                    PlayerScreen(
                        videoName = currentVideoName,
                        onBack = { finish() },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(EXTRA_VIDEO_NAME)?.let {
            currentVideoName = it
        }
    }

    companion object {
        const val EXTRA_VIDEO_NAME = "extra_video_name"

        fun createIntent(context: Context, videoName: String): Intent {
            return Intent(context, PlayerActivity::class.java).apply {
                putExtra(EXTRA_VIDEO_NAME, videoName)
                if (context !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
        }
    }
}
