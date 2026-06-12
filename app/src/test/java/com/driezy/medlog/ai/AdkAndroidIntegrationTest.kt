package com.driezy.medlog.ai

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdkAndroidIntegrationTest {
    private val projectRoot = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").exists() }

    private fun source(path: String) = File(projectRoot, path).readText()

    @Test
    fun `app uses android adk runtime and ksp processor`() {
        val versions = source("gradle/libs.versions.toml")
        val build = source("app/build.gradle.kts")

        assertTrue(
            "Version catalog should pin a Google ADK Kotlin version.",
            versions.contains("googleAdkKotlin = \"0.2.0\""),
        )
        assertTrue(
            "Version catalog should expose the Android ADK runtime.",
            versions.contains("google-adk-kotlin-core-android = { module = \"com.google.adk:google-adk-kotlin-core-android\""),
        )
        assertTrue(
            "Version catalog should expose the ADK KSP processor.",
            versions.contains("google-adk-kotlin-processor = { module = \"com.google.adk:google-adk-kotlin-processor\""),
        )
        assertTrue(
            "App module should depend on the Android ADK runtime.",
            build.contains("implementation(libs.google.adk.kotlin.core.android)"),
        )
        assertTrue(
            "App module should run the ADK processor through KSP.",
            build.contains("ksp(libs.google.adk.kotlin.processor)"),
        )
        assertFalse(
            "Android apps should not also include the JVM ADK core artifact.",
            build.contains("google-adk-kotlin-core:") ||
                build.contains("libs.google.adk.kotlin.core)"),
        )
        assertTrue(
            "ADK transitive jars require duplicate Java resource metadata to be excluded.",
            build.contains("excludes += \"/META-INF/INDEX.LIST\"") &&
                build.contains("excludes += \"/META-INF/DEPENDENCIES\""),
        )
    }
}
