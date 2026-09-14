package com.localstream.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BaselineProfileTest {

    private val profileFiles = listOf(
        File("src/main/baseline-prof.txt"),
        File("src/main/baselineProfiles/baseline-prof.txt"),
    )

    @Test
    fun baselineProfileFileExistsAndIsNotEmpty() {
        for (file in profileFiles) {
            assertTrue("${file.path} doit exister", file.exists())
            val lines = file.readLines().filter { it.isNotBlank() }
            assertFalse("${file.path} ne doit pas etre vide", lines.isEmpty())
        }
    }

    @Test
    fun baselineProfileContainsCoreStartupAndScrollClasses() {
        val expectedClasses = listOf(
            "Lcom/localstream/app/MainActivity;",
            "Lcom/localstream/app/LocalStreamApplication;",
            "Lcom/localstream/app/ui/navigation/LocalStreamNavHostKt;",
            "Lcom/localstream/app/ui/screens/HomeScreenKt;",
            "Lcom/localstream/app/ui/screens/LibraryScreenKt;",
            "Lcom/localstream/app/ui/screens/PermissionScreenKt;",
            "Lcom/localstream/app/ui/components/VideoCardKt;",
            "Lcom/localstream/app/ui/components/VideoRowKt;",
            "Lcom/localstream/app/ui/components/HeroSectionKt;",
            "Lcom/localstream/app/ui/components/LocalStreamBottomBarKt;",
            "Lcom/localstream/app/ui/player/PlayerScreenKt;",
            "Lcom/localstream/app/ui/player/EpisodesSelectionSheetKt;",
            "Lcom/localstream/app/ui/home/HomeViewModel;",
            "Lcom/localstream/app/data/db/AppDatabase;",
            "Lcom/localstream/app/data/db/dao/PlaybackStateDao;",
            "Lcom/localstream/app/data/db/entity/PlaybackStateEntity;",
            "Lcom/localstream/app/data/repository/VideoRepository;",
        )

        for (file in profileFiles) {
            val content = file.readText()
            for (expected in expectedClasses) {
                assertTrue("${file.path} doit contenir $expected", content.contains(expected))
            }
        }
    }

    @Test
    fun baselineProfileClassesAreValidClassDescriptors() {
        val classLoader = Thread.currentThread().contextClassLoader ?: ClassLoader.getSystemClassLoader()
        for (file in profileFiles) {
            verifyClassDescriptors(file, classLoader)
        }
    }

    private fun verifyClassDescriptors(file: File, classLoader: ClassLoader) {
        val lines = file.readLines().filter { it.startsWith("Lcom/localstream/app/") && it.endsWith(";") }
        for (line in lines) {
            val className = line.removePrefix("L").removeSuffix(";").replace('/', '.')
            try {
                Class.forName(className, false, classLoader)
            } catch (e: ClassNotFoundException) {
                throw AssertionError("Classe $className introuvable dans le projet (${file.path})", e)
            }
        }
    }
}
