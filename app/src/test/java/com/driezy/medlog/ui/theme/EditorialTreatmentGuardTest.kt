package com.driezy.medlog.ui.theme

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorialTreatmentGuardTest {
    private val projectRoot = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").exists() }

    @Test
    fun `editorial treatment typography is tokenized in theme`() {
        val type = File(projectRoot, "app/src/main/java/com/driezy/medlog/ui/theme/Type.kt").readText()
        val theme = File(projectRoot, "app/src/main/java/com/driezy/medlog/ui/theme/Theme.kt").readText()

        assertTrue("Editorial treatments should have a dedicated token class.", type.contains("data class EditorialTypography"))
        assertTrue("Editorial typography should use the app font family.", type.contains("MedLogEditorialTypography") && type.contains("MedLogFontFamily"))
        assertTrue("Editorial typography should be exposed through MaterialTheme.", theme.contains("editorialTypography"))
    }

    @Test
    fun `home progress is the editorial showcase moment`() {
        val homeInfo = File(projectRoot, "app/src/main/java/com/driezy/medlog/ui/screen/home/HomeInfoComponents.kt").readText()
        val moment = homeInfo.substringAfter("private fun EditorialProgressMoment")

        assertTrue("Home progress should render a dedicated editorial moment.", homeInfo.contains("EditorialProgressMoment("))
        assertTrue("Editorial moment should consume theme tokens.", moment.contains("MaterialTheme.editorialTypography"))
        assertTrue("Editorial moment should keep progress number changes animated.", moment.contains("AnimatedContent("))
        assertFalse("Editorial moment should not be implemented as ad hoc displaySmall usage.", moment.contains("displaySmall"))
    }
}
