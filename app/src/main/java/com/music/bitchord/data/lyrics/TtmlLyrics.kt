package com.music.bitchord.data.lyrics

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource

/**
 * Apple Music's word-timed lyric format.
 *
 * A document is `<p>` per sung line, each holding one `<span>` per syllable
 * with its own `begin`/`end`:
 *
 * ```xml
 * <p begin="27.395" end="28.960" ttm:agent="v1">
 *   <span begin="27.395" end="27.549">I</span>
 *   <span begin="27.549" end="27.740">been</span>
 * </p>
 * ```
 *
 * Syllables of one word are written as adjacent spans with no whitespace
 * between them ("e" + "nough"), so whitespace — not the span boundary — is
 * what separates words. That is the whole trick to reading this format.
 *
 * Parsed with DOM rather than a pull parser so this stays plain JVM code and
 * can be unit tested off-device.
 */
object TtmlLyrics {

    /**
     * Roles that are not the lead vocal. Translations and romanisations are
     * alternate renderings of the same line and would double it up;
     * background vocals overlap the line they answer, which the player's
     * "last line whose stamp has passed" cursor has no way to show.
     */
    private val SKIPPED_ROLES = setOf("x-translation", "x-roman", "x-bg")

    fun parse(ttml: String): List<LyricLine> = runCatching {
        val factory = DocumentBuilderFactory.newInstance().apply {
            // The document declares four namespaces and we address attributes
            // by their qualified names (ttm:agent), so leave prefixes intact.
            isNamespaceAware = false
            // Lyrics arrive from a third-party host; refuse to resolve
            // anything the document asks us to go and fetch.
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
        val document = factory.newDocumentBuilder().parse(InputSource(StringReader(ttml)))
        val paragraphs = document.getElementsByTagName("p")

        val lines = ArrayList<LyricLine>(paragraphs.length)
        for (i in 0 until paragraphs.length) {
            val paragraph = paragraphs.item(i) as? Element ?: continue
            lineFrom(paragraph)?.let(lines::add)
        }
        lines.sortedBy { it.timeMs }.withInstrumentalGaps()
    }.getOrDefault(emptyList())

    private fun lineFrom(paragraph: Element): LyricLine? {
        val pieces = mutableListOf<Piece>()
        collect(paragraph, pieces)
        val words = mergeIntoWords(pieces)

        if (words.isEmpty()) {
            // Line-synced TTML: a <p> with a stamp and bare text, no spans.
            val text = paragraph.textContent?.trim().orEmpty()
            val begin = time(paragraph.getAttribute("begin")) ?: return null
            return if (text.isEmpty()) null else LyricLine(begin, text)
        }

        // Prefer the paragraph's own stamp: Apple sets it a hair before the
        // first syllable on lines that open with a soft consonant, and that
        // lead-in is when the line should appear.
        val begin = time(paragraph.getAttribute("begin")) ?: words.first().startMs
        return LyricLine(
            timeMs = minOf(begin, words.first().startMs),
            text = words.joinToString(" ") { it.text },
            words = words,
        )
    }

    /**
     * Flattens a paragraph into timed spans and the whitespace between them.
     * Nested spans (Apple wraps background vocals, and occasionally whole
     * phrases, in an outer timed span) recurse to their leaves, so only the
     * innermost timings — the ones actually per-syllable — survive.
     */
    private fun collect(node: Node, out: MutableList<Piece>) {
        val children = node.childNodes
        for (i in 0 until children.length) {
            when (val child = children.item(i)) {
                is Element -> {
                    if (child.getAttribute("ttm:role") in SKIPPED_ROLES) continue
                    val begin = time(child.getAttribute("begin"))
                    val end = time(child.getAttribute("end"))
                    if (begin != null && end != null && !hasTimedChild(child)) {
                        out += Piece.Timed(child.textContent.orEmpty(), begin, end)
                    } else {
                        collect(child, out)
                    }
                }
                else -> if (child.nodeType == Node.TEXT_NODE) {
                    val text = child.textContent.orEmpty()
                    if (text.isNotEmpty()) out += Piece.Text(text)
                }
            }
        }
    }

    private fun hasTimedChild(element: Element): Boolean {
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i) as? Element ?: continue
            if (child.getAttribute("begin").isNotEmpty() || hasTimedChild(child)) return true
        }
        return false
    }

    /**
     * Glues syllables back into words. A word ends at the first whitespace
     * after it — whether that whitespace is a text node between two spans or
     * part of a span's own text — and its span runs from the first syllable's
     * start to the last one's end.
     */
    private fun mergeIntoWords(pieces: List<Piece>): List<LyricWord> {
        val words = mutableListOf<LyricWord>()
        val current = StringBuilder()
        var start = 0L
        var end = 0L
        // Untimed text is punctuation hanging off a span, or a line that was
        // never word-timed at all. Either way it can't carry a word of its
        // own — a word needs a span to get its timing from.
        var timed = false

        fun flush() {
            val text = current.toString().trim()
            current.setLength(0)
            if (text.isNotEmpty() && timed) words += LyricWord(start, end, text)
            timed = false
        }

        pieces.forEach { piece ->
            when (piece) {
                is Piece.Text -> when {
                    piece.text.isBlank() -> flush()
                    // Trailing punctuation belongs to the word it follows;
                    // anything before the first span has no timing to join.
                    timed -> current.append(piece.text)
                    else -> Unit
                }
                is Piece.Timed -> {
                    if (piece.text.isBlank()) return@forEach
                    // Leading whitespace closes off whatever came before it.
                    if (piece.text.first().isWhitespace()) flush()
                    if (current.isEmpty()) start = piece.start
                    current.append(piece.text.trim())
                    end = piece.end
                    timed = true
                    if (piece.text.last().isWhitespace()) flush()
                }
            }
        }
        flush()
        return words
    }

    /**
     * TTML clock values: `27.395`, `1:05.20`, `1:02:03.4`, or a plain number
     * with a `s`/`ms` unit. Returned in milliseconds.
     */
    internal fun time(value: String?): Long? {
        val raw = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (raw.endsWith("ms")) return raw.dropLast(2).toDoubleOrNull()?.toLong()
        val stripped = raw.removeSuffix("s")
        val parts = stripped.split(':')
        val seconds = when (parts.size) {
            1 -> parts[0].toDoubleOrNull()
            2 -> parts[0].toDoubleOrNull()?.let { m -> parts[1].toDoubleOrNull()?.let { m * 60 + it } }
            3 -> parts[0].toDoubleOrNull()?.let { h ->
                parts[1].toDoubleOrNull()?.let { m ->
                    parts[2].toDoubleOrNull()?.let { h * 3600 + m * 60 + it }
                }
            }
            else -> null
        } ?: return null
        return (seconds * 1000).toLong()
    }

    private sealed interface Piece {
        data class Text(val text: String) : Piece
        data class Timed(val text: String, val start: Long, val end: Long) : Piece
    }
}
