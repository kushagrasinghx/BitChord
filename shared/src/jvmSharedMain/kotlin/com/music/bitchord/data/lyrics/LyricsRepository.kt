package com.music.bitchord.data.lyrics

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Collections
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

private data class LyricsLookupKey(
    val identity: String,
    val sources: Set<LyricsSource>,
    val order: List<LyricsSource>,
    val prioritizeSyllableSync: Boolean,
) {
    companion object {
        fun of(
            videoId: String,
            title: String,
            artist: String,
            durationMs: Long,
            sources: Set<LyricsSource>,
            order: List<LyricsSource>,
            prioritize: Boolean,
        ): LyricsLookupKey {
            val identity = videoId.trim().takeIf { it.isNotEmpty() } ?: listOf(
                title.forLyricsSearch().trim().lowercase(Locale.ROOT),
                artist.artistForLyricsSearch().trim().lowercase(Locale.ROOT),
                (durationMs / 1000).toString(),
            ).joinToString("\u0000")
            return LyricsLookupKey(identity, sources.toSet(), order.toList(), prioritize)
        }
    }
}

private enum class LookupStatus { STARTED, FOUND, NOT_FOUND, CANCELLED }
private data class LookupProvider(val status: LookupStatus, val result: LyricsRepository.Result? = null)
private data class CachedLyricsSnapshot(
    val result: LyricsRepository.Result,
    val providers: Map<LyricsSource, LookupProvider>,
)
private sealed interface LookupEvent {
    data class Provider(val source: LyricsSource, val value: LookupProvider) : LookupEvent
    data class Progress(val result: LyricsRepository.Result) : LookupEvent
}

private class LyricsLookupCoordinator {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private val cache = object : LinkedHashMap<LyricsLookupKey, CachedLyricsSnapshot>(40, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<LyricsLookupKey, CachedLyricsSnapshot>?) =
            size > 40
    }
    private inner class SharedLookup(val speculative: Boolean) {
        val listeners = mutableListOf<(LookupEvent) -> Unit>()
        val providers = linkedMapOf<LyricsSource, LookupProvider>()
        var progress: LyricsRepository.Result? = null
        var cancellation: Job? = null
        lateinit var deferred: Deferred<LyricsRepository.Result?>
    }
    private val inFlight = mutableMapOf<LyricsLookupKey, SharedLookup>()

    suspend fun get(
        key: LyricsLookupKey,
        speculative: Boolean,
        listener: (LookupEvent) -> Unit,
        execute: suspend (Boolean, (LookupEvent) -> Unit) -> LyricsRepository.Result?,
    ): LyricsRepository.Result? {
        val shared: SharedLookup
        synchronized(lock) {
            cache[key]?.let { snapshot ->
                snapshot.providers.forEach { (source, state) -> deliver(listener, LookupEvent.Provider(source, state)) }
                deliver(listener, LookupEvent.Progress(snapshot.result))
                return snapshot.result
            }
            shared = inFlight[key] ?: SharedLookup(speculative).also { entry ->
                entry.deferred = scope.async(start = CoroutineStart.LAZY) {
                    try {
                        val result = execute(speculative) { event -> publish(entry, event) }
                        currentCoroutineContext().ensureActive()
                        synchronized(lock) {
                            if (result != null) {
                                cache[key] = CachedLyricsSnapshot(result, entry.providers.toMap())
                            }
                        }
                        result
                    } finally {
                        synchronized(lock) {
                            entry.cancellation?.cancel()
                            if (inFlight[key] === entry) inFlight.remove(key)
                        }
                    }
                }
                inFlight[key] = entry
            }
            shared.cancellation?.cancel()
            shared.cancellation = null
            shared.listeners.add(listener)
            shared.providers.forEach { (source, state) -> deliver(listener, LookupEvent.Provider(source, state)) }
            shared.progress?.let { deliver(listener, LookupEvent.Progress(it)) }
            shared.deferred.start()
        }
        try {
            return shared.deferred.await()
        } finally {
            synchronized(lock) {
                shared.listeners.remove(listener)
                if (shared.listeners.isEmpty() && !shared.deferred.isCompleted) {
                    if (shared.speculative) {
                        // Allow the foreground to claim an in-flight prefetch.
                        shared.cancellation = scope.launch {
                            delay(500L)
                            synchronized(lock) {
                                if (shared.listeners.isEmpty() && inFlight[key] === shared) {
                                    inFlight.remove(key)
                                    shared.deferred.cancel()
                                }
                            }
                        }
                    } else {
                        if (inFlight[key] === shared) inFlight.remove(key)
                        shared.deferred.cancel()
                    }
                }
            }
        }
    }

    private fun publish(shared: SharedLookup, event: LookupEvent) = synchronized(lock) {
        when (event) {
            is LookupEvent.Provider -> shared.providers[event.source] = event.value
            is LookupEvent.Progress -> shared.progress = event.result
        }
        shared.listeners.toList().forEach { deliver(it, event) }
    }

    private fun deliver(listener: (LookupEvent) -> Unit, event: LookupEvent) {
        try {
            listener(event)
        } catch (_: Exception) {
            // A subscriber cannot interrupt the shared lookup.
        }
    }
}

/**
 * Retrieves lyrics from the enabled providers. Independent sources start at once;
 * sources that can use an ISRC share a short recording-identification request.
 * The first result is published immediately, with a short window for a better
 * priority or word-synced result before the remaining requests are cancelled.
 */
object LyricsRepository {

    /** Lyrics, and which source they turned out to come from. */
    data class Result(val source: LyricsSource, val lines: List<LyricLine>)

    /**
     * Fetch enabled sources and report provider progress. The first result is
     * emitted via [onProgress], then better results can replace it during the
     * grace period. Source priority and the syllable setting choose upgrades.
     */
    suspend fun lyrics(
        videoId: String,
        title: String,
        artist: String,
        durationMs: Long,
        album: String? = null,
        sources: Set<LyricsSource> = LyricsSource.entries.toSet(),
        order: List<LyricsSource> = LyricsSource.entries,
        prioritizeSyllableSync: Boolean = false,
        isrc: String? = null,
        /** Called from the provider job itself, including for lazily-started providers. */
        onSourceStarted: ((LyricsSource) -> Unit)? = null,
        /** Reports completed attempts; cancelled race losers are reported separately. */
        onSourceResult: ((LyricsSource, Result?) -> Unit)? = null,
        /** Lets callers turn a cancelled race loser back into "not fetched". */
        onSourceCancelled: ((LyricsSource) -> Unit)? = null,
        onProgress: ((Result) -> Unit)? = null,
        speculative: Boolean = false,
    ): Result? {
        val key = LyricsLookupKey.of(videoId, title, artist, durationMs, sources, order, prioritizeSyllableSync)
        return coordinator.get(key, speculative, { event ->
            when (event) {
                is LookupEvent.Progress -> onProgress?.invoke(event.result)
                is LookupEvent.Provider -> when (event.value.status) {
                    LookupStatus.STARTED -> onSourceStarted?.invoke(event.source)
                    LookupStatus.FOUND, LookupStatus.NOT_FOUND -> onSourceResult?.invoke(event.source, event.value.result)
                    LookupStatus.CANCELLED -> onSourceCancelled?.invoke(event.source)
                }
            }
        }) { background, emit ->
            lookup(videoId, title, artist, durationMs, album, sources, order, prioritizeSyllableSync, isrc,
                onSourceStarted = { emit(LookupEvent.Provider(it, LookupProvider(LookupStatus.STARTED))) },
                onSourceResult = { source, result -> emit(LookupEvent.Provider(source, LookupProvider(
                    if (result == null) LookupStatus.NOT_FOUND else LookupStatus.FOUND, result))) },
                onSourceCancelled = { emit(LookupEvent.Provider(it, LookupProvider(LookupStatus.CANCELLED))) },
                onProgress = { emit(LookupEvent.Progress(it)) }, speculative = background)
        }
    }

    private val coordinator = LyricsLookupCoordinator()

    private suspend fun lookup(
        videoId: String, title: String, artist: String, durationMs: Long, album: String?,
        sources: Set<LyricsSource>, order: List<LyricsSource>, prioritizeSyllableSync: Boolean, isrc: String?,
        onSourceStarted: ((LyricsSource) -> Unit)?, onSourceResult: ((LyricsSource, Result?) -> Unit)?,
        onSourceCancelled: ((LyricsSource) -> Unit)?, onProgress: ((Result) -> Unit)?, speculative: Boolean,
    ): Result? = coroutineScope {
        val sequence = (order + LyricsSource.offered)
            .distinct()
            .filter { it in sources && !it.hidden }

        // Every source but [SimpMusicLyrics] is asked for a name, and
        // YouTube's is not the name anyone catalogued. Cleaned once, here,
        // rather than by whichever source thought to do it for itself.
        val searchTitle = title.forLyricsSearch()
        val searchArtist = artist.artistForLyricsSearch()

        val known = isrc?.takeIf { it.isNotBlank() } ?: isrcs[videoId]
        // Independent sources start immediately; only recording-aware sources await this.
        val identification = async(Dispatchers.IO) {
            if (known == null) identify(videoId, searchTitle, searchArtist, durationMs, album, sequence)
            else null
        }
        val documents = SharedDocuments(this)

        val completions = Channel<Pair<LyricsSource, Result?>>(Channel.UNLIMITED)
        val racing = sequence.associateWith { source ->
            async(Dispatchers.IO, start = if (source == LyricsSource.GENIUS || (speculative && source == LyricsSource.MUSIXMATCH)) kotlinx.coroutines.CoroutineStart.LAZY else kotlinx.coroutines.CoroutineStart.DEFAULT) {
                onSourceStarted?.invoke(source)
                try {
                    val found = withTimeout(4_000L) {
                        val hit = if (source in setOf(LyricsSource.LRC_RED, LyricsSource.BINI_LYRICS, LyricsSource.LYRICS_PLUS)) identification.await() else null
                        val recording = known ?: hit?.isrc?.takeIf { it.isNotBlank() }
                        fetch(source, videoId, searchTitle, searchArtist, durationMs, album, recording, hit, documents::get, speculative)
                            ?.let { result(source, it) }
                    }
                    onSourceResult?.invoke(source, found)
                    completions.send(source to found)
                } catch (_: TimeoutCancellationException) {
                    onSourceCancelled?.invoke(source)
                    completions.send(source to null)
                } catch (cancelled: CancellationException) {
                    onSourceCancelled?.invoke(source)
                    throw cancelled
                } catch (_: Exception) {
                    onSourceResult?.invoke(source, null)
                    completions.send(source to null)
                }
            }
        }
        try {
            val pending = sequence.toMutableSet()
            var best: Result? = null
            var deadline = 0L
            while (pending.isNotEmpty()) {
                if (pending.all { it == LyricsSource.GENIUS || (speculative && it == LyricsSource.MUSIXMATCH) }) {
                    val currentBest = best
                    val candidate = sequence.firstOrNull { source ->
                        source in pending && (currentBest == null ||
                            (speculative && sequence.indexOf(source) < sequence.indexOf(currentBest.source)))
                    }
                    candidate?.let { racing[it]?.start() }
                }
                val remaining = if (best == null) Long.MAX_VALUE else deadline - System.nanoTime() / 1_000_000L
                if (remaining <= 0) break
                val completed = if (best == null) completions.receive() else withTimeoutOrNull(remaining) { completions.receive() } ?: break
                pending.remove(completed.first)
                val found = completed.second ?: continue
                val old = best
                val better = old == null ||
                    (prioritizeSyllableSync && found.lines.any { it.isWordSynced } && old.lines.none { it.isWordSynced }) ||
                    ((!prioritizeSyllableSync || found.lines.any { it.isWordSynced } == old.lines.any { it.isWordSynced }) &&
                        sequence.indexOf(found.source) < sequence.indexOf(old.source))
                if (better) {
                    best = found
                    onProgress?.invoke(found)
                    if (old == null) deadline = System.nanoTime() / 1_000_000L + 1_500L
                }
                if (best?.let { (speculative && prioritizeSyllableSync && it.lines.any { line -> line.isWordSynced }) ||
                    (it.source == sequence.firstOrNull() && (!prioritizeSyllableSync || it.lines.any { line -> line.isWordSynced })) } == true) break
            }
            best
        } finally {
            racing.values.forEach { it.cancel() }
            identification.cancel()
            documents.cancel()
            completions.close()
        }
    }

    private suspend fun fetch(
        source: LyricsSource,
        videoId: String,
        title: String,
        artist: String,
        durationMs: Long,
        album: String?,
        isrc: String?,
        /** What [identify] already found, where it ran; saves a second search. */
        hit: BiniLyrics.Hit?,
        /** Fetches a document once per lookup, however many sources want it. */
        get: suspend (String) -> String?,
        speculative: Boolean,
    ): List<LyricLine>? {
        val found = when (source) {
            LyricsSource.LRC_RED -> LrcRed.lyrics(title, artist, durationMs, isrc, get)
            LyricsSource.BETTER_LYRICS -> BetterLyrics.lyrics(title, artist, durationMs, album)
            LyricsSource.BETTER_LYRICS_PORTATO -> BetterLyrics.portato(title, artist, durationMs, album)
            LyricsSource.LYRICS_PLUS -> LyricsPlus.lyrics(title, artist, durationMs, album, isrc, speculative)
            LyricsSource.BINI_LYRICS ->
                (hit?.let { BiniLyrics.lyricsFor(it, get) }
                    ?: BiniLyrics.lyrics(title, artist, durationMs, album, isrc, get))
                    ?.also { remember(videoId, it.isrc) }
                    ?.lines
            LyricsSource.UNISON -> Unison.lyrics(title, artist, durationMs, album)
            LyricsSource.SIMP_MUSIC -> SimpMusicLyrics.lyrics(videoId, durationMs)
            LyricsSource.YOUTUBE_TRANSCRIPT -> YouTubeTranscriptLyrics.lyrics(videoId)
            LyricsSource.YOUTUBE_MUSIC -> YouTubeMusicLyrics.lyrics(videoId)
            LyricsSource.LRCLIB -> LrcLib.lyrics(title, artist, durationMs)
            LyricsSource.MUSIXMATCH -> Musixmatch.lyrics(title, artist, durationMs)
            LyricsSource.PAXSENIX -> PaxSenix.lyrics(title, artist, durationMs, album)
            LyricsSource.PAXSENIX_SPOTIFY -> PaxSenix.spotifyLyrics(title, artist, durationMs)
            LyricsSource.PAXSENIX_MUSIXMATCH -> PaxSenix.musixmatchLyrics(title, artist, durationMs)
            LyricsSource.KUGOU -> KuGou.lyrics(title, artist, durationMs, album)
            LyricsSource.MEGALOBIZ -> Megalobiz.lyrics(title, artist)
            LyricsSource.GENIUS -> Genius.lyrics(title, artist)
        }
        return found
    }

    /**
     * Whichever source won, its lines get the same last pass: the answering
     * vocal split off the lead so it can be drawn under it. Done here rather
     * than in each parser because most of them write it as a bracket and only
     * [TtmlLyrics] knows it structurally — [withBackgroundVocals] leaves that
     * one's own split alone.
     */
    private fun result(source: LyricsSource, lines: List<LyricLine>) =
        Result(source, lines.withBackgroundVocals())

    /**
     * Longest the lookup will wait to find out which recording this is.
     *
     * Short on purpose. Knowing the recording makes every match better, but not
     * knowing it only puts things back where they were a release ago, and a
     * lyrics panel sitting empty because one host is having a slow morning is a
     * worse failure than a fuzzy match.
     */
    private const val IDENTIFY_TIMEOUT_MS = 2_000L

    /**
     * Identifies the recording for providers that can use an ISRC.
     *
     * Skipped entirely when the source that answers it is switched off, and
     * given up on rather than waited out — see [IDENTIFY_TIMEOUT_MS]. What it
     * finds is remembered, so a track pays for this once rather than once per
     * lookup, and the hit is handed back so [BiniLyrics] need not search twice.
     */
    private suspend fun identify(
        videoId: String,
        title: String,
        artist: String,
        durationMs: Long,
        album: String?,
        sequence: List<LyricsSource>,
    ): BiniLyrics.Hit? {
        if (LyricsSource.BINI_LYRICS !in sequence) return null
        val hit = withTimeoutOrNull(IDENTIFY_TIMEOUT_MS) {
            runCatching { BiniLyrics.identify(title, artist, durationMs, album) }.getOrNull()
        }
        if (hit == null) return null
        remember(videoId, hit.isrc)
        return hit
    }

    /** How many recordings to keep in hand; see [isrcs]. */
    private const val REMEMBERED = 100

    /**
     * The recording behind a video id, once something has worked it out.
     *
     * Bounded, least-recently-used, and in memory only. This is a shortcut,
     * not a store: losing it costs one fuzzy match, which is what every lookup
     * did before any of this, and keeping it on disk would mean keeping a
     * wrong answer on disk too.
     */
    private val isrcs: MutableMap<String, String> = Collections.synchronizedMap(
        object : LinkedHashMap<String, String>(REMEMBERED, 0.75f, true) {
            override fun removeEldestEntry(eldest: Map.Entry<String, String>) = size > REMEMBERED
        },
    )

    private fun remember(videoId: String, isrc: String?) {
        if (isrc.isNullOrBlank() || videoId.isEmpty()) return
        isrcs.put(videoId, isrc)
    }

    /**
     * Documents fetched during one lookup, by URL, so two sources after the
     * same file make one request between them. That is the ordinary case, not
     * an edge: [BiniLyrics]' search answers with an [LrcRed] URL, and [LrcRed],
     * handed the ISRC that search found, asks for exactly that URL too.
     *
     * Each download runs in the lookup's own scope rather than the asking
     * source's, so the source that started it losing the race doesn't take
     * the other one's copy down with it; [cancel] ends whatever is left.
     */
    private class SharedDocuments(private val scope: CoroutineScope) {
        private val inFlight = ConcurrentHashMap<String, Deferred<String?>>()

        suspend fun get(url: String): String? =
            inFlight.computeIfAbsent(url) { scope.async(Dispatchers.IO) { lyricsGet(url) } }.await()

        fun cancel() = inFlight.values.forEach { it.cancel() }
    }
}
