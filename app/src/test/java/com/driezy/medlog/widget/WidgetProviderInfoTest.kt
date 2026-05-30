package com.driezy.medlog.widget

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetProviderInfoTest {
    private val projectRoot = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").exists() }

    @Test
    fun `glance widgets define the required loading initial layout`() {
        listOf(
            "med_log_widget_info.xml",
            "next_dose_widget_info.xml",
            "streak_widget_info.xml",
        ).forEach { fileName ->
            val text = File(projectRoot, "app/src/main/res/xml/$fileName").readText()
            assertTrue(
                "$fileName should define Glance's loading layout.",
                text.contains("""android:initialLayout="@layout/glance_default_loading_layout""""),
            )
        }
    }

    @Test
    fun `glance theme does not depend on Compose UI configuration locals`() {
        val text = File(projectRoot, "app/src/main/java/com/driezy/medlog/widget/MedLogGlanceTheme.kt").readText()

        assertFalse(
            "Glance AppWidget composition does not provide Compose UI LocalConfiguration.",
            text.contains("isSystemInDarkTheme"),
        )
    }
}
