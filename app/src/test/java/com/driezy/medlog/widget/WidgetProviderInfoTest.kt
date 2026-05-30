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

    @Test
    fun `widgets use shared Material chrome and launcher background shape`() {
        val chrome = File(projectRoot, "app/src/main/java/com/driezy/medlog/widget/WidgetChrome.kt").readText()
        val widgetFiles = listOf(
            "MedLogWidget.kt",
            "NextDoseWidget.kt",
            "StreakWidget.kt",
        )

        assertTrue(chrome.contains("appWidgetBackground()"))
        assertTrue(chrome.contains("system_app_widget_background_radius"))
        assertTrue(chrome.contains("WidgetHeader("))
        assertTrue(chrome.contains("WidgetIconBadge("))
        assertTrue(chrome.contains("WidgetActionButton("))
        widgetFiles.forEach { fileName ->
            val text = File(projectRoot, "app/src/main/java/com/driezy/medlog/widget/$fileName").readText()
            assertTrue("$fileName should use shared widget container chrome.", text.contains("WidgetContainer("))
        }
    }

    @Test
    fun `widgets avoid emoji decoration and keep action touch targets large`() {
        val widgetFiles = listOf(
            "MedLogWidget.kt",
            "NextDoseWidget.kt",
            "StreakWidget.kt",
        )
        val forbiddenDecorations = listOf("💊", "🔥", "🎉")

        widgetFiles.forEach { fileName ->
            val text = File(projectRoot, "app/src/main/java/com/driezy/medlog/widget/$fileName").readText()
            forbiddenDecorations.forEach { token ->
                assertFalse("$fileName should use Material icon resources instead of $token.", text.contains(token))
            }
            assertFalse("$fileName should not use undersized 24dp action targets.", text.contains(".size(24.dp)"))
            assertFalse("$fileName should not use undersized 36dp action targets.", text.contains(".size(36.dp)"))
        }

        val chrome = File(projectRoot, "app/src/main/java/com/driezy/medlog/widget/WidgetChrome.kt").readText()
        assertTrue("Shared widget action should meet 48dp touch target guidance.", chrome.contains(".size(48.dp)"))
    }
}
