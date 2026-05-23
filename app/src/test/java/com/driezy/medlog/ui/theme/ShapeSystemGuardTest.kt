package com.driezy.medlog.ui.theme

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ShapeSystemGuardTest {
    private val projectRoot = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").exists() }

    @Test
    fun `compose shape scale follows Material 3 role values`() {
        val shapes = File(projectRoot, "app/src/main/java/com/driezy/medlog/ui/theme/Shapes.kt").readText()
        val expected = mapOf(
            "extraSmall" to "RoundedCornerShape(4.dp)",
            "small" to "RoundedCornerShape(8.dp)",
            "medium" to "RoundedCornerShape(12.dp)",
            "large" to "RoundedCornerShape(16.dp)",
            "extraLarge" to "RoundedCornerShape(28.dp)",
        )

        val lines = shapes.lines()
        expected.forEach { (role, value) ->
            assertTrue(
                "MedLogShapes.$role should be $value.",
                lines.any { it.contains(role) && it.contains(value) },
            )
        }
    }
}
