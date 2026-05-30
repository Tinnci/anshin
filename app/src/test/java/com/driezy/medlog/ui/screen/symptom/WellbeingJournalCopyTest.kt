package com.driezy.medlog.ui.screen.symptom

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WellbeingJournalCopyTest {
    private val projectRoot = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").exists() }

    private fun source(path: String) = File(projectRoot, path).readText()

    @Test
    fun `diary feature uses inclusive wellbeing language in Chinese resources`() {
        val strings = source("app/src/main/res/values/strings.xml")

        assertFalse("User-facing copy should not call the feature 症状日记.", strings.contains("症状日记"))
        assertTrue(strings.contains("""<string name="welcome_p4_symptom_title">身心记录</string>"""))
        assertTrue(strings.contains("""<string name="settings_symptom_title">身心记录</string>"""))
        assertTrue(strings.contains("""<string name="symptom_screen_title">身心记录</string>"""))
        assertTrue(strings.contains("""<string name="symptom_dialog_supporting_text">不需要出现症状才记录；可以只记感受、身体变化、用药反应或备注。</string>"""))
        assertTrue(strings.contains("""<string name="symptom_section_symptoms">身体信号（可选）</string>"""))
        assertTrue(strings.contains("""<string name="symptom_section_side_effects">用药反应（可选）</string>"""))
        assertTrue(strings.contains("""<string name="symptom_empty_action">记录今天</string>"""))
    }

    @Test
    fun `diary feature uses inclusive wellbeing language in English resources`() {
        val strings = source("app/src/main/res/values-en/strings.xml")

        assertFalse("User-facing copy should not call the feature Symptom Diary.", strings.contains("Symptom Diary"))
        assertTrue(strings.contains("""<string name="welcome_p4_symptom_title">Wellbeing Journal</string>"""))
        assertTrue(strings.contains("""<string name="settings_symptom_title">Wellbeing Journal</string>"""))
        assertTrue(strings.contains("""<string name="symptom_screen_title">Wellbeing Journal</string>"""))
        assertTrue(strings.contains("""<string name="symptom_dialog_supporting_text">You do not need symptoms to make an entry; record feelings, body changes, medication responses, or notes.</string>"""))
        assertTrue(strings.contains("""<string name="symptom_section_symptoms">Body signals (optional)</string>"""))
        assertTrue(strings.contains("""<string name="symptom_section_side_effects">Medication responses (optional)</string>"""))
        assertTrue(strings.contains("""<string name="symptom_empty_action">Log today</string>"""))
    }

    @Test
    fun `empty diary state uses bounded centered copy and an explicit action`() {
        val screen = source("app/src/main/java/com/driezy/medlog/ui/screen/symptom/SymptomDiaryScreen.kt")

        assertTrue(screen.contains("SymptomEmptyState("))
        assertTrue(screen.contains("textAlign = TextAlign.Center"))
        assertTrue(screen.contains("widthIn(max = 300.dp)"))
        assertTrue(screen.contains("onCreate = viewModel::startAdd"))
    }
}
