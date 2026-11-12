package com.music.bitchord.wear

/**
 * The wire between the phone app and the watch app.
 *
 * One file, compiled into both — `:app` and `:wearos` each add this directory
 * as a source root — so a key renamed on one side cannot quietly stop matching
 * the other. Nothing here may reach for a class either app does not have.
 *
 * Which phone a watch talks to is not decided here at all. The Wearable Data
 * Layer only ever delivers between apps with the same package name *and* the
 * same signing key, so the dev watch app (`com.dev.bitchord`) can only ever see
 * the dev phone app, and prod only prod. That is why the watch app takes the
 * phone app's application ids rather than ids of its own.
 *
 * Two channels:
 *  - **State**, phone → watch, as DataItems ([PATH_NOW_PLAYING], [PATH_QUEUE]).
 *    The Data Layer keeps the last one, so a watch app that opens while the
 *    phone is busy still draws the last thing that was playing at once.
 *  - **Commands**, watch → phone, as messages under [PATH_COMMAND]. The command
 *    is the last path segment and its argument, if any, the UTF-8 payload.
 */
object WearProtocol {

    /** Advertised by the phone app (res/values/wear.xml) so the watch can find it. */
    const val CAPABILITY_PHONE = "bitchord_phone"

    /** Advertised by the watch app, so the phone only does the work with one around. */
    const val CAPABILITY_WATCH = "bitchord_watch"

    const val PATH_NOW_PLAYING = "/bitchord/now_playing"
    const val PATH_QUEUE = "/bitchord/queue"
    const val PATH_COMMAND = "/bitchord/command"
    const val PATH_OUTPUTS = "/bitchord/outputs"

    /**
     * Phone → watch messages driving the watch as the speaker, the command as
     * the last segment: [SPEAKER_LOAD], [SPEAKER_AHEAD], [SPEAKER_JUMP],
     * [SPEAKER_PLAY], [SPEAKER_PAUSE], [SPEAKER_SEEK], [SPEAKER_REPEAT],
     * [SPEAKER_VOLUME], [SPEAKER_STOP].
     */
    const val PATH_SPEAKER = "/bitchord/speaker"

    /** DataMap: [KEY_TRACKS] (the playing one first), [KEY_POSITION_MS], [KEY_PLAYING], [KEY_REPEAT]. */
    const val SPEAKER_LOAD = "load"

    /** DataMap: [KEY_TRACKS], what follows the playing track — replaces what the watch has after it. */
    const val SPEAKER_AHEAD = "ahead"

    /** UTF-8 media id of a track the watch already holds. */
    const val SPEAKER_JUMP = "jump"
    const val SPEAKER_PLAY = "play"
    const val SPEAKER_PAUSE = "pause"

    /** UTF-8 position in ms. */
    const val SPEAKER_SEEK = "seek"

    /** UTF-8 Player.REPEAT_MODE_*. */
    const val SPEAKER_REPEAT = "repeat"

    /** UTF-8 "+n"/"-n" for steps, or "=n" for an absolute level. */
    const val SPEAKER_VOLUME = "volume"
    const val SPEAKER_STOP = "stop"

    /**
     * A ChannelClient channel the watch's player reads a track through. The
     * watch writes the phone's own media URI (UTF), a byte position and a
     * length (-1 for open-ended); the phone answers with a status byte
     * ([STREAM_OK] or [STREAM_FAILED]), the length it can serve (-1 unknown),
     * and then the bytes, through the same resolver and cache its own player
     * reads from.
     */
    const val PATH_STREAM = "/bitchord/stream"
    /**
     * Phone → watch message: say what is installed. Answered with a
     * [PATH_WATCH_INFO] DataItem ([KEY_VERSION_CODE], [KEY_VERSION_NAME],
     * [KEY_APK_SHA256]) — one per watch, told apart by the item's node.
     */
    const val PATH_WATCH_INFO_REQUEST = "/bitchord/watch/info_request"
    const val PATH_WATCH_INFO = "/bitchord/watch/info"
    const val KEY_VERSION_CODE = "version_code"
    const val KEY_VERSION_NAME = "version_name"

    /** Of the installed APK, so two builds sharing a version number are still told apart. */
    const val KEY_APK_SHA256 = "apk_sha256"

    /**
     * Phone → watch channel carrying a newer watch APK, which the watch app
     * hands to its package installer. Payload: the raw APK.
     */
    const val PATH_APK = "/bitchord/watch/apk"

    const val STREAM_OK: Int = 0
    const val STREAM_FAILED: Int = 1

    // ---- Commands (last path segment under PATH_COMMAND) ----

    /** Ask for a fresh [PATH_NOW_PLAYING]. Wakes the phone's player if it has to. */
    const val CMD_SYNC = "sync"
    const val CMD_TOGGLE = "toggle"
    const val CMD_NEXT = "next"
    const val CMD_PREVIOUS = "previous"
    const val CMD_LIKE = "like"
    const val CMD_SHUFFLE = "shuffle"
    const val CMD_REPEAT = "repeat"

    /** Payload: signed number of volume steps, e.g. "2" or "-1". */
    const val CMD_VOLUME = "volume"

    /** Ask for a fresh [PATH_QUEUE]. */
    const val CMD_QUEUE = "queue"

    /** Payload: position to seek to in the current track, in ms. */
    const val CMD_SEEK = "seek"

    /** Payload: absolute queue index to jump to. */
    const val CMD_SKIP_TO = "skip_to"

    /** Ask for a fresh [PATH_OUTPUTS]. */
    const val CMD_OUTPUTS = "outputs"

    /** Payload: an [KEY_OUTPUT_ID] from [PATH_OUTPUTS]. Routes the phone's audio there. */
    const val CMD_SELECT_OUTPUT = "select_output"

    /**
     * Make the watch the speaker. Like casting: the phone keeps its queue,
     * paused, and drives the watch's player with [PATH_SPEAKER] messages. A
     * phone output picked with [CMD_SELECT_OUTPUT] ends it.
     */
    const val CMD_SPEAKER_WATCH = "speaker_watch"

    /** Watch → phone, payload a DataMap: the watch speaker's player state ([KEY_STATE] and friends). */
    const val CMD_SPEAKER_STATUS = "speaker_status"

    // ---- PATH_NOW_PLAYING keys ----

    /** False when the phone has nothing queued; nothing below is set then. */
    const val KEY_HAS_TRACK = "has_track"
    const val KEY_MEDIA_ID = "media_id"
    const val KEY_TITLE = "title"
    const val KEY_ARTIST = "artist"
    const val KEY_ALBUM = "album"

    /** playWhenReady: what the play/pause glyph shows. */
    const val KEY_PLAYING = "playing"

    /** isPlaying: whether the playhead is actually moving, i.e. whether to extrapolate it. */
    const val KEY_ADVANCING = "advancing"
    const val KEY_POSITION_MS = "position_ms"

    /** Wall-clock millis [KEY_POSITION_MS] was read at. The watch keeps the phone's time. */
    const val KEY_POSITION_AT = "position_at"
    const val KEY_DURATION_MS = "duration_ms"
    const val KEY_SPEED = "speed"
    const val KEY_HAS_PREVIOUS = "has_previous"
    const val KEY_HAS_NEXT = "has_next"
    const val KEY_LIKED = "liked"

    /** Whether the phone's player offers Like for this track at all. No heart is drawn when false. */
    const val KEY_CAN_LIKE = "can_like"
    const val KEY_SHUFFLE = "shuffle"

    /** Media3's Player.REPEAT_MODE_* (0 off, 1 one, 2 all). */
    const val KEY_REPEAT = "repeat"

    /** The phone player's own mesh colours, ARGB, so the watch never runs Palette. */
    const val KEY_COLORS = "colors"

    /** A small JPEG of the cover, as an Asset. */
    const val KEY_ART = "art"

    /** Identifies [KEY_ART], so the watch only decodes a cover it has not got. */
    const val KEY_ART_ID = "art_id"
    const val KEY_VOLUME = "volume"
    const val KEY_VOLUME_MAX = "volume_max"

    // ---- PATH_OUTPUTS keys ----

    /** DataMap list: [KEY_OUTPUT_ID], [KEY_OUTPUT_NAME], [KEY_OUTPUT_KIND], [KEY_OUTPUT_ACTIVE]. */
    const val KEY_OUTPUTS = "outputs"
    const val KEY_OUTPUT_ID = "output_id"

    /** Blank for the phone's built-in outputs, which the watch names by [KEY_OUTPUT_KIND]. */
    const val KEY_OUTPUT_NAME = "output_name"

    /** The phone's AudioRouting.Kind name: PHONE, WIRED, USB, BLUETOOTH, HDMI, OTHER. */
    const val KEY_OUTPUT_KIND = "output_kind"
    const val KEY_OUTPUT_ACTIVE = "output_active"

    // ---- Watch speaker ----

    /** On [PATH_NOW_PLAYING] and [PATH_OUTPUTS]: the watch is the speaker. */
    const val KEY_WATCH_SPEAKER = "watch_speaker"

    /** DataMap list: [KEY_MEDIA_ID], [KEY_TITLE], [KEY_ARTIST], [KEY_URI]. */
    const val KEY_TRACKS = "tracks"

    /** The phone's own media URI for a track, which the watch streams it by. */
    const val KEY_URI = "uri"

    /** Status: Player.STATE_*. With [KEY_MEDIA_ID], [KEY_PLAYING], [KEY_ADVANCING], [KEY_POSITION_MS], [KEY_DURATION_MS], [KEY_VOLUME], [KEY_VOLUME_MAX]. */
    const val KEY_STATE = "state"

    // ---- PATH_QUEUE keys ----

    /** DataMap list, one per row: [KEY_TITLE], [KEY_ARTIST], [KEY_INDEX], [KEY_ART]. */
    const val KEY_ITEMS = "items"
    const val KEY_INDEX = "index"

    /** Absolute index of the playing item. */
    const val KEY_CURRENT_INDEX = "current_index"

    /** The playing item's id when the queue was read, to tell a stale list from a live one. */
    const val KEY_QUEUE_MEDIA_ID = "queue_media_id"

    /** Side of the cover JPEG on [PATH_NOW_PLAYING]. */
    const val ART_PX = 240

    /** Side of each row's thumbnail on [PATH_QUEUE]. */
    const val ROW_ART_PX = 96
}
