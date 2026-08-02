package com.driezy.medlog.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenChromeModelsTest {
    @Test
    fun `compact layout keeps secondary and danger actions in overflow`() {
        val actions = listOf(
            TopBarAction("save", "Save", 1, TopBarActionPriority.Primary),
            TopBarAction("settings", "Settings", 2, TopBarActionPriority.Secondary),
            TopBarAction("delete", "Delete", 3, TopBarActionPriority.Danger),
        )

        val placement = placeTopBarActions(actions, MainScreenWidthClass.Compact)

        assertEquals(listOf("save"), placement.visible.map { it.id })
        assertEquals(listOf("settings", "delete"), placement.overflow.map { it.id })
    }
}
