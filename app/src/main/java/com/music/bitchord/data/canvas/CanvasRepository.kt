package com.music.bitchord.data.canvas

import com.music.bitchord.data.DebugLog as Log
import com.music.bitchord.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Finds the looping video that belongs behind a track's or a release's cover
 * art — Spotify's Canvas, Apple's motion artwork.
 *
 * Three sources, asked in turn until one answers. They cover genuinely
 * different ground rather than being three routes to the same catalogue:
 * Apple has the most, Tidal has square covers on a lot of what Apple misses,
 * and the community index is the only one that reaches back catalogue. Order
 * is by hit rate, so the later two are rarely reached.
 *
 * Every one of them is a public endpoint belonging to someone else, reached
 * without an account, and all of them will confidently answer a search with
 * the wrong record. So the shape of this is: ask, then re-check the answer
 * against what was asked for ([CanvasArtwork.matches]), and treat any failure
 * — network, parse, mismatch — as simply no canvas. The still art is always
 * underneath, so nothing here can break the player or the album page.
 *
 * Results are cached, misses included: nothing having a canvas is the common
 * case, and without negative caching every revisit would pay for three
 * lookups again to learn the same thing.
 */
object CanvasRepository {

    private const val TAG = "CanvasRepository"
    private const val CACHE_SIZE = 64

    /**
     * A settled answer for one track or release.
     *
     * [withAlbum] records whether the album name was known when this was
     * worked out. It is the one thing that can turn a miss into a hit later:
     * the album is what makes the catalogue searches land, and on the player
     * it resolves a beat after the track starts. A miss reached without it is
     * therefore provisional; everything else is final.
     */
    private class Entry(val artwork: CanvasArtwork?, val withAlbum: Boolean)

    private val cache = object : LinkedHashMap<String, Entry>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>) =
            size > CACHE_SIZE
    }

    // Skipping through a queue fires a lookup per track. Serialising them
    // keeps three providers' worth of requests off the wire at once, and means
    // a track that was already resolved by the time its turn comes up is
    // answered from the cache instead of fetched again.
    private val lock = Mutex()

    /**
     * The canvas for [song], or null when there isn't one. Never throws.
     *
     * A local file has no catalogue identity worth searching on and no network
     * expectation attached to playing it, so it is answered as a miss without
     * a request.
     */
    suspend fun canvasFor(song: Song): CanvasArtwork? {
        if (song.localUri != null || song.localPath != null) return null

        val title = song.title.cleaned()
        val artist = song.artist.cleaned()
        if (title.isBlank() || artist.isBlank()) return null

        // Keyed on the track alone. The album is deliberately not part of
        // this: it arrives after the player opens, and keying on it made the
        // late arrival look like a different question and run the whole chain
        // a second time. [reusable] decides when the earlier answer still
        // stands instead.
        val album = song.albumName
        val key = "song|${song.videoId}"

        return resolve(key, album != null) {
            firstHit(
                { AppleMusicCanvas.search(title, artist, album) },
                { TidalCanvas.search(title, artist, album) },
                { CommunityCanvas.search(title, artist, album) },
            ) { it.matches(title, artist, album) }
        }
    }

    /**
     * A canvas already worked out for [song], without going near the network.
     *
     * Lets a caller paint what it knows before it starts waiting on anything —
     * reopening the player on a track resolved a minute ago should not go
     * through the settling delay again to arrive back at the same clip.
     */
    fun cached(song: Song): CanvasArtwork? =
        synchronized(cache) { cache["song|${song.videoId}"]?.artwork }

    /**
     * The canvas for a release, for the album page's header artwork.
     *
     * A separate lookup rather than the first track's: the services hang
     * motion artwork off the album, so asking for it directly is both fewer
     * requests and a better match than picking a song and hoping it sits on
     * the right edition.
     */
    suspend fun canvasForAlbum(album: String, artist: String): CanvasArtwork? {
        val name = album.cleaned()
        val credit = artist.cleaned()
        if (name.isBlank() || credit.isBlank()) return null

        return resolve("album|$name|$credit", withAlbum = true) {
            firstHit(
                { AppleMusicCanvas.searchAlbum(name, credit) },
                { TidalCanvas.searchAlbum(name, credit) },
                { CommunityCanvas.searchAlbum(name, credit) },
                // Album artwork names itself in both fields, so this is the
                // same check the track path makes.
            ) { it.matches(name, credit, name) }
        }
    }

    private suspend fun resolve(
        key: String,
        withAlbum: Boolean,
        lookUp: () -> CanvasArtwork?,
    ): CanvasArtwork? = lock.withLock {
        synchronized(cache) {
            cache[key]?.let { if (it.reusable(withAlbum)) return@withLock it.artwork }
        }
        val found = withContext(Dispatchers.IO) { lookUp() }
        synchronized(cache) { cache[key] = Entry(found, withAlbum) }
        found
    }

    /**
     * Whether this answer can stand in for a lookup that now knows [withAlbum].
     *
     * A hit is a hit — the album could only have confirmed it. A miss stands
     * too, unless it was reached blind and there is now an album name to try,
     * which is the one case worth spending a second round of requests on.
     */
    private fun Entry.reusable(withAlbum: Boolean): Boolean =
        artwork != null || this.withAlbum || !withAlbum

    /**
     * The first source that answers with something that survives [accept].
     *
     * Sources are passed unevaluated so each is only reached — and only paid
     * for — if the ones before it came up empty. One that throws is treated as
     * one that found nothing: none of these hosts are ours, and a missing
     * canvas is not worth surfacing as an error.
     */
    private fun firstHit(
        vararg sources: () -> CanvasArtwork?,
        accept: (CanvasArtwork) -> Boolean,
    ): CanvasArtwork? {
        for (source in sources) {
            val found = runCatching { source() }
                .onFailure { Log.d(TAG, "source failed: ${it.message}") }
                .getOrNull()
                ?: continue
            if (!accept(found)) {
                Log.d(TAG, "rejected '${found.title}' by '${found.artist}'")
                continue
            }
            return found
        }
        return null
    }

    /**
     * YouTube Music titles carry packaging the catalogue services never see —
     * "| Official Video", bracketed tags, "(Lyrical)". Searching with it finds
     * nothing and matching against it rejects everything, so it comes off
     * before either. Same treatment as the lyrics lookup gives it.
     */
    private fun String.cleaned(): String = replace(NOISE, " ")
        .substringBefore(" | ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .ifBlank { this }

    private val NOISE = Regex(
        """\((?:from|official|lyrical|video|audio)[^)]*\)|\[[^]]*]|""" +
            """\b(?:official (?:video|audio|music video)|lyrical|full song|4k video)\b""",
        RegexOption.IGNORE_CASE,
    )
}
