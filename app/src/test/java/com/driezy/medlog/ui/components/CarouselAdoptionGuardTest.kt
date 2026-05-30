package com.driezy.medlog.ui.components

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CarouselAdoptionGuardTest {
    private val projectRoot = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").exists() }

    @Test
    fun `health metrics summary uses Material3 carousel`() {
        val healthScreen = File(projectRoot, "app/src/main/java/com/driezy/medlog/ui/screen/health/HealthScreen.kt").readText()
        val metricsSection = healthScreen.substringAfter("private fun HealthMetricsSection")
            .substringBefore("private fun SectionHeader")

        assertTrue(
            "Health metrics should use Material3 carousel APIs.",
            healthScreen.contains("androidx.compose.material3.carousel.HorizontalUncontainedCarousel") &&
                healthScreen.contains("androidx.compose.material3.carousel.rememberCarouselState"),
        )
        assertTrue(
            "HealthMetricsSection should render stat cards through HorizontalUncontainedCarousel.",
            metricsSection.contains("HorizontalUncontainedCarousel("),
        )
        assertFalse(
            "HealthMetricsSection should not keep the old horizontalScroll Row.",
            metricsSection.contains("horizontalScroll(rememberScrollState())"),
        )
    }

    @Test
    fun `settings widget previews use Material3 carousel`() {
        val settingsComponents = File(projectRoot, "app/src/main/java/com/driezy/medlog/ui/screen/settings/SettingsContentComponents.kt").readText()
        val settingsSections = File(projectRoot, "app/src/main/java/com/driezy/medlog/ui/screen/settings/SettingsSections.kt").readText()

        assertTrue(
            "Settings widget previews should use Material3 carousel APIs.",
            settingsComponents.contains("androidx.compose.material3.carousel.HorizontalCenteredHeroCarousel") &&
                settingsComponents.contains("androidx.compose.material3.carousel.rememberCarouselState"),
        )
        assertTrue(
            "Widget settings page should route repeated widget cards through a dedicated carousel.",
            settingsSections.contains("WidgetPreviewCarousel("),
        )
    }

    @Test
    fun `filter chips remain controls outside carousel layouts`() {
        val healthScreen = File(projectRoot, "app/src/main/java/com/driezy/medlog/ui/screen/health/HealthScreen.kt").readText()
        val drugsScreen = File(projectRoot, "app/src/main/java/com/driezy/medlog/ui/screen/drugs/DrugsScreen.kt").readText()
        val healthFilter = healthScreen.substringAfter("key = \"type_filter\"")
            .substringBefore("key = \"stats_row\"")
        val drugsFilter = drugsScreen.substringAfter("西药 / 中药 筛选")
            .substringBefore("搜索结果计数")

        assertFalse("Health filter chips should not be carousel items.", healthFilter.contains("Carousel("))
        assertFalse("Drug filter chips should not be carousel items.", drugsFilter.contains("Carousel("))
    }
}
