package com.driezy.medlog.ui.theme

import androidx.compose.ui.graphics.toArgb
import com.driezy.medlog.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemePaletteTest {
    @Test
    fun `curated palettes include literary multilingual names`() {
        val palettes = ThemePalette.entries.map { it.displayName to it.descriptionRes }

        assertEquals(
            listOf(
                "Anshin" to R.string.settings_palette_desc_anshin,
                "Meadow · 青青草" to R.string.settings_palette_desc_meadow,
                "Wakakusa · 若草" to R.string.settings_palette_desc_wakakusa,
                "Celadon · 青瓷" to R.string.settings_palette_desc_celadon,
                "Aube · 曙光" to R.string.settings_palette_desc_aube,
            ),
            palettes,
        )
    }

    @Test
    fun `palette semantic metadata lives with the palette definition`() {
        ThemePalette.entries.forEach { palette ->
            assertTrue("${palette.name} should expose a display name.", palette.displayName.isNotBlank())
            assertTrue("${palette.name} should expose a color image.", palette.imagery.isNotBlank())
            assertTrue("${palette.name} should expose a string resource description.", palette.descriptionRes != 0)
            assertEquals(palette.lightColorScheme, palette.colorScheme(darkTheme = false))
            assertEquals(palette.darkColorScheme, palette.colorScheme(darkTheme = true))
        }
    }

    @Test
    fun `custom palettes provide distinct light and dark color schemes`() {
        ThemePalette.entries
            .filterNot { it == ThemePalette.ANSHIN }
            .forEach { palette ->
                val lightScheme = palette.colorScheme(darkTheme = false)
                val darkScheme = palette.colorScheme(darkTheme = true)

                assertNotEquals(
                    "${palette.name} should not reuse the default light primary color.",
                    MedLogLightColorScheme.primary.toArgb(),
                    lightScheme.primary.toArgb(),
                )
                assertNotEquals(
                    "${palette.name} should not reuse the default dark primary color.",
                    MedLogDarkColorScheme.primary.toArgb(),
                    darkScheme.primary.toArgb(),
                )
            }
    }

    @Test
    fun `invalid stored palette names fall back to Anshin`() {
        assertEquals(ThemePalette.ANSHIN, ThemePalette.fromStoredName("missing"))
        assertEquals(ThemePalette.ANSHIN, ThemePalette.fromStoredName(null))
    }

    @Test
    fun `custom palette selection disables dynamic color override`() {
        assertTrue(ThemePalette.ANSHIN.allowsDynamicColor)
        assertTrue(ThemePalette.MEADOW.allowsDynamicColor.not())
    }
}
