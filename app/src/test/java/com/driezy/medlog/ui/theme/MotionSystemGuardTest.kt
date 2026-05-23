package com.driezy.medlog.ui.theme

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionSystemGuardTest {
    private val projectRoot = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").exists() }

    @Test
    fun `theme uses expressive motion scheme`() {
        val theme = File(projectRoot, "app/src/main/java/com/driezy/medlog/ui/theme/Theme.kt").readText()

        assertTrue("MedLogTheme should set MotionScheme.expressive().", theme.contains("motionScheme = MotionScheme.expressive()"))
    }

    @Test
    fun `custom motion uses scheme spring tokens instead of legacy durations`() {
        val files = listOf(
            "app/src/main/java/com/driezy/medlog/ui/MedLogApp.kt",
            "app/src/main/java/com/driezy/medlog/ui/screen/history/HistoryScreen.kt",
            "app/src/main/java/com/driezy/medlog/ui/screen/detail/MedicationDetailScreen.kt",
            "app/src/main/java/com/driezy/medlog/ui/screen/welcome/WelcomeScreen.kt",
        ).map { File(projectRoot, it) }
        val forbidden = listOf("tween(", "durationMillis", "Spring.DampingRatio", "Spring.Stiffness")
        val offenders = files.flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                val token = forbidden.firstOrNull { line.contains(it) }
                if (token == null) null else "${file.relativeTo(projectRoot).path}:${index + 1}: $token"
            }
        }

        assertTrue("Use MaterialTheme.motionScheme spring tokens for custom motion:\n${offenders.joinToString("\n")}", offenders.isEmpty())
    }

    @Test
    fun `navigation transitions use motion scheme tokens`() {
        val app = File(projectRoot, "app/src/main/java/com/driezy/medlog/ui/MedLogApp.kt").readText()

        assertTrue("Navigation transitions should use a tokenized helper.", app.contains("materialSharedAxisX"))
        assertTrue("Navigation transitions should use MaterialTheme.motionScheme.", app.contains("MaterialTheme.motionScheme"))
    }
}
