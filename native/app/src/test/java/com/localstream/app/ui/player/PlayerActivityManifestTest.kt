package com.localstream.app.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PlayerActivityManifestTest {

    @Test
    fun playerActivityIsDeclaredWithSensorLandscapeAndPipInManifest() {
        val manifestFile = File("src/main/AndroidManifest.xml")
        assertTrue("AndroidManifest.xml doit exister", manifestFile.exists())
        val manifestContent = manifestFile.readText()

        assertTrue(
            "PlayerActivity doit être déclarée dans le manifest",
            manifestContent.contains("""android:name=".ui.player.PlayerActivity"""")
        )
        assertTrue(
            "PlayerActivity doit être configurée en sensorLandscape dans le manifest",
            manifestContent.contains("""android:screenOrientation="sensorLandscape"""")
        )
        assertTrue(
            "PlayerActivity doit supporter le Picture-in-Picture dans le manifest",
            manifestContent.contains("""android:supportsPictureInPicture="true"""")
        )
    }

    @Test
    fun playerActivityConstantsAreValid() {
        assertEquals("extra_video_name", PlayerActivity.EXTRA_VIDEO_NAME)
    }
}
