package com.driezy.medlog.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class LocalizationResourceParityTest {

    private val locales = listOf("values", "values-en", "values-ja", "values-ko")
    private val formatSpecRegex = Regex("""%(?:(\d+)\$)?[-#+ 0,(]*\d*(?:\.\d+)?([a-zA-Z%])""")

    @Test
    fun `all supported locales have complete string and plural key parity and matching placeholders`() {
        val resRoot = locateAppResDir()

        val stringsByLocale = mutableMapOf<String, Map<String, String>>()
        val pluralsByLocale = mutableMapOf<String, Map<String, Map<String, String>>>()

        for (locale in locales) {
            val dir = File(resRoot, locale)
            assertTrue("Expected resource directory ${dir.path} to exist", dir.isDirectory)

            val strings = mutableMapOf<String, String>()
            val plurals = mutableMapOf<String, Map<String, String>>()

            dir.listFiles { file -> file.isFile && file.extension == "xml" }
                ?.sortedBy { it.name }
                ?.forEach { xmlFile ->
                    parseResourceXml(xmlFile, strings, plurals)
                }

            stringsByLocale[locale] = strings
            pluralsByLocale[locale] = plurals
        }

        val baseStrings = stringsByLocale.getValue("values")
        val basePlurals = pluralsByLocale.getValue("values")

        assertTrue("Base locale should have translatable strings", baseStrings.isNotEmpty())

        for (locale in locales.drop(1)) {
            val targetStrings = stringsByLocale.getValue(locale)
            val missingStrings = baseStrings.keys - targetStrings.keys
            val extraStrings = targetStrings.keys - baseStrings.keys

            assertEquals("Missing <string> keys in $locale", emptySet<String>(), missingStrings)
            assertEquals("Extra <string> keys in $locale", emptySet<String>(), extraStrings)

            baseStrings.forEach { (key, baseValue) ->
                val targetValue = targetStrings.getValue(key)
                assertTrue("Blank translation for <string name=\"$key\"> in $locale", targetValue.isNotBlank())
                assertEquals(
                    "Format specifier mismatch for <string name=\"$key\"> in $locale",
                    extractFormatPlaceholders(baseValue),
                    extractFormatPlaceholders(targetValue),
                )
            }

            val targetPlurals = pluralsByLocale.getValue(locale)
            val missingPlurals = basePlurals.keys - targetPlurals.keys
            val extraPlurals = targetPlurals.keys - basePlurals.keys

            assertEquals("Missing <plurals> keys in $locale", emptySet<String>(), missingPlurals)
            assertEquals("Extra <plurals> keys in $locale", emptySet<String>(), extraPlurals)

            basePlurals.forEach { (key, baseItems) ->
                val targetItems = targetPlurals.getValue(key)
                assertTrue("Empty <plurals name=\"$key\"> in $locale", targetItems.isNotEmpty())
                val baseSpecs = baseItems.values.firstOrNull()?.let(::extractFormatPlaceholders).orEmpty()
                targetItems.forEach { (quantity, itemValue) ->
                    assertTrue(
                        "Blank plural item ($quantity) for <plurals name=\"$key\"> in $locale",
                        itemValue.isNotBlank(),
                    )
                    val itemSpecs = extractFormatPlaceholders(itemValue)
                    if (quantity == "other") {
                        assertEquals(
                            "Format specifier mismatch for <plurals name=\"$key\"> (other) in $locale",
                            baseSpecs,
                            itemSpecs,
                        )
                    } else {
                        assertTrue(
                            "Unexpected format specifier in <plurals name=\"$key\"> ($quantity) in $locale: $itemSpecs vs $baseSpecs",
                            baseSpecs.containsAll(itemSpecs),
                        )
                    }
                }
            }
        }
    }

    private fun locateAppResDir(): File {
        val candidates = listOf(
            File("src/main/res"),
            File("app/src/main/res"),
        )
        return candidates.firstOrNull { it.isDirectory }
            ?: error("Could not locate app/src/main/res from working directory ${File(".").absolutePath}")
    }

    private fun parseResourceXml(
        xmlFile: File,
        stringsOut: MutableMap<String, String>,
        pluralsOut: MutableMap<String, Map<String, String>>,
    ) {
        val factory = DocumentBuilderFactory.newInstance()
        val doc = factory.newDocumentBuilder().parse(xmlFile)
        val root = doc.documentElement ?: return
        val children = root.childNodes

        for (i in 0 until children.length) {
            val node = children.item(i) as? Element ?: continue
            val translatable = node.getAttribute("translatable")
            if (translatable.equals("false", ignoreCase = true)) continue

            when (node.tagName) {
                "string" -> {
                    val name = node.getAttribute("name")
                    if (name.isNotBlank()) {
                        assertTrue(
                            "Duplicate <string name=\"$name\"> in ${xmlFile.parentFile?.name}/${xmlFile.name}",
                            name !in stringsOut,
                        )
                        stringsOut[name] = node.textContent
                    }
                }
                "plurals" -> {
                    val name = node.getAttribute("name")
                    if (name.isNotBlank()) {
                        assertTrue(
                            "Duplicate <plurals name=\"$name\"> in ${xmlFile.parentFile?.name}/${xmlFile.name}",
                            name !in pluralsOut,
                        )
                        val items = mutableMapOf<String, String>()
                        val itemNodes = node.getElementsByTagName("item")
                        for (j in 0 until itemNodes.length) {
                            val item = itemNodes.item(j) as? Element ?: continue
                            val quantity = item.getAttribute("quantity")
                            if (quantity.isNotBlank()) {
                                items[quantity] = item.textContent
                            }
                        }
                        pluralsOut[name] = items
                    }
                }
            }
        }
    }

    private fun extractFormatPlaceholders(text: String): Set<String> = formatSpecRegex.findAll(text)
        .filter { it.value != "%%" }
        .mapIndexed { index, match ->
            val explicitIndex = match.groupValues[1].takeIf { it.isNotBlank() } ?: (index + 1).toString()
            val conversion = match.groupValues[2].lowercase()
            "%$explicitIndex\$$conversion"
        }
        .toSet()
}
