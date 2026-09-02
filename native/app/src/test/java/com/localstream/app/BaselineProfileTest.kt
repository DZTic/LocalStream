package com.localstream.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BaselineProfileTest {

    @Test
    fun baselineProfileFileExistsAndIsNotEmpty() {
        val file = File("src/main/baseline-prof.txt")
        assertTrue("baseline-prof.txt doit exister dans src/main/", file.exists())
        val lines = file.readLines().filter { it.isNotBlank() }
        assertFalse("baseline-prof.txt ne doit pas etre vide", lines.isEmpty())
    }

    @Test
    fun baselineProfileContainsCoreStartupAndScrollClasses() {
        val file = File("src/main/baseline-prof.txt")
        val content = file.readText()

        val expectedClasses = listOf(
            "Lcom/localstream/app/MainActivity;",
            "Lcom/localstream/app/LocalStreamApplication;",
            "Lcom/localstream/app/ui/navigation/LocalStreamNavHostKt;",
            "Lcom/localstream/app/ui/screens/HomeScreenKt;",
            "Lcom/localstream/app/ui/screens/LibraryScreenKt;",
            "Lcom/localstream/app/ui/components/VideoCardKt;",
            "Lcom/localstream/app/ui/components/VideoRowKt;",
            "Lcom/localstream/app/ui/components/HeroSectionKt;",
            "Lcom/localstream/app/ui/home/HomeViewModel;",
            "Lcom/localstream/app/data/db/AppDatabase;",
            "Lcom/localstream/app/data/repository/VideoRepository;",
        )

        for (expected in expectedClasses) {
            assertTrue("Doit contenir $expected", content.contains(expected))
        }
    }

    @Test
    fun baselineProfileClassesAreValidClassDescriptors() {
        val file = File("src/main/baseline-prof.txt")
        val lines = file.readLines().filter { it.isNotBlank() }

        val classLoader = Thread.currentThread().contextClassLoader ?: ClassLoader.getSystemClassLoader()

        for (line in lines) {
            if (line.startsWith("Lcom/localstream/app/") && line.endsWith(";")) {
                val className = line.removePrefix("L").removeSuffix(";").replace('/', '.')
                try {
                    Class.forName(className, false, classLoader)
                } catch (e: ClassNotFoundException) {
                    throw AssertionError("Classe $className introuvable dans le projet", e)
                }
            }
        }
    }
}
