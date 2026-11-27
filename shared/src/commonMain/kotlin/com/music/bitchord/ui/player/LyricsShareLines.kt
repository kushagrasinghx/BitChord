package com.music.bitchord.ui.player

import com.music.bitchord.data.lyrics.LyricLine

/*
 * The lyrics share's rules: what a pick puts on the card, how much it may hold,
 * and which stretches of the song the share video plays. Plain Kotlin, so the
 * web player's share sheet runs these same ones (shared/src/jsMain/.../web/LyricsWeb.kt).
 */

/**
 * One picked passage, as the string that goes on the card.
 *
 * Held as text rather than as [LyricLine] because the card has no idea what a
 * line's timing is: it is handed what the reader was looking at, translation and
 * all, and nothing else.
 *
 * @property subText The romanization or translation drawn small underneath, or
 * null when the panel was showing the original alone.
 * @property isGap A stand-in for the lines skipped between two picked ones,
 * drawn as a centred ellipsis rather than as words.
 */
data class LyricsShareLine(
    val text: String,
    val subText: String?,
    val isGap: Boolean = false,
    /**
     * Where the line is sung in the track, when the lyrics are synced: the
     * share video plays this stretch of the song and lights the line for it.
     * Null for unsynced lyrics and for gap markers.
     */
    val startMs: Long? = null,
    val endMs: Long? = null,
    /**
     * The line as the panel animates it — word and syllable stamps included —
     * so the share video can sweep, lift and swell the words exactly as the
     * player does. Null for unsynced lyrics.
     */
    val timing: LyricLine? = null,
)

/**
 * How much text one card is allowed to hold: the picked words, in characters.
 *
 * About five short lines — the point where the size ladder still lands near its
 * top rung and the type stays big enough to read in a chat without pinching.
 * Past it the only ways to keep going would be to shrink the type or to grow
 * the card past the 9:16 it is meant to be, so the player stops the pick
 * instead: the line is not added, and the bar says why. Nothing already picked
 * is ever dropped.
 */
const val SHARE_CARD_CHAR_BUDGET = 250

/**
 * Whether a line of [text] can still join a card already holding [taken]
 * characters — the whole of the rule the player enforces at the pick.
 *
 * Only the words are counted. The translation or romanization drawn under each
 * line rides along and costs the card height, which the ladder pays for by
 * taking a rung or two; counting it here instead would make the same five lines
 * pickable or not depending on a toggle that has nothing to do with how long
 * the verse is.
 */
fun fitsOnCard(taken: Int, text: String?): Boolean =
    taken + (text?.length ?: 0) <= SHARE_CARD_CHAR_BUDGET

/**
 * The lines [picks] (indices into [lines]) put on a card, in order: each with
 * the translation or romanization [subLines] has for it and, when the lyrics
 * are synced, the stretch of the track it is sung over.
 */
fun pickedShareLines(
    lines: List<LyricLine>,
    subLines: List<LyricLine>?,
    picks: Set<Int>,
): List<LyricsShareLine> {
    val chosen = picks.sorted()
    val synced = lines.any { it.timeMs > 0L }
    return buildList {
        chosen.forEachIndexed { position, index ->
            // A jump over unchosen lines is marked on the card, so the
            // picture says the verse was cut here rather than letting two
            // distant halves read as if they ran on from each other.
            if (position > 0 && index > chosen[position - 1] + 1) {
                add(LyricsShareLine(text = "", subText = null, isGap = true))
            }
            val line = lines.getOrNull(index) ?: return@forEachIndexed
            val sub = subLines?.getOrNull(index)?.text
            val window = if (synced) singWindow(lines, index, chosen.getOrNull(position + 1)) else null
            add(
                LyricsShareLine(
                    text = line.text,
                    subText = sub?.takeIf { it.isNotBlank() && it != line.text },
                    startMs = window?.first,
                    endMs = window?.second,
                    timing = line.takeIf { synced },
                ),
            )
        }
    }.filter { it.isGap || it.text.isNotBlank() }
}

/**
 * The stretch of the track line [index] of [lines] is sung over, for the share
 * video: from its stamp to the next line's, so a run of picked lines plays as
 * one unbroken take. The last line of a run (when [nextPicked] isn't the line
 * right after it) stops where the provider says it was sung until, or at the
 * next stamp — but never runs on through a long instrumental after it.
 */
internal fun singWindow(lines: List<LyricLine>, index: Int, nextPicked: Int?): Pair<Long, Long>? {
    val line = lines.getOrNull(index) ?: return null
    val start = line.timeMs.coerceAtLeast(0L)
    val nextStamp = lines.getOrNull(index + 1)?.timeMs?.takeIf { it > start }
    val end = if (nextPicked == index + 1 && nextStamp != null) {
        nextStamp
    } else {
        val tail = line.sungUntilMs?.takeIf { it > start }?.plus(SHARE_TAIL_MS)
            ?: nextStamp
            ?: (start + SHARE_LINE_FALLBACK_MS)
        minOf(tail, nextStamp ?: Long.MAX_VALUE, start + SHARE_LINE_MAX_MS)
    }
    return start to end
}

/** A beat of air after a line's own end, so its last word isn't clipped. */
private const val SHARE_TAIL_MS = 400L

/** A last line with nothing after it to time it by. */
private const val SHARE_LINE_FALLBACK_MS = 5_000L

/** Longest one line is held for when it ends a run: no minute-long outros. */
private const val SHARE_LINE_MAX_MS = 9_000L

/**
 * The picked lines on the share video's clock: which stretches of the track
 * play, back to back, and when each line is lit in the result.
 */
class ShareTimeline(
    /** Start and end of each stretch of the track, in track milliseconds. */
    val segments: List<Pair<Long, Long>>,
    /** Each sung line — its index in the request — and when it is lit, in output μs. */
    val windows: List<LineWindow>,
    val durationMs: Long,
) {
    /** Where in the track the video is at [outputUs]: the stretches laid end to end. */
    fun trackMsAt(outputUs: Long): Long {
        var left = (outputUs / 1000).coerceAtLeast(0L)
        for ((start, end) in segments) {
            val length = end - start
            if (left < length) return start + left
            left -= length
        }
        return segments.lastOrNull()?.second ?: 0L
    }
}

class LineWindow(val line: Int, val fromUs: Long, val toUs: Long)

/**
 * The timeline for [lines], or null when they carry no timing (unsynced
 * lyrics). Lines that run on from each other share one stretch, so a verse
 * plays as one take; a skip ("(...)" on the card) cuts straight to the next.
 */
fun shareTimeline(lines: List<LyricsShareLine>): ShareTimeline? {
    val segments = mutableListOf<Pair<Long, Long>>()
    val windows = mutableListOf<LineWindow>()
    var cursor = 0L
    lines.forEachIndexed { index, line ->
        val start = line.startMs ?: return@forEachIndexed
        val room = MAX_SHARE_MS - cursor
        if (room <= MIN_LINE_MS) return@forEachIndexed
        val end = minOf(line.endMs ?: return@forEachIndexed, start + room)
        if (end - start < MIN_LINE_MS) return@forEachIndexed
        val last = segments.lastOrNull()
        if (last != null && last.second == start) {
            segments[segments.lastIndex] = last.first to end
        } else {
            segments += start to end
        }
        windows += LineWindow(index, cursor * 1000, (cursor + end - start) * 1000)
        cursor += end - start
    }
    return if (windows.isEmpty()) null else ShareTimeline(segments, windows, cursor)
}

/** Instagram's limit for one story clip. */
private const val MAX_SHARE_MS = 60_000L

/** Shorter than this and a "line" is a stamp glitch, not something sung. */
private const val MIN_LINE_MS = 300L
