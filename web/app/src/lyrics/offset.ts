import { create } from 'zustand';
import { persist } from 'zustand/middleware';

/**
 * Lyrics offset: one shift for every synced lyric against the playback clock.
 * Global, not per song, because the phone keeps a single value
 * (AppSettings.lyricsOffsetMs, KEY_LYRICS_OFFSET_MS).
 */

/** PlayerHost.kt MIN_LYRICS_OFFSET_MS / MAX_LYRICS_OFFSET_MS. */
export const MIN_LYRICS_OFFSET_MS = -5_000;
export const MAX_LYRICS_OFFSET_MS = 5_000;
/** LyricsOffsetSheet.kt StepMs: what − and + move it by, and what the slider snaps to. */
export const LYRICS_OFFSET_STEP_MS = 100;

/** AppSettings.setLyricsOffsetMs: clamped, so a stored value can never push the lyrics past the range. */
const clamp = (ms: number) => Math.min(MAX_LYRICS_OFFSET_MS, Math.max(MIN_LYRICS_OFFSET_MS, Math.round(ms)));

export const useLyricsOffset = create<{ offsetMs: number; setOffsetMs: (ms: number) => void }>()(
  persist((set) => ({ offsetMs: 0, setOffsetMs: (ms) => set({ offsetMs: clamp(ms) }) }), {
    name: 'bitchord:lyrics-offset',
    version: 1,
    merge: (saved, current) => ({ ...current, offsetMs: clamp(Number((saved as { offsetMs?: unknown })?.offsetMs) || 0) }),
  }),
);

/** PlayerLyrics.adjustedLyricsPosition: a positive offset shows lyrics later, so the lyrics read an earlier time. */
export const adjustedLyricsPosition = (positionMs: number, offsetMs: number) => Math.max(0, positionMs - offsetMs);

/** PlayerLyrics.adjustedLyricsSeekTarget: tapping a line lands where that line now shows. */
export const adjustedLyricsSeekTarget = (lineTimeMs: number, offsetMs: number) => Math.max(0, lineTimeMs + offsetMs);

/** LyricsOffsetSheet.formatOffset: "+0.0 s". */
export const formatLyricsOffset = (ms: number) => `${ms < 0 ? '−' : '+'}${(Math.abs(ms) / 1000).toFixed(1)} s`;
