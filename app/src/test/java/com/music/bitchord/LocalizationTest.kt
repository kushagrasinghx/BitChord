package com.music.bitchord

import com.music.bitchord.ui.components.SUPPORTED_LANGUAGES
import com.music.bitchord.ui.components.languageDisplayNameRes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class LocalizationTest {

    @Test
    fun alarmStrings_arePresentInEverySupportedLocale() {
        val projectRoot = File(".").canonicalFile
        val resDir = if (File(projectRoot, "app/src/main/res").exists()) File(projectRoot, "app/src/main/res") else File(projectRoot, "src/main/res")
        val alarmKeys = Regex("name=\"(alarm_[^\"]+)\"").findAll(File(resDir, "values/strings.xml").readText())
            .map { it.groupValues[1] }.toSet()
            .filter { key ->
                File(resDir.parentFile, "main/java").walkTopDown().filter { it.extension == "kt" }
                    .any { it.readText().contains("R.string.$key") }
            }.toSet()
        SUPPORTED_LANGUAGES.filterNot { it.tag == "en" }.forEach { language ->
            val directory = File(resDir, "values-${language.tag}")
            val translated = directory.listFiles { file -> file.extension == "xml" }.orEmpty()
                .flatMap { Regex("name=\"(alarm_[^\"]+)\"").findAll(it.readText()).map { match -> match.groupValues[1] }.toList() }
                .toSet()
            assertTrue("values-${language.tag} missing alarm keys: ${alarmKeys - translated}", translated.containsAll(alarmKeys))
        }
    }

    @Test
    fun supportedLanguages_containsVietnamese() {
        val viLanguage = SUPPORTED_LANGUAGES.firstOrNull { it.tag == "vi" }
        assertNotNull("Vietnamese language must be in SUPPORTED_LANGUAGES", viLanguage)
        assertEquals(R.string.vietnamese, viLanguage?.nameRes)
        assertEquals(R.string.vietnamese, languageDisplayNameRes("vi"))
    }

    @Test
    fun localesConfig_matchesSupportedLanguages() {
        val projectRoot = File(".").canonicalFile
        val resDir = if (File(projectRoot, "app/src/main/res").exists()) {
            File(projectRoot, "app/src/main/res")
        } else {
            File(projectRoot, "src/main/res")
        }
        val localesConfigFile = File(resDir, "xml/locales_config.xml")
        assertTrue("locales_config.xml must exist", localesConfigFile.exists())

        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(localesConfigFile)
        val localeNodes = doc.getElementsByTagName("locale")

        val configuredLocales = mutableListOf<String>()
        for (i in 0 until localeNodes.length) {
            val node = localeNodes.item(i)
            val name = node.attributes.getNamedItem("android:name")?.nodeValue
            if (name != null) configuredLocales.add(name)
        }

        assertTrue("locales_config.xml must include 'vi'", configuredLocales.contains("vi"))

        for (lang in SUPPORTED_LANGUAGES) {
            assertTrue(
                "locales_config.xml must include '${lang.tag}'",
                configuredLocales.contains(lang.tag),
            )
        }
    }

    @Test
    fun vietnameseStrings_containsAllBaseKeys() {
        val projectRoot = File(".").canonicalFile
        val resDir = if (File(projectRoot, "app/src/main/res").exists()) {
            File(projectRoot, "app/src/main/res")
        } else {
            File(projectRoot, "src/main/res")
        }
        val baseStringsFile = File(resDir, "values/strings.xml")
        val viStringsFile = File(resDir, "values-vi/strings.xml")

        assertTrue("values/strings.xml must exist", baseStringsFile.exists())
        assertTrue("values-vi/strings.xml must exist", viStringsFile.exists())

        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()

        val baseDoc = builder.parse(baseStringsFile)
        val viDoc = builder.parse(viStringsFile)

        fun extractKeys(doc: org.w3c.dom.Document): Set<String> {
            val keys = mutableSetOf<String>()
            val tags = listOf("string", "plurals", "string-array")
            for (tag in tags) {
                val nodes = doc.getElementsByTagName(tag)
                for (i in 0 until nodes.length) {
                    val node = nodes.item(i)
                    val name = node.attributes.getNamedItem("name")?.nodeValue
                    if (name != null) keys.add(name)
                }
            }
            return keys
        }

        val baseKeys = extractKeys(baseDoc)
        val viKeys = extractKeys(viDoc)

        val missingKeys = baseKeys - viKeys
        assertTrue(
            "values-vi/strings.xml must have all keys from values/strings.xml. Missing: $missingKeys",
            missingKeys.isEmpty(),
        )
    }
}
