package com.music.bitchord.playback

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
    /** Written only by whoever owns playback — the phone's controller, the desktop's engine. */
    var positionMs by mutableLongStateOf(0L)
}
