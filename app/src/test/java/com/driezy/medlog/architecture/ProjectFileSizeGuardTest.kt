package com.driezy.medlog.architecture

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectFileSizeGuardTest {
    private val projectRoot = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").exists() }

    @Test
    fun maintainedSourceAndResourceFilesStayUnderOneThousandLines() {
        val oversizedFiles = projectRoot
            .walkTopDown()
            .filter { it.isFile }
            .filter { it.isMaintainedSourceOrResource() }
            .mapNotNull { file ->
                val lineCount = file.readLines().size
                if (lineCount > 1_000) {
                    "${file.relativeTo(projectRoot).path}:$lineCount"
                } else {
                    null
                }
            }
            .toList()

        assertTrue(
            "Split files over 1000 lines into focused modules/resources:\n${oversizedFiles.joinToString("\n")}",
            oversizedFiles.isEmpty(),
        )
    }

    private fun File.isMaintainedSourceOrResource(): Boolean {
        val relativePath = relativeTo(projectRoot).invariantSeparatorsPath
        if (
            relativePath.startsWith(".git/") ||
            relativePath.contains("/build/") ||
            relativePath.contains("/.gradle/") ||
            relativePath.contains("/.venv/") ||
            relativePath.contains("/.pixi/") ||
            relativePath.contains("/__pycache__/")
        ) {
            return false
        }
        if (relativePath.startsWith("app/src/main/assets/") || relativePath.startsWith("scripts/data/")) {
            return false
        }
        if (relativePath.startsWith("seven_segment_ocr/exported/")) {
            return false
        }
        if (relativePath.startsWith("seven_segment_ocr/kaggle_domain_output/")) {
            return false
        }
        return extension in setOf("kt", "java", "kts", "py") ||
            relativePath.matches(Regex("""app/src/main/res/values[^/]*/.*\.xml"""))
    }
}
