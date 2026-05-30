package com.driezy.medlog.ui.icons

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class MaterialSymbolsMigrationGuardTest {
    private val projectRoot = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").exists() }

    @Test
    fun `compose material icons dependency is removed`() {
        val forbidden = listOf(
            "androidx-material-icons-extended",
            "material-icons-extended",
            "androidx.compose.material:material-icons",
        )
        val files = listOf(
            File(projectRoot, "app/build.gradle.kts"),
            File(projectRoot, "gradle/libs.versions.toml"),
        )

        val offenders = files.flatMap { file ->
            forbidden.filter { token -> file.readText().contains(token) }
                .map { token -> "${file.relativeTo(projectRoot).path}: $token" }
        }

        assertTrue("Remove Compose Material Icons dependency:\n${offenders.joinToString("\n")}", offenders.isEmpty())
    }

    @Test
    fun `source code does not import compose material icons`() {
        val sourceRoot = File(projectRoot, "app/src/main/java")
        val offenders = sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    when {
                        line.contains("androidx.compose.material.icons") ->
                            "${file.relativeTo(projectRoot).path}:${index + 1}: $line"
                        Regex("""\bIcons\.(Rounded|Filled|AutoMirrored|Outlined|Sharp|TwoTone)\b""").containsMatchIn(line) ->
                            "${file.relativeTo(projectRoot).path}:${index + 1}: $line"
                        else -> null
                    }
                }
            }
            .toList()

        assertTrue("Use MedLogIcons backed by Material Symbols XML:\n${offenders.joinToString("\n")}", offenders.isEmpty())
    }

    @Test
    fun `material symbols drawable set and icon entry point exist`() {
        val drawableRoot = File(projectRoot, "app/src/main/res/drawable")
        val symbolDrawables = drawableRoot.listFiles { file ->
            file.isFile && file.name.startsWith("ic_symbol_") && file.extension == "xml"
        }.orEmpty()

        assertTrue("Expected Material Symbols XML drawable set under res/drawable.", symbolDrawables.size >= 24)
        assertTrue(
            "Expected MedLogIcons entry point.",
            File(projectRoot, "app/src/main/java/com/driezy/medlog/ui/icons/MedLogIcons.kt").exists(),
        )
    }

    @Test
    fun `material symbols are rendered through explicit MedLogIcon API`() {
        val iconEntryPoint = File(projectRoot, "app/src/main/java/com/driezy/medlog/ui/icons/MedLogIcons.kt").readText()
        val offenders = buildList {
            if (Regex("""fun\s+Icon\s*\(""").containsMatchIn(iconEntryPoint)) {
                add("MedLogIcons.kt must not define a fake Icon(imageVector: Int) overload.")
            }
            File(projectRoot, "app/src/main/java").walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .forEach { file ->
                    file.readLines().forEachIndexed { index, line ->
                        if (line.contains("import com.driezy.medlog.ui.icons.Icon")) {
                            add("${file.relativeTo(projectRoot).path}:${index + 1}: import MedLogIcon instead")
                        }
                        if (line.contains("imageVector = MedLogIcons") || line.contains("imageVector = formIcon") || line.contains("imageVector = health")) {
                            add("${file.relativeTo(projectRoot).path}:${index + 1}: use MedLogIcon(icon = ...) for drawable symbols")
                        }
                    }
                }
        }

        assertTrue("Use explicit MedLogIcon API for Material Symbols XML:\n${offenders.joinToString("\n")}", offenders.isEmpty())
    }

    @Test
    fun `material symbols source is reproducible`() {
        val script = File(projectRoot, "scripts/update_material_symbols.mjs")
        val scriptText = if (script.exists()) script.readText() else ""
        val entryPoint = File(projectRoot, "app/src/main/java/com/driezy/medlog/ui/icons/MedLogIcons.kt").readText()
        val symbolDrawables = File(projectRoot, "app/src/main/res/drawable").listFiles { file ->
            file.isFile && file.name.startsWith("ic_symbol_") && file.extension == "xml"
        }.orEmpty()

        assertTrue("Expected reproducible Material Symbols update script.", script.exists())
        assertTrue("Script should source from Google Fonts Material Symbols Rounded endpoints.", scriptText.contains("materialsymbolsrounded"))
        assertTrue("Script should explain why the Google Fonts family zip is not used for Android XML.", scriptText.contains("download?family=Material+Symbols"))
        assertTrue("MedLogIcons should be generated from the script.", entryPoint.contains("Generated by scripts/update_material_symbols.mjs"))
        symbolDrawables.forEach { file ->
            val text = file.readText()
            assertTrue("${file.name} must use Android VectorDrawable XML.", text.contains("<vector"))
            assertTrue("${file.name} must be generated from Material Symbols.", text.contains("Generated by scripts/update_material_symbols.mjs"))
        }
    }

    @Test
    fun `selected navigation icons use filled material symbol variants`() {
        val entryPoint = File(projectRoot, "app/src/main/java/com/driezy/medlog/ui/icons/MedLogIcons.kt").readText()
        val destinations = File(projectRoot, "app/src/main/java/com/driezy/medlog/ui/navigation/MedLogDestinations.kt").readText()
        val navigation = File(projectRoot, "app/src/main/java/com/driezy/medlog/ui/navigation/MedLogNavigationComponents.kt").readText()
        val expected = listOf(
            "HomeSelected",
            "HistorySelected",
            "MedicalServicesSelected",
            "EditNoteSelected",
            "MonitorHeartSelected",
        )

        expected.forEach { property ->
            assertTrue("Expected filled selected symbol property MedLogIcons.$property.", entryPoint.contains("val $property: Int"))
            assertTrue("Top-level destination should provide selected icon MedLogIcons.$property.", destinations.contains("MedLogIcons.$property"))
        }
        assertTrue("Navigation items should render selectedIcon when selected.", navigation.contains("if (selected) dest.selectedIcon else dest.icon"))
        assertTrue("Generator should fetch filled symbol variants.", File(projectRoot, "scripts/update_material_symbols.mjs").readText().contains("urlVariant: \"fill1\""))
    }

    @Test
    fun `large display icons use optical size material symbol variants`() {
        val drawableRoot = File(projectRoot, "app/src/main/res/drawable")
        val entryPoint = File(projectRoot, "app/src/main/java/com/driezy/medlog/ui/icons/MedLogIcons.kt").readText()
        val expected = mapOf(
            "MedicationDisplay40" to "ic_symbol_medication_opsz40.xml",
            "RefreshDisplay48" to "ic_symbol_refresh_opsz48.xml",
            "MonitorHeartDisplay48" to "ic_symbol_monitor_heart_opsz48.xml",
            "SearchOffDisplay48" to "ic_symbol_search_off_opsz48.xml",
            "CheckCircleDisplay48" to "ic_symbol_check_circle_opsz48.xml",
        )

        expected.forEach { (property, fileName) ->
            val drawable = File(drawableRoot, fileName)
            assertTrue("Expected optical-size drawable $fileName.", drawable.exists())
            assertTrue("Expected MedLogIcons.$property.", entryPoint.contains("val $property: Int = R.drawable.${fileName.removeSuffix(".xml")}"))
            assertTrue("$fileName should record its optical size.", drawable.readText().contains("optical size"))
        }
        val scriptText = File(projectRoot, "scripts/update_material_symbols.mjs").readText()
        assertTrue("Generator should configure 40dp optical-size symbols.", scriptText.contains("MedicationDisplay40"))
        assertTrue("Generator should configure 48dp optical-size symbols.", scriptText.contains("RefreshDisplay48"))
        assertTrue("Generator should fetch size-specific SVG endpoints.", scriptText.contains("""${'$'}{sizeDp}px.svg"""))
    }
}
