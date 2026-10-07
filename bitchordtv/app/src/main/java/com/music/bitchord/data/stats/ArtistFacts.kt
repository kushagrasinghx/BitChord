package com.music.bitchord.data.stats

import android.content.Context
import android.net.Uri
import android.util.Log
import com.music.bitchord.BuildConfig
import com.music.bitchord.data.Http
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * What the Replay knows about an artist beyond their name: their picture, their
 * page, and what kind of music they make.
 *
 * ## Why any of this needs looking up
 *
 * Listening is counted per *name*, because a name is the only thing every track
 * carries — see [ListeningStats]. That is enough to rank artists and nothing
 * else. Three things the artist chart wants are simply not in the listening:
 *
 *  - **A picture of the artist.** Every track carries its own sleeve, and using
 *    that gave an artist chart illustrated with album covers — the same cover as
 *    the song chart above it, which reads as the page having drawn the wrong
 *    list rather than as a deliberate choice.
 *  - **A page to open.** A browse id only rides along when the row that queued
 *    the track happened to have one, which for a home-feed card or an AutoPlay
 *    suggestion it does not.
 *  - **A genre.** Nothing this app already talks to states one. YouTube Music's
 *    browse responses carry none — an album page bills itself "Album • 2023" —
 *    and the source modules hand back audio, not taxonomy.
 *
 * ## One store, two sources, two gates
 *
 * The picture and the page come from a YouTube Music artist search, which this
 * app is already talking to constantly and which is therefore not worth a
 * setting. The genre comes from Last.fm's `artist.getTopTags`, which is a
 * different service and *is* a setting ([AppSettings.replayGenres]): it sends an
 * artist's name and nothing else — no track, no time, no id, no indication that
 * anything was played — and turned off, the genre chart simply isn't drawn while
 * every other chart is unaffected.
 *
 * Both are asked once per artist and kept on this device for good, so the cost
 * is a couple of requests the first time somebody new turns up.
 *
 * ## Why the tags are filtered rather than used
 *
 * Last.fm tags are folksonomy, not taxonomy: alongside "shoegaze" sit "seen
 * live", "favourites", "albums i own" and several thousand more that describe
 * the tagger rather than the music. Taking the top tag verbatim produces a chart
 * whose leading genre is "awesome". So a tag only counts if it matches
 * [VOCABULARY] — a fixed list of things that are actually genres — and an artist
 * with no matching tag contributes nothing rather than a wrong answer.
 */
object ArtistFacts {

    private lateinit var file: File

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** artist (lowercased) → what is known about them. */
    private val known = ConcurrentHashMap<String, StoredArtist>()

    /** Names already queued this session, so a track on repeat asks once. */
    private val queued = ConcurrentHashMap.newKeySet<String>()

    private val requests = Channel<String>(Channel.UNLIMITED)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var dirty = false

    /**
     * Bumped whenever a lookup lands.
     *
     * The Replay reads these facts once, while it is building its charts, so a
     * picture that arrives a second after the page opened would otherwise not
     * appear until the page was opened again — which for a page opened a few
     * times a year means never. Watching this lets it rebuild.
     */
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    fun init(context: Context) {
        file = File(context.filesDir, FILE_NAME)
        scope.launch {
            load()
            worker()
        }
        // Writes are on a timer rather than one per answer. The cache holds
        // every artist ever played, so rewriting it after each lookup meant a
        // few hundred kilobytes per artist during a backfill — for a file that
        // is only read once, at launch.
        scope.launch {
            while (true) {
                delay(SAVE_INTERVAL_MS)
                save()
            }
        }
    }

    private val ready: Boolean get() = this::file.isInitialized

    /** Whether a genre chart can be drawn at all on this build and these settings. */
    val genresAvailable: Boolean
        get() = AppSettings.replayGenres.value && BuildConfig.LASTFM_API_KEY.isNotBlank()

    // ── Reading ─────────────────────────────────────────────────────────────
    //
    // None of these reach the network. The Replay is drawn from what is already
    // known, and an artist that isn't yet simply keeps the track's sleeve and no
    // genre this time round. Asking here would put a round trip per artist
    // behind a page that opens with fifty of them on it.

    fun genresFor(artist: String): List<String> {
        if (!genresAvailable) return emptyList()
        return known[key(artist)]?.genres.orEmpty()
    }

    /** A picture of the artist, or null to fall back to whatever the row has. */
    fun imageFor(artist: String): String? = known[key(artist)]?.image

    /** The artist's page, so a chart row opens it without searching first. */
    fun browseIdFor(artist: String): String? = known[key(artist)]?.browseId

    /**
     * An artist was played. Looks them up if anything about them is missing.
     *
     * Called from the recording path rather than from the Replay page, so the
     * answers accumulate quietly while music plays and the page has them in hand
     * when it opens.
     */
    fun noticed(artist: String) {
        if (!ready) return
        val key = key(artist)
        if (key.isEmpty() || key.length > MAX_NAME_LENGTH) return
        val entry = known[key]
        if (entry != null && !entry.wants()) return
        if (!queued.add(key)) return
        requests.trySend(artist.trim())
    }

    /** What is still worth asking about for this artist. */
    private fun StoredArtist.wants(): Boolean {
        // A miss is remembered too, or an artist neither service has heard of is
        // asked about on every play forever. It expires, because the reason for
        // a miss is as often a dropped connection as an unknown artist.
        val wantsCard = browseId == null &&
            System.currentTimeMillis() - cardAt > TimeUnit.DAYS.toMillis(RETRY_DAYS)
        val wantsGenres = genresAvailable && genres.isEmpty() &&
            System.currentTimeMillis() - genresAt > TimeUnit.DAYS.toMillis(RETRY_DAYS)
        return wantsCard || wantsGenres
    }

    // ── The lookups ─────────────────────────────────────────────────────────

    /**
     * One artist at a time, spaced out.
     *
     * Serial and slow on purpose: this is background enrichment for a page that
     * may not be opened for months, and it shares [Http.client] and the Innertube
     * session with playback. Nothing here is worth a millisecond of a stream's
     * latency.
     */
    private suspend fun worker() {
        for (name in requests) {
            val existing = known[key(name)]
            if (existing?.browseId == null) {
                runCatching { fetchCard(name) }
                    .onFailure { Log.w(TAG, "Artist lookup failed for $name", it) }
            }
            if (genresAvailable && existing?.genres.isNullOrEmpty()) {
                runCatching { fetchGenres(name) }
                    .onFailure { Log.w(TAG, "Genre lookup failed for $name", it) }
            }
            // Published straight away so an open Replay picks the answer up;
            // the disk copy follows on its own timer, above.
            _revision.value++
            delay(REQUEST_SPACING_MS)
        }
    }

    /**
     * The artist's picture and page, off a YouTube Music artist search.
     *
     * A search rather than a browse, because a browse needs the id and the id is
     * half of what this is for. The top artist hit for a name is the artist:
     * that is the same lookup the app already makes to find a video's catalogue
     * release, and the same one a person would make.
     */
    private suspend fun fetchCard(name: String) {
        val hit = YtMusicRepository.search(name, SearchFilter.ARTISTS).getOrNull()
            ?.filterIsInstance<SearchResult.Browse>()
            ?.firstOrNull()
            ?.item
        val entry = known[key(name)] ?: StoredArtist(key = key(name))
        known[key(name)] = entry.copy(
            // Only when the hit is actually this artist. A search for a name
            // nobody has heard of still returns *something*, and filing a
            // stranger's photograph under someone else's name is worse than
            // keeping the sleeve.
            image = hit?.thumbnailUrl?.takeIf { hit.title.equals(name, ignoreCase = true) }
                ?: entry.image,
            browseId = hit?.browseId?.takeIf { hit.title.equals(name, ignoreCase = true) }
                ?: entry.browseId,
            cardAt = System.currentTimeMillis(),
        )
        dirty = true
    }

    private fun fetchGenres(name: String) {
        val url = "https://ws.audioscrobbler.com/2.0/?method=artist.gettoptags" +
            "&artist=${Uri.encode(name)}" +
            "&api_key=${Uri.encode(BuildConfig.LASTFM_API_KEY)}" +
            "&autocorrect=1&format=json"
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        val body = Http.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return
            response.body?.string()
        } ?: return

        val tags = runCatching {
            Json.parseToJsonElement(body).jsonObject["toptags"]
                ?.jsonObject?.get("tag")?.jsonArray
                ?.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.content }
        }.getOrNull().orEmpty()

        val genres = tags.asSequence()
            .mapNotNull { canonical(it) }
            .distinct()
            .take(MAX_GENRES_PER_ARTIST)
            .toList()

        val entry = known[key(name)] ?: StoredArtist(key = key(name))
        known[key(name)] = entry.copy(genres = genres, genresAt = System.currentTimeMillis())
        dirty = true
    }

    /**
     * A tag as a genre, or null if it isn't one.
     *
     * Matched against [VOCABULARY] after normalising punctuation and a handful
     * of spellings that are the same genre — "hip-hop" and "hip hop", "r&b" and
     * "rnb" — because splitting one genre across two rows is the same failure as
     * admitting "seen live", just less obvious on the page.
     */
    private fun canonical(tag: String): String? {
        val cleaned = tag.trim().lowercase(Locale.ROOT)
            .replace('-', ' ')
            .replace("&", "and")
            .replace(Regex("[^a-z0-9 ]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (cleaned.isEmpty()) return null
        return VOCABULARY[cleaned] ?: VOCABULARY[ALIASES[cleaned] ?: return null]
    }

    private fun key(artist: String): String = artist.trim().lowercase(Locale.ROOT)

    // ── Persistence ─────────────────────────────────────────────────────────

    private fun load() {
        if (!ready || !file.exists()) return
        runCatching {
            json.decodeFromString(Stored.serializer(), file.readText())
        }.onSuccess { stored ->
            stored.artists.forEach { known[it.key] = it }
        }.onFailure {
            Log.w(TAG, "Discarding unreadable artist cache", it)
            file.delete()
        }
    }

    private fun save() {
        if (!ready || !dirty) return
        dirty = false
        runCatching {
            val stored = Stored(
                artists = known.values
                    .sortedByDescending { maxOf(it.cardAt, it.genresAt) }
                    .take(MAX_ARTISTS),
            )
            file.writeText(json.encodeToString(Stored.serializer(), stored))
        }.onFailure { Log.w(TAG, "Could not write artist cache", it) }
    }

    @Serializable
    private data class Stored(val version: Int = 2, val artists: List<StoredArtist> = emptyList())

    @Serializable
    data class StoredArtist(
        val key: String,
        val genres: List<String> = emptyList(),
        val genresAt: Long = 0L,
        val image: String? = null,
        val browseId: String? = null,
        val cardAt: Long = 0L,
    )

    private const val TAG = "BitChordArtists"
    private const val FILE_NAME = "artist_facts.json"
    private const val USER_AGENT = "BitChord/${BuildConfig.VERSION_NAME}"
    private const val MAX_NAME_LENGTH = 120
    private const val MAX_GENRES_PER_ARTIST = 2
    private const val MAX_ARTISTS = 4_000
    private const val REQUEST_SPACING_MS = 1_500L
    private const val SAVE_INTERVAL_MS = 5_000L
    private const val RETRY_DAYS = 14L

    /**
     * Genres whose display name isn't just their words capitalised.
     *
     * Declared before [VOCABULARY] because that is where it is read: an object's
     * properties initialise in source order, and a lookup table consulted by an
     * earlier initialiser is still null when it runs.
     */
    private val SPELLINGS = mapOf(
        "randb" to "R&B",
        "edm" to "EDM",
        "lo fi" to "Lo-Fi",
        "hip hop" to "Hip-Hop",
        "k pop" to "K-Pop",
        "j pop" to "J-Pop",
        "j rock" to "J-Rock",
        "c pop" to "C-Pop",
        "drum and bass" to "Drum & Bass",
        "singer songwriter" to "Singer-Songwriter",
        "post punk" to "Post-Punk",
        "post rock" to "Post-Rock",
        "bossa nova" to "Bossa Nova",
    )

    /**
     * The genres a chart may name, mapped from their normalised form to how they
     * are spelt on screen.
     *
     * A fixed list rather than a heuristic because the failure mode of a
     * heuristic here is silent and permanent: a tag that slips through becomes a
     * row on someone's Replay, and there is no signal anywhere that it was
     * wrong. Adding to this list is cheap; letting it grow itself is not.
     */
    private val VOCABULARY: Map<String, String> = listOf(
        "pop", "rock", "hip hop", "rap", "randb", "soul", "funk", "jazz", "blues",
        "country", "folk", "indie", "indie pop", "indie rock", "alternative",
        "alternative rock", "metal", "heavy metal", "punk", "punk rock", "hardcore",
        "electronic", "house", "deep house", "techno", "trance", "dubstep",
        "drum and bass", "edm", "ambient", "lo fi", "synthpop", "disco",
        "classical", "opera", "soundtrack", "instrumental", "acoustic",
        "reggae", "reggaeton", "dancehall", "ska", "latin", "salsa", "bossa nova",
        "afrobeats", "afrobeat", "k pop", "j pop", "j rock", "c pop",
        "bollywood", "punjabi", "desi", "bhangra", "hindi", "sufi", "ghazal",
        "singer songwriter", "emo", "grunge", "shoegaze", "psychedelic",
        "progressive rock", "hard rock", "garage rock", "post punk", "new wave",
        "gospel", "christian", "world", "experimental", "trap", "drill", "grime",
        "phonk", "hyperpop", "chillout", "downtempo", "jungle", "garage",
        "bluegrass", "americana", "swing", "big band", "motown", "britpop",
        "dream pop", "art pop", "noise", "industrial", "gothic", "doom metal",
        "black metal", "death metal", "thrash metal", "metalcore", "post rock",
        "math rock", "jam band", "surf rock", "rockabilly", "boom bap",
        "cloud rap", "conscious hip hop", "west coast rap", "east coast rap",
    ).associateWith { normalised ->
        SPELLINGS[normalised] ?: normalised.split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { it.uppercase(Locale.ROOT) }
        }
    }

    /** Tags that are a genre in [VOCABULARY] under another name. */
    private val ALIASES = mapOf(
        "rnb" to "randb",
        "r and b" to "randb",
        "rhythm and blues" to "randb",
        "contemporary randb" to "randb",
        "hiphop" to "hip hop",
        "hip hop rap" to "hip hop",
        "lofi" to "lo fi",
        "lo fi hip hop" to "lo fi",
        "chillhop" to "lo fi",
        "kpop" to "k pop",
        "jpop" to "j pop",
        "jrock" to "j rock",
        "cpop" to "c pop",
        "korean" to "k pop",
        "dnb" to "drum and bass",
        "drum n bass" to "drum and bass",
        "drumandbass" to "drum and bass",
        "electronica" to "electronic",
        "electro" to "electronic",
        "dance" to "electronic",
        "electropop" to "synthpop",
        "synth pop" to "synthpop",
        "indierock" to "indie rock",
        "indiepop" to "indie pop",
        "alt rock" to "alternative rock",
        "altrock" to "alternative rock",
        "singersongwriter" to "singer songwriter",
        "female vocalists" to "pop",
        "hindi pop" to "bollywood",
        "indian" to "desi",
        "filmi" to "bollywood",
        "afro beats" to "afrobeats",
        "afropop" to "afrobeats",
        "amapiano" to "afrobeats",
        "regueton" to "reggaeton",
        "latin pop" to "latin",
        "trip hop" to "downtempo",
        "nu metal" to "metal",
        "classic rock" to "rock",
        "soft rock" to "rock",
        "pop rock" to "rock",
        "pop punk" to "punk",
        "hardcore punk" to "hardcore",
        "orchestral" to "classical",
        "film score" to "soundtrack",
        "score" to "soundtrack",
        "ost" to "soundtrack",
        "chill" to "chillout",
        "chillwave" to "chillout",
        "worship" to "christian",
        "rap rock" to "rap",
        "gangsta rap" to "rap",
        "underground hip hop" to "hip hop",
    )
}
