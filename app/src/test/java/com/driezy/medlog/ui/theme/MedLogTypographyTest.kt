package com.driezy.medlog.ui.theme

import com.driezy.medlog.data.repository.FontMode
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class MedLogTypographyTest {

    @Test
    fun `baseline and emphasized typography use MedLog font family`() {
        listOf(
            MedLogTypography.displayLarge,
            MedLogTypography.headlineLarge,
            MedLogTypography.titleLarge,
            MedLogTypography.titleMedium,
            MedLogTypography.labelLarge,
            MedLogTypography.bodyLarge,
        ).forEach { style ->
            assertSame(MedLogFontFamily, style.fontFamily)
        }

        listOf(
            MedLogEmphasizedTypography.displayLarge,
            MedLogEmphasizedTypography.headlineLarge,
            MedLogEmphasizedTypography.titleLarge,
            MedLogEmphasizedTypography.titleMedium,
            MedLogEmphasizedTypography.labelLarge,
            MedLogEmphasizedTypography.bodyLarge,
            MedLogEditorialTypography.progressNumeral,
            MedLogEditorialTypography.progressTotal,
            MedLogEditorialTypography.celebrationWord,
        ).forEach { style ->
            assertSame(MedLogFontFamily, style.fontFamily)
        }
    }

    @Test
    fun `system font mode leaves font family unspecified`() {
        val typography = medLogTypography(FontMode.SYSTEM)
        val emphasized = medLogEmphasizedTypography(FontMode.SYSTEM)
        val editorial = medLogEditorialTypography(FontMode.SYSTEM)

        listOf(
            typography.displayLarge,
            typography.headlineLarge,
            typography.titleLarge,
            typography.titleMedium,
            typography.labelLarge,
            typography.bodyLarge,
            emphasized.displayLarge,
            emphasized.headlineLarge,
            emphasized.titleLarge,
            emphasized.titleMedium,
            emphasized.labelLarge,
            emphasized.bodyLarge,
            editorial.progressNumeral,
            editorial.progressTotal,
            editorial.celebrationWord,
        ).forEach { style ->
            assertNull(style.fontFamily)
        }
    }
}
