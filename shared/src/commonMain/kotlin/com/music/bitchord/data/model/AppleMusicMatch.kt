package com.music.bitchord.data.model

const val APPLE_PENDING_PREFIX = "am:"
const val APPLE_MISSING_PREFIX = "am-miss:"

/** Library rows waiting for their YouTube counterpart, not yet offered for playback. */
val Song.isAppleMusicMatchPending: Boolean get() = videoId.startsWith(APPLE_PENDING_PREFIX)

/** No YouTube counterpart; the suffix is an Apple catalog id, not an unavailable recording. */
val Song.isAppleMusicMatchMissing: Boolean get() = videoId.startsWith(APPLE_MISSING_PREFIX)

/** Apple-only recordings remain resolvable by MusicKit; unlike Spotify misses they are playable. */
val Song.isUnresolvedExternal: Boolean get() = isUnresolvedSpotify || isAppleMusicMatchPending

val Song.appleMusicCatalogId: String?
    get() = videoId.takeIf { isAppleMusicMatchMissing }
        ?.removePrefix(APPLE_MISSING_PREFIX)
        ?.takeIf { id -> id.isNotEmpty() && id.length <= 32 && id.first() in '1'..'9' && id.all { it in '0'..'9' } }
