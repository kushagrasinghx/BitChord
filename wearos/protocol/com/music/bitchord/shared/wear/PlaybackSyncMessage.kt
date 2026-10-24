package com.music.bitchord.shared.wear

import org.json.JSONObject

enum class SyncEvent { TRACK, PLAY, PAUSE, SEEK, HEARTBEAT }

/**
 * Sent phone -> watch over MessageClient.
 *
 * [positionMs] is the phone player position at [sentAtWallMs] (phone wall clock). The watch adds
 * the (clamped) transit latency while playing so both clocks agree on "now".
 *
 * [lyricsHash]: null = lyrics still loading on the phone, [WearSyncPaths.NO_LYRICS_HASH] = none exist,
 * anything else = [SyncedLyricsPayload.contentHash] (lets the watch hit its offline cache).
 */
data class PlaybackSyncMessage(
    val event: SyncEvent,
    val trackId: String?,
    val title: String,
    val artist: String,
    val isPlaying: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val speed: Float,
    val sentAtWallMs: Long,
    val lyricsHash: String?,
) {
    fun toBytes(): ByteArray = JSONObject()
        .put("ev", event.name)
        .put("id", trackId)
        .put("ti", title)
        .put("ar", artist)
        .put("pl", isPlaying)
        .put("p", positionMs)
        .put("d", durationMs)
        .put("sp", speed.toDouble())
        .put("at", sentAtWallMs)
        .put("h", lyricsHash)
        .toString()
        .toByteArray(Charsets.UTF_8)

    companion object {
        fun fromBytes(bytes: ByteArray): PlaybackSyncMessage? = try {
            val o = JSONObject(String(bytes, Charsets.UTF_8))
            PlaybackSyncMessage(
                event = SyncEvent.valueOf(o.getString("ev")),
                trackId = o.optString("id", "").ifEmpty { null },
                title = o.optString("ti", ""),
                artist = o.optString("ar", ""),
                isPlaying = o.optBoolean("pl", false),
                positionMs = o.optLong("p", 0L),
                durationMs = o.optLong("d", 0L),
                speed = o.optDouble("sp", 1.0).toFloat(),
                sentAtWallMs = o.optLong("at", 0L),
                lyricsHash = o.optString("h", "").ifEmpty { null },
            )
        } catch (e: Exception) {
            null
        }
    }
}

/** Watch -> phone transport commands (body of [WearSyncPaths.PLAYBACK_COMMAND]). */
enum class PlaybackCommand {
    PLAY, PAUSE, NEXT, PREVIOUS;

    fun toBytes(): ByteArray = name.toByteArray(Charsets.UTF_8)

    companion object {
        fun fromBytes(bytes: ByteArray): PlaybackCommand? {
            val s = String(bytes, Charsets.UTF_8)
            return values().firstOrNull { it.name == s }
        }
    }
}