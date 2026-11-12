package com.music.bitchord.playback

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The playhead, deliberately kept out of [PlayerState].
 *
 * It moves twice a second; everything else on [PlayerState] moves on a track
 * change. Carried in the same object, the two are one snapshot read — and
 * [rememberPlayerState] returns a value, which makes it non-restartable, which
 * pushes that read up into its *caller's* scope. In this app the caller is the
 * root of the whole UI, so a ticking playhead invalidated the entire tree twice
 * a second: every tab, both floating bars, and the three real-time blurs
 * underneath them, whether or not anything on screen showed a position.
 *
 * Split out and held behind a stable object, the tick is a read of this alone.
 * Whoever draws a scrubber reads it and recomposes; nobody else hears about it.
 * Take care to keep it that way — reading [positionMs] high in the tree and
 * passing the `Long` down puts the invalidation straight back where it was.
 */
@Stable
class PlaybackPosition {
    /** The latest reading of the playhead. Written through [report], with the time it was taken. */
    var positionMs by mutableLongStateOf(0L)
        private set

    /**
     * When [positionMs] was read off the player, on the [System.nanoTime] clock.
     *
     * The lyrics run a clock of their own between readings, and the only thing
     * that lets them use a reading is knowing *when* it was true. The moment the
     * UI first sees one is not that: it waits on the main thread, and a busy
     * frame there — a heavy recomposition, a collection, the blur stack — holds
     * it back by as long as the frame takes. Dated by when it was seen, a reading
     * from before a half-second stall looked half a second old, and the lyrics
     * either stopped to wait for it or rolled back to meet it.
     */
    var sampledAtNanos by mutableLongStateOf(0L)
        private set

    /**
     * Bumped by the owner every time the playhead jumps on purpose — a seek, a
     * skip, a repeat starting over — so the lyrics can tell a real jump from a
     * reading that is merely late or early. Nothing else is allowed to move them
     * backwards.
     */
    var seeks by mutableIntStateOf(0)

    /**
     * False while playback is meant to be running but the playhead is not moving yet: a seek still
     * waiting on its audio. The lyrics carry the position forward on the frame clock between
     * reports, and through that wait they ran ahead of the song and then sat frozen until it
     * caught up. The phone never sets it — Media3 already reports a buffering player as not
     * playing — the desktop engine does, because its "playing" also drives Listen Together.
     */
    var advancing by mutableStateOf(true)

    /**
     * Records a reading of the playhead and when it was taken. Pass
     * [sampledAtNanos] when the reading was made somewhere else and handed over
     * — the desktop's audio thread — so it carries the moment it was true and
     * not the moment it arrived.
     */
    fun report(positionMs: Long, sampledAtNanos: Long = System.nanoTime()) {
        this.positionMs = positionMs
        this.sampledAtNanos = sampledAtNanos
    }
}
