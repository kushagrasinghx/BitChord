/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.player

import android.media.MediaCodecList
import timber.log.Timber
import java.util.Locale

object VideoQualityPreference {
    const val DATA_SAVER = -1

    const val HIGH_QUALITY = -2

    const val STORED_AUTO = 0

    const val AUTO_HEIGHT_CEILING = 1080

    const val DATA_SAVER_HEIGHT_CEILING = 480

    fun isExactHeight(preference: Int?): Boolean = preference != null && preference > 0

    fun ceilingFor(preference: Int?): Int {
        val deviceMax = VideoDecoderCapabilities.maxSupportedHeight()
        val requested =
            when (preference) {
                null -> AUTO_HEIGHT_CEILING
                DATA_SAVER -> DATA_SAVER_HEIGHT_CEILING
                HIGH_QUALITY -> deviceMax
                else -> preference
            }
        return minOf(requested, deviceMax)
    }

    fun toPreferredHeight(stored: Int): Int? = if (stored == STORED_AUTO) null else stored

    fun toStoredQuality(preferredHeight: Int?): Int = preferredHeight ?: STORED_AUTO
}

object VideoDecoderCapabilities {
    private const val FALLBACK_MAX_HEIGHT = 2160

    private val VIDEO_MIME_TYPES =
        setOf(
            "video/av01",
            "video/x-vnd.on2.vp9",
            "video/hevc",
            "video/avc",
        )

    @Volatile
    private var cachedMaxHeight: Int? = null

    fun maxSupportedHeight(): Int =
        cachedMaxHeight ?: probeMaxSupportedHeight().also { probed ->
            cachedMaxHeight = probed
            Timber.tag("VideoDecoder").d("Max decodable video height: %dp", probed)
        }

    private fun probeMaxSupportedHeight(): Int =
        runCatching {
            MediaCodecList(MediaCodecList.REGULAR_CODECS)
                .codecInfos
                .asSequence()
                .filterNot { it.isEncoder }
                .flatMap { info ->
                    info.supportedTypes
                        .asSequence()
                        .map { type -> info to type.lowercase(Locale.US) }
                }.filter { (_, mime) -> mime in VIDEO_MIME_TYPES }
                .mapNotNull { (info, mime) ->

                    runCatching {
                        info.getCapabilitiesForType(mime).videoCapabilities?.supportedHeights?.upper
                    }.getOrNull()
                }.maxOrNull()
        }.getOrElse { error ->
            Timber.tag("VideoDecoder").w(error, "MediaCodecList probe failed")
            null
        }?.takeIf { it > 0 } ?: FALLBACK_MAX_HEIGHT
}

internal fun formatHeightLabel(height: Int): String {
    val qualityName =
        when (height) {
            4320 -> " (8K)"
            2160 -> " (4K)"
            1440 -> " (QHD)"
            1080 -> " (FHD)"
            720 -> " (HD)"
            480 -> " (SD)"
            else -> ""
        }
    return "${height}p$qualityName"
}
