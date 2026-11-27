@file:OptIn(ExperimentalJsExport::class)

package com.music.bitchord.web

import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.ui.replay.year.BestPart
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** A synced lyric line, as much of one as [BestPart.chorusMs] reads. */
@JsExport
class BestPartLine(val timeMs: Double, val text: String)

/** BestPart.kt `BestPartStart`: where the best part starts, and what said so (HEATMAP, LYRICS, FALLBACK). */
@JsExport
class BestPartPick(val startMs: Double, val source: String)

/**
 * BestPart.kt `BestPart.pick`, the Android app's own choice: the bump in YouTube's
 * "Most replayed" curve, else the chorus from synced lyrics, else a fixed point.
 * [nextResponse] is the raw www.youtube.com `next` answer (WEB client), or null.
 */
@JsExport
fun bestPart(nextResponse: String?, lines: Array<BestPartLine>, durationMs: Double): BestPartPick {
    val heat = nextResponse
        ?.let { runCatching { Json.parseToJsonElement(it) as? JsonObject }.getOrNull() }
        ?.let(BestPart::parseHeatmap)
    val pick = BestPart.pick(heat, lines.map { LyricLine(it.timeMs.toLong(), it.text) }, durationMs.toLong())
    return BestPartPick(pick.startMs.toDouble(), pick.source.name)
}
