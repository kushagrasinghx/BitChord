package com.music.bitchord.data.applemusic

import com.music.bitchord.data.model.APPLE_MISSING_PREFIX
import com.music.bitchord.data.model.APPLE_PENDING_PREFIX
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.appleMusicCatalogId
import com.music.bitchord.data.model.isAppleMusicMatchMissing
import com.music.bitchord.data.model.isUnresolvedExternal
import com.music.bitchord.data.spotify.SpotifyImporter
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AppleMusicTrackOrigin { LIBRARY, CATALOG }
enum class AppleMusicTrackMatchStatus { PENDING, YOUTUBE, APPLE_ONLY, UNAVAILABLE }

/** Apply the same guard to Songs restored from LocalPlaylistStore, without a mapping wrapper. */
fun Song.canPlayAppleMusicRow(appleMusicConnected: Boolean, appleMusicSelected: Boolean): Boolean =
    !isUnresolvedExternal && (!isAppleMusicMatchMissing ||
        (appleMusicConnected && appleMusicSelected && appleMusicCatalogId != null))

data class AppleMusicTrackMapping(
    val track: AppleMusicTrack,
    val origin: AppleMusicTrackOrigin,
    val song: Song,
    val status: AppleMusicTrackMatchStatus,
    /** A failed lookup is not evidence of absence; retrying the page tries matching again. */
    val lookupFailed: Boolean = false,
) {
    val catalogId: String? get() = track.catalogId?.takeIf(AppleMusicMatcher::isCatalogId)

    /** Catalog resources can also appear in library playlist relationships. */
    val libraryId: String? get() = track.id.takeIf {
        origin == AppleMusicTrackOrigin.LIBRARY && it != catalogId
    }

    fun isPlayable(appleMusicConnected: Boolean, appleMusicSelected: Boolean): Boolean = when (status) {
        AppleMusicTrackMatchStatus.YOUTUBE -> true
        AppleMusicTrackMatchStatus.APPLE_ONLY -> appleMusicConnected && appleMusicSelected && catalogId != null
        AppleMusicTrackMatchStatus.PENDING, AppleMusicTrackMatchStatus.UNAVAILABLE -> false
    }
}

/**
 * Pass the SAME AppleMusicMatcher instance used by playback, constructed off main when
 * persistent. Library-to-YouTube matching reuses SpotifyImporter without changing its
 * algorithm or desktop behavior. Cache seeding and all matching run on [dispatcher].
 */
class AppleMusicTrackMapper(
    private val appleMusicMatcher: AppleMusicMatcher,
    private val youtubeMatcher: YouTubeMatcher = SharedYouTubeMatcher,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val parallelism: Int = 6,
) {
    fun interface YouTubeMatcher {
        suspend fun match(track: AppleMusicTrack): Song?
    }

    init {
        require(parallelism in 1..6)
    }

    fun pending(track: AppleMusicTrack, origin: AppleMusicTrackOrigin): AppleMusicTrackMapping =
        AppleMusicTrackMapping(track, origin, track.asSong(APPLE_PENDING_PREFIX + track.id), AppleMusicTrackMatchStatus.PENDING)

    suspend fun match(
        track: AppleMusicTrack,
        origin: AppleMusicTrackOrigin,
        storefront: String?,
        checkCurrent: () -> Unit = {},
    ): AppleMusicTrackMapping = withContext(dispatcher) {
        currentCoroutineContext().ensureActive()
        checkCurrent()
        var failed = false
        val match = if (!track.isVideo && track.title.isNotBlank() && track.artist.isNotBlank()) {
            try {
                youtubeMatcher.match(track)?.takeIf { it.isYouTubeSong() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                failed = true
                null
            }
        } else null
        currentCoroutineContext().ensureActive()
        checkCurrent()
        if (match != null) {
            if (storefront != null && !track.isVideo) {
                appleMusicMatcher.seed(storefront, match.videoId, track)
            }
            AppleMusicTrackMapping(
                track, origin,
                match.copy(thumbnailUrl = match.thumbnailUrl ?: track.imageUrl),
                AppleMusicTrackMatchStatus.YOUTUBE,
            )
        } else {
            val catalogId = track.catalogId?.takeIf(AppleMusicMatcher::isCatalogId)?.takeUnless { track.isVideo }
            AppleMusicTrackMapping(
                track, origin,
                track.asSong(catalogId?.let { APPLE_MISSING_PREFIX + it } ?: (APPLE_PENDING_PREFIX + "unavailable:" + track.id)),
                if (catalogId != null) AppleMusicTrackMatchStatus.APPLE_ONLY else AppleMusicTrackMatchStatus.UNAVAILABLE,
                lookupFailed = failed,
            )
        }
    }

    /** A bounded worker pool preserves order and duplicate playlist entries without launching a job per row. */
    suspend fun matchAll(
        tracks: List<AppleMusicTrack>,
        origin: AppleMusicTrackOrigin,
        storefront: String?,
        checkCurrent: () -> Unit = {},
        onResolved: (Int, AppleMusicTrackMapping) -> Unit = { _, _ -> },
    ): List<AppleMusicTrackMapping> = withContext(dispatcher) {
        val next = AtomicInteger(0)
        val rows = arrayOfNulls<AppleMusicTrackMapping>(tracks.size)
        coroutineScope {
            repeat(minOf(parallelism, tracks.size)) {
                launch {
                    while (true) {
                        val index = next.getAndIncrement()
                        if (index >= tracks.size) break
                        val row = match(tracks[index], origin, storefront, checkCurrent)
                        currentCoroutineContext().ensureActive()
                        checkCurrent()
                        rows[index] = row
                        onResolved(index, row)
                    }
                }
            }
        }
        rows.map { checkNotNull(it) }
    }

    private fun Song.isYouTubeSong(): Boolean = videoId.isNotBlank() &&
        ':' !in videoId && localUri == null && !isVideo

    private fun AppleMusicTrack.asSong(videoId: String) = Song(
        videoId = videoId,
        title = title,
        artist = artist,
        thumbnailUrl = imageUrl,
        durationText = durationMs.takeIf { it > 0 }?.let {
            val seconds = it / 1_000
            "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
        },
        albumName = album,
        isExplicit = explicit,
        isVideo = isVideo,
    )

    private object SharedYouTubeMatcher : YouTubeMatcher {
        override suspend fun match(track: AppleMusicTrack): Song? = SpotifyImporter.matchToYouTube(
            title = track.title, artist = track.artist, album = track.album,
            durationMs = track.durationMs, explicit = track.explicit,
        )
    }
}
