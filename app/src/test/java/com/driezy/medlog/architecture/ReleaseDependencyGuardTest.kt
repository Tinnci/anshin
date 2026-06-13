package com.driezy.medlog.architecture

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseDependencyGuardTest {
    private val projectRoot = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").exists() }

    @Test
    fun adkRuntimeExcludesKxml2XmlPullParserImplementation() {
        val buildScript = File(projectRoot, "app/build.gradle.kts").readText()

        assertTrue(
            "ADK Android currently brings net.sf.kxml:kxml2 transitively; release R8 fails because kxml2 packages org.xmlpull.v1.XmlPullParser as a program class. Exclude kxml2 from the ADK runtime dependency.",
            Regex(
                """implementation\(libs\.google\.adk\.kotlin\.core\.android\)\s*\{[^}]*exclude\(group\s*=\s*"net\.sf\.kxml",\s*module\s*=\s*"kxml2"\)""",
                RegexOption.DOT_MATCHES_ALL,
            ).containsMatchIn(buildScript),
        )
    }
}
