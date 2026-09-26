package com.music.bitchord.desktop

import com.music.bitchord.data.model.Song
import com.music.bitchord.data.NerdStats
import com.music.bitchord.data.innertube.StreamResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

data class DesktopPlaybackState(
    val song: Song? = null,
    val isPlaying: Boolean = false,
    val volume: Float = 1.0f,
    val isLoading: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val error: String? = null,
    val streamFormat: DesktopStreamFormat? = null,
    /** Which configured source is serving this — null before anything opened. */
    val streamSourceId: String? = null,
    /** Whether a source is still looking for a better copy of this track while it plays. */
    val searchingBetter: Boolean = false,
    /** True while two tracks are actually being mixed, which the scrubber shows. */
    val mixing: Boolean = false,
    /** Where the next transition will sit, as fractions of this track — drawn on the scrubber. */
    val transitionWindow: com.music.bitchord.data.settings.TransitionWindow? = null,
    /** How far Automix has got on this track and the next — see the player's line. */
    val smartAnalysis: com.music.bitchord.data.settings.SmartAnalysis =
        com.music.bitchord.data.settings.SmartAnalysis(),
)

internal data class DesktopStream(
    val url: String,
    val format: DesktopStreamFormat = DesktopStreamFormat(),
    val headers: Map<String, String> = emptyMap(),
    /** The configured source that produced this stream, for media-failure fallback. */
    val sourceId: String? = null,
    /** Whether the source says this is the immersive mix rather than a stereo one. */
    val isDolbyAtmos: Boolean = false,
    /**
     * Whether this server will only hand the file over a window at a time — see
     * [DesktopRangeStream].
     */
    val windowedReads: Boolean = false,
)

data class DesktopStreamFormat(
    val codec: String? = null,
    val kbps: Int? = null,
    val sampleRateHz: Int? = null,
    val bitDepth: Int? = null,
    /** How many channels the decoder is being fed; null before anything measured one. */
    val channels: Int? = null,
) {
    /** The codec's short name, whether it arrived bare or inside a MIME type. */
    private val codecName: String?
        get() = codec?.substringAfterLast('/')?.substringBefore(';')?.trim()?.lowercase()

    val isLossless: Boolean
        get() = codecName != null && (
            codecName in LOSSLESS_CODECS ||
                // FFmpeg names every uncompressed flavour `pcm_<layout>`, and Media3 names the same
                // bytes `audio/raw`.
                codecName.orEmpty().startsWith("pcm_") ||
                codecName == "raw"
            )

    /** Dolby Atmos decoded as E-AC-3 JOC; deliberately distinct from lossless. */
    val isDolbyAtmos: Boolean
        get() = codecName == "eac3" || codecName == "eac3-joc" || codecName == "ec-3"

    /**
     * Better than CD — the line Tidal, Qobuz and Apple Music all draw it at: past 16-bit or past
     * 48kHz, not merely lossless.
     */
    val isHiRes: Boolean
        get() = isLossless && ((bitDepth ?: 0) > 16 || (sampleRateHz ?: 0) > 48_000)

    /** Lossy, but the top of what lossy gets — a 320kbps AAC rather than YouTube's 160kbps Opus. */
    val isHiQuality: Boolean
        get() = !isLossless && (kbps ?: 0) >= HI_QUALITY_KBPS

    /** The rate to state on screen. */
    private val statedKbps: Int?
        get() = when {
            !isLossless -> kbps
            bitDepth != null && sampleRateHz != null && channels != null ->
                bitDepth * sampleRateHz * channels / 1_000
            else -> null
        }

    /** The codec under its usual name rather than its container's or FFmpeg's. */
    internal val codecLabel: String?
        get() = when (val name = codecName) {
            null -> null
            "opus" -> "Opus"
            "aac", "mp4a-latm", "mp4a", "mp4", "m4a" -> "AAC"
            "vorbis" -> "Vorbis"
            "mp3", "mpeg", "mpeg-4" -> "MP3"
            "flac" -> "FLAC"
            "alac" -> "ALAC"
            "eac3", "ec-3", "eac3-joc" -> "E-AC-3"
            "ac3" -> "AC-3"
            else -> if (name.startsWith("pcm_")) "PCM" else name.uppercase()
        }

    /** "FLAC · 24-bit · 96.0 kHz · 4608 kbps · Stereo" — whichever of those is actually known. */
    val summary: String
        get() = listOfNotNull(
            codecLabel,
            bitDepth?.let { "$it-bit" },
            sampleRateHz?.let { "${it / 1000f} kHz".replace(".0 ", " ") },
            statedKbps?.let { "$it kbps" },
            when (channels) {
                null -> null
                1 -> "Mono"
                2 -> "Stereo"
                else -> "$channels channels"
            },
        ).joinToString(" · ").ifBlank { "Unknown format" }

    private companion object {
        val LOSSLESS_CODECS = setOf("flac", "alac", "wav", "aiff", "ape", "wv", "wavpack", "dsf", "dff", "tta")

        /** The bitrate a lossy stream has to reach to be worth calling out. */
        const val HI_QUALITY_KBPS = 256
    }
}

/**
 * A YouTube track's stream, resolved the way the phone resolves it.
 *
 * The phone's own [StreamResolver], shared from `:shared`: InnerTubeX's
 * live-benchmarked client catalog and cipher tiers first, each minted URL
 * probed before it is trusted, NewPipe as the one fallback, and a track that
 * cannot play remembered as such. The media fetch wears the headers of
 * whichever client minted the URL.
 */
internal object DesktopStreamClient {
    suspend fun resolve(song: Song, maxKbps: Int = Int.MAX_VALUE): Result<DesktopStream> = runCatching {
        val url = withContext(Dispatchers.IO) {
            ceiling.set(maxKbps)
            try {
                StreamResolver.resolve(song.videoId)
            } finally {
                ceiling.remove()
            }
        }
        DesktopTrackLog.log("  youtube: resolved '${song.title}' through InnerTubeX")
        DesktopStream(
            url = url,
            format = DesktopStreamFormat(
                codec = mimeOf(url),
                kbps = NerdStats.pickedBitrateKbps(song.videoId),
            ),
            headers = StreamResolver.mediaHeadersFor(url),
            sourceId = "youtube",
            // Google's media servers answer 403 to a request for a whole file and 206 to one for
            // a window of it, and FFmpeg's own HTTP client asks for the whole thing.
            windowedReads = true,
        )
    }

    suspend fun resolveUrl(song: Song): Result<String> = resolve(song).map(DesktopStream::url)

    /**
     * The ceiling a resolve on this thread was asked for, read by
     * [StreamResolver.maxKbps] — the phone reads its own setting there instead.
     */
    internal val ceiling = ThreadLocal<Int>()

    /** `mime=audio/webm` off a googlevideo URL: the container, which is what FFmpeg is told. */
    private fun mimeOf(url: String): String? =
        runCatching { java.net.URI(url).rawQuery }.getOrNull()
            ?.split('&')
            ?.firstOrNull { it.startsWith("mime=") }
            ?.substringAfter('=')
            ?.let { java.net.URLDecoder.decode(it, "UTF-8") }
            ?.substringAfterLast('/')
            ?.lowercase()
}
