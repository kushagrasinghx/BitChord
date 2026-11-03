package com.music.bitchord.data.settings

/** Selects audio only; recommendations and radio remain backed by YouTube Music. */
enum class PlaybackProvider { YOUTUBE_MUSIC, APPLE_MUSIC }

internal fun availablePlaybackProvider(
    persisted: String?,
    musicKitAvailable: Boolean,
    connected: Boolean,
): PlaybackProvider {
    val requested = PlaybackProvider.entries.firstOrNull { it.name == persisted }
        ?: PlaybackProvider.YOUTUBE_MUSIC
    return requested.takeUnless {
        it == PlaybackProvider.APPLE_MUSIC && (!musicKitAvailable || !connected)
    } ?: PlaybackProvider.YOUTUBE_MUSIC
}
