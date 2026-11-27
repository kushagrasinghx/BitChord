package com.music.bitchord.wear.lyrics

/**
 * Data Layer contract for the synced-lyrics screen of the watch app (phone <-> watch).
 *
 * What is playing, and where, already reaches the watch through [WearProtocol] (NowPlaying); this
 * only adds the lyrics themselves and what the watch asks of the phone about them.
 */
object WearSyncPaths {

    // --- phone -> watch, DataClient ------------------------------------------------------
    /** The lyrics of the current track: a gzip JSON asset plus [KEY_TRACK_ID] and [KEY_HASH]. */
    const val LYRICS_PAYLOAD = "/bitchord/lyrics/payload"
    const val KEY_TRACK_ID = "trackId"
    const val KEY_HASH = "hash"
    const val KEY_ASSET = "lyricsAsset"
    const val KEY_UPDATED_AT = "updatedAt"

    /** Sent as the hash when the phone looked and found no synced lyrics. */
    const val NO_LYRICS_HASH = "none"

    /** Per-line translated or Latin-script text, aligned to the payload's lines. */
    const val LYRICS_EXTRA = "/bitchord/lyrics/extra"
    const val KEY_KIND = "kind"
    const val KEY_STATUS = "status"
    const val KEY_LANG = "lang"
    const val KEY_TEXTS = "texts"
    const val KIND_TRANSLATION = "translation"
    const val KIND_ROMANIZATION = "romanization"
    const val KIND_ORIGINAL = "original"
    const val STATUS_OK = "ok"
    const val STATUS_SAME = "same"
    const val STATUS_UNAVAILABLE = "unavailable"

    /** The player's lyrics offset in ms; positive = lyrics later. */
    const val LYRICS_OFFSET = "/bitchord/lyrics/offset"
    const val KEY_OFFSET_MS = "offsetMs"

    /** The sources that have synced lyrics for the track, as the phone finds them. */
    const val LYRICS_SOURCES = "/bitchord/lyrics/sources"
    const val KEY_IDS = "ids"
    const val KEY_LABELS = "labels"
    const val KEY_KINDS = "kinds"
    const val KEY_CURRENT = "current"
    const val KEY_DONE = "done"

    // --- watch -> phone, MessageClient ---------------------------------------------------
    /** Body: trackId. Sends the lyrics of that track again. */
    const val LYRICS_REQUEST = "/bitchord/lyrics/request"

    /** Body: "trackId\nmode\nlanguage", mode = translation | romanization | original. */
    const val LYRICS_TRANSFORM = "/bitchord/lyrics/transform"

    /** Body: trackId. Asks which sources have synced lyrics for it; answered through [LYRICS_SOURCES]. */
    const val LYRICS_SOURCES_REQUEST = "/bitchord/lyrics/sources/request"

    /** Body: "trackId\nsourceId". Plays the lyrics of that source on the watch. */
    const val LYRICS_SELECT = "/bitchord/lyrics/select"

    /** Body: the watch's send time. The answer echoes it and adds the phone's clock and playhead. */
    const val CLOCK_PING = "/bitchord/lyrics/clock/ping"
    const val CLOCK_PONG = "/bitchord/lyrics/clock/pong"

    /** A lyrics line off by more than this snaps; less is blended away. */
    const val DRIFT_SNAP_THRESHOLD_MS = 500L
}
