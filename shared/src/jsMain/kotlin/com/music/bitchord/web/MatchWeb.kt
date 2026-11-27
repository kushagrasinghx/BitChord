@file:OptIn(ExperimentalJsExport::class)

package com.music.bitchord.web

import com.music.bitchord.data.model.Song
import com.music.bitchord.data.sources.TrackMatcher
import kotlin.math.roundToInt

/** One catalogue row, as much of a Song as TrackMatcher.kt reads. */
@JsExport
class MatchRow(val title: String, val artist: String, val durationSec: Double?, val album: String?, val explicit: Boolean?)

/** TrackMatcher.kt `Target`: the recording being looked for. */
@JsExport
class MatchWanted(
    val title: String,
    val artist: String,
    val durationSec: Double?,
    val album: String?,
    val isExplicit: Boolean?,
    val isVideo: Boolean,
)

// youtubei.js gives NaN for a runtime it couldn't read; like a zero, that is no runtime.
private fun seconds(value: Double?): Int? = value?.takeIf { it.isFinite() && it > 0 }?.roundToInt()

// A Song states its runtime as text ("3:45"), which TrackMatcher.secondsOf reads back.
private fun MatchRow.song(): Song {
    val seconds = seconds(durationSec)
    val clock = seconds?.let { "${it / 60}:${(it % 60).toString().padStart(2, '0')}" }
    return Song("", title, artist, thumbnailUrl = null, durationText = clock, albumName = album, isExplicit = explicit)
}

private fun MatchWanted.target() =
    TrackMatcher.Target(title, artist, seconds(durationSec), album, isExplicit, isVideo)

/** TrackMatcher.kt `ranked`: the rows that really are [wanted], most confident first, as indices into [rows]. */
@JsExport
fun matchRanked(rows: Array<MatchRow>, wanted: MatchWanted): Array<Int> {
    val songs = rows.map { it.song() }
    return TrackMatcher.ranked(songs, wanted.target()).map { picked -> songs.indexOfFirst { it === picked } }.toTypedArray()
}

/** TrackMatcher.kt `queries`: what to put to a search box, best first. */
@JsExport
fun matchQueries(wanted: MatchWanted): Array<String> = TrackMatcher.queries(wanted.target()).toTypedArray()

/** TrackMatcher.kt `hasConflictingAlbums`. */
@JsExport
fun matchHasConflictingAlbums(rows: Array<MatchRow>, wanted: MatchWanted): Boolean =
    TrackMatcher.hasConflictingAlbums(rows.map { it.song() }, wanted.target())

/** TrackMatcher.kt `uniquelyMostCreditedCloseMatch`, as an index into [rows]; -1 for none. */
@JsExport
fun matchMostCredited(rows: Array<MatchRow>, wanted: MatchWanted): Int {
    val songs = rows.map { it.song() }
    val pick = TrackMatcher.uniquelyMostCreditedCloseMatch(songs, wanted.target())
    return songs.indexOfFirst { it === pick }
}

/** TrackMatcher.kt `foldLatin`: Latin letters without their accents. */
@JsExport
fun foldLatin(text: String): String = TrackMatcher.foldLatin(text)

/** TrackMatcher.kt `romajiOf`: kana in Hepburn romaji. */
@JsExport
fun romajiOf(text: String): String = TrackMatcher.romajiOf(text)
