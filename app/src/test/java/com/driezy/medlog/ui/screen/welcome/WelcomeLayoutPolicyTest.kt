package com.driezy.medlog.ui.screen.welcome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WelcomeLayoutPolicyTest {
    @Test
    fun `regular device keeps illustration and supporting copy`() {
        val profile = welcomeLayoutProfile(
            fontScale = 1f,
            screenWidthDp = 412,
            screenHeightDp = 915,
        )

        assertFalse(profile.constrained)
        assertTrue(profile.showIllustration)
        assertTrue(profile.showSupportingText)
    }

    @Test
    fun `short screen compacts decoration without removing actions`() {
        val profile = welcomeLayoutProfile(
            fontScale = 1f,
            screenWidthDp = 412,
            screenHeightDp = 640,
        )

        assertTrue(profile.constrained)
        assertFalse(profile.showIllustration)
        assertTrue(profile.keepActionsPinned)
    }

    @Test
    fun `two hundred percent font scale uses compact readable hierarchy`() {
        val profile = welcomeLayoutProfile(
            fontScale = 2f,
            screenWidthDp = 412,
            screenHeightDp = 915,
        )

        assertTrue(profile.constrained)
        assertFalse(profile.showSupportingText)
        assertTrue(profile.keepActionsPinned)
    }

    @Test
    fun `motion policy removes stagger for constrained and reduced motion layouts`() {
        assertEquals(0L, welcomeEntryDelayMs(index = 3, constrained = true, motionEnabled = true))
        assertEquals(0L, welcomeEntryDelayMs(index = 3, constrained = false, motionEnabled = false))
        assertEquals(120L, welcomeEntryDelayMs(index = 3, constrained = false, motionEnabled = true))
    }
}
