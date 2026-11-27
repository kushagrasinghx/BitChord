@file:OptIn(ExperimentalJsExport::class)

package com.music.bitchord.web

import com.music.bitchord.data.lyrics.EnhancedLrc
import com.music.bitchord.data.lyrics.LyricAlignment
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.lyrics.LyricSyllable
import com.music.bitchord.data.lyrics.LyricWord
import com.music.bitchord.data.lyrics.TimedRun
import com.music.bitchord.data.lyrics.artistForLyricsSearch as lyricsArtistForLyricsSearch
import com.music.bitchord.data.lyrics.forLyricsSearch as lyricsForLyricsSearch
import com.music.bitchord.data.lyrics.isGeniusSectionHeader
import com.music.bitchord.data.lyrics.lineAlignments as lyricsLineAlignments
import com.music.bitchord.data.lyrics.withBackgroundVocals as lyricsWithBackgroundVocals
import com.music.bitchord.data.lyrics.withInstrumentalGaps as lyricsWithInstrumentalGaps
import com.music.bitchord.data.lyrics.wordsFromRuns as lyricsWordsFromRuns

/*
 * The lyric model crosses as the web's own plain objects (web/app/src/lyrics/model.ts:
 * LyricLine, LyricWord, LyricSyllable, TimedRun), so its callers keep spreading and
 * comparing them as before. Times are milliseconds.
 */

private fun plain(fill: (dynamic) -> Unit): dynamic {
    val o: dynamic = js("({})")
    fill(o)
    return o
}

private fun ms(value: dynamic): Long = (value as Number).toLong()

private fun syllableOf(o: dynamic) = LyricSyllable(ms(o.startMs), ms(o.endMs), o.startChar as Int, o.endChar as Int)

private fun LyricSyllable.toJs(): dynamic = plain {
    it.startMs = startMs.toDouble(); it.endMs = endMs.toDouble(); it.startChar = startChar; it.endChar = endChar
}

private fun wordOf(o: dynamic) =
    LyricWord(ms(o.startMs), ms(o.endMs), o.text as String, (o.syllables as Array<dynamic>).map { syllableOf(it) })

private fun LyricWord.toJs(): dynamic = plain {
    it.startMs = startMs.toDouble(); it.endMs = endMs.toDouble(); it.text = text
    it.syllables = syllables.map { s -> s.toJs() }.toTypedArray()
}

private fun lineOf(o: dynamic): LyricLine {
    val until = o.sungUntilMs
    val background = o.background
    return LyricLine(
        timeMs = ms(o.timeMs),
        text = o.text as String,
        words = (o.words as Array<dynamic>).map { wordOf(it) },
        sungUntilMs = if (until == null) null else ms(until),
        background = if (background == null) null else lineOf(background),
        alignment = if (o.alignment == "end") LyricAlignment.End else LyricAlignment.Start,
    )
}

private fun LyricLine.toJs(): dynamic = plain {
    it.timeMs = timeMs.toDouble(); it.text = text
    it.words = words.map { w -> w.toJs() }.toTypedArray()
    it.sungUntilMs = sungUntilMs?.toDouble()
    it.background = background?.toJs()
    it.alignment = if (alignment == LyricAlignment.End) "end" else "start"
}

private fun linesOf(lines: Array<dynamic>) = lines.map { lineOf(it) }

private fun List<LyricLine>.toJs(): Array<dynamic> = map { it.toJs() }.toTypedArray()

/** LyricLine.kt `LyricSyllable.normalized`: in order, end to end, covering the word. */
@JsExport
fun normalizedSyllables(parts: Array<dynamic>, length: Int): Array<dynamic> =
    LyricSyllable.normalized(parts.map { syllableOf(it) }, length).map { it.toJs() }.toTypedArray()

/** SyllableRuns.kt `wordsFromRuns`: stamped runs glued into words. */
@JsExport
fun wordsFromRuns(runs: Array<dynamic>, spacingIsExplicit: Boolean): Array<dynamic> =
    lyricsWordsFromRuns(runs.map { TimedRun(ms(it.startMs), ms(it.endMs), it.raw as String) }, spacingIsExplicit)
        .map { it.toJs() }.toTypedArray()

/** LyricGaps.kt `withInstrumentalGaps`: blank lines for the instrumental stretches. */
@JsExport
fun withInstrumentalGaps(lines: Array<dynamic>): Array<dynamic> = linesOf(lines).lyricsWithInstrumentalGaps().toJs()

/** BackgroundVocals.kt `withBackgroundVocals`: a trailing "(answer)" becomes the line's background vocal. */
@JsExport
fun withBackgroundVocals(lines: Array<dynamic>): Array<dynamic> = linesOf(lines).lyricsWithBackgroundVocals().toJs()

/** LyricAlignments.kt `lineAlignments`: which side each line is sung from, "start" or "end". [types] maps agent ids to types. */
@JsExport
fun lineAlignments(singers: Array<String?>, types: dynamic): Array<String> {
    val ids = js("Object").keys(types) as Array<String>
    return lyricsLineAlignments(singers.toList(), ids.associateWith { types[it] as String })
        .map { if (it == LyricAlignment.End) "end" else "start" }
        .toTypedArray()
}

/** LyricSections.kt `isGeniusSectionHeader`: Genius's "[Chorus]" markers. */
@JsExport
fun isSectionHeader(text: String): Boolean = isGeniusSectionHeader(text)

/** EnhancedLrc.kt `parse`: word-stamped LRC; empty when nothing in it is word-stamped. */
@JsExport
fun parseEnhancedLrc(lrc: String): Array<dynamic> = EnhancedLrc.parse(lrc).toJs()

/** EnhancedLrc.kt `decodeEntities`: the HTML escapes some providers serve. */
@JsExport
fun decodeEntities(text: String): String = EnhancedLrc.decodeEntities(text)

/** LyricsQuery.kt `forLyricsSearch`: a YouTube title as a lyrics database indexed it. */
@JsExport
fun forLyricsSearch(title: String): String = title.lyricsForLyricsSearch()

/** LyricsQuery.kt `artistForLyricsSearch`: " - Topic" off an auto-generated channel name. */
@JsExport
fun artistForLyricsSearch(artist: String): String = artist.lyricsArtistForLyricsSearch()
