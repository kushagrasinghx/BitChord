package com.music.bitchord.data.model

/**
 * A Spotify playlist page lists Spotify's tracks first and finds each one's
 * YouTube Music counterpart afterwards. Until then a row carries this prefix
 * on its video id; one with no counterpart carries [SPOTIFY_MISSING_PREFIX].
 * Neither can be played, and the rows say so rather than failing on tap.
 */
const val SPOTIFY_PENDING_PREFIX = "sp:"
const val SPOTIFY_MISSING_PREFIX = "sp-miss:"

/** Still looking for the YouTube Music version of this Spotify track. */
val Song.isMatchPending: Boolean get() = videoId.startsWith(SPOTIFY_PENDING_PREFIX)

/** Looked, and there is no YouTube Music version to play. */
val Song.isMatchMissing: Boolean get() = videoId.startsWith(SPOTIFY_MISSING_PREFIX)

val Song.isUnresolvedSpotify: Boolean get() = isMatchPending || isMatchMissing
