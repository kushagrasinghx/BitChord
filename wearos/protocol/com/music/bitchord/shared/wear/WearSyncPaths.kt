package com.music.bitchord.shared.wear

object WearSyncPaths {
    const val APPLICATION_ID = "com.music.bitchord"

    const val LYRICS_PAYLOAD = "/bitchord/lyrics/payload"
    const val PLAYBACK_STATE = "/bitchord/playback/state"
    const val PLAYBACK_HEARTBEAT = "/bitchord/playback/heartbeat"

    const val PLAYBACK_COMMAND = "/bitchord/playback/command"
    const val LYRICS_REQUEST = "/bitchord/lyrics/request"

    /** Watch -> phone: its send time. Phone -> watch: that time echoed, then the phone's clock. */
    const val CLOCK_PING = "/bitchord/lyrics/clock/ping"
    const val CLOCK_PONG = "/bitchord/lyrics/clock/pong"

    const val KEY_TRACK_ID = "trackId"
    const val KEY_HASH = "hash"
    const val KEY_ASSET = "lyricsAsset"
    const val KEY_UPDATED_AT = "updatedAt"

    const val NO_LYRICS_HASH = "none"

    const val HEARTBEAT_INTERVAL_MS = 5_000L
    const val DRIFT_SNAP_THRESHOLD_MS = 500L
}
