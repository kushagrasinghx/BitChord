package com.music.bitchord.ui.player

/**
 * The sing-along half of Karaoke: pure decisions about when one press of the
 * Karaoke glyph brings the existing lyrics surface up behind its drawer, and
 * when the drawer should instead offer the one tap that turns synced lyrics
 * on.
 *
 * Kept out of [NowPlayingScreen] and [KaraokeSheet] so the coordination the
 * plan sets out as state-transition rules can be tested without composing
 * either — every rule below has a line in `KaraokeSingAlongTest`. The rules
 * themselves own no state: each reads the press it was handed and answers.
 */
/**
 * Whether the press that just turned Karaoke on should also open the existing
 * lyrics panel behind the drawer.
 *
 * Only on the enabling edge: a press while Karaoke is already on adjusts the
 * level and nothing else — the panel is the listener's to bring back, so a
 * re-press never forces it up, just as closing it never switched Karaoke off.
 * Only when the audio is actually being processed: a route that holds the
 * reduction up — bit-perfect, a Cast speaker, the watch, a mono track, an
 * undecoded stream — leaves the drawer to show its reason instead of a
 * sing-along that never started. And only with synced lyrics already
 * allowed: that preference governs third-party network lookups, so no
 * activation may flip it — the drawer offers the explicit tap instead (see
 * [offersSyncedLyricsEnable]).
 */
internal fun opensLyricsOnKaraokeActivation(
    /** Whether Karaoke was already on when the glyph was pressed. */
    alreadyEnabled: Boolean,
    /** The runtime verdict on the audio actually playing, read at the press. */
    availability: KaraokeAvailability,
    /** The listener's own synced-lyrics preference, read at the press. */
    syncedLyricsEnabled: Boolean,
): Boolean = !alreadyEnabled &&
    availability == KaraokeAvailability.AVAILABLE &&
    syncedLyricsEnabled

/**
 * Whether the Karaoke drawer should offer its explicit **Enable synced
 * lyrics** tap. Shown only while Karaoke is on with the preference off —
 * the one state where sing-along is a single deliberate tap away — and never
 * otherwise: with the preference already on there is nothing to enable, and
 * with Karaoke off the drawer is about the audio alone.
 */
internal fun offersSyncedLyricsEnable(
    karaokeEnabled: Boolean,
    syncedLyricsEnabled: Boolean,
): Boolean = karaokeEnabled && !syncedLyricsEnabled
