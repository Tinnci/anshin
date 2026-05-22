package com.driezy.medlog.ui.theme

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
        ).forEach { style ->
            assertSame(MedLogFontFamily, style.fontFamily)
        }
    }
}
