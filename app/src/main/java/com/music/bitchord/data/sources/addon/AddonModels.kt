package com.music.bitchord.data.sources.addon

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/**
 * The wire shapes of the addon protocol this app speaks.
 *
 * An addon is an ordinary HTTP server, not code: it answers `/manifest.json`,
 * `/search?q=` and `/stream/{id}` with JSON, and nothing is ever executed on
 * this side. That is the whole reason this package exists alongside
 * [module][com.music.bitchord.data.sources.module] rather than inside it —
 * the two speak different protocols, and the module one has to download and
 * run somebody's JavaScript to ask the same two questions.
 *
 * Protocol reference: <https://eclipsemusic.app/docs>.
 *
 * Every field the app does not need is absent from these classes rather than
 * present and ignored; what is here is what is read, and a field that stops
 * being read should leave rather than sit here looking load-bearing. The parser
 * is lenient and ignores unknown keys, so an addon that publishes more than
 * this — video renditions, chapters, DRM, ISRCs, artwork sizes — is not a parse
 * failure, it is an addon whose extras this app has no use for yet.
 */

// ── Manifest ─────────────────────────────────────────────────────────────

/**
 * What an addon says it is and what it can do.
 *
 * [resources] is the part that decides anything, and only barely: see
 * [isPlayable] for why `search` is the one thing required and why an addon
 * that declares nothing at all is still given the benefit of the doubt. The
 * manifest is a description of an addon, not the addon — an addon that never
 * published one is identified by asking its endpoints instead, in
 * [SourceFormats.identify].
 */
@Serializable
data class AddonManifest(
    @SerialName("id") val id: String = "",
    @SerialName("name") val name: String = "",
    @SerialName("version") val version: String = "",
    @SerialName("resources") val resources: List<String> = emptyList(),
    /**
     * The settings schema the addon declares, when it declares one.
     *
     * Read for its *defaults*, not to build a UI. The protocol has the host
     * render a form for these and send whatever the user picked back as query
     * parameters on every request; BitChord has no such form, so it sends the
     * declared defaults instead. That is explicitly enough — the spec says an
     * addon "that never reads a setting still behaves correctly", and the
     * default is what the reference host sends until a user changes something.
     * What it buys is the addon that gates its lossless tier behind a
     * `quality` setting: sending nothing leaves that addon on whatever its
     * server-side default happens to be, which is not always the one it
     * advertises.
     */
    @SerialName("settings") val settings: List<AddonSetting> = emptyList(),
) {
    fun declares(resource: String): Boolean =
        resources.any { it.equals(resource, ignoreCase = true) }

    /**
     * Whether this addon is worth asking anything.
     *
     * Only `search` is required, and only when the addon said what it does at
     * all. Everything this app asks an addon starts by looking a track up by
     * name, so one that cannot be searched has nothing to offer — but one that
     * does not declare `stream` may still work, because a search row is allowed
     * to carry its own `streamURL` and
     * [AddonSource][com.music.bitchord.data.sources.AddonSource] falls back to
     * it. An empty [resources] is silence rather than a claim, and silence is
     * settled by asking the endpoints, not by refusing here.
     */
    val isPlayable: Boolean get() = resources.isEmpty() || declares("search")

    /** What to show when the user did not name the addon themselves. */
    val displayName: String get() = name.ifBlank { id }
}

/**
 * One declared setting, reduced to the two things this app does with it.
 *
 * [default] is what gets sent, and [options] is what a `quality` request is
 * matched against — see [AddonClient.settingsFor]. The rest of the schema is a
 * form description: a type, a label, a help line, bounds. The reference host
 * renders a form from those; this app does not, and carrying fields nothing
 * reads would make the class look like it presents a UI it has no part in.
 */
@Serializable
data class AddonSetting(
    @SerialName("key") val key: String = "",
    @SerialName("default") val default: JsonElement? = null,
    @SerialName("options") val options: List<AddonSettingOption> = emptyList(),
) {
    /** [default] as the string that would go in a query parameter, or null. */
    val defaultValue: String? get() = default?.asQueryValue()
}

/** One choice in a select. Only the value travels; the label is the host's to draw. */
@Serializable
data class AddonSettingOption(
    @SerialName("value") val value: JsonElement? = null,
) {
    val stringValue: String? get() = value?.asQueryValue()
}

// ── Search ───────────────────────────────────────────────────────────────

/**
 * A search answer. Every array is optional — the spec is explicit that an
 * addon returns "only what you have" — and only [tracks] is read, because a
 * [MusicSource][com.music.bitchord.data.sources.MusicSource] is asked for
 * tracks and nothing else.
 */
@Serializable
data class AddonSearchResponse(
    @SerialName("tracks") val tracks: List<AddonTrack> = emptyList(),
)

@Serializable
data class AddonTrack(
    @SerialName("id") val id: String = "",
    @SerialName("title") val title: String = "",
    @SerialName("artist") val artist: String = "",
    @SerialName("album") val album: String = "",
    /** Seconds. Read as a double so an addon sending `240.0` is not a parse failure. */
    @SerialName("duration") val duration: Double? = null,
    @SerialName("artworkURL") val artworkURL: String? = null,
    @SerialName("albumArtworkURL") val albumArtworkURL: String? = null,
    /** `mp3`, `flac`, `aac`, `m4a` — what the row is, when the addon says. */
    @SerialName("format") val format: String = "",
    /** Not in the spec, but several addons send it and it says the same thing. */
    @SerialName("audioQuality") val audioQuality: String = "",
    /**
     * A stream URL for the row itself. When present the spec says the host
     * skips the `/stream` call entirely, and so does this app — one round trip
     * saved on the path that has to finish before audio starts.
     */
    @SerialName("streamURL") val streamURL: String? = null,
) {
    val durationSec: Int? get() = duration?.takeIf { it > 0 }?.toInt()

    /** Whichever artwork the addon filled in. */
    val artwork: String? get() = artworkURL?.ifBlank { null } ?: albumArtworkURL?.ifBlank { null }
}

// ── Stream ───────────────────────────────────────────────────────────────

/**
 * What is on the end of a `/stream/{id}` call.
 *
 * The routing fields — [codec], [container], [manifest], [encrypted],
 * [sampleRate], [bitDepth] — are the reason this protocol is a better fit for
 * this app than the one it replaces. A module returned a URL and left the
 * player to sniff it, which is how a DASH manifest with no `.mpd` on the end
 * reached a progressive extractor and failed as an unreadable container. Here
 * the addon states the transport up front, and
 * [StreamContainer][com.music.bitchord.playback.StreamContainer] is told
 * before the first byte is fetched.
 *
 * All of them are optional. The spec asks addons to "send them whenever you
 * know them", which means an addon that knows nothing sends a bare `url` and
 * the old guess-from-the-extension path still applies.
 */
@Serializable
data class AddonStream(
    @SerialName("url") val url: String = "",
    /** Free text: `mp3`, `flac`, `dash`. Describes the audio, not the transport. */
    @SerialName("format") val format: String = "",
    @SerialName("quality") val quality: String = "",
    /** Non-standard spellings of [quality] that addons in the wild send instead. */
    @SerialName("streamQuality") val streamQuality: String = "",
    @SerialName("audioQuality") val audioQuality: String = "",
    @SerialName("codec") val codec: String? = null,
    @SerialName("fileCodec") val fileCodec: String? = null,
    @SerialName("container") val container: String? = null,
    @SerialName("containerFormat") val containerFormat: String? = null,
    /** `none`, `hls` or `dash` — what the URL points at. */
    @SerialName("manifest") val manifest: String? = null,
    /** An older spelling some addons send in place of [manifest]. */
    @SerialName("mediaType") val mediaType: String? = null,
    @SerialName("mimeType") val mimeType: String? = null,
    /** `false`, or the name of a DRM scheme. Anything but false is refused. */
    @SerialName("encrypted") val encrypted: JsonElement? = null,
    @SerialName("sampleRate") val sampleRate: Double? = null,
    @SerialName("bitDepth") val bitDepth: Double? = null,
    /** Bits per second, per the spec's neighbours; normalised in [kbps]. */
    @SerialName("bitrate") val bitrate: Double? = null,
    @SerialName("audioMode") val audioMode: String? = null,
    @SerialName("audioModes") val audioModes: List<String> = emptyList(),
    /**
     * Why there is no [url], when the addon bothered to say.
     *
     * Not in the spec, which has an addon answer a failure with a status code.
     * The live addon measured against does not: asked for a track one of its
     * backends could not serve, it answered `HTTP 200` with
     * `{"error": "No playable audio for … — all clients exhausted"}`, and for
     * another, `{"error": "Deezer ARL required — set it in the setup page"}`.
     *
     * Both are misses as far as this app is concerned and both already behave
     * correctly — no `url` means no stream, and the next source is tried. What
     * this field buys is the log line saying *which* miss it was, and the
     * second of those two is a configuration problem on the addon's own setup
     * page that a user can go and fix. Losing that text would turn it into a
     * track that silently plays from YouTube instead.
     */
    @SerialName("error") val error: String? = null,
) {
    /** Whichever codec field the addon filled in, lowercased. */
    val statedCodec: String?
        get() = (codec?.ifBlank { null } ?: fileCodec?.ifBlank { null })?.lowercase()

    /** Whichever container field the addon filled in, lowercased. */
    val statedContainer: String?
        get() = (container?.ifBlank { null } ?: containerFormat?.ifBlank { null })?.lowercase()

    /** Every free-text quality field at once, for the label readers to scan. */
    val qualityText: String get() = "$quality $streamQuality $audioQuality $format"

    /**
     * Whether this rendition is behind a DRM scheme.
     *
     * The spec is unambiguous that an addon must "never send `drm` unless the
     * request carried `?drm=`", and this app never sends one — so an encrypted
     * answer here is an addon ignoring the protocol, and the honest response is
     * to decline it rather than hand the player bytes it has no key for. Read
     * as an element because the field is specified as "Boolean or String":
     * `false` is clear, and any other value names the scheme.
     */
    val isEncrypted: Boolean
        get() {
            val element = encrypted as? JsonPrimitive ?: return false
            element.booleanOrNull?.let { return it }
            return element.content.isNotBlank() &&
                !element.content.equals("false", ignoreCase = true) &&
                !element.content.equals("none", ignoreCase = true)
        }

    /**
     * The transport the addon declares, normalised, or null when it says the
     * URL is the audio itself — or says nothing at all.
     *
     * Three fields are consulted, and the third is the one that matters in
     * practice. [manifest] is what the spec defines. [mediaType] is not in the
     * spec but the reference web client reads it and the addons that send it
     * mean the same thing.
     *
     * [format] is last and is the reason this is not a one-liner. Measured
     * against a live addon in September 2026, a Tidal Hi-Res track came back
     * as:
     *
     * ```
     *   {"url": "https://im-fa.manifest.tidal.com/1/manifests/Egk0MTc1OTQ2NzEY…",
     *    "format": "dash", "quality": "Tidal · Hi-Res FLAC (DASH)",
     *    "streamQuality": "[Tidal] HI_RES_LOSSLESS"}
     * ```
     *
     * — no `manifest` field, no `codec`, no `container`, and a URL carrying no
     * `.mpd` for anything downstream to sniff. The only statement that it is a
     * manifest at all is the word `dash` in a field the spec documents as
     * describing the *audio*. Read it and the track plays through
     * `DashMediaSource`; ignore it and a DASH document reaches a progressive
     * extractor, which is the `ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED`
     * failure [StreamContainer][com.music.bitchord.playback.StreamContainer]
     * exists to describe.
     *
     * Reading [format] is safe precisely because this normalises rather than
     * accepts: the field usually holds a codec or a container — `flac`, `mp3`,
     * `mp4`, all of which the same live addon also returns — and every one of
     * those falls through to null. Only the two words that can *only* mean a
     * transport are honoured. `none` is a positive statement of "this is a
     * file" and maps to null for the same reason, not because it is unknown.
     */
    val transport: String?
        get() {
            val stated = manifest?.ifBlank { null }
                ?: mediaType?.ifBlank { null }
                ?: format.ifBlank { null }
                ?: return null
            return when (stated.lowercase()) {
                "hls", "m3u8", "application/x-mpegurl", "application/vnd.apple.mpegurl" -> HLS
                "dash", "mpd", "application/dash+xml" -> DASH
                else -> null
            }
        }

    /**
     * The sample rate, from the field if the addon filled it in and from its
     * own quality label if it did not.
     *
     * Both halves are load-bearing. The live addon measured against states
     * neither `sampleRate` nor `bitDepth` on a Hi-Res FLAC and puts everything
     * it knows in free text — which is exactly what the reference web client
     * parses, and the only reason a Hi-Res track can be described as anything
     * more specific than "FLAC".
     */
    val sampleRateHz: Int?
        get() = sampleRate?.takeIf { it > 0 }?.let {
            // Tolerated because a module in the old system did exactly this:
            // some publishers state kHz where the field is specified in Hz.
            if (it < 1000) (it * 1000).toInt() else it.toInt()
        } ?: KHZ_LABEL.find(qualityText)?.groupValues?.get(1)?.toDoubleOrNull()
            ?.takeIf { it > 0 }?.let { (it * 1000).toInt() }

    /** As [sampleRateHz], for bit depth: the number in `24-bit` when there is no field. */
    val bits: Int?
        get() = bitDepth?.takeIf { it > 0 }?.toInt()
            ?: BIT_DEPTH_LABEL.find(qualityText)?.groupValues?.get(1)?.toIntOrNull()
                ?.takeIf { it in 8..32 }

    /**
     * The bitrate in kbps, whichever unit the addon used.
     *
     * The spec does not name a unit for `bitrate`, and addons send both: a
     * FLAC at `1411` and an MP3 at `320000` are the same claim in different
     * units. Anything over 3000 is read as bits per second, which no audio
     * rendition this app will ever play can exceed as kbps.
     */
    val kbps: Int?
        get() = bitrate?.takeIf { it > 0 }?.let { if (it > 3_000) (it / 1000).toInt() else it.toInt() }
            ?: KBPS_LABEL.find(qualityText)?.groupValues?.get(1)?.toIntOrNull()

    /** Whether the addon says this is the immersive mix rather than a stereo one. */
    val isDolbyAtmos: Boolean
        get() = ATMOS.containsMatchIn(
            "$qualityText ${audioMode.orEmpty()} ${audioModes.joinToString(" ")} ${statedCodec.orEmpty()}",
        )

    companion object {
        const val HLS = "hls"
        const val DASH = "dash"

        private val KBPS_LABEL = Regex("""(\d{2,4})\s*kbps""", RegexOption.IGNORE_CASE)
        private val KHZ_LABEL = Regex("""([\d.]+)\s*kHz""", RegexOption.IGNORE_CASE)
        private val BIT_DEPTH_LABEL = Regex("""(\d{1,2})\s*-?\s*bit""", RegexOption.IGNORE_CASE)
        private val ATMOS = Regex("""atmos|dolby|eac3[_-]?joc|ec-?3""", RegexOption.IGNORE_CASE)
    }
}

/**
 * A JSON scalar as the string that would go in a query parameter.
 *
 * Numbers and booleans reach here as unquoted primitives and strings as quoted
 * ones; [JsonPrimitive.content] is the literal text either way — `320` stays
 * `320` and never becomes `320.0`, which matters because the value is handed
 * straight to somebody's query-string parser and compared against an options
 * list. `null` and any composite value have no sensible single-parameter form
 * and are dropped rather than stringified into something an addon would have
 * to guess at.
 */
private fun JsonElement.asQueryValue(): String? {
    if (this is JsonNull) return null
    val primitive = this as? JsonPrimitive ?: return null
    return primitive.content.ifBlank { null }
}
