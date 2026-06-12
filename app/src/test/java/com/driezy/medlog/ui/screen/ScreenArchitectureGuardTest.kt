package com.driezy.medlog.ui.screen

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenArchitectureGuardTest {
    private val projectRoot = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").exists() }

    @Test
    fun complexFeatureScreensKeepRouteFilesSmall() {
        val limits = mapOf(
            "app/src/main/java/com/driezy/medlog/ui/screen/health/HealthScreen.kt" to 420,
            "app/src/main/java/com/driezy/medlog/ui/screen/addmedication/AddMedicationScreen.kt" to 520,
            "app/src/main/java/com/driezy/medlog/ui/screen/welcome/WelcomeScreen.kt" to 520,
            "app/src/main/java/com/driezy/medlog/ui/screen/detail/MedicationDetailScreen.kt" to 420,
            "app/src/main/java/com/driezy/medlog/ui/screen/drugs/DrugsScreen.kt" to 420,
            "app/src/main/java/com/driezy/medlog/ui/screen/history/HistoryScreen.kt" to 220,
            "app/src/main/java/com/driezy/medlog/ui/screen/symptom/SymptomDiaryScreen.kt" to 220,
            "app/src/main/java/com/driezy/medlog/ui/screen/addmedication/AddMedicationFormComponents.kt" to 340,
            "app/src/main/java/com/driezy/medlog/ui/screen/addmedication/AddMedicationBasicSections.kt" to 340,
            "app/src/main/java/com/driezy/medlog/ui/screen/addmedication/AddMedicationScheduleSections.kt" to 380,
            "app/src/main/java/com/driezy/medlog/ui/screen/addmedication/AddMedicationInventorySections.kt" to 180,
            "app/src/main/java/com/driezy/medlog/ui/screen/settings/SettingsCardComponents.kt" to 420,
            "app/src/main/java/com/driezy/medlog/ui/screen/settings/SettingsRowsComponents.kt" to 420,
            "app/src/main/java/com/driezy/medlog/ui/screen/settings/CloudAiSettingsPanel.kt" to 1150,
            "app/src/main/java/com/driezy/medlog/ui/screen/settings/SettingsAppearanceContent.kt" to 300,
            "app/src/main/java/com/driezy/medlog/ui/screen/settings/SettingsReminderContent.kt" to 420,
            "app/src/main/java/com/driezy/medlog/ui/screen/settings/SettingsIntelligenceContent.kt" to 220,
            "app/src/main/java/com/driezy/medlog/ui/screen/settings/SettingsHomeModulesContent.kt" to 220,
            "app/src/main/java/com/driezy/medlog/ui/screen/settings/SettingsWidgetsContent.kt" to 320,
            "app/src/main/java/com/driezy/medlog/ui/screen/settings/SettingsDataContent.kt" to 180,
        )

        limits.forEach { (relativePath, maxLines) ->
            val lineCount = File(projectRoot, relativePath).readLines().size
            assertTrue(
                "$relativePath has $lineCount lines; keep route files under $maxLines lines by moving sections/components into focused files.",
                lineCount <= maxLines,
            )
        }
    }

    @Test
    fun mainRoutesDoNotReintroducePageLevelOverlayBooleans() {
        val routeFiles = listOf(
            "app/src/main/java/com/driezy/medlog/ui/screen/home/HomeScreen.kt",
            "app/src/main/java/com/driezy/medlog/ui/screen/addmedication/AddMedicationScreen.kt",
            "app/src/main/java/com/driezy/medlog/ui/screen/health/HealthScreen.kt",
            "app/src/main/java/com/driezy/medlog/ui/screen/drugs/DrugsScreen.kt",
            "app/src/main/java/com/driezy/medlog/ui/screen/history/HistoryScreen.kt",
            "app/src/main/java/com/driezy/medlog/ui/screen/detail/MedicationDetailScreen.kt",
            "app/src/main/java/com/driezy/medlog/ui/screen/symptom/SymptomDiaryScreen.kt",
            "app/src/main/java/com/driezy/medlog/ui/screen/welcome/WelcomeScreen.kt",
        )
        val forbiddenState = Regex("""var\s+show[A-Za-z0-9_]*\s+by\s+remember""")

        routeFiles.forEach { relativePath ->
            val source = File(projectRoot, relativePath).readText()
            assertTrue(
                "$relativePath should model transient dialogs, sheets, and scanners with ScreenOverlay instead of page-level showXxx Booleans.",
                !forbiddenState.containsMatchIn(source),
            )
        }
    }
}
