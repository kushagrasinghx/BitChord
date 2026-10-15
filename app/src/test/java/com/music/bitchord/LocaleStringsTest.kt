package com.music.bitchord

import com.music.bitchord.ui.components.SUPPORTED_LANGUAGES
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Every language the app offers must translate every key in values/strings.xml. A missing key
 * doesn't crash, it silently falls back to English mid-screen, so this catches it at test time.
 */
@RunWith(Parameterized::class)
class LocaleStringsTest(private val tag: String) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun locales(): List<String> = SUPPORTED_LANGUAGES.map { it.tag }.filter { it != "en" }

        private val resDir: File by lazy {
            val projectRoot = File(".").canonicalFile
            if (File(projectRoot, "app/src/main/res").exists()) {
                File(projectRoot, "app/src/main/res")
            } else {
                File(projectRoot, "src/main/res")
            }
        }

        private fun parse(file: File): Document =
            DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)

        /** Keys that need translating: `translatable="false"` entries are excluded. */
        private fun translatableKeys(doc: Document): Set<String> {
            val keys = mutableSetOf<String>()
            for (tagName in listOf("string", "plurals", "string-array")) {
                val nodes = doc.getElementsByTagName(tagName)
                for (i in 0 until nodes.length) {
                    val element = nodes.item(i) as Element
                    if (element.getAttribute("translatable") == "false") continue
                    val name = element.getAttribute("name")
                    if (name.isNotEmpty()) keys.add(name)
                }
            }
            return keys
        }

        private val baseKeys: Set<String> by lazy {
            translatableKeys(parse(File(resDir, "values/strings.xml")))
        }
    }

    @Test
    fun strings_containsAllBaseKeys() {
        // BCP 47 tags don't map 1:1 to resource dirs: zh-TW lives in
        // values-zh-rTW and zh-CN falls back to values-zh. Resolve the real
        // dir the same way Android does — exact tag, then language-region,
        // then bare language — instead of demanding a dir per tag.
        val candidates = buildList {
            add(File(resDir, "values-$tag/strings.xml"))
            if ("-" in tag) {
                val lang = tag.substringBefore("-")
                val region = tag.substringAfter("-").uppercase()
                add(File(resDir, "values-$lang-r$region/strings.xml"))
                add(File(resDir, "values-$lang/strings.xml"))
            }
        }
        val localeFile = candidates.firstOrNull { it.exists() }
        assertTrue(
            "no strings file found for $tag (tried ${candidates.map { it.name }})",
            localeFile != null,
        )

        val missingKeys = baseKeys - translatableKeys(parse(localeFile!!))
        assertTrue(
            "${localeFile.parentFile.name}/strings.xml is missing ${missingKeys.size} keys from values/strings.xml: $missingKeys",
            missingKeys.isEmpty(),
        )
    }
}
