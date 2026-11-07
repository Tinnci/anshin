package com.driezy.medlog.feature.onboarding

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
    fun `welcome keeps the brand mark on small phones and only drops it on very short screens`() {
        assertTrue(welcomeLayoutProfile(1f, 360, 640).showBrandMark)
        assertFalse(welcomeLayoutProfile(1f, 923, 411).showBrandMark)
    }

    @Test
    fun `tall screen keeps illustrations at enlarged text`() {
        assertTrue(welcomeLayoutProfile(2f, 412, 915).showIllustration)
    }

    @Test
    fun `short landscape window moves actions onto one row`() {
        assertTrue(welcomeLayoutProfile(1f, 923, 411).inlineActions)
        assertFalse(welcomeLayoutProfile(1f, 412, 915).inlineActions)
    }

    @Test
    fun `step count yields to skip on narrow screens at enlarged text`() {
        assertFalse(welcomeLayoutProfile(2f, 360, 640).showStepCount)
        assertTrue(welcomeLayoutProfile(2f, 412, 915).showStepCount)
    }

    @Test
    fun `motion policy removes stagger for constrained and reduced motion layouts`() {
        assertEquals(0L, welcomeEntryDelayMs(index = 3, constrained = true, motionEnabled = true))
        assertEquals(0L, welcomeEntryDelayMs(index = 3, constrained = false, motionEnabled = false))
        assertEquals(120L, welcomeEntryDelayMs(index = 3, constrained = false, motionEnabled = true))
    }
}
