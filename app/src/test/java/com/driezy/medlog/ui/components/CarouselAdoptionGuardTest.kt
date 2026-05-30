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
        val healthSource = File(projectRoot, "app/src/main/java/com/driezy/medlog/ui/screen/health")
            .walkTopDown()
            .filter { it.extension == "kt" }
            .joinToString("\n") { it.readText() }
        val metricsSection = healthSource.substringAfter("fun HealthMetricsSection")
            .substringBefore("fun SectionHeader")

        assertTrue(
            "Health metrics should use Material3 carousel APIs.",
            healthSource.contains("androidx.compose.material3.carousel.HorizontalUncontainedCarousel") &&
                healthSource.contains("androidx.compose.material3.carousel.rememberCarouselState"),
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
        val settingsSource = File(projectRoot, "app/src/main/java/com/driezy/medlog/ui/screen/settings")
            .walkTopDown()
            .filter { it.extension == "kt" }
            .joinToString("\n") { it.readText() }

        assertTrue(
            "Settings widget previews should use Material3 carousel APIs.",
            settingsSource.contains("androidx.compose.material3.carousel.HorizontalCenteredHeroCarousel") &&
                settingsSource.contains("androidx.compose.material3.carousel.rememberCarouselState"),
        )
        assertTrue(
            "Widget settings page should route repeated widget cards through a dedicated carousel.",
            settingsSource.contains("WidgetPreviewCarousel("),
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
