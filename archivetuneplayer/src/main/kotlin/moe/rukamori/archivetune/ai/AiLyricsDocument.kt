/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ai

import moe.rukamori.archivetune.betterlyrics.QRCParser
import moe.rukamori.archivetune.lyrics.LyricsUtils
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.StringReader
import java.io.StringWriter
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

data class AiLyricsSegment(
    val id: Int,
    val text: String,
)

sealed interface AiLyricsDocument {
    val formatName: String
    val segments: List<AiLyricsSegment>

    fun rebuild(translations: Map<Int, String>): String
}

object AiLyricsDocumentParser {
    fun parse(rawLyrics: String): AiLyricsDocument =
        if (LyricsUtils.isTtml(rawLyrics)) {
            parseTtml(rawLyrics).getOrElse { parseLineBased(rawLyrics, formatName = "TTML fallback") }
        } else if (QRCParser.isQrc(rawLyrics)) {
            parseQrc(rawLyrics)
        } else {
            parseLineBased(
                rawLyrics = rawLyrics,
                formatName = if (rawLyrics.lineSequence().any { SyncedLineRegex.containsMatchIn(it) }) "synced LRC" else "plain text",
            )
        }

    private fun parseLineBased(
        rawLyrics: String,
        formatName: String,
    ): AiLyricsDocument {
        val lines = rawLyrics.split('\n')
        val templates = ArrayList<LineTemplate>(lines.size)
        val segments = ArrayList<AiLyricsSegment>()
        val seenSyncedLineKeys = HashSet<String>()
        lines.forEach { line ->
            val syncedMatch = SyncedLineRegex.matchEntire(line)
            if (syncedMatch != null) {
                val syncedLineKey = syncedMatch.groupValues[1].filterNot { it.isWhitespace() }
                if (!seenSyncedLineKeys.add(syncedLineKey)) return@forEach

                val content = syncedMatch.groupValues[3]
                val segmentId =
                    if (content.isBlank()) {
                        null
                    } else {
                        segments.size.also { segments.add(AiLyricsSegment(it, content.trim())) }
                    }
                templates.add(
                    LineTemplate(
                        prefix = syncedMatch.groupValues[1],
                        separator = syncedMatch.groupValues[2],
                        suffix = syncedMatch.groupValues[4],
                        content = content.trim(),
                        segmentId = segmentId,
                        original = line,
                    ),
                )
            } else {
                val plainMatch = PlainLineRegex.matchEntire(line)
                val content = plainMatch?.groupValues?.get(2).orEmpty()
                val segmentId =
                    if (content.isBlank()) {
                        null
                    } else {
                        segments.size.also { segments.add(AiLyricsSegment(it, content.trim())) }
                    }
                templates.add(
                    LineTemplate(
                        prefix = plainMatch?.groupValues?.get(1).orEmpty(),
                        separator = "",
                        suffix = plainMatch?.groupValues?.get(3).orEmpty(),
                        content = content.trim(),
                        segmentId = segmentId,
                        original = line,
                    ),
                )
            }
        }
        return LineBasedLyricsDocument(formatName = formatName, templates = templates, segments = segments)
    }

    private fun parseQrc(rawLyrics: String): AiLyricsDocument {
        val lyricContent = extractQrcLyricContent(rawLyrics)
        val lines = lyricContent.split('\n')
        val entries = ArrayList<QrcLineEntry>(lines.size)
        val segments = ArrayList<AiLyricsSegment>()
        val seenTimingKeys = HashSet<String>()
        lines.forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || QrcMetadataLineRegex.matches(trimmed)) {
                entries.add(QrcLineEntry(raw = line, segmentId = -1, timingPrefix = "", cleanText = ""))
                return@forEach
            }
            val qrcMatch = QrcLineRegex.matchEntire(trimmed)
            if (qrcMatch != null) {
                val startMs = qrcMatch.groupValues[1]
                val durMs = qrcMatch.groupValues[2]
                val content = qrcMatch.groupValues[3]
                val timingKey = "$startMs,$durMs"
                if (!seenTimingKeys.add(timingKey)) {
                    entries.add(QrcLineEntry(raw = line, segmentId = -1, timingPrefix = "", cleanText = ""))
                    return@forEach
                }
                val cleanText = extractQrcCleanText(content)
                if (cleanText.isBlank()) {
                    entries.add(QrcLineEntry(raw = line, segmentId = -1, timingPrefix = "", cleanText = ""))
                    return@forEach
                }
                val segmentId = segments.size
                segments.add(AiLyricsSegment(segmentId, cleanText))
                entries.add(
                    QrcLineEntry(
                        raw = line,
                        segmentId = segmentId,
                        timingPrefix = "[$startMs,$durMs]",
                        cleanText = cleanText,
                    ),
                )
                return@forEach
            }
            val lrcMatch = SyncedLineRegex.matchEntire(line)
            if (lrcMatch != null) {
                val prefix = lrcMatch.groupValues[1]
                val lrcTimingKey = prefix.filterNot { it.isWhitespace() }
                if (!seenTimingKeys.add(lrcTimingKey)) {
                    entries.add(QrcLineEntry(raw = line, segmentId = -1, timingPrefix = "", cleanText = ""))
                    return@forEach
                }
                val content = lrcMatch.groupValues[3].trim()
                if (content.isBlank()) {
                    entries.add(QrcLineEntry(raw = line, segmentId = -1, timingPrefix = "", cleanText = ""))
                    return@forEach
                }
                val segmentId = segments.size
                segments.add(AiLyricsSegment(segmentId, content))
                entries.add(
                    QrcLineEntry(
                        raw = line,
                        segmentId = segmentId,
                        timingPrefix = prefix,
                        cleanText = content,
                    ),
                )
                return@forEach
            }
            val plainMatch = PlainLineRegex.matchEntire(line)
            val plainContent = plainMatch?.groupValues?.get(2).orEmpty().trim()
            if (plainContent.isBlank()) {
                entries.add(QrcLineEntry(raw = line, segmentId = -1, timingPrefix = "", cleanText = ""))
                return@forEach
            }
            val segmentId = segments.size
            segments.add(AiLyricsSegment(segmentId, plainContent))
            entries.add(
                QrcLineEntry(
                    raw = line,
                    segmentId = segmentId,
                    timingPrefix = "",
                    cleanText = plainContent,
                ),
            )
        }
        return QrcLyricsDocument(entries = entries, segments = segments)
    }

    private fun extractQrcCleanText(content: String): String {
        if (content.isEmpty()) return ""
        val words = ArrayList<String>()
        var lastMatchEnd = 0
        QrcWordTimingRegex.findAll(content).forEach { match ->
            val wordText = match.groupValues[1]
            if (wordText.isNotEmpty()) words.add(wordText)
            lastMatchEnd = match.range.last + 1
        }
        if (lastMatchEnd < content.length) {
            val trailing = content.substring(lastMatchEnd).trim()
            if (trailing.isNotEmpty()) words.add(trailing)
        }
        if (words.isEmpty()) return content.trim().replace(Regex("\\s+"), " ")
        return words.joinToString("").trim().replace(Regex("\\s+"), " ")
    }

    private fun extractQrcLyricContent(content: String): String {
        val attributeMatch = QrcLyricContentAttributeRegex.find(content) ?: return content
        val valueStart = attributeMatch.range.last + 1
        val valueEnd = content.indexOf('"', startIndex = valueStart)
        if (valueEnd < 0) return content
        val raw = content.substring(valueStart, valueEnd)
        return raw
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
    }

    private fun parseTtml(rawLyrics: String): Result<AiLyricsDocument> =
        runCatching {
            val factory =
                DocumentBuilderFactory.newInstance().apply {
                    isNamespaceAware = true
                    runCatching { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
                    runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
                    runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
                    runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
                }
            val document = factory.newDocumentBuilder().parse(InputSource(StringReader(rawLyrics)))
            val paragraphs = ArrayList<TtmlParagraph>()
            val segments = ArrayList<AiLyricsSegment>()
            val elements = document.getElementsByTagName("*")
            for (index in 0 until elements.length) {
                val element = elements.item(index) as? Element ?: continue
                if (!element.tagName.endsWith("p", ignoreCase = true)) continue
                val text = readTtmlLineText(element)
                if (text.isBlank()) continue
                val segmentId = segments.size
                val lineKey =
                    readAttributeBySuffix(element, "key")
                        ?: "archivetune-translation-$segmentId".also { key -> element.setAttribute("key", key) }
                segments.add(AiLyricsSegment(segmentId, text))
                paragraphs.add(TtmlParagraph(element = element, segmentId = segmentId, key = lineKey, cleanText = text))
            }
            require(segments.isNotEmpty()) { "TTML has no translatable text" }
            TtmlLyricsDocument(
                document = document,
                originalHadDeclaration = rawLyrics.trimStart().startsWith("<?xml", ignoreCase = true),
                paragraphs = paragraphs,
                segments = segments,
            )
        }

    private fun readTtmlLineText(paragraphElement: Element): String {
        val builder = StringBuilder()
        val children = paragraphElement.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i) ?: continue
            when (child.nodeType) {
                Node.ELEMENT_NODE -> {
                    val childElement = child as? Element ?: continue
                    if (!childElement.tagName.endsWith("span", ignoreCase = true)) continue
                    val spanText = childElement.textContent.orEmpty()
                    if (spanText.isNotEmpty()) builder.append(spanText)
                }
                Node.TEXT_NODE -> {
                    val text = child.textContent.orEmpty()
                    if (text.isNotEmpty()) builder.append(text)
                }
            }
        }
        val joined = builder.toString()

        val collapsed = joined.replace(Regex("\\s+"), " ").trim()
        if (collapsed.isNotEmpty()) return collapsed

        return paragraphElement.textContent?.trim().orEmpty().replace(Regex("\\s+"), " ")
    }

    private fun readAttributeBySuffix(
        element: Element,
        suffix: String,
    ): String? {
        val directValue = element.getAttribute(suffix).takeIf { it.isNotBlank() }
        if (directValue != null) return directValue

        val attrs = element.attributes ?: return null
        for (i in 0 until attrs.length) {
            val node = attrs.item(i) ?: continue
            val name = node.nodeName ?: continue
            if (name.equals(suffix, ignoreCase = true) || name.endsWith(":$suffix", ignoreCase = true)) {
                val value = node.nodeValue?.trim()
                if (!value.isNullOrEmpty()) return value
            }
        }
        return null
    }

    private val SyncedLineRegex = Regex("""^(\s*(?:\[[^\]]+])+)(\s*)(.*?)(\s*)$""")
    private val PlainLineRegex = Regex("""^(\s*)(.*?)(\s*)$""")
    private val QrcLineRegex = Regex("""^\[(\d{1,8}),(\d{1,8})](.*)$""")
    private val QrcWordTimingRegex = Regex("""(.*?)\(\d{1,8},\d{1,8}(?:,\d{1,8})?\)""")
    private val QrcMetadataLineRegex = Regex("""^\[[A-Za-z]+:.*]$""")
    private val QrcLyricContentAttributeRegex = Regex("""LyricContent\s*=\s*\"""", RegexOption.IGNORE_CASE)
}

private data class QrcLineEntry(
    val raw: String,
    val segmentId: Int,
    val timingPrefix: String,
    val cleanText: String,
)

private data class QrcLyricsDocument(
    private val entries: List<QrcLineEntry>,
    override val segments: List<AiLyricsSegment>,
) : AiLyricsDocument {
    override val formatName: String = "QRC"

    override fun rebuild(translations: Map<Int, String>): String =
        entries.flatMap { entry ->
            if (entry.segmentId < 0) {
                listOf(entry.raw)
            } else {
                val translated = translations[entry.segmentId]?.trim().orEmpty()
                if (translated.isBlank() || translated.equals(entry.cleanText, ignoreCase = true)) {
                    listOf(entry.raw)
                } else if (entry.timingPrefix.isEmpty()) {
                    listOf(entry.raw, translated)
                } else {
                    listOf(entry.raw, "${entry.timingPrefix}${translated}")
                }
            }
        }.joinToString("\n")
}

private data class LineTemplate(
    val prefix: String,
    val separator: String,
    val suffix: String,
    val content: String,
    val segmentId: Int?,
    val original: String,
)

private data class LineBasedLyricsDocument(
    override val formatName: String,
    private val templates: List<LineTemplate>,
    override val segments: List<AiLyricsSegment>,
) : AiLyricsDocument {
    override fun rebuild(translations: Map<Int, String>): String =
        templates.joinToString("\n") { template ->
            val id = template.segmentId ?: return@joinToString template.original
            val translated = translations[id]?.trim().orEmpty()
            if (translated.isBlank() || translated.equals(template.content, ignoreCase = true)) {
                template.original
            } else {
                val translatedLine = "${template.prefix}${template.separator}$translated${template.suffix}"
                "${template.original}\n$translatedLine"
            }
        }
}

private data class TtmlParagraph(
    val element: Element,
    val segmentId: Int,
    val key: String,
    val cleanText: String,
)

private data class TtmlLyricsDocument(
    private val document: Document,
    private val originalHadDeclaration: Boolean,
    private val paragraphs: List<TtmlParagraph>,
    override val segments: List<AiLyricsSegment>,
) : AiLyricsDocument {
    override val formatName: String = "TTML"

    override fun rebuild(translations: Map<Int, String>): String {
        removeArchiveTuneTranslationElements(document.documentElement)

        val translatedParagraphs =
            paragraphs.mapNotNull { paragraph ->
                val translated =
                    translations[paragraph.segmentId]
                        ?.trim()
                        ?.takeIf {
                            it.isNotBlank() &&
                                !it.equals(paragraph.cleanText, ignoreCase = true)
                        }
                        ?: return@mapNotNull null
                paragraph to translated
            }

        if (translatedParagraphs.isNotEmpty()) {
            val metadata = findOrCreateMetadataElement(document, document.documentElement)
            val translationElement =
                document.createElement("translation").apply {
                    setAttribute("data-archivetune", "translation")
                }

            translatedParagraphs.forEach { (paragraph, translated) ->
                val textElement =
                    document.createElement("text").apply {
                        setAttribute("for", paragraph.key)
                        appendChild(document.createTextNode(translated))
                    }
                translationElement.appendChild(textElement)
            }

            metadata.appendChild(translationElement)
        }
        return transform(document, originalHadDeclaration)
    }

    private fun removeArchiveTuneTranslationElements(root: Element) {
        val elements = root.getElementsByTagName("*")
        val removableElements = ArrayList<Element>()
        for (index in 0 until elements.length) {
            val element = elements.item(index) as? Element ?: continue
            if (element.tagName.endsWith("translation", ignoreCase = true) &&
                element.getAttribute("data-archivetune") == "translation"
            ) {
                removableElements.add(element)
            }
        }
        removableElements.forEach { element ->
            element.parentNode?.removeChild(element)
        }
    }

    private fun findOrCreateMetadataElement(
        document: Document,
        root: Element,
    ): Element {
        val elements = root.getElementsByTagName("*")
        for (index in 0 until elements.length) {
            val element = elements.item(index) as? Element ?: continue
            if (element.tagName.endsWith("metadata", ignoreCase = true)) return element
        }

        val metadata = document.createElement("metadata")
        val head = findOrCreateHeadElement(document, root)
        head.appendChild(metadata)
        return metadata
    }

    private fun findOrCreateHeadElement(
        document: Document,
        root: Element,
    ): Element {
        val elements = root.getElementsByTagName("*")
        for (index in 0 until elements.length) {
            val element = elements.item(index) as? Element ?: continue
            if (element.tagName.endsWith("head", ignoreCase = true)) return element
        }

        val head = document.createElement("head")
        root.insertBefore(head, root.firstChild)
        return head
    }

    private fun transform(
        document: Document,
        includeDeclaration: Boolean,
    ): String {
        val transformer =
            TransformerFactory.newInstance().newTransformer().apply {
                setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, if (includeDeclaration) "no" else "yes")
                setOutputProperty(OutputKeys.ENCODING, "UTF-8")
            }
        val writer = StringWriter()
        transformer.transform(DOMSource(document), StreamResult(writer))
        return writer.toString()
    }
}
