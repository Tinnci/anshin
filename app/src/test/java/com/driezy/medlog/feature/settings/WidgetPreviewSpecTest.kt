package com.driezy.medlog.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class WidgetPreviewSpecTest {

    @Test
    fun `next dose preview uses relative countdown instead of fake wall clock`() {
        val spec = WidgetPreviewSpec.forType(WidgetPreviewType.NEXT_DOSE, showActions = true)

        assertEquals(45, spec.minutesUntilNext)
        assertFalse(spec.primaryText.contains(":"))
    }

    @Test
    fun `today preview reflects action mode state`() {
        val actionSpec = WidgetPreviewSpec.forType(WidgetPreviewType.TODAY, showActions = true)
        val statusSpec = WidgetPreviewSpec.forType(WidgetPreviewType.TODAY, showActions = false)

        assertEquals(true, actionSpec.showActionButton)
        assertEquals(false, statusSpec.showActionButton)
        assertEquals(0.5f, actionSpec.progress)
    }
}
