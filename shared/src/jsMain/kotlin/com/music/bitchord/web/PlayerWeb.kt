@file:OptIn(ExperimentalJsExport::class)

package com.music.bitchord.web

import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.model.youTubeThumbnailFallback as modelThumbnailFallback
import com.music.bitchord.playback.AutoMixArtworkTuning
import com.music.bitchord.ui.replay.year.BestPart
import com.music.bitchord.ui.replay.year.Heatmap
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/*
 * The now-playing screen's rules, for the web player: the Best Part button's curve,
 * the AutoMix cover dissolve's timing and mask, and the YouTube thumbnail fallback.
 * Adapters only, as in Web.kt: the rules themselves are the Android app's own.
 */

/**
 * BestPart.kt `Heatmap`. Kept by the web between visits, as BestParts.kt keeps the
 * curve rather than the start on the phone: the start depends on lyrics as well.
 */
@JsExport
class BestPartHeat(val segmentMs: Double, val intensity: DoubleArray)

/** BestPart.kt `parseHeatmap`: the "Most replayed" curve in a youtube.com `next` answer, or null. */
@JsExport
fun bestPartHeatmap(nextResponse: String): BestPartHeat? =
    runCatching { Json.parseToJsonElement(nextResponse) as? JsonObject }.getOrNull()
        ?.let(BestPart::parseHeatmap)
        ?.let { heat -> BestPartHeat(heat.segmentMs.toDouble(), DoubleArray(heat.intensity.size) { heat.intensity[it].toDouble() }) }

/** BestPart.kt `BestPart.pick` from a kept curve (null when the video has none). */
@JsExport
fun bestPartFromHeat(heat: BestPartHeat?, lines: Array<BestPartLine>, durationMs: Double): BestPartPick {
    val curve = heat?.let { Heatmap(it.segmentMs.toLong(), it.intensity.map(Double::toFloat)) }
    val pick = BestPart.pick(curve, lines.map { LyricLine(it.timeMs.toLong(), it.text) }, durationMs.toLong())
    return BestPartPick(pick.startMs.toDouble(), pick.source.name)
}

/** AutoMixVisualState.kt `AutoMixArtworkWindow`, in seconds on the mix's own clock. */
@JsExport
class AutoMixWindow(val startSeconds: Double, val endSeconds: Double)

/** `AutoMixArtworkTuning.windowAfter`: when the cover dissolves, for a swap at [swap] in an overlap ending at [end]. */
@JsExport
fun autoMixArtworkWindow(swap: Double, end: Double, preparedForSeconds: Double): AutoMixWindow? =
    AutoMixArtworkTuning.windowAfter(swap, end, preparedForSeconds)?.let { AutoMixWindow(it.startSeconds, it.endSeconds) }

/** `AutoMixArtworkTuning.revealProgress`: the dissolve's easing over its window. */
@JsExport
fun autoMixRevealProgress(progress: Double): Double = AutoMixArtworkTuning.revealProgress(progress.toFloat()).toDouble()

/** `AutoMixArtworkTuning`'s mask and blur numbers, which AndroidAutoMixReveal.kt's shader reads. */
@JsExport
class AutoMixMaskTuning(
    val noiseScale: Double,
    val noiseSoftness: Double,
    val noiseLow: Double,
    val noiseHigh: Double,
    val noiseSeedSpan: Double,
    val noiseOctaves: Int,
    val blurStartDp: Double,
    val blurEndProgress: Double,
)

@JsExport
fun autoMixMaskTuning(): AutoMixMaskTuning = with(AutoMixArtworkTuning) {
    AutoMixMaskTuning(
        NOISE_SCALE.toDouble(), NOISE_SOFTNESS.toDouble(), NOISE_LOW.toDouble(), NOISE_HIGH.toDouble(),
        NOISE_SEED_SPAN.toDouble(), NOISE_OCTAVES, BLUR_START_DP.toDouble(), BLUR_END_PROGRESS.toDouble(),
    )
}

/** Models.kt `youTubeThumbnailFallback`: the `hqdefault` every video has, for an upgraded thumbnail; else null. */
@JsExport
fun youTubeThumbnailFallback(url: String): String? = modelThumbnailFallback(url)
