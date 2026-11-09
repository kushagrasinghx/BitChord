package com.music.bitchord.playback.cast

import androidx.media3.common.C

/**
 * Whether the receiver's PLAYING status is news about the track the phone is on.
 *
 * Told to stop — because nothing it could play was left, or because a load
 * failed — a receiver keeps reporting PLAYING for the track it was already on,
 * and taking that as a resume put the bar back on a track the phone had left
 * behind. The exception is the track the phone is actually on: starting that one
 * again is a resume, not a ghost.
 */
internal fun adoptsReceiverPlaying(finished: Boolean, phoneId: String?, receiverId: String?): Boolean =
    !finished || (phoneId != null && receiverId == phoneId)

/**
 * Everything [castPositionMs] has to go on: what the receiver last said, what
 * the phone asked for, and the clocks both are measured against — one value a
 * caller builds once and a test can build without a Cast client.
 *
 * [durationMs] is [C.TIME_UNSET] when the receiver has not said how long the
 * track is, in which case there is nothing to clamp to.
 */
internal data class CastPositionInputs(
    val liveMs: Long?,
    val askedMs: Long,
    val requestedAtMs: Long,
    val nowMs: Long,
    val handoverOutstanding: Boolean,
    val switchTimeoutMs: Long,
    val seekGraceMs: Long,
    val playing: Boolean,
    val anchorPositionMs: Long,
    val anchorAtMs: Long,
    val durationMs: Long,
)

/**
 * The position to report for a receiver, given what it last said and what the
 * phone asked for.
 *
 * Split out from the session so the awkward parts — the handover window, the
 * seek grace and the extrapolation between three-second statuses — are a
 * question about numbers rather than about a Cast client. A receiver that has
 * not moved onto the track it was handed is still reporting the one before, and
 * so is a seek that has not been answered yet; neither should move the bar.
 */
internal fun castPositionMs(inputs: CastPositionInputs): Long = with(inputs) {
    // Handed a new track and it has not moved onto it yet: everything it reports
    // is of the track before, so report what was asked for. Bounded, so a
    // receiver that never moves on cannot freeze the bar.
    if (handoverOutstanding && nowMs - requestedAtMs < switchTimeoutMs) return askedMs
    // A seek is asked of the receiver and answered a moment later; in between,
    // its own clock still reads the old place and the bar would spring back.
    if (liveMs == null || nowMs - requestedAtMs < seekGraceMs) return askedMs
    if (!playing || anchorAtMs == 0L) return liveMs.coerceAtLeast(0L)
    // Its status only lands every few seconds. Between updates the receiver
    // keeps playing, so the place it reached is the last one it reported plus
    // however long we have been running since. Waiting for the next update
    // instead would step the bar in three-second jumps.
    val extrapolated = anchorPositionMs + (nowMs - anchorAtMs)
    if (durationMs != C.TIME_UNSET) extrapolated.coerceAtMost(durationMs) else extrapolated
}
