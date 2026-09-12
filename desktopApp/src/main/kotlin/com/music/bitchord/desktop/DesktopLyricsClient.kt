package com.music.bitchord.desktop

import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.durationMillis
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.buildJsonObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Collections
import java.util.LinkedHashMap
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.abs

data class DesktopLyricWord(val startMs: Long, val endMs: Long, val text: String)

/** Which side of the panel a line is sung from. */
enum class DesktopLyricAlignment { Start, End }

data class DesktopLyricLine(
    val timeMs: Long,
    val text: String,
    val words: List<DesktopLyricWord> = emptyList(),
    val sungUntilMs: Long? = null,
    /**
     * The answering vocal — the "(ooh)" or the echoed half-phrase a second voice sings over the
     * lead.
     */
    val background: DesktopLyricLine? = null,
    val alignment: DesktopLyricAlignment = DesktopLyricAlignment.Start,
) {
    val isWordSynced: Boolean get() = words.isNotEmpty()

    /** A break between verses, carried as a line with nothing in it. */
    val isGap: Boolean get() = text.isEmpty()

    /** Whether anything actually said when the singing stops, rather than only when it starts. */
    val hasKnownEnd: Boolean get() = sungUntilMs != null || words.isNotEmpty()

    /** When the singing stops, as far as anything said; the start otherwise. */
    val endMs: Long
        get() {
            val lead = words.lastOrNull()?.endMs ?: sungUntilMs ?: timeMs
            return maxOf(lead, background?.endMs ?: lead)
        }

    /** Where each of [words] sits in [text], as character ranges. */
    val wordSpans: List<IntRange> by lazy(LazyThreadSafetyMode.NONE) {
        var offset = 0
        words.map { word ->
            val start = text.indexOf(word.text, offset).takeIf { it >= 0 } ?: offset
            val end = start + word.text.length
            offset = end
            start until end
        }
    }

    /** Whether anything on this line is off the floor at [positionMs]. */
    fun isLifted(positionMs: Long): Boolean {
        val first = words.firstOrNull() ?: return false
        if (positionMs <= first.startMs) return false
        // A word animated letter by letter runs on past the ordinary fall — its last letter only
        // starts moving as the word ends.
        return positionMs < words.last().endMs + WORD_RISE_MS || isGrowing(positionMs)
    }

    /** How far the word covering [positionMs] has lifted, 0..1. */
    fun wordLift(index: Int, positionMs: Long): Float {
        val word = words.getOrNull(index) ?: return 0f
        val rising = ((positionMs - word.startMs) / WORD_RISE_MS).coerceIn(0f, 1f)
        val falling = (1f - (positionMs - word.endMs) / WORD_RISE_MS).coerceIn(0f, 1f)
        return smoothStep(minOf(rising, falling))
    }

    fun revealedChars(positionMs: Long): Float {
        if (words.isEmpty()) return if (positionMs >= timeMs) text.length.toFloat() else 0f
        var offset = 0
        words.forEachIndexed { index, word ->
            val start = text.indexOf(word.text, offset).takeIf { it >= 0 } ?: offset
            val end = start + word.text.length
            if (positionMs < word.startMs) return start.toFloat()
            if (positionMs < word.endMs) {
                val span = (word.endMs - word.startMs).coerceAtLeast(1L)
                return start + ((positionMs - word.startMs).toFloat() / span) * word.text.length
            }
            val next = words.getOrNull(index + 1)
            if (next != null && positionMs < next.startMs) {
                val nextStart = text.indexOf(next.text, end).takeIf { it >= 0 } ?: end
                val span = (next.startMs - word.endMs).coerceAtLeast(1L)
                return end + ((positionMs - word.endMs).toFloat() / span) * (nextStart - end)
            }
            offset = end
        }
        return text.length.toFloat()
    }

    /**
     * How much of a word's lift is left at [positionMs] — 1 while it is being sung, easing to 0
     * over [WORD_RISE_MS] once it is past.
     */
    fun wordFall(index: Int, positionMs: Long): Float {
        val word = words.getOrNull(index) ?: return 0f
        return smoothStep((1f - (positionMs - word.endMs) / WORD_RISE_MS).coerceIn(0f, 1f))
    }

    /** The words held long enough to be worth animating letter by letter. */
    val growingWords: List<DesktopGrowingWord> by lazy(LazyThreadSafetyMode.NONE) {
        words.mapIndexedNotNull { index, word ->
            if (word.canGrow()) DesktopGrowingWord(index, word) else null
        }
    }

    /** The letter-by-letter treatment for word [index], where it has earned one. */
    fun growingAt(index: Int): DesktopGrowingWord? =
        growingWords.firstOrNull { it.index == index }

    /** Whether any word on this line is mid-flight at [positionMs]. */
    fun isGrowing(positionMs: Long): Boolean = growingWords.any {
        positionMs >= it.startMs && positionMs <= it.restsAtMs
    }
}

/** One word held long enough to be animated a letter at a time. */
class DesktopGrowingWord internal constructor(
    /** Which of the line's words this is. */
    val index: Int,
    word: DesktopLyricWord,
) {
    val startMs: Long = word.startMs
    val endMs: Long = word.endMs

    private val chars: Int = word.text.length
    private val scalePeak = FloatArray(chars)
    private val shiftPeak = FloatArray(chars)
    private val risePeak = FloatArray(chars)
    private val bloomPeak = FloatArray(chars)

    /** When the last letter has finished moving and is just sitting lifted. */
    val restsAtMs: Long

    init {
        val held = (endMs - startMs).coerceAtLeast(1L).toFloat()
        // How much of the full treatment this word has earned.
        val earned = ((held - GROW_RAMP_MIN_MS) / (GROW_RAMP_MAX_MS - GROW_RAMP_MIN_MS))
            .coerceIn(0f, 1f)
            .let { it * it * it }
        val decay = decayRate(chars, held)
        // A word barely over the line glows less than one held twice as long, and a long word
        // spreads what it has over more letters.
        val bloomPace = minOf(GROW_BLOOM_PACE_MAX, held / GROW_BLOOM_PACE_MS)
        val bloomSpread = when {
            chars <= 3 -> GROW_BLOOM_SHORT
            chars >= 6 -> GROW_BLOOM_LONG
            else -> 1f
        }
        // Short words swell a little more, having fewer letters to do it with.
        val base = if (chars <= 3) GROW_BASE_SHORT else GROW_BASE_LONG
        val liftPace = (held / GROW_LIFT_PACE_MS).coerceIn(GROW_LIFT_FLOOR, 1f)

        for (i in 0 until chars) {
            val place = if (chars > 1) i.toFloat() / (chars - 1) else 0f
            val reach = earned * (1f - place * decay)
            val scale = 1f + base + reach * GROW_SCALE_RANGE
            scalePeak[i] = scale * GROW_SCALE_TRIM
            bloomPeak[i] = (GROW_BLOOM_FLOOR + reach * GROW_BLOOM_RANGE) * bloomPace * bloomSpread
            // The lift is read off the swell rather than set on its own.
            risePeak[i] = ((scale - 1f) / GROW_SCALE_CEILING) * liftPace
            // Letters lean away from the middle of the word as it swells, so it opens outwards
            // rather than every letter sliding the same way.
            val centre = (i + 0.5f) / chars
            shiftPeak[i] = (centre - 0.5f) * 2f * (scale - 1f) * GROW_SHIFT_EM * GROW_SCALE_TRIM
        }

        val last = (chars - 1).coerceAtLeast(0) * GROW_STAGGER + GROW_SPAN
        restsAtMs = startMs + (held * last).toLong()
    }

    /**
     * Where letter [charIndex] has got to at [positionMs], written into [into] rather than
     * returned.
     */
    fun sampleInto(charIndex: Int, positionMs: Long, into: DesktopCharGrowth) {
        val span = (endMs - startMs).coerceAtLeast(1L).toFloat()
        val elapsed = positionMs - startMs - charIndex * span * GROW_STAGGER
        val phase = (elapsed / (span * GROW_SPAN)).coerceIn(0f, 1f)
        val peakScale = scalePeak[charIndex]
        when {
            phase < GROW_IN -> {
                val t = smoothStep(phase / GROW_IN)
                into.scale = 1f + (peakScale - 1f) * t
                into.shift = shiftPeak[charIndex] * t
                into.rise = risePeak[charIndex] * t
                into.bloom = bloomPeak[charIndex] * t
            }
            phase < GROW_HOLD -> {
                into.scale = peakScale
                into.shift = shiftPeak[charIndex]
                into.rise = risePeak[charIndex]
                into.bloom = bloomPeak[charIndex]
            }
            phase < GROW_OUT -> {
                val t = smoothStep((phase - GROW_HOLD) / (GROW_OUT - GROW_HOLD))
                into.scale = peakScale + (1f - peakScale) * t
                into.shift = shiftPeak[charIndex] * (1f - t)
                into.rise = risePeak[charIndex] + (GROW_REST - risePeak[charIndex]) * t
                into.bloom = bloomPeak[charIndex] * (1f - t)
            }
            else -> {
                into.scale = 1f
                into.shift = 0f
                into.rise = GROW_REST
                into.bloom = 0f
            }
        }
    }
}

/** Where one letter of a [DesktopGrowingWord] is, filled in by `sampleInto`. */
class DesktopCharGrowth {
    /** Swell, about the letter's own centre. */
    var scale: Float = 1f

    /** Lean away from the middle of the word, in ems. */
    var shift: Float = 0f

    /** Lift, in multiples of the ordinary sung-word rise. */
    var rise: Float = 0f

    /** Bloom, 0..1, at its own peak partway up rather than at the top. */
    var bloom: Float = 0f
}

/**
 * Whether this word is held long enough, and is short enough, to be animated a letter at a time.
 */
private fun DesktopLyricWord.canGrow(): Boolean {
    val length = text.length
    if (length == 0 || length > GROW_MAX_CHARS) return false
    if ('-' in text || text.any { it.isBlockScript() || it.isJoinedScript() }) return false
    val held = endMs - startMs
    return when {
        length == 1 -> held >= GROW_MIN_SOLO_MS
        length <= 3 -> held >= GROW_MIN_SHORT_MS + (length - 2) * GROW_SHORT_STEP_MS
        length == 4 -> held >= GROW_MIN_FOUR_MS
        else -> held >= GROW_MIN_LONG_MS && held >= length * GROW_MS_PER_CHAR
    }
}

/** How much of the movement is taken back off the later letters of a word. */
private fun decayRate(length: Int, heldMs: Float): Float {
    val long = length > GROW_DECAY_LONG_CHARS
    val quick = heldMs < GROW_DECAY_QUICK_MS
    if (!long && !quick) return 0f
    var strength = 0f
    if (long) {
        strength += minOf((length - GROW_DECAY_LONG_CHARS) / 5f, 1f) * GROW_DECAY_LONG
    }
    if (quick) {
        val short = maxOf(0f, 1f - (heldMs - GROW_DECAY_QUICK_FLOOR_MS) / 400f)
        strength += short * if (length > 3) GROW_DECAY_QUICK else GROW_DECAY_QUICK_TINY
    }
    return minOf(strength, GROW_DECAY_MAX)
}

private fun Char.isBlockScript(): Boolean =
    this in '\u4e00'..'\u9fff' || this in '\u3040'..'\u309f' ||
        this in '\u30a0'..'\u30ff' || this in '\uac00'..'\ud7af'

private fun Char.isJoinedScript(): Boolean =
    this in '\u0590'..'\u08ff'

/** Longest word worth running a wave through; past this it reads as a ripple. */
private const val GROW_MAX_CHARS = 7

/** How long a word of each length has to be held before it qualifies. */
private const val GROW_MIN_SOLO_MS = 1_100L
private const val GROW_MIN_SHORT_MS = 1_360L
private const val GROW_SHORT_STEP_MS = 140L
private const val GROW_MIN_FOUR_MS = 1_050L
private const val GROW_MIN_LONG_MS = 900L
private const val GROW_MS_PER_CHAR = 200L

private const val GROW_DECAY_LONG_CHARS = 5
private const val GROW_DECAY_QUICK_MS = 1_200f
private const val GROW_DECAY_QUICK_FLOOR_MS = 800f
private const val GROW_DECAY_LONG = 0.4f
private const val GROW_DECAY_QUICK = 0.3f
private const val GROW_DECAY_QUICK_TINY = 0.1f
private const val GROW_DECAY_MAX = 0.7f

/**
 * Each letter starts this far — as a share of the word's own length — after the one before it, so
 * the swell travels rather than pulsing.
 */
private const val GROW_STAGGER = 0.09f

/** The whole move runs half again as long as the word is held. */
private const val GROW_SPAN = 1.5f

/** Up by [GROW_IN], held to [GROW_HOLD], settled by [GROW_OUT]. */
private const val GROW_IN = 0.25f
private const val GROW_HOLD = 0.30f
private const val GROW_OUT = 0.75f

/** Where a letter comes to rest: the same small lift every sung word carries. */
private const val GROW_REST = 1f

/** The window over which a longer hold earns more of the treatment. */
private const val GROW_RAMP_MIN_MS = 400f
private const val GROW_RAMP_MAX_MS = 3_000f

/** Swell every qualifying letter gets, and the range the hold adds on top. */
private const val GROW_BASE_SHORT = 0.05f
private const val GROW_BASE_LONG = 0.04f
private const val GROW_SCALE_RANGE = 0.08f

/** The most any letter swells, which is what [DesktopGrowingWord] reads the lift off. */
private const val GROW_SCALE_CEILING = 0.1f

/** Taken back off the swell so it stops a hair short of the ceiling. */
private const val GROW_SCALE_TRIM = 0.98f

/** Lean, as a share of the font size at full swell. */
private const val GROW_SHIFT_EM = 25f / 34f

/** The bloom's floor, and what the hold adds to it. */
private const val GROW_BLOOM_FLOOR = 0.35f
private const val GROW_BLOOM_RANGE = 0.45f
private const val GROW_BLOOM_PACE_MS = 1_500f
private const val GROW_BLOOM_PACE_MAX = 1.1f
private const val GROW_BLOOM_SHORT = 0.85f
private const val GROW_BLOOM_LONG = 1.1f

/** A word held this long lifts as far as it is going to. */
private const val GROW_LIFT_PACE_MS = 2_000f
private const val GROW_LIFT_FLOOR = 0.3f

/** How long a word takes to rise, and to settle back down once it is past. */
internal const val WORD_RISE_MS = 700f

/** Ease in and out of the ends, so the lift has no corners on it. */
internal fun smoothStep(fraction: Float) = fraction * fraction * (3f - 2f * fraction)

data class DesktopLyrics(val source: String, val lines: List<DesktopLyricLine>) {
    val plainText: String get() = lines.joinToString("\n") { it.text }
    val wordSynced: Boolean get() = lines.any(DesktopLyricLine::isWordSynced)
}

/** Android's ordered, racing lyrics provider chain for the desktop target. */
object DesktopLyricsClient {
    private const val MIN_GAP_MS = 4_000L
    private const val USER_AGENT = "BitChord (https://github.com/kushagrasinghx/BitChord)"
    private const val LRCLIB = "LRCLIB"
    private const val LYRICS_PLUS = "LyricsPlus"
    private const val BETTER = "BetterLyrics"
    private const val PAXSENIX = "PaxSenix"
    private const val SIMP = "SimpMusic"
    private const val KUGOU = "KuGou"
    private const val GENIUS = "Genius"
    private const val BINI = "BiniLyrics"
    private const val UNISON = "Unison"
    private const val MUSIXMATCH = "Musixmatch"
    private const val CACHE_TTL_MS = 12L * 60L * 60L * 1_000L

    /** One lyric database, as the settings screen needs to describe it. */
    internal data class Source(val name: String, val detail: String, val wordSynced: Boolean)

    /** Every source, in the order they are asked out of the box. */
    internal val sources: List<Source> = listOf(
        Source(BINI, "The same Apple timings, matched on the recording itself", true),
        Source(BETTER, "Apple Music timings, word by word", true),
        Source(PAXSENIX, "Apple Music timings again, on a second host", true),
        Source(LYRICS_PLUS, "Syllable by syllable, on community mirrors", true),
        Source(SIMP, "Matched on the video, so never the wrong edit", true),
        Source(UNISON, "Contributed by listeners, so it has what nobody licensed", true),
        Source(KUGOU, "Whole lines, strong outside the English catalogue", false),
        Source(LRCLIB, "Whole lines only, and always up", false),
        Source(MUSIXMATCH, "Whole lines, from the biggest lyrics database there is", false),
        Source(GENIUS, "Plain text fallback, massive web catalogue", false),
    )

    private val providers = listOf(
        Provider(BINI, true) { ask -> biniLyrics(ask) },
        Provider(BETTER, true) { ask -> betterLyrics(ask) },
        Provider(PAXSENIX, true) { ask -> paxSenix(ask) },
        Provider(LYRICS_PLUS, true) { ask -> lyricsPlus(ask) },
        Provider(SIMP, true) { ask -> simpMusic(ask) },
        Provider(UNISON, true) { ask -> unison(ask) },
        Provider(KUGOU, false) { ask -> kuGou(ask) },
        Provider(LRCLIB, false) { ask -> lrclib(ask) },
        Provider(MUSIXMATCH, false) { ask -> musixmatch(ask) },
        Provider(GENIUS, false) { ask -> genius(ask) },
    )

    private val client = HttpClient(CIO)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }
    private val lastLyricsPlusHost = AtomicReference<String?>(null)
    private val appleToken = AtomicReference<String?>(null)
    private val cache = ConcurrentHashMap<String, CachedLyrics>()

    suspend fun lookup(song: Song, durationMs: Long = song.durationMillis()): Result<DesktopLyrics> {
        // Off in Settings means no lookup at all rather than a lookup nobody sees.
        if (!DesktopPersistence().boolean(KEY_SYNCED_LYRICS, true)) {
            return Result.failure(IllegalStateException("Synced lyrics are switched off"))
        }
        // Every source unticked is a state the listener can reach, and it means the same thing as
        // the feature being off.
        if (configuredProviders().isEmpty()) {
            return Result.failure(IllegalStateException("No lyrics sources are enabled"))
        }
        val cacheKey = "${song.videoId}|$durationMs"
        cache[cacheKey]?.takeIf { System.currentTimeMillis() - it.createdAt < CACHE_TTL_MS }?.result?.let { return it }

        // A downloaded or local file carries its own words. Asking four servers for a string
        // already on disk is a round trip, and it is why a download showed nothing offline.
        embedded(song)?.let { lines ->
            val result = Result.success(DesktopLyrics("Downloaded", lines.withBackgroundVocals().withGaps()))
            cache[cacheKey] = CachedLyrics(System.currentTimeMillis(), result)
            return result
        }

        // Every source but SimpMusic is asked for a name, and the name this app has is YouTube's,
        // which is not the name anyone catalogued.
        val searchTitle = song.title.forLyricsSearch()
        val searchArtist = song.artist.artistForLyricsSearch()

        // Settled before anyone is asked for words, so every source that can name the recording
        // does rather than describing it.
        val chain = configuredProviders()
        val known = isrcs[song.videoId]
        val hit = if (known == null) {
            identify(song.videoId, searchTitle, searchArtist, song.albumName, durationMs, chain)
        } else {
            null
        }
        val ask = Ask(
            videoId = song.videoId,
            title = searchTitle,
            artist = searchArtist,
            album = song.albumName?.takeIf(String::isNotBlank),
            durationMs = durationMs,
            isrc = known ?: hit?.isrc?.takeIf(String::isNotBlank),
            hit = hit,
        )

        val result = coroutineScope {
            // Every source is asked at once, but the answers are read back **in the configured
            // order**, not in the order they arrive. Taking whoever replied first is what let a
            // quick source with sloppy timings beat the one the listener put at the top, which is
            // how the words ended up running ahead of the singing.
            //
            // Genius is a plain web scraper, so it is started lazily and only reached if every
            // synced source above it missed.
            val requests = chain.map { provider ->
                provider to async(
                    Dispatchers.IO,
                    start = if (provider.name == GENIUS) CoroutineStart.LAZY else CoroutineStart.DEFAULT,
                ) {
                    try {
                        provider.fetch(ask).also { lines ->
                            if (lines == null) {
                            } else {
                            }
                        }
                    } catch (_: CancellationException) {
                        throw CancellationException()
                    } catch (failure: Throwable) {
                        null
                    }
                }
            }
            var lineSynced: DesktopLyrics? = null
            val prioritizeSyllables = DesktopPersistence().boolean(KEY_PRIORITIZE_SYLLABLES, false)
            try {
                for ((provider, job) in requests) {
                    if (provider.name == GENIUS) {
                        if (lineSynced != null) {
                            continue
                        }
                    }
                    val lines = runCatching { job.await() }.getOrNull() ?: continue
                    val result = DesktopLyrics(provider.name, lines.withBackgroundVocals().withGaps())
                    if (result.wordSynced) {
                        return@coroutineScope Result.success(result)
                    }
                    if (!prioritizeSyllables) {
                        return@coroutineScope Result.success(result)
                    }
                    if (lineSynced == null) lineSynced = result
                }
                lineSynced?.let(Result.Companion::success) ?: run {
                    Result.failure(IllegalStateException("No lyrics were found"))
                }
            } finally {
                requests.forEach { it.second.cancel() }
            }
        }
        cache[cacheKey] = CachedLyrics(System.currentTimeMillis(), result)
        return result
    }

    /** The words inside the file this track plays from, when it plays from one. */
    private fun embedded(song: Song): List<DesktopLyricLine>? {
        val path = song.localPath?.let { runCatching { java.nio.file.Paths.get(it) }.getOrNull() }
            ?: song.localUri?.let { runCatching { java.nio.file.Paths.get(java.net.URI(it)) }.getOrNull() }
            ?: return null
        val raw = DesktopEmbeddedLyrics.read(path) ?: return null
        return parseLrc(raw).takeIf { lines -> lines.any { it.text.isNotBlank() } }
    }

    /** What a source is asked, once the name has been cleaned and the recording worked out. */
    private data class Ask(
        val videoId: String,
        val title: String,
        val artist: String,
        val album: String?,
        val durationMs: Long,
        val isrc: String?,
        val hit: BiniHit?,
    )

    /**
     * How long to wait to be told which recording this is before giving up and matching on the
     * name.
     */
    private const val IDENTIFY_TIMEOUT_MS = 2_500L

    /** The one request made before the race: which recording is this? */
    private suspend fun identify(
        videoId: String,
        title: String,
        artist: String,
        album: String?,
        durationMs: Long,
        chain: List<Provider>,
    ): BiniHit? {
        if (chain.none { it.name == BINI }) return null
        val hit = withTimeoutOrNull(IDENTIFY_TIMEOUT_MS) {
            runCatching { biniIdentify(title, artist, album, durationMs, isrc = null) }.getOrNull()
        } ?: return null
        remember(videoId, hit.isrc)
        return hit
    }

    /** How many recordings to keep in hand; see [isrcs]. */
    private const val REMEMBERED = 100

    /** The recording behind a video id, once something has worked it out. */
    private val isrcs: MutableMap<String, String> = Collections.synchronizedMap(
        object : LinkedHashMap<String, String>(REMEMBERED, 0.75f, true) {
            override fun removeEldestEntry(eldest: Map.Entry<String, String>) = size > REMEMBERED
        },
    )

    private fun remember(videoId: String, isrc: String?) {
        if (isrc.isNullOrBlank() || videoId.isEmpty()) return
        isrcs[videoId] = isrc
    }

    private data class Provider(
        val name: String,
        val wordSynced: Boolean,
        val fetch: suspend (Ask) -> List<DesktopLyricLine>?,
    )

    private data class CachedLyrics(val createdAt: Long, val result: Result<DesktopLyrics>)

    /** Keys the settings screen writes and this reads. */
    internal const val KEY_SYNCED_LYRICS = "synced_lyrics"
    internal const val KEY_PRIORITIZE_SYLLABLES = "prioritize_syllable_lyrics"
    internal const val KEY_LYRICS_BLUR = "lyrics_blur"
    internal const val KEY_LYRICS_SOURCES = "lyrics_sources"

    /**
     * Which sources have ever been put in front of the listener; see
     * [DesktopPersistence.lyricsEnabledSources].
     */
    internal const val KEY_LYRICS_SOURCES_SEEN = "lyrics_sources_seen"
    internal const val KEY_LYRICS_ORDER = "lyrics_source_order"

    /** The sources that will actually be asked, in the order they are asked. */
    internal fun enabledSources(order: List<String>, enabled: Set<String>): List<String> =
        order.filter { it in enabled }

    private fun configuredProviders(): List<Provider> {
        val persistence = DesktopPersistence()
        val wanted = enabledSources(persistence.lyricsSourceOrder(), persistence.lyricsEnabledSources())
        val byName = providers.associateBy { it.name }
        return wanted.mapNotNull { byName[it] }
    }

    /** Genius, scraped. */
    private suspend fun genius(ask: Ask): List<DesktopLyricLine>? = withContext(Dispatchers.IO) {
        val title = cleanGeniusQuery(ask.title)
        val artist = cleanGeniusQuery(ask.artist)
        val url = geniusSongUrl(title, artist) ?: return@withContext null
        val html = get(url, mapOf("Accept" to "text/html,application/xhtml+xml")) ?: return@withContext null
        parseGenius(html)?.takeIf(List<DesktopLyricLine>::isNotEmpty)
    }

    private suspend fun geniusSongUrl(title: String, artist: String): String? {
        val query = "$artist $title".trim()
        val body = get("https://genius.com/api/search/multi?q=${URLEncoder.encode(query, "UTF-8")}")
            ?: return null
        return runCatching {
            val sections = json.parseToJsonElement(body).jsonObject["response"]?.jsonObject
                ?.get("sections") as? JsonArray ?: return null
            val hits = sections.firstOrNull {
                (it as? JsonObject)?.get("type")?.jsonPrimitive?.contentOrNull == "song"
            }?.jsonObject?.get("hits") as? JsonArray ?: return null
            val candidates = hits.mapNotNull { (it as? JsonObject)?.get("result")?.jsonObject }
            bestGeniusMatch(candidates, title.lowercase(Locale.ROOT), artist.lowercase(Locale.ROOT))
                ?.get("url")?.jsonPrimitive?.contentOrNull
        }.getOrNull()
    }

    private fun bestGeniusMatch(
        candidates: List<JsonObject>,
        title: String,
        artist: String,
    ): JsonObject? = candidates.maxByOrNull { item ->
        val hitTitle = item["title"]?.jsonPrimitive?.contentOrNull?.lowercase(Locale.ROOT).orEmpty()
        val hitArtist = item["artist_names"]?.jsonPrimitive?.contentOrNull?.lowercase(Locale.ROOT).orEmpty()
        var score = 0
        if (hitTitle == title) score += 50 else if (hitTitle.contains(title) || title.contains(hitTitle)) score += 25
        if (hitArtist.contains(artist) || artist.contains(hitArtist)) score += 40
        // Translations, tracklists and album-art pages all match the title well and are none of
        // them the song.
        val path = item["path"]?.jsonPrimitive?.contentOrNull.orEmpty()
        if (path.contains("translation", true) && !title.contains("translation")) score -= 30
        if (path.contains("türkçe", true) || path.contains("polskie-tlumaczenie", true)) score -= 40
        if (path.contains("tracklist", true) || path.contains("album-art", true)) score -= 50
        score
    }

    internal fun parseGenius(html: String): List<DesktopLyricLine>? {
        val document = org.jsoup.Jsoup.parse(html)
        // Modern Genius uses data-lyrics-container; older pages use div.lyrics.
        val containers = document.select("div[data-lyrics-container=true]")
            .ifEmpty { document.select("div.lyrics") }
        if (containers.isEmpty()) return null
        val text = buildString {
            containers.forEach { container ->
                container.select(
                    "[data-exclude-from-selection=true], .LyricsHeader__Container, " +
                        ".SongBioPreview__Container, .InreadAd__Container, button, script, style",
                ).remove()
                container.select("br").forEach { it.replaceWith(org.jsoup.nodes.TextNode("\n")) }
                container.select("p").forEach { it.prepend("\n") }
                container.wholeText().takeIf(String::isNotBlank)?.let { append(it).append('\n') }
            }
        }
        if (text.isBlank()) return null
        return geniusLines(stripGeniusArtifacts(text))
    }

    /** "You might also like", trailing "123Embed", and invisible spaces. */
    internal fun stripGeniusArtifacts(raw: String): String = raw
        .replace('\u00A0', ' ')
        .replace('\u200B', ' ')
        .replace('\uFEFF', ' ')
        .replace(GENIUS_ALSO_LIKE, "")
        .trim()
        .replace(GENIUS_TRAILING_EMBED, "")
        .trim()

    internal fun geniusLines(text: String): List<DesktopLyricLine> {
        val out = mutableListOf<DesktopLyricLine>()
        var lastWasGap = false
        text.lines().forEach { raw ->
            val line = raw.trim().replace(GENIUS_TRAILING_EMBED, "").trim()
            if (line.isEmpty()) {
                if (!lastWasGap && out.isNotEmpty()) {
                    out += DesktopLyricLine(timeMs = 0L, text = "")
                    lastWasGap = true
                }
            } else {
                out += DesktopLyricLine(timeMs = 0L, text = line)
                lastWasGap = false
            }
        }
        while (out.isNotEmpty() && out.first().isGap) out.removeAt(0)
        while (out.isNotEmpty() && out.last().isGap) out.removeAt(out.lastIndex)
        return out
    }

    private fun cleanGeniusQuery(text: String): String = text
        .replace(GENIUS_NOISE, " ")
        .substringBefore(" | ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .ifBlank { text }

    private val GENIUS_NOISE = Regex(
        """\((?:from|feat\.?|official|lyrical|video|audio|remix|music video|visualizer)[^)]*\)|\[[^]]*]|""" +
            """\b(?:official (?:video|audio|music video)|lyrical|full song|4k video)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val GENIUS_ALSO_LIKE = Regex("""\d*You might also like""", RegexOption.IGNORE_CASE)
    private val GENIUS_TRAILING_EMBED = Regex("""\d*Embed\s*$""", RegexOption.IGNORE_CASE)

    private suspend fun get(url: String, extraHeaders: Map<String, String> = emptyMap()): String? =
        runCatching {
            val response = client.get(url) {
                header("User-Agent", USER_AGENT)
                header("Accept", "application/json")
                extraHeaders.forEach { (key, value) -> header(key, value) }
            }
            if (response.status == HttpStatusCode.OK) response.bodyAsText() else null
        }.getOrNull()

    private suspend fun lrclib(ask: Ask): List<DesktopLyricLine>? {
        val seconds = (ask.durationMs / 1_000).toInt()
        val exact = get(query("https://lrclib.net/api/get", mapOf(
            "track_name" to cleanTitle(ask.title), "artist_name" to cleanTitle(ask.artist), "duration" to seconds.toString(),
        )))?.let { root(it)?.get("syncedLyrics")?.jsonPrimitive?.contentOrNull }
        val lrc = exact ?: get(query("https://lrclib.net/api/search", mapOf(
            "track_name" to cleanTitle(ask.title), "artist_name" to cleanTitle(ask.artist),
        )))?.let { raw ->
            val rows = json.parseToJsonElement(raw) as? JsonArray ?: return@let null
            rows.mapNotNull { it as? JsonObject }
                .filter { it["syncedLyrics"]?.jsonPrimitive?.contentOrNull?.isNotBlank() == true }
                .minByOrNull { abs((it["duration"]?.jsonPrimitive?.doubleOrNull ?: 0.0) - seconds) }
                ?.get("syncedLyrics")?.jsonPrimitive?.contentOrNull
        }
        return lrc?.let(::parseLrc)?.takeIf(List<DesktopLyricLine>::isNotEmpty)
    }

    private suspend fun lyricsPlus(ask: Ask): List<DesktopLyricLine>? {
        val hosts = lastLyricsPlusHost.get()?.let { listOf(it) + LYRICS_PLUS_HOSTS.filterNot { host -> host == it } }
            ?: LYRICS_PLUS_HOSTS
        for (host in hosts) {
            val params = buildMap {
                put("title", ask.title); put("artist", ask.artist)
                if (ask.durationMs > 0) put("duration", (ask.durationMs / 1_000).toString())
                ask.album?.let { put("album", it) }
                // Sent alongside the name rather than instead of it.
                ask.isrc?.takeIf(String::isNotBlank)?.let { put("isrc", it) }
            }
            val response = get(query("$host/v2/lyrics/get", params))?.let(::root) ?: continue
            val rows = response["lyrics"] as? JsonArray ?: continue
            val sung = rows.mapNotNull { element ->
                val row = element as? JsonObject ?: return@mapNotNull null
                val start = row["time"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
                val words = mergeSyllables(row["syllabus"] as? JsonArray)
                val text = row["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val built = when {
                    words.isNotEmpty() -> DesktopLyricLine(
                        timeMs = minOf(start, words.first().startMs),
                        text = words.joinToString(" ") { it.text },
                        words = words,
                    )
                    // Some sources are only line-synced; still worth showing.
                    text.isNotBlank() -> DesktopLyricLine(
                        timeMs = start,
                        text = text.trim(),
                        sungUntilMs = row["duration"]?.jsonPrimitive?.longOrNull?.takeIf { it > 0 }?.let { start + it },
                    )
                    else -> null
                }
                built?.to(row["element"])
            }.sortedBy { it.first.timeMs }
            if (sung.isEmpty()) continue

            val sides = desktopLineAlignments(sung.map { singerOf(it.second) }, agentTypes(response))
            lastLyricsPlusHost.set(host)
            return sung.mapIndexed { index, (line, element) ->
                // A payload that names no voices at all can still mark a line as the answering
                // side, which is how the older shape of this API said the same thing.
                val side = if (sides[index] == DesktopLyricAlignment.End || element.saysOpposite()) {
                    DesktopLyricAlignment.End
                } else {
                    DesktopLyricAlignment.Start
                }
                line.copy(alignment = side)
            }
        }
        return null
    }

    /** Glues syllables back into words. */
    private fun mergeSyllables(syllables: JsonArray?): List<DesktopLyricWord> {
        val words = mutableListOf<DesktopLyricWord>()
        val current = StringBuilder()
        var start = 0L
        var end = 0L

        syllables.orEmpty().forEach { element ->
            val syllable = element as? JsonObject ?: return@forEach
            val text = syllable["text"]?.jsonPrimitive?.contentOrNull ?: return@forEach
            if (text.isBlank()) return@forEach
            val time = syllable["time"]?.jsonPrimitive?.longOrNull ?: return@forEach
            if (current.isEmpty()) start = time
            current.append(text.trim())
            end = time + (syllable["duration"]?.jsonPrimitive?.longOrNull ?: 0L)
            if (text.last().isWhitespace()) {
                words += DesktopLyricWord(start, end, current.toString())
                current.setLength(0)
            }
        }
        if (current.isNotEmpty()) words += DesktopLyricWord(start, end, current.toString())
        return words
    }

    /**
     * The declared voices, keyed the way the lines refer to them — by alias where one is given,
     * which is what a line's `singer` actually holds.
     */
    private fun agentTypes(response: JsonObject): Map<String, String> = buildMap {
        val agents = response["metadata"]?.jsonObject?.get("agents") as? JsonObject ?: return@buildMap
        agents.forEach { (id, element) ->
            val agent = element as? JsonObject ?: return@forEach
            val type = agent["type"]?.jsonPrimitive?.contentOrNull ?: return@forEach
            put(agent["alias"]?.jsonPrimitive?.contentOrNull ?: id, type)
        }
    }

    /** Who sang a line. */
    private fun singerOf(element: JsonElement?): String? =
        (element as? JsonObject)?.get("singer")?.let { it as? JsonPrimitive }?.contentOrNull

    /** The older shape: `element` as a list of tags, one of which is the side. */
    private fun JsonElement?.saysOpposite(): Boolean {
        val array = this as? JsonArray ?: return false
        return array.any { entry ->
            val tag = (entry as? JsonPrimitive)?.takeIf { it.isString }?.content
            tag == "opposite" || tag == "right"
        }
    }

    /**
     * Apple Music TTML again, from a third host — and the only one here that will answer to a
     * recording rather than to a name.
     */
    private suspend fun biniLyrics(ask: Ask): List<DesktopLyricLine>? {
        val hit = ask.hit ?: biniIdentify(ask.title, ask.artist, ask.album, ask.durationMs, ask.isrc) ?: return null
        remember(ask.videoId, hit.isrc)
        val document = hit.lyricsUrl?.takeIf(String::isNotBlank) ?: return null
        val ttml = get(document) ?: return null
        return DesktopTtmlLyrics.parse(ttml).takeIf(List<DesktopLyricLine>::isNotEmpty)
    }

    /** Which recording this is, without fetching its words. */
    private suspend fun biniIdentify(
        title: String,
        artist: String,
        album: String?,
        durationMs: Long,
        isrc: String?,
    ): BiniHit? {
        val params = buildMap {
            if (!isrc.isNullOrBlank()) {
                // Nothing else is worth sending: the recording is named, and a title alongside it
                // could only ever disagree with it.
                put("isrc", isrc)
            } else {
                put("track", title)
                put("artist", artist)
                album?.takeIf(String::isNotBlank)?.let { put("album", it) }
                val seconds = durationMs / 1_000
                if (seconds > 0) put("duration", seconds.toString())
            }
        }
        // A miss is a 404 here rather than an empty result set, which [get] already turns into a
        // null.
        val body = get(query(BINI_HOST, params))?.let(::root) ?: return null
        val first = (body["results"] as? JsonArray)?.firstOrNull()?.jsonObject ?: return null
        return BiniHit(
            isrc = first["isrc"]?.jsonPrimitive?.contentOrNull,
            lyricsUrl = first["lyricsUrl"]?.jsonPrimitive?.contentOrNull,
        )
    }

    /** One BiniLyrics search result: the recording, and where its words live. */
    private data class BiniHit(val isrc: String?, val lyricsUrl: String?)

    /**
     * A community-submitted lyrics database, and the only source here whose contents are
     * contributed rather than licensed.
     */
    private suspend fun unison(ask: Ask): List<DesktopLyricLine>? {
        val params = buildMap {
            put("song", ask.title); put("artist", ask.artist)
            ask.album?.let { put("album", it) }
            val seconds = ask.durationMs / 1_000
            if (seconds > 0) put("duration", seconds.toString())
        }
        val body = get(query("https://unison.boidu.dev/lyrics", params))?.let(::root) ?: return null
        if (body["success"]?.jsonPrimitive?.booleanOrNull != true) return null
        val entry = body["data"]?.jsonObject ?: return null
        val text = entry["lyrics"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank) ?: return null
        val format = entry["format"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val syncType = entry["syncType"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val lines = when {
            format.equals("ttml", ignoreCase = true) -> DesktopTtmlLyrics.parse(text)
            syncType.equals("plain", ignoreCase = true) -> plainLines(text)
            // Word stamps first: the format field says only "lrc", and an enhanced file is still
            // one.
            else -> parseEnhancedLrc(text).ifEmpty { parseLrc(text) }
        }
        return lines.takeIf(List<DesktopLyricLine>::isNotEmpty)
    }

    /**
     * Lyrics with no timing: one line each, all stamped zero, which is how every unsynced source
     * here states the same thing.
     */
    private fun plainLines(text: String): List<DesktopLyricLine> = text.lineSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .map { DesktopLyricLine(timeMs = 0, text = it) }
        .toList()

    private suspend fun betterLyrics(ask: Ask): List<DesktopLyricLine>? {
        val params = buildMap {
            put("s", ask.title); put("a", ask.artist)
            if (ask.durationMs > 0) put("d", (ask.durationMs / 1_000).toString())
            ask.album?.let { put("al", it) }
        }
        val ttml = get(query("https://lyrics-api.boidu.dev/getLyrics", params))
            ?.let(::root)?.get("ttml")?.jsonPrimitive?.contentOrNull ?: return null
        return DesktopTtmlLyrics.parse(ttml).takeIf(List<DesktopLyricLine>::isNotEmpty)
    }

    private suspend fun paxSenix(ask: Ask): List<DesktopLyricLine>? {
        val token = appleToken() ?: return null
        val search = get(
            query("https://amp-api.music.apple.com/v1/catalog/us/search", mapOf(
                "term" to "${cleanTitle(ask.title)} ${cleanTitle(ask.artist)}",
                "types" to "songs", "limit" to "10", "l" to "en-US",
            )),
            mapOf("Authorization" to "Bearer $token", "Origin" to "https://music.apple.com"),
        ) ?: return null
        val rows = root(search)?.get("results")?.jsonObject?.get("songs")?.jsonObject?.get("data") as? JsonArray ?: return null
        val seconds = (ask.durationMs / 1_000).toInt()
        val songRow = rows.mapNotNull { it.jsonObject }.maxByOrNull { row ->
            val attributes = row["attributes"]?.jsonObject ?: return@maxByOrNull 0
            val name = attributes["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val artist = attributes["artistName"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val duration = attributes["durationInMillis"]?.jsonPrimitive?.longOrNull?.div(1_000) ?: seconds.toLong()
            (if (name.equals(ask.title, true)) 80 else if (name.contains(ask.title, true)) 40 else 0) +
                (if (artist.contains(ask.artist, true)) 40 else 0) - abs(duration - seconds)
        } ?: return null
        val id = songRow["id"]?.jsonPrimitive?.contentOrNull ?: return null
        val body = get(query("https://lyrics.paxsenix.org/apple-music/lyrics", mapOf("id" to id)))?.let(::root) ?: return null
        listOf("ttmlContent", "elrcMultiPerson", "elrc").forEach { key ->
            body[key]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)?.let { value ->
                val parsed = if (key == "ttmlContent") DesktopTtmlLyrics.parse(value) else parseEnhancedLrc(value).ifEmpty { parseLrc(value) }
                if (parsed.isNotEmpty()) return parsed
            }
        }
        return null
    }

    private suspend fun appleToken(): String? {
        appleToken.get()?.let { return it }
        val home = get("https://music.apple.com/us/new", mapOf("Accept" to "text/html")) ?: return null
        val scriptPath = Regex("/assets/index~[^\"]+\\.js").find(home)?.value ?: return null
        val script = get("https://music.apple.com$scriptPath", mapOf("Accept" to "text/javascript")) ?: return null
        return Regex("eyJ[A-Za-z0-9_-]+\\.eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+").find(script)?.value
            ?.also { appleToken.compareAndSet(null, it) }
    }

    private suspend fun simpMusic(ask: Ask): List<DesktopLyricLine>? {
        if (ask.videoId.contains(':')) return null
        val response = get("https://api-lyrics.simpmusic.org/v1/${urlEncode(ask.videoId)}")?.let(::root) ?: return null
        if (response["success"]?.jsonPrimitive?.contentOrNull != "true") return null
        val seconds = (ask.durationMs / 1_000).toInt()
        val tracks = response["data"] as? JsonArray ?: return null
        val track = tracks.mapNotNull { it as? JsonObject }
            .filter { seconds <= 0 || abs((it["duration"]?.jsonPrimitive?.longOrNull ?: 0L) - seconds) <= 10 }
            .minByOrNull { abs((it["duration"]?.jsonPrimitive?.longOrNull ?: 0L) - seconds) } ?: return null
        track["richSyncLyrics"]?.jsonPrimitive?.contentOrNull?.let(::parseEnhancedLrc)
            ?.takeIf(List<DesktopLyricLine>::isNotEmpty)?.let { return it }
        return track["syncedLyrics"]?.jsonPrimitive?.contentOrNull?.let(::parseLrc)
    }

    private suspend fun kuGou(ask: Ask): List<DesktopLyricLine>? {
        val seconds = (ask.durationMs / 1_000).toInt()
        val keyword = "${ask.title} - ${ask.artist}"
        val hashes = get(query("https://mobileservice.kugou.com/api/v3/search/song", mapOf(
            "version" to "9108", "plat" to "0", "pagesize" to "8", "showtype" to "0", "keyword" to keyword,
        )))?.let { root(it)?.get("data")?.jsonObject?.get("info") as? JsonArray }
            ?.mapNotNull { it.jsonObject["hash"]?.jsonPrimitive?.contentOrNull }.orEmpty()
        for (hash in hashes) {
            val candidate = get(query("https://lyrics.kugou.com/search", mapOf("ver" to "1", "man" to "yes", "client" to "pc", "hash" to hash)))
                ?.let { root(it)?.get("candidates") as? JsonArray }?.firstOrNull()?.jsonObject ?: continue
            val lrc = downloadKuGou(candidate) ?: continue
            parseLrc(lrc).takeIf(List<DesktopLyricLine>::isNotEmpty)?.let { return it }
        }
        val candidate = get(query("https://lyrics.kugou.com/search", mapOf(
            "ver" to "1", "man" to "yes", "client" to "pc", "keyword" to keyword, "duration" to (seconds * 1_000).toString(),
        )))?.let { root(it)?.get("candidates") as? JsonArray }?.firstOrNull()?.jsonObject ?: return null
        return downloadKuGou(candidate)?.let(::parseLrc)
    }

    private suspend fun downloadKuGou(candidate: JsonObject): String? {
        val id = candidate["id"]?.jsonPrimitive?.contentOrNull ?: return null
        val accessKey = candidate["accesskey"]?.jsonPrimitive?.contentOrNull ?: return null
        val body = get(query("https://lyrics.kugou.com/download", mapOf(
            "fmt" to "lrc", "charset" to "utf8", "client" to "pc", "ver" to "1", "id" to id, "accesskey" to accessKey,
        ))) ?: return null
        val content = root(body)?.get("content")?.jsonPrimitive?.contentOrNull ?: return null
        return runCatching { String(Base64.getDecoder().decode(content), Charsets.UTF_8) }.getOrNull()
    }

    private suspend fun musixmatch(ask: Ask): List<DesktopLyricLine>? {
        val tokenBody = get(signed("https://apic.musixmatch.com/ws/1.1/token.get?app_id=web-desktop-app-v1.0")) ?: return null
        val token = root(tokenBody)?.get("message")?.jsonObject?.get("body")?.jsonObject
            ?.get("user_token")?.jsonPrimitive?.contentOrNull ?: return null
        val tracks = get(signed(query("https://apic.musixmatch.com/ws/1.1/track.search", mapOf(
            "app_id" to "web-desktop-app-v1.0", "q_track" to ask.title, "q_artist" to ask.artist,
            "f_has_lyrics" to "1", "s_track_rating" to "desc", "page_size" to "10", "page" to "1", "usertoken" to token,
        ))))?.let { root(it)?.get("message")?.jsonObject?.get("body")?.jsonObject?.get("track_list") as? JsonArray }.orEmpty()
        val seconds = (ask.durationMs / 1_000).toInt()
        val track = tracks.mapNotNull { it.jsonObject["track"]?.jsonObject }.maxByOrNull { row ->
            val titleScore = if (row["track_name"]?.jsonPrimitive?.contentOrNull.equals(ask.title, true)) 80 else 0
            val artistScore = if (row["artist_name"]?.jsonPrimitive?.contentOrNull.orEmpty().contains(ask.artist, true)) 40 else 0
            titleScore + artistScore - abs((row["track_length"]?.jsonPrimitive?.longOrNull ?: seconds.toLong()) - seconds)
        } ?: return null
        if (track["has_subtitles"]?.jsonPrimitive?.contentOrNull != "1") return null
        val id = track["track_id"]?.jsonPrimitive?.contentOrNull ?: return null
        val subtitle = get(signed(query("https://apic.musixmatch.com/ws/1.1/track.subtitle.get", mapOf(
            "app_id" to "web-desktop-app-v1.0", "track_id" to id, "subtitle_format" to "mxm", "usertoken" to token,
        ))))?.let { root(it)?.get("message")?.jsonObject?.get("body")?.jsonObject?.get("subtitle")?.jsonObject
            ?.get("subtitle_body")?.jsonPrimitive?.contentOrNull } ?: return null
        val rows = runCatching { json.parseToJsonElement(subtitle) as JsonArray }.getOrNull() ?: return null
        val lrc = rows.mapNotNull { row ->
            val text = row.jsonObject["text"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val time = row.jsonObject["time"]?.jsonObject?.get("total")?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            "[${"%02d:%06.3f".format(Locale.US, time.toInt() / 60, time % 60)}]$text"
        }.joinToString("\n")
        return parseLrc(lrc).takeIf(List<DesktopLyricLine>::isNotEmpty)
    }

    private fun signed(url: String): String {
        val date = SimpleDateFormat("yyyyMMdd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(MUSIXMATCH_SECRET.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val signature = Base64.getEncoder().encodeToString(mac.doFinal("$url$date".toByteArray(Charsets.UTF_8)))
        return "$url&signature=${urlEncode(signature)}&signature_protocol=sha256"
    }

    private fun parseLrc(lrc: String): List<DesktopLyricLine> = lrc.lineSequence().mapNotNull { line ->
        val match = LRC_LINE.find(line) ?: return@mapNotNull null
        DesktopLyricLine(
            timeMs = stamp(match.groupValues[1], match.groupValues[2], match.groupValues[3]),
            text = line.substring(match.range.last + 1).trim(),
        )
    }.sortedBy(DesktopLyricLine::timeMs).toList()

    private fun parseEnhancedLrc(lrc: String): List<DesktopLyricLine> {
        val rows = lrc.lineSequence().mapNotNull { line ->
            val row = ENHANCED_LINE.matchEntire(line.trim()) ?: return@mapNotNull null
            val words = ENHANCED_WORD.findAll(row.groupValues[4]).mapNotNull { match ->
                val text = decodeEntities(match.groupValues[4]).trim()
                text.takeIf(String::isNotBlank)?.let {
                    val start = stamp(match.groupValues[1], match.groupValues[2], match.groupValues[3])
                    DesktopLyricWord(start, start, it)
                }
            }.toList()
            row to words
        }.toList()
        if (rows.none { it.second.isNotEmpty() }) return emptyList()
        return rows.mapIndexedNotNull { index, (_, originalWords) ->
            if (originalWords.isEmpty()) return@mapIndexedNotNull null
            val nextLine = rows.getOrNull(index + 1)?.second?.firstOrNull()?.startMs
            val words = originalWords.mapIndexed { wordIndex, word ->
                word.copy(endMs = maxOf(word.startMs, originalWords.getOrNull(wordIndex + 1)?.startMs ?: nextLine ?: word.startMs + 800))
            }
            DesktopLyricLine(words.first().startMs, words.joinToString(" ") { it.text }, words)
        }
    }

    private fun List<DesktopLyricLine>.withGaps(): List<DesktopLyricLine> {
        if (isEmpty()) return this
        val output = mutableListOf<DesktopLyricLine>()
        if (first().timeMs >= MIN_GAP_MS) output += DesktopLyricLine(0, "")
        forEachIndexed { index, line ->
            output += line
            val next = getOrNull(index + 1) ?: return@forEachIndexed
            val end = line.words.lastOrNull()?.endMs ?: line.sungUntilMs ?: line.timeMs
            if (end > line.timeMs && next.timeMs - end >= MIN_GAP_MS) output += DesktopLyricLine(end, "")
        }
        return output
    }

    private fun root(body: String): JsonObject? = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull()

    private fun query(base: String, params: Map<String, String>): String =
        base + params.entries.joinToString("&", prefix = "?") { "${urlEncode(it.key)}=${urlEncode(it.value)}" }

    private fun urlEncode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

    private fun cleanTitle(value: String): String = value
        .replace(Regex("\\s*[(\\[][^)\\]]*(official|video|audio|lyrics?|visualizer|live|remaster)[^)]*[)\\]]", RegexOption.IGNORE_CASE), "")
        .substringBefore(" | ").replace(Regex("\\s+"), " ").trim()

    private fun stamp(minutes: String, seconds: String, fraction: String): Long =
        minutes.toLong() * 60_000 + seconds.toLong() * 1_000 + if (fraction.length == 2) fraction.toLong() * 10 else fraction.toLong()

    private fun decodeEntities(value: String): String = value
        .replace(Regex("&#x([0-9a-fA-F]+);")) { it.groupValues[1].toInt(16).toChar().toString() }
        .replace(Regex("&#(\\d+);")) { it.groupValues[1].toInt().toChar().toString() }
        .replace("&apos;", "'").replace("&quot;", "\"").replace("&nbsp;", " ")
        .replace("&lt;", "<").replace("&gt;", ">")
        .replace("&amp;", "&")

    private const val BINI_HOST = "https://lyrics-api.binimum.org/"

    private val LYRICS_PLUS_HOSTS = listOf(
        "https://lyricsplus.prjktla.my.id", "https://lyricsplus.atomix.one", "https://lyricsplus.binimum.org",
        "https://lyricsplus.prjktla.workers.dev", "https://lyricsplus-seven.vercel.app", "https://lyrics-plus-backend.vercel.app",
    )
    private val LRC_LINE = Regex("""^\[(\d{1,3}):(\d{2})[.:](\d{2,3})\]""")
    private val ENHANCED_LINE = Regex("""^\[(\d{1,3}):(\d{2})[.:](\d{2,3})\](.*)$""")
    private val ENHANCED_WORD = Regex("""<(\d{1,3}):(\d{2})[.:](\d{2,3})>([^<]*)""")
    private const val MUSIXMATCH_SECRET = "RJDefUswhwjkZDeM"
}
