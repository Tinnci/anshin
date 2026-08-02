package com.driezy.medlog.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class NavigationHierarchyTest {
    @Test
    fun `mobile top level navigation keeps five primary destinations`() {
        assertEquals(5, TOP_LEVEL_DESTINATIONS.size)
        assertFalse(TOP_LEVEL_DESTINATIONS.any { it.route == Route.Settings })
    }
}
