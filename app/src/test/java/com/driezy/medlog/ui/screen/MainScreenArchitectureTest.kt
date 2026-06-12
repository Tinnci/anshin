package com.driezy.medlog.ui.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainScreenArchitectureTest {
    private val projectRoot = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").exists() }

    private val mainRouteFiles = listOf(
        "app/src/main/java/com/driezy/medlog/ui/screen/home/HomeScreen.kt",
        "app/src/main/java/com/driezy/medlog/ui/screen/addmedication/AddMedicationScreen.kt",
        "app/src/main/java/com/driezy/medlog/ui/screen/health/HealthScreen.kt",
        "app/src/main/java/com/driezy/medlog/ui/screen/drugs/DrugsScreen.kt",
        "app/src/main/java/com/driezy/medlog/ui/screen/history/HistoryScreen.kt",
        "app/src/main/java/com/driezy/medlog/ui/screen/detail/MedicationDetailScreen.kt",
        "app/src/main/java/com/driezy/medlog/ui/screen/symptom/SymptomDiaryScreen.kt",
        "app/src/main/java/com/driezy/medlog/ui/screen/welcome/WelcomeScreen.kt",
    )

    @Test
    fun mainRouteFilesUseSharedScreenScaffoldInsteadOfLocalScaffold() {
        mainRouteFiles.forEach { relativePath ->
            val source = File(projectRoot, relativePath).readText()

            assertTrue(
                "$relativePath must use MedLogScreenScaffold so loading, empty, snackbar, FAB, and top bar behavior stay centralized.",
                source.contains("MedLogScreenScaffold("),
            )
            assertFalse(
                "$relativePath must not declare a raw Scaffold; route chrome belongs in MedLogScreenScaffold.",
                Regex("""(?<!MedLogScreen)Scaffold\(""").containsMatchIn(source),
            )
            assertFalse(
                "$relativePath must not declare a raw LargeTopAppBar; toolbar actions belong in PriorityTopBarActions.",
                source.contains("LargeTopAppBar("),
            )
        }
    }

    @Test
    fun overlayHeavyRoutesUseScreenOverlayHost() {
        listOf(
            "app/src/main/java/com/driezy/medlog/ui/screen/home/HomeScreen.kt",
            "app/src/main/java/com/driezy/medlog/ui/screen/addmedication/AddMedicationScreen.kt",
            "app/src/main/java/com/driezy/medlog/ui/screen/health/HealthScreen.kt",
            "app/src/main/java/com/driezy/medlog/ui/screen/detail/MedicationDetailScreen.kt",
            "app/src/main/java/com/driezy/medlog/ui/screen/symptom/SymptomDiaryScreen.kt",
        ).forEach { relativePath ->
            val source = File(projectRoot, relativePath).readText()
            assertTrue(
                "$relativePath must render transient dialogs/sheets/scanners through ScreenOverlayHost.",
                source.contains("ScreenOverlayHost("),
            )
        }
    }
}
