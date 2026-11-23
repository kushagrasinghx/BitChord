package com.music.bitchord.download

import android.content.Context
import android.net.Uri
import com.music.bitchord.data.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.File
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max

/**
 * An offline copy of an MPEG-DASH audio manifest, saved as an HLS package.
 *
 * ### Why DASH exists here at all
 *
 * Because the shape of a module's stream is the backend's choice and not ours.
 * The Tidal module served `.m3u8` until September 2026 and `.mpd` afterwards —
 * same track, same requested tier, no version change on our side. Playback
 * survived that by being told the mime type
 * ([PlaybackService][com.music.bitchord.playback.PlaybackService]'s
 * `withResolvedStreamType`); downloads did not, because
 * [Downloads.routeFor][Downloads] recognised only `.m3u8` as a manifest and
 * treated everything else as a file. A `.mpd` therefore went down the ordinary
 * fetch-and-save path and wrote **2,738 bytes of XML into a file called
 * `Artist - Track.flac`** — a download that reported success, carried no
 * lyrics and no cover, and could never play. [MediaTagger.embed] wraps its
 * taggers in `runCatching`, so even tagging that file failed quietly.
 *
 * ### Why it is written out as HLS
 *
 * The samples are identical. Both manifests describe the same fMP4 segments —
 * an initialisation segment and a run of numbered media segments — and Media3
 * reads those through the same extractor either way. So the only real
 * difference is the index sitting in front of them, and rewriting the index is
 * far less work than teaching the rest of the app a second package format:
 * [DownloadStore.delete] finds a package by looking for a file named
 * `playlist.m3u8`, [Downloads.remember] finds the saved cover by the same
 * test, and playback picks the HLS source off the `.m3u8` extension. Emitting
 * a playlist those three already understand means none of them change, and an
 * offline DASH download is byte-for-byte as playable as an offline HLS one.
 *
 * The sibling of [OfflineHls], deliberately not merged with it. That one
 * rewrites a playlist it was given and is the code path every existing offline
 * download was saved by; this one builds a playlist from a different index
 * format. Sharing a body between them would put the proven path at risk to
 * save about twenty lines.
 *
 * ### What is not supported
 *
 * Encrypted manifests (`ContentProtection`), and multi-period ones. Both are
 * rejected outright rather than half-saved, on the same reasoning as
 * [OfflineHls]: a package that is missing part of its audio is worse than a
 * download that says it could not be made, because only the second is
 * recoverable by the person it happens to.
 */
internal object OfflineDash {

    /** Whether [url] is a manifest this can save, judged the way the app judges every other one — on the extension. */
    fun handles(url: String): Boolean =
        url.substringBefore('?').endsWith(".mpd", ignoreCase = true)

    suspend fun save(
        context: Context,
        id: String,
        url: String,
        headers: Map<String, String>,
        onProgress: (Long, Long) -> Unit,
        lyrics: LyricsTag.Embeddable?,
        artwork: MediaTagger.Artwork?,
    ): Uri = withContext(Dispatchers.IO) {
        // The same directory the HLS packages live in, and the same layout, so
        // that everything which reads one reads both. The name is historical
        // and is left alone on purpose: it is a path already stored in the
        // download records of every device that has one.
        val root = File(File(context.filesDir, "offline-hls"), id.hashCode().toUInt().toString(16))
        root.deleteRecursively()
        if (!root.mkdirs()) error("Could not create offline package")
        try {
            fun request(target: String) = Request.Builder().url(target).apply {
                headers.forEach { (name, value) -> header(name, value) }
            }.build()
            val manifest = Http.client.newCall(request(url)).execute().use { response ->
                if (!response.isSuccessful) error("DASH manifest failed (HTTP ${response.code})")
                response.body?.string() ?: error("Empty DASH manifest")
            }
            val plan = parse(manifest)
            val base = url.toHttpUrlOrNull() ?: error("Invalid DASH URL")

            // The initialisation segment is segment-00000 so the numbering
            // matches what OfflineHls produces for the same stream — the
            // `#EXT-X-MAP` fragment is index 0 there too.
            val remotes = listOf(plan.initialization) + plan.media
            remotes.forEachIndexed { index, remote ->
                coroutineContext.ensureActive()
                val target = base.resolve(remote)?.toString() ?: error("Invalid DASH segment")
                Http.client.newCall(request(target)).execute().use { response ->
                    if (!response.isSuccessful) error("DASH segment failed (HTTP ${response.code})")
                    val body = response.body?.byteStream() ?: error("Empty DASH segment")
                    body.use { input -> File(root, localName(index)).outputStream().use(input::copyTo) }
                }
                onProgress((index + 1).toLong(), remotes.size.toLong())
            }

            File(root, "playlist.m3u8").writeText(playlist(plan))
            lyrics?.let { File(root, "lyrics.lrc").writeText(it.enhanced ?: it.plain.orEmpty()) }
            artwork?.let { File(root, "cover.${if (it.mime.contains("png")) "png" else "jpg"}").writeBytes(it.bytes) }
            Uri.fromFile(File(root, "playlist.m3u8"))
        } catch (e: Throwable) {
            root.deleteRecursively()
            throw e
        }
    }

    private fun localName(index: Int) = "segment-${index.toString().padStart(5, '0')}.m4s"

    /** One audio rendition's segments, resolved from the template into plain URLs. */
    internal class Plan(
        val initialization: String,
        val media: List<String>,
        val seconds: List<Double>,
    )

    /**
     * The segment list a manifest describes.
     *
     * Read with regexes rather than an XML parser, and narrowly: this is not a
     * general DASH implementation and should not read like one. It handles the
     * single-period, single-audio-representation, `SegmentTemplate` manifests
     * the source modules actually serve, and refuses everything else instead of
     * guessing. Media3 remains the real DASH implementation — this only has to
     * be right about the manifests that reach a download.
     */
    internal fun parse(manifest: String): Plan {
        if (Regex("<ContentProtection", RegexOption.IGNORE_CASE).containsMatchIn(manifest)) {
            error("Encrypted DASH cannot be saved")
        }
        if (Regex("<Period[\\s>]").findAll(manifest).count() > 1) {
            error("Multi-period DASH cannot be saved")
        }
        // Tidal lists every rendition of the track in one manifest — HE-AAC,
        // then AAC-LC, then FLAC — and reading the first SegmentTemplate in the
        // file took HE-AAC every time: 97kbps saved under a FLAC badge. The
        // player never showed it because Media3 picks the best one itself.
        val rendition = best(manifest)
        val scope = rendition?.body?.takeIf { Regex("<SegmentTemplate", RegexOption.IGNORE_CASE).containsMatchIn(it) }
            ?: manifest
        val template = Regex("<SegmentTemplate([^>]*)>", RegexOption.IGNORE_CASE).find(scope)?.groupValues?.get(1)
            ?: error("DASH manifest has no segment template")
        fun attr(name: String) =
            Regex("""\b$name\s*=\s*"([^"]*)"""", RegexOption.IGNORE_CASE).find(template)?.groupValues?.get(1)

        val initialization = attr("initialization")?.let(::unescape)
            ?: error("DASH manifest has no initialization segment")
        val mediaTemplate = attr("media")?.let(::unescape)
            ?: error("DASH manifest has no media template")
        // Only `$Number$` is supported. `$Time$` would need the timeline's
        // running offsets rather than its durations, and no module serves it.
        if (!mediaTemplate.contains("\$Number\$")) error("Unsupported DASH media template")
        val timescale = attr("timescale")?.toDoubleOrNull()?.takeIf { it > 0 } ?: 1.0
        val startNumber = attr("startNumber")?.toIntOrNull() ?: 1

        // A SegmentTimeline states each segment's real length, including the
        // short final one, which is what makes the EXTINF values below exact
        // rather than a division that leaves the last segment overstated.
        val ticks = mutableListOf<Long>()
        // From the chosen rendition's own template: each one ends on a
        // different final segment length.
        Regex("<SegmentTimeline>(.*?)</SegmentTimeline>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(scope)?.groupValues?.get(1)
            ?.let { timeline ->
                Regex("<S\\b([^>]*)/?>", RegexOption.IGNORE_CASE).findAll(timeline).forEach { entry ->
                    val body = entry.groupValues[1]
                    fun of(name: String) =
                        Regex("""\b$name\s*=\s*"([^"]*)"""").find(body)?.groupValues?.get(1)
                    val d = of("d")?.toLongOrNull() ?: return@forEach
                    // `r` is how many times the entry *repeats*, so r="56" is
                    // 57 segments. Reading it as a count drops one segment and
                    // truncates every download by a few seconds.
                    repeat((of("r")?.toIntOrNull() ?: 0) + 1) { ticks += d }
                }
            }
        if (ticks.isEmpty()) {
            // No timeline: fall back to a fixed duration repeated across the
            // presentation, which is the other legal way to write this.
            val d = attr("duration")?.toLongOrNull()?.takeIf { it > 0 }
                ?: error("DASH manifest has neither a timeline nor a segment duration")
            val total = Regex("""mediaPresentationDuration\s*=\s*"([^"]*)"""").find(manifest)
                ?.groupValues?.get(1)?.let(::isoSeconds)
                ?: error("DASH manifest states no duration")
            repeat(max(1, ceil(total / (d / timescale)).toInt())) { ticks += d }
        }

        val media = ticks.indices.map { at ->
            mediaTemplate.replace("\$Number\$", (startNumber + at).toString())
        }
        if (media.isEmpty()) error("DASH manifest has no segments")
        return Plan(initialization, media, ticks.map { it / timescale })
    }

    private class Rendition(val bandwidth: Long, val codecs: String, val body: String)

    /**
     * A manifest ready to be played back from the cache, and the segment URLs
     * to warm alongside it.
     *
     * [text] is the original manifest with two edits, both confined to the
     * rendition [best] chose:
     *
     *  - Every other `Representation` is removed. The player reads this
     *    manifest back and builds its source from it; leaving the lower tiers
     *    in would let adaptive selection wander onto a rendition whose
     *    segments were never warmed. One rendition in, one rendition out —
     *    and [best] is the lossless one, or the manifest is refused below.
     *  - That rendition's `SegmentTemplate` names its segments absolutely.
     *    The player fetches segments by the URLs this manifest carries, and
     *    the cache entries those fetches hit are keyed by URL — the same
     *    strings [media] lists — so the manifest itself is what makes the
     *    warmed entries addressable after the URLs' tokens have expired.
     *
     * Everything about the manifest that [parse] refuses — encrypted,
     * multi-period, `$Time$` templates, no lossless rendition — is refused
     * here too, by returning null; the caller falls back to ordinary live
     * streaming.
     */
    internal class Trimmed(
        val text: String,
        /** The absolute initialization segment URL. */
        val initialization: String,
        /** The absolute media segment URLs, in play order. */
        val media: List<String>,
    )

    internal fun trimmedForCache(manifest: String, manifestUrl: String): Trimmed? {
        val plan = runCatching { parse(manifest) }.getOrNull() ?: return null
        val rendition = best(manifest) ?: return null
        // A manifest with no lossless rendition does not satisfy the Lossless
        // request cache-ahead is working for — warming its AAC would mark the
        // track as served while the setting is still unmet.
        if (!rendition.codecs.isLossless()) return null

        // Strip every other Representation. The match text is compared whole,
        // so the kept block is removed nowhere — two renditions cannot share a
        // bandwidth and codecs pair and produce equal text.
        val kept = Regex(
            """<Representation\b[^>]*?(?:/>|>.*?</Representation>)""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
        val trimmed = kept.replace(manifest) { match ->
            if (match.value == renditionBlock(manifest, rendition)) match.value else ""
        }
        if (trimmed == manifest && kept.findAll(manifest).count() > 1) return null

        // Absolute URLs into the template, escaped the way they were read.
        // The values are read unescaped, resolved against the manifest's own
        // URL, and written back escaped — a round-trip that is a *no-op* for
        // the manifests this meets in practice, because Tidal's own CDN
        // manifests name their segments absolutely already. An unchanged
        // template is therefore not a failure; what must hold is checked
        // below instead of a diff.
        val scope = rendition.body.takeIf {
            Regex("<SegmentTemplate", RegexOption.IGNORE_CASE).containsMatchIn(it)
        } ?: trimmed
        val templateTag = Regex("<SegmentTemplate[^>]*>", RegexOption.IGNORE_CASE).find(scope)
            ?.value
            ?: return null
        val rebuilt = templateTag
            .replaceAttribute("initialization") { unescape(it).let { rel -> escape(absolutize(rel, manifestUrl)) } }
            .replaceAttribute("media") { unescape(it).let { rel -> escape(absolutize(rel, manifestUrl)) } }

        // What the stored manifest must carry: the template the player reads
        // back names an absolute URL for the initialization segment and for
        // its media segments. Anything short of that — an attribute the
        // rewrite lost, a template still naming relative paths with no base
        // to resolve them against — and the warmed segments would not be
        // reachable at all.
        fun valueOf(tag: String, name: String) =
            Regex("""\b$name\s*=\s*"([^"]*)"""", RegexOption.IGNORE_CASE)
                .find(tag)?.groupValues?.get(1)?.let(::unescape)
        val rewrittenInit = valueOf(rebuilt, "initialization")
        val rewrittenMedia = valueOf(rebuilt, "media")
        if (rewrittenInit == null || rewrittenMedia == null ||
            !rewrittenInit.startsWith("http", true) ||
            !rewrittenMedia.startsWith("http", true)
        ) {
            return null
        }

        // Spliced in literally, deliberately: [templateTag] is text, not a
        // pattern, and the URLs inside it can carry regex metacharacters — a
        // `?` query alone would change what a regex replacement matches.
        val at = trimmed.indexOf(templateTag)
        if (at < 0) return null
        val text = trimmed.replaceRange(at, at + templateTag.length, rebuilt)

        val base = absolutize(plan.initialization, manifestUrl)
        val media = plan.media.map { absolutize(it, manifestUrl) }
        if (base.isBlank() || media.isEmpty()) return null
        return Trimmed(text, base, media)
    }

    /** The full `<Representation>` block [rendition] was parsed from. */
    private fun renditionBlock(manifest: String, rendition: Rendition): String =
        Regex(
            """<Representation\b[^>]*?>(?:.*?</Representation>)?""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        ).findAll(manifest)
            .firstOrNull { match ->
                Regex("""\bbandwidth\s*=\s*"([^"]*)"""", RegexOption.IGNORE_CASE)
                    .find(match.value)?.groupValues?.get(1)?.toLongOrNull() == rendition.bandwidth &&
                    Regex("""\bcodecs\s*=\s*"([^"]*)"""", RegexOption.IGNORE_CASE)
                        .find(match.value)?.groupValues?.get(1).orEmpty()
                        .lowercase(Locale.ROOT) == rendition.codecs
            }
            ?.value
            ?: rendition.body.let { "<Representation>$it</Representation>" }

    /** Rewrites `name="value"` inside a `<SegmentTemplate>` tag string. */
    private fun String.replaceAttribute(name: String, map: (String) -> String): String {
        val match = Regex("""\b$name\s*=\s*"([^"]*)"""").find(this) ?: return this
        val replaced = match.value.replace(match.groupValues[1], map(match.groupValues[1]))
        return replaceRange(match.range, replaced)
    }

    /** Re-escapes what [unescape] read, narrowly: the entities a URL can contain. */
    private fun escape(value: String) = value
        .replace("&", "&amp;")
        .replace("\"", "&quot;")

    /**
     * RFC 3986 reference resolution, done on strings so `$Number$` survives
     * untouched — a URL resolver would be within its rights to encode it.
     */
    internal fun absolutize(relative: String, manifestUrl: String): String {
        if (relative.startsWith("https://", true) || relative.startsWith("http://", true)) return relative
        if (relative.startsWith("//")) {
            val schemeEnd = manifestUrl.indexOf(':')
            return manifestUrl.substring(0, schemeEnd + 1) + relative
        }
        return if (relative.startsWith("/")) {
            val authorityStart = manifestUrl.indexOf("://") + 3
            val authorityEnd = manifestUrl.indexOf('/', authorityStart)
                .let { if (it >= 0) it else manifestUrl.length }
            manifestUrl.substring(0, authorityEnd) + relative
        } else {
            val withoutQuery = manifestUrl.substringBefore('?')
            withoutQuery.substringBeforeLast('/') + "/" + relative
        }
    }

    /**
     * The rendition to keep: lossless if there is one, else the highest
     * bandwidth. Null for a manifest with no `Representation` at all, which
     * leaves the template wherever the manifest put it.
     */
    private fun best(manifest: String): Rendition? {
        val renditions = Regex(
            """<Representation\b([^>]*?)(?:/>|>(.*?)</Representation>)""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        ).findAll(manifest).map { match ->
            val attrs = match.groupValues[1]
            fun attr(name: String) =
                Regex("""\b$name\s*=\s*"([^"]*)"""", RegexOption.IGNORE_CASE).find(attrs)?.groupValues?.get(1)
            Rendition(
                bandwidth = attr("bandwidth")?.toLongOrNull() ?: 0L,
                codecs = attr("codecs").orEmpty().lowercase(Locale.ROOT),
                body = match.groupValues[2],
            )
        }.toList()
        return renditions.maxWithOrNull(
            compareBy<Rendition>({ it.codecs.isLossless() }, { it.bandwidth }),
        )
    }

    private fun String.isLossless() = startsWith("flac") || startsWith("alac")

    /** The playlist Media3 will read back, pointing at the files just written. */
    internal fun playlist(plan: Plan): String = buildString {
        appendLine("#EXTM3U")
        appendLine("#EXT-X-VERSION:7")
        appendLine("#EXT-X-PLAYLIST-TYPE:VOD")
        appendLine("#EXT-X-TARGETDURATION:${ceil(plan.seconds.maxOrNull() ?: 0.0).toInt()}")
        appendLine("""#EXT-X-MAP:URI="${localName(0)}"""")
        plan.seconds.forEachIndexed { at, seconds ->
            appendLine("#EXTINF:${"%.3f".format(Locale.ROOT, seconds)},")
            appendLine(localName(at + 1))
        }
        appendLine("#EXT-X-ENDLIST")
    }

    /** The five entities an MPD's URLs can arrive escaped with — `&amp;` being the one that always does. */
    private fun unescape(value: String) = value
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&amp;", "&")

    /** `PT3M50.461S` → `230.461`. Hours, minutes and seconds, which is all a track ever needs. */
    private fun isoSeconds(value: String): Double? {
        val match = Regex("""PT(?:(\d+(?:\.\d+)?)H)?(?:(\d+(?:\.\d+)?)M)?(?:(\d+(?:\.\d+)?)S)?""")
            .find(value) ?: return null
        val (h, m, s) = match.destructured
        val seconds = (h.toDoubleOrNull() ?: 0.0) * 3600 +
            (m.toDoubleOrNull() ?: 0.0) * 60 +
            (s.toDoubleOrNull() ?: 0.0)
        return seconds.takeIf { it > 0 }
    }
}
