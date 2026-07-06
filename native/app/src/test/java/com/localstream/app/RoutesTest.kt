package com.localstream.app

import com.localstream.app.ui.navigation.Routes
import com.localstream.app.ui.navigation.TopLevelDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests unitaires JVM du socle : logique de routage pure (sans Android).
 */
class RoutesTest {

    @Test
    fun details_buildsRouteWithId() {
        assertEquals("details/42", Routes.details("42"))
    }

    @Test
    fun detailsTemplate_matchesArgumentName() {
        assertTrue(Routes.DETAILS.contains("{${Routes.ARG_ID}}"))
    }

    @Test
    fun topLevelDestinations_areFourAndRoutesUnique() {
        val routes = TopLevelDestination.entries.map { it.route }
        assertEquals(4, routes.size)
        assertEquals(routes.size, routes.toSet().size)
    }
}
