package com.music.bitchord.desktop

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.OutputStream
import java.net.URI
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max

/**
 * A download whose URL is an HLS or DASH manifest, saved as one ordinary audio file.
 *
 * The desktop port of Android's `ManifestFile`, `OfflineDash.parse` and `Mp4Sidx` — same logic,
 * same reasons. A Tidal-backed addon answers every lossless request with a manifest, and fetching
 * that URL as if it were audio saved a few kilobytes of XML as `Artist - Title.flac`, reported
 * success, and never played. Here the manifest is read, the best rendition chosen, its segments
 * fetched in order, and joined:
 *
 *  - **`.flac`** — the samples are FLAC frames, and the sample entry's `dfLa` holds the stream's
 *    own metadata blocks, so the file is `fLaC`, those blocks, then every frame. STREAMINFO's
 *    sample count is rewritten from the fragments' durations so the file can seek.
 *  - **`.m4a`** — init then fragments as one fragmented MP4, each segment's own `styp`/`sidx`
 *    dropped and one `sidx` for the whole file added, so other players can seek it too.
 */
internal object DesktopManifestDownload {

    /** Whether [stream] points at an index of the audio rather than the audio. */
    fun isManifest(stream: DesktopStream): Boolean = transportOf(stream) != null

    /** `dash`, `hls`, or null — what the source declared, else the URL's extension. */
    private fun transportOf(stream: DesktopStream): String? =
        stream.transport?.lowercase()?.takeIf { it == DesktopAddonStream.DASH || it == DesktopAddonStream.HLS }
            ?: when (stream.url.substringBefore('?').substringAfterLast('.').lowercase(Locale.ROOT)) {
                "mpd" -> DesktopAddonStream.DASH
                "m3u8" -> DesktopAddonStream.HLS
                else -> null
            }

    /** Fetches every segment of [stream] and writes the joined [extension] file to [sink]. */
    suspend fun save(
        stream: DesktopStream,
        extension: String,
        sink: OutputStream,
        tempDir: File,
        userAgent: String,
        onProgress: (done: Long, total: Long) -> Unit,
    ) {
        fun fetch(target: String): ByteArray =
            DesktopDownloadHttp.client.newCall(DesktopDownloadHttp.request(target, userAgent, stream.headers))
                .execute()
                .use { response ->
                    check(response.isSuccessful) { "Segment failed (HTTP ${response.code})" }
                    response.body.bytes()
                }

        val base = URI(stream.url)
        val index = String(fetch(stream.url), Charsets.UTF_8)
        val (initRef, mediaRefs) = if (transportOf(stream) == DesktopAddonStream.DASH) {
            parseDash(index).let { it.initialization to it.media }
        } else {
            hlsSegments(index)
        }
        fun resolve(ref: String) = base.resolve(ref).toString()
        val total = mediaRefs.size + 1L

        val init = fetch(resolve(initRef))
        onProgress(1, total)
        val scope = currentCoroutineContext()
        // Lazy, so a hi-res track is never more than one segment in memory.
        val media = mediaRefs.asSequence().mapIndexed { i, ref ->
            scope.ensureActive()
            fetch(resolve(ref)).also { onProgress(i + 2L, total) }
        }
        assemble(tempDir, extension, init, media, sink)
        sink.flush()
    }

    /** Everything after the fetching: [init] and [media] in, one [extension] file out. */
    internal fun assemble(tempDir: File, extension: String, init: ByteArray, media: Sequence<ByteArray>, sink: OutputStream) {
        when (extension) {
            "flac" -> writeFlac(tempDir, init, media, sink)
            else -> {
                // Joined in a temporary file first, because the whole-file index goes in front.
                val joined = File.createTempFile("manifest-", ".m4a", tempDir)
                try {
                    joined.outputStream().buffered().use { out ->
                        out.write(init)
                        media.forEach { segment ->
                            Mp4.boxes(segment).filter { it.type != "styp" && it.type != "sidx" }.forEach {
                                out.write(segment, it.offset, it.size)
                            }
                        }
                    }
                    val bytes = joined.readBytes()
                    sink.write(Mp4.withSidx(bytes))
                } finally {
                    joined.delete()
                }
            }
        }
    }

    // ── DASH ─────────────────────────────────────────────────────────────

    internal class Plan(val initialization: String, val media: List<String>)

    /**
     * The segment list of a single-period, `SegmentTemplate` DASH manifest, from its best
     * rendition — lossless if there is one, else the highest bandwidth. Tidal lists HE-AAC first
     * and FLAC last, so "the first template in the file" is the 97 kbps one.
     */
    internal fun parseDash(manifest: String): Plan {
        if (Regex("<ContentProtection", RegexOption.IGNORE_CASE).containsMatchIn(manifest)) {
            error("Encrypted DASH cannot be saved")
        }
        if (Regex("<Period[\\s>]").findAll(manifest).count() > 1) error("Multi-period DASH cannot be saved")
        val scope = bestRendition(manifest)?.takeIf { Regex("<SegmentTemplate", RegexOption.IGNORE_CASE).containsMatchIn(it) }
            ?: manifest
        val template = Regex("<SegmentTemplate([^>]*)>", RegexOption.IGNORE_CASE).find(scope)?.groupValues?.get(1)
            ?: error("DASH manifest has no segment template")
        fun attr(name: String) =
            Regex("""\b$name\s*=\s*"([^"]*)"""", RegexOption.IGNORE_CASE).find(template)?.groupValues?.get(1)

        val initialization = attr("initialization")?.let(::unescape) ?: error("DASH manifest has no initialization segment")
        val mediaTemplate = attr("media")?.let(::unescape) ?: error("DASH manifest has no media template")
        if (!mediaTemplate.contains("\$Number\$")) error("Unsupported DASH media template")
        val timescale = attr("timescale")?.toDoubleOrNull()?.takeIf { it > 0 } ?: 1.0
        val startNumber = attr("startNumber")?.toIntOrNull() ?: 1

        var count = 0
        Regex("<SegmentTimeline>(.*?)</SegmentTimeline>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(scope)?.groupValues?.get(1)
            ?.let { timeline ->
                Regex("<S\\b([^>]*)/?>", RegexOption.IGNORE_CASE).findAll(timeline).forEach { entry ->
                    val body = entry.groupValues[1]
                    if (Regex("""\bd\s*=\s*"\d+"""").containsMatchIn(body)) {
                        // `r` is how many times the entry *repeats*: r="56" is 57 segments.
                        count += (Regex("""\br\s*=\s*"(\d+)"""").find(body)?.groupValues?.get(1)?.toIntOrNull() ?: 0) + 1
                    }
                }
            }
        if (count == 0) {
            val d = attr("duration")?.toLongOrNull()?.takeIf { it > 0 }
                ?: error("DASH manifest has neither a timeline nor a segment duration")
            val total = Regex("""mediaPresentationDuration\s*=\s*"([^"]*)"""").find(manifest)
                ?.groupValues?.get(1)?.let(::isoSeconds)
                ?: error("DASH manifest states no duration")
            count = max(1, ceil(total / (d / timescale)).toInt())
        }
        return Plan(initialization, (0 until count).map { mediaTemplate.replace("\$Number\$", (startNumber + it).toString()) })
    }

    /** The body of the rendition to keep, or null when the manifest lists none. */
    private fun bestRendition(manifest: String): String? = Regex(
        """<Representation\b([^>]*?)(?:/>|>(.*?)</Representation>)""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    ).findAll(manifest).maxWithOrNull(
        compareBy(
            { match ->
                val codecs = Regex("""\bcodecs\s*=\s*"([^"]*)"""", RegexOption.IGNORE_CASE)
                    .find(match.groupValues[1])?.groupValues?.get(1).orEmpty().lowercase(Locale.ROOT)
                codecs.startsWith("flac") || codecs.startsWith("alac")
            },
            { match ->
                Regex("""\bbandwidth\s*=\s*"(\d+)"""", RegexOption.IGNORE_CASE)
                    .find(match.groupValues[1])?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            },
        ),
    )?.groupValues?.get(2)

    private fun unescape(value: String) = value
        .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")

    /** `PT3M50.461S` → `230.461`. */
    private fun isoSeconds(value: String): Double? {
        val match = Regex("""PT(?:(\d+(?:\.\d+)?)H)?(?:(\d+(?:\.\d+)?)M)?(?:(\d+(?:\.\d+)?)S)?""").find(value) ?: return null
        val (h, m, s) = match.destructured
        return ((h.toDoubleOrNull() ?: 0.0) * 3600 + (m.toDoubleOrNull() ?: 0.0) * 60 + (s.toDoubleOrNull() ?: 0.0))
            .takeIf { it > 0 }
    }

    // ── HLS ──────────────────────────────────────────────────────────────

    internal fun hlsSegments(playlist: String): Pair<String, List<String>> {
        if (!playlist.startsWith("#EXTM3U")) error("Invalid HLS playlist")
        if (playlist.contains("#EXT-X-KEY:") && !playlist.contains("METHOD=NONE")) error("Encrypted HLS cannot be saved")
        val init = Regex("""#EXT-X-MAP:.*URI="([^"]+)"""").find(playlist)?.groupValues?.get(1)
            ?: error("HLS playlist has no initialisation segment")
        val media = playlist.lineSequence().map(String::trim).filter { it.isNotEmpty() && !it.startsWith('#') }.toList()
        if (media.isEmpty()) error("HLS playlist has no fragments")
        return init to media
    }

    // ── FLAC ─────────────────────────────────────────────────────────────

    /** FLAC-in-MP4 to native FLAC; frames go to a temporary file until STREAMINFO's total is known. */
    private fun writeFlac(tempDir: File, init: ByteArray, media: Sequence<ByteArray>, sink: OutputStream) {
        val track = Mp4.track(init) ?: error("Not an MP4 initialisation segment")
        val stsd = Mp4.find(init, "moov", "trak", "mdia", "minf", "stbl", "stsd") ?: error("No sample description")
        val entry = Mp4.boxes(init, stsd.content + 8, stsd.end).firstOrNull()
        if (entry?.type != "fLaC") error("Stream is ${entry?.type ?: "unknown"}, not FLAC")
        // AudioSampleEntry: 28 bytes of fields before its child boxes.
        val dfla = Mp4.boxes(init, entry.content + 28, entry.end).firstOrNull { it.type == "dfLa" }
            ?: error("FLAC stream has no dfLa")
        val blocks = metadataBlocks(init.copyOfRange(dfla.content + 4, dfla.end))

        val frames = File.createTempFile("manifest-", ".flac", tempDir)
        try {
            var ticks = 0L
            frames.outputStream().buffered().use { out ->
                media.forEach { segment ->
                    Mp4.boxes(segment).filter { it.type == "moof" }.forEach { moof ->
                        val samples = Mp4.samples(segment, moof, track) ?: error("Unreadable FLAC fragment")
                        samples.forEach {
                            out.write(segment, it.offset, it.size)
                            ticks += it.duration
                        }
                    }
                }
            }
            if (ticks <= 0) error("Manifest held no audio")

            val info = blocks.first().second
            val rate = ((info[10].toLong() and 0xFF) shl 12) or ((info[11].toLong() and 0xFF) shl 4) or
                ((info[12].toLong() and 0xFF) shr 4)
            val totalSamples = if (track.timescale > 0 && rate > 0) ticks * rate / track.timescale else ticks
            info[13] = ((info[13].toInt() and 0xF0) or ((totalSamples shr 32).toInt() and 0x0F)).toByte()
            for (i in 0 until 4) info[14 + i] = (totalSamples shr (8 * (3 - i))).toByte()

            sink.write("fLaC".toByteArray(Charsets.ISO_8859_1))
            blocks.forEachIndexed { i, (type, body) ->
                val last = if (i == blocks.lastIndex) 0x80 else 0
                sink.write(byteArrayOf((last or type).toByte(), (body.size shr 16).toByte(), (body.size shr 8).toByte(), body.size.toByte()))
                sink.write(body)
            }
            frames.inputStream().use { it.copyTo(sink) }
        } finally {
            frames.delete()
        }
    }

    /** `dfLa`'s payload as (type, body) pairs, STREAMINFO first. */
    private fun metadataBlocks(payload: ByteArray): List<Pair<Int, ByteArray>> {
        val out = mutableListOf<Pair<Int, ByteArray>>()
        var p = 0
        while (p + 4 <= payload.size) {
            val type = payload[p].toInt() and 0x7F
            val length = ((payload[p + 1].toInt() and 0xFF) shl 16) or
                ((payload[p + 2].toInt() and 0xFF) shl 8) or (payload[p + 3].toInt() and 0xFF)
            if (p + 4 + length > payload.size) break
            out += type to payload.copyOfRange(p + 4, p + 4 + length)
            val last = payload[p].toInt() and 0x80 != 0
            p += 4 + length
            if (last) break
        }
        if (out.firstOrNull()?.let { it.first == 0 && it.second.size == 34 } != true) error("FLAC stream has no STREAMINFO")
        return out
    }

    // ── MP4 boxes ────────────────────────────────────────────────────────

    /** The read-only box walking both assemblers need. A box that doesn't fit ends the list. */
    internal object Mp4 {
        class Box(val offset: Int, val headerLen: Int, val size: Int, val type: String) {
            val content get() = offset + headerLen
            val end get() = offset + size
        }

        class Track(val id: Long, val timescale: Long, val defaultDuration: Long, val defaultSize: Long)

        class Sample(val offset: Int, val size: Int, val duration: Long)

        fun boxes(bytes: ByteArray, start: Int = 0, end: Int = bytes.size): List<Box> {
            val out = mutableListOf<Box>()
            var pos = start
            while (pos + 8 <= end) {
                var size = u32(bytes, pos)
                var header = 8
                if (size == 1L) {
                    if (pos + 16 > end) break
                    size = u64(bytes, pos + 8)
                    header = 16
                } else if (size == 0L) {
                    size = (end - pos).toLong()
                }
                if (size < header || pos + size > end) break
                out += Box(pos, header, size.toInt(), String(bytes, pos + 4, 4, Charsets.ISO_8859_1))
                pos += size.toInt()
            }
            return out
        }

        fun child(bytes: ByteArray, parent: Box, type: String): Box? =
            boxes(bytes, parent.content, parent.end).firstOrNull { it.type == type }

        fun find(bytes: ByteArray, vararg path: String): Box? {
            var box = boxes(bytes).firstOrNull { it.type == path.first() } ?: return null
            for (type in path.drop(1)) box = child(bytes, box, type) ?: return null
            return box
        }

        /** The single track an init segment (or a whole fragmented file) describes. */
        fun track(bytes: ByteArray): Track? {
            val trak = find(bytes, "moov", "trak") ?: return null
            val tkhd = child(bytes, trak, "tkhd") ?: return null
            val mdhd = find(bytes, "moov", "trak", "mdia", "mdhd") ?: return null
            val id = u32(bytes, tkhd.content + if (bytes[tkhd.content].toInt() == 1) 20 else 12)
            val timescale = u32(bytes, mdhd.content + if (bytes[mdhd.content].toInt() == 1) 20 else 12)
            val trex = find(bytes, "moov", "mvex")?.let { mvex ->
                boxes(bytes, mvex.content, mvex.end).firstOrNull { it.type == "trex" && u32(bytes, it.content + 4) == id }
            }
            return Track(
                id = id,
                timescale = timescale,
                defaultDuration = trex?.let { u32(bytes, it.content + 12) } ?: 0L,
                defaultSize = trex?.let { u32(bytes, it.content + 16) } ?: 0L,
            )
        }

        /** [track]'s samples within one `moof`, or null for a layout this can't place. */
        fun samples(bytes: ByteArray, moof: Box, track: Track): List<Sample>? {
            val out = mutableListOf<Sample>()
            for (traf in boxes(bytes, moof.content, moof.end).filter { it.type == "traf" }) {
                val parts = boxes(bytes, traf.content, traf.end)
                val tfhd = parts.firstOrNull { it.type == "tfhd" } ?: continue
                if (u32(bytes, tfhd.content + 4) != track.id) continue
                val tf = flags(bytes, tfhd)
                if (tf and 0x1 != 0) return null // base_data_offset: an absolute position
                var p = tfhd.content + 8
                if (tf and 0x2 != 0) p += 4
                val defaultDuration = if (tf and 0x8 != 0) u32(bytes, p).also { p += 4 } else track.defaultDuration
                val defaultSize = if (tf and 0x10 != 0) u32(bytes, p) else track.defaultSize
                var cursor = boxes(bytes, moof.end, bytes.size).firstOrNull { it.type == "mdat" }?.content ?: return null
                for (trun in parts.filter { it.type == "trun" }) {
                    val f = flags(bytes, trun)
                    val count = u32(bytes, trun.content + 4).toInt()
                    var q = trun.content + 8
                    if (f and 0x1 != 0) {
                        cursor = moof.offset + u32(bytes, q).toInt()
                        q += 4
                    }
                    if (f and 0x4 != 0) q += 4
                    repeat(count) {
                        val duration = if (f and 0x100 != 0) u32(bytes, q).also { q += 4 } else defaultDuration
                        val size = if (f and 0x200 != 0) u32(bytes, q).also { q += 4 } else defaultSize
                        if (f and 0x400 != 0) q += 4
                        if (f and 0x800 != 0) q += 4
                        if (size <= 0 || cursor + size > bytes.size) return null
                        out += Sample(cursor, size.toInt(), duration)
                        cursor += size.toInt()
                    }
                }
            }
            return out
        }

        /**
         * [bytes] with one `sidx` for the whole file after `moov`, or [bytes] itself when it is not
         * a single-track fragmented MP4 or already has one.
         */
        fun withSidx(bytes: ByteArray): ByteArray = runCatching { indexed(bytes) }.getOrNull() ?: bytes

        private fun indexed(bytes: ByteArray): ByteArray? {
            val top = boxes(bytes)
            if (top.any { it.type == "sidx" }) return null
            val moov = top.firstOrNull { it.type == "moov" } ?: return null
            val moofs = top.filter { it.type == "moof" }
            if (moofs.isEmpty() || moofs.first().offset < moov.end) return null
            if (boxes(bytes, moov.content, moov.end).count { it.type == "trak" } != 1) return null
            val track = track(bytes)?.takeIf { it.timescale > 0 } ?: return null
            val ticks = moofs.map { moof -> (samples(bytes, moof, track) ?: return null).sumOf { it.duration } }
            if (ticks.any { it <= 0 }) return null
            val ends = moofs.drop(1).map { it.offset } + bytes.size
            val firstDecodeTime = child(bytes, moofs.first(), "traf")?.let { child(bytes, it, "tfdt") }?.let {
                if (bytes[it.content].toInt() == 1) u64(bytes, it.content + 4) else u32(bytes, it.content + 4)
            } ?: 0L

            val sidxSize = 40 + 12 * moofs.size
            val out = ByteArray(sidxSize)
            w32(out, 0, sidxSize.toLong())
            "sidx".forEachIndexed { i, c -> out[4 + i] = c.code.toByte() }
            out[8] = 1 // version 1: 64-bit times and offset
            w32(out, 12, track.id)
            w32(out, 16, track.timescale)
            w64(out, 20, firstDecodeTime)
            // From the end of the sidx, which sits where moov ended.
            w64(out, 28, (moofs.first().offset - moov.end).toLong())
            out[38] = (moofs.size shr 8).toByte()
            out[39] = moofs.size.toByte()
            moofs.forEachIndexed { i, moof ->
                val at = 40 + 12 * i
                w32(out, at, (ends[i] - moof.offset).toLong())
                w32(out, at + 4, ticks[i])
                w32(out, at + 8, 0x90000000L) // starts with SAP, type 1
            }
            return bytes.copyOf(moov.end) + out + bytes.copyOfRange(moov.end, bytes.size)
        }

        private fun flags(b: ByteArray, box: Box): Int = u32(b, box.content).toInt() and 0xFFFFFF

        fun u32(b: ByteArray, off: Int): Long =
            ((b[off].toLong() and 0xFF) shl 24) or ((b[off + 1].toLong() and 0xFF) shl 16) or
                ((b[off + 2].toLong() and 0xFF) shl 8) or (b[off + 3].toLong() and 0xFF)

        private fun u64(b: ByteArray, off: Int): Long {
            var v = 0L
            for (i in 0 until 8) v = (v shl 8) or (b[off + i].toLong() and 0xFF)
            return v
        }

        private fun w32(b: ByteArray, off: Int, value: Long) {
            for (i in 0 until 4) b[off + i] = ((value shr (8 * (3 - i))) and 0xFF).toByte()
        }

        private fun w64(b: ByteArray, off: Int, value: Long) {
            for (i in 0 until 8) b[off + i] = ((value shr (8 * (7 - i))) and 0xFF).toByte()
        }
    }
}
