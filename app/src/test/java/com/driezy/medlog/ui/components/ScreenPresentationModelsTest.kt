package com.driezy.medlog.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenPresentationModelsTest {
    @Test
    fun compactTopBarPlacesSecondaryAndDangerActionsInOverflow() {
        val actions = listOf(
            TopBarAction(id = "save", label = "Save", icon = 1, priority = TopBarActionPriority.Primary),
            TopBarAction(id = "scan", label = "Scan", icon = 2, priority = TopBarActionPriority.Secondary),
            TopBarAction(id = "delete", label = "Delete", icon = 3, priority = TopBarActionPriority.Danger),
        )

        val placement = placeTopBarActions(actions, MainScreenWidthClass.Compact)

        assertEquals(listOf("save"), placement.visible.map { it.id })
        assertEquals(listOf("scan", "delete"), placement.overflow.map { it.id })
    }

    @Test
    fun expandedTopBarStillKeepsDangerActionsInOverflow() {
        val actions = listOf(
            TopBarAction(id = "edit", label = "Edit", icon = 1, priority = TopBarActionPriority.Primary),
            TopBarAction(id = "archive", label = "Archive", icon = 2, priority = TopBarActionPriority.Secondary),
            TopBarAction(id = "delete", label = "Delete", icon = 3, priority = TopBarActionPriority.Danger),
        )

        val placement = placeTopBarActions(actions, MainScreenWidthClass.Expanded)

        assertEquals(listOf("edit", "archive"), placement.visible.map { it.id })
        assertEquals(listOf("delete"), placement.overflow.map { it.id })
    }

    @Test
    fun overlayModelBindsConfirmActionsToTheTargetItem() {
        val overlay = ScreenOverlay.Confirm(
            id = "delete:42",
            title = "Delete",
            body = "Delete record?",
            confirmLabel = "Delete",
            dismissLabel = "Cancel",
            targetKey = "42",
            isDanger = true,
        )

        assertEquals("42", overlay.targetKey)
        assertTrue(overlay.isDanger)
    }

    @Test
    fun chromeDescriptorsCarryActionIdsInsteadOfCallbacks() {
        val fab = ScreenFab(id = "add", label = "Add", icon = 1)
        val empty = ScreenEmptyState(title = "Empty", actionLabel = "Create", actionId = "create")

        assertEquals("add", fab.id)
        assertEquals("create", empty.actionId)
    }
}
