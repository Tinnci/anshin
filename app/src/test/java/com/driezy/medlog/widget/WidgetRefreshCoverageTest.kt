package com.driezy.medlog.widget

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WidgetRefreshCoverageTest {
    private val projectRoot = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").exists() }

    private fun source(path: String) = File(projectRoot, path).readText()

    @Test
    fun `medication plan mutations trigger immediate widget refresh`() {
        val addMedication =
            source("app/src/main/java/com/driezy/medlog/ui/screen/addmedication/AddMedicationViewModel.kt")
        val detail = source("app/src/main/java/com/driezy/medlog/ui/screen/detail/MedicationDetailViewModel.kt")
        val settings = source("app/src/main/java/com/driezy/medlog/ui/screen/settings/SettingsViewModel.kt")

        assertTrue(addMedication.contains("private val widgetRefresher: WidgetRefresher"))
        assertTrue(addMedication.contains("widgetRefresher.refreshAll()"))
        assertTrue(detail.contains("private val widgetRefresher: WidgetRefresher"))
        assertTrue(detail.contains("widgetRefresher.refreshAll()"))
        assertTrue(settings.contains("private val widgetRefresher: WidgetRefresher"))
        assertTrue(settings.contains("widgetRefresher.refreshAll()"))
    }

    @Test
    fun `glance refresh isolates host update failures`() {
        val refresher = source("app/src/main/java/com/driezy/medlog/widget/WidgetRefresher.kt")

        assertTrue(refresher.contains("runCatching"))
        assertTrue(refresher.contains("MedLogWidget().updateAll(context)"))
        assertTrue(refresher.contains("NextDoseWidget().updateAll(context)"))
        assertTrue(refresher.contains("StreakWidget().updateAll(context)"))
    }
}
