package com.music.bitchord.data.sources

import java.util.Base64
import com.music.bitchord.data.TrackLog
import com.music.bitchord.data.debrid.DebridProviders
import com.music.bitchord.data.debrid.CacheNames
import com.music.bitchord.data.debrid.CatalogMetadata
import com.music.bitchord.data.debrid.DebridCache
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings

private const val TAG = "BitChord"

/**
 * Bit-exact audio from the listener's own debrid cache.
 *
 * ## How to use (listeners)
 *
 * 1. Get an API key from your debrid provider's settings page.
 * 2. Open Settings → Sources → the Debrid row, paste the key, hit Test
 *    (it validates the key live), then Save — saving also switches the
 *    source on.
 * 3. Play anything. The source is asked in rank order alongside the rest;
 *    on the Lossless rung it answers first with bit-exact FLAC when the
 *    track is cached, and playback silently falls through to the next
 *    source when it isn't. Nothing is needed per track.
 *
 * ## How streaming works (pipeline, per track)
 *
 * Catalogue metadata names the recording and its stable id, deterministic
 * file names are built per quality tier and hashed, and the debrid cache
 * is asked about all of them in one batched call. A hit whose held name
 * equals the asked name is the track; the download URL itself is resolved
 * at play time, since those links expire and a URL minted during search is
 * a 403 waiting to happen.
 *
 * The track id carries everything [stream] needs (catalogue id, title,
 * artist), so a queue restored after a restart resolves without anything
 * held in memory and without another metadata lookup on the play path.
 */
class DebridSource(
    override val config: SourceConfig,
) : MusicSource, SourceRegistry.ConfigBacked {

    override val configId: String get() = config.id
    override val kind: SourceKind get() = SourceKind.DEBRID
    override val displayName: String
        get() = config.label.ifBlank {
            val provider = DebridProviders.labelFor(providerId)
            if (providerId.isBlank() || provider == "Debrid") SourceKind.DEBRID.label
            else "${SourceKind.DEBRID.label} · $provider"
        }

    private val providerId: String
        get() = config.provider.ifBlank { DebridProviders.DEFAULT }

    private fun apiKey(): String = AppSettings.debridApiKey.value.trim()

    override suspend fun health(): SourceHealth {
        if (!DebridProviders.isKnown(providerId)) {
            return SourceHealth.Rejected("Unknown debrid provider ($providerId)")
        }
        if (apiKey().isEmpty()) {
            return SourceHealth.Rejected("API key required")
        }
        return runCatching { DebridCache.validateKey(apiKey()) }
            .fold(
                onSuccess = { valid ->
                    if (valid) SourceHealth.Ok(DebridProviders.labelFor(providerId))
                    else SourceHealth.Rejected("The API key was rejected")
                },
                onFailure = { SourceHealth.Unreachable(it.message ?: "Unreachable") },
            )
    }

    override suspend fun search(
        query: String,
        limit: Int,
        waitForAll: Boolean,
        request: StreamRequest?,
    ): List<Song> {
        val key = apiKey()
        if (key.isEmpty() || !DebridProviders.isKnown(providerId)) return emptyList()
        TrackLog.d(TAG, "▶ Debrid search \"$query\" limit=$limit")
        val tracks = CatalogMetadata.searchTracks(query, limit = MAX_CATALOG_CANDIDATES)
        if (tracks.isEmpty()) return emptyList()
        // Relevance-ordered; past the first rows that share no words with the
        // question, the catalogue is naming other songs with the same chorus.
        val shortlisted = tracks
            .filter { CacheNames.queryOverlap(query, it.title, it.artists.firstOrNull().orEmpty()) >= MIN_QUERY_OVERLAP }
            .take(MAX_TRACKS_PER_SEARCH)
            .ifEmpty { tracks.take(1) }
        // One batched cache check across every tier of every shortlisted
        // track; each track is then matched against only its own file names.
        val wantedByTrack = shortlisted.associate { track ->
            val artist = track.artists.firstOrNull().orEmpty()
            track.id to CacheNames.candidates(track.title, artist, track.id).associateBy { it.md5 }
        }
        val hits = DebridCache.checkCached(key, wantedByTrack.values.flatMap { it.keys })
            .mapValues { it.value.name }
        val songs = shortlisted.mapNotNull { track ->
            val tiers = CacheNames.matchedTiers(wantedByTrack.getValue(track.id), hits)
                .filter { tierFor(request).contains(it) }
            if (tiers.isEmpty()) return@mapNotNull null
            songOf(track)
        }
        TrackLog.d(TAG, "  ✓ Debrid cached ${songs.size}/${shortlisted.size} track(s) for \"$query\"")
        return songs.take(limit)
    }

    override suspend fun stream(trackId: String, request: StreamRequest): SourceStream? {
        val key = apiKey()
        if (key.isEmpty() || !DebridProviders.isKnown(providerId)) return null
        val (catalogId, title, artist) = parseTrackId(trackId) ?: return null
        TrackLog.d(TAG, "▶ Debrid stream catalogId=$catalogId request=$request")
        val wanted = CacheNames.candidates(title, artist, catalogId).associateBy { it.md5 }
        val refs = DebridCache.checkCached(key, wanted.keys.toList())
        val tier = CacheNames.matchedTiers(wanted, refs.mapValues { it.value.name })
            .firstOrNull { it in tierFor(request) }
            ?: run {
                TrackLog.d(TAG, "  ✗ Debrid: no cached tier for catalogId=$catalogId at $request")
                return null
            }
        val torrentId = refs.entries
            .firstOrNull { (md5, ref) -> wanted[md5]?.tier == tier && ref.name == wanted[md5]?.name }
            ?.value?.torrentId ?: return null
        val url = DebridCache.downloadUrl(key, torrentId) ?: return null
        TrackLog.d(TAG, "  ✓ Debrid ${tier.label} torrent=$torrentId")
        return SourceStream(url = url, format = CacheNames.formatOf(tier))
    }

    /**
     * Which tiers answer [request], best first. Lossless asks for bit-exact
     * only; a capped connection excludes the unbounded FLAC rungs; anything
     * else takes the best the cache holds.
     */
    internal fun tierFor(request: StreamRequest?): List<CacheNames.Tier> = when (request) {
        is StreamRequest.Lossless -> CacheNames.TIERS.filter { it.kbps == null }
        is StreamRequest.Capped -> CacheNames.TIERS.filter { it.kbps != null && it.kbps <= request.maxKbps }
        else -> CacheNames.TIERS
    }

    private fun songOf(track: CatalogMetadata.CatalogTrack): Song {
        val artist = track.artists.firstOrNull().orEmpty()
        return Song(
            videoId = SourceRegistry.trackKey(config.id, encodeTrackId(track.id, track.title, artist)),
            title = track.title,
            artist = artist.ifBlank { "Unknown Artist" },
            albumName = track.album,
            thumbnailUrl = track.coverId?.let(CatalogMetadata::coverUrl),
            durationText = track.durationSec?.let { seconds ->
                String.format("%d:%02d", seconds / 60, seconds % 60)
            },
            sourceQuality = "HIGH",
            isExplicit = track.explicit,
        )
    }

    internal companion object {
        /** Catalogue rows read per query; only the first couple ever survive matching. */
        const val MAX_CATALOG_CANDIDATES = 5

        /** Tracks taken to the cache check per query — one batched call. */
        const val MAX_TRACKS_PER_SEARCH = 2

        /** Minimum query-word overlap before a catalogue row is worth hashing. */
        const val MIN_QUERY_OVERLAP = 0.5

        /**
         * Everything [stream] needs, in the id itself: catalogue id, title
         * and artist, newline-separated and base64url-encoded (no `:` so it
         * can never collide with [SourceRegistry]'s `::` track-key separator).
         */
        internal fun encodeTrackId(catalogId: Long, title: String, artist: String): String {
            val payload = "$catalogId\n$title\n$artist".toByteArray(Charsets.UTF_8)
            return "tb" + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload)
        }

        internal fun parseTrackId(rawId: String): Triple<Long, String, String>? {
            if (!rawId.startsWith("tb")) return null
            val parts = runCatching {
                String(Base64.getUrlDecoder().decode(rawId.removePrefix("tb")), Charsets.UTF_8)
            }.getOrNull()?.split("\n") ?: return null
            if (parts.size != 3) return null
            val id = parts[0].toLongOrNull() ?: return null
            return Triple(id, parts[1], parts[2])
        }
    }
}
