/** What the sleep timer is waiting for: a time, or the end of the song playing. */
export type SleepTimer = { kind: 'minutes'; endsAt: number; minutes: number } | { kind: 'track'; videoId: string | null };

export const SLEEP_CHOICES = [5, 10, 15, 30, 45, 60] as const;

/** A timed stop fades over its last 10 s; the end of a song over its last 3 s, as songs end quietly anyway. */
export const FADE_MS = { minutes: 10_000, track: 3_000 } as const;

/**
 * The end of a song stops this long before it: at the very end the player has
 * already moved on to the next song, and would play it.
 */
export const END_MARGIN_MS = 500;

/**
 * How long until the timer stops playback, in ms. For the end of a song,
 * that is just before it would hand over to the next: a crossfade starts the
 * next song early, so the timer stops there too.
 */
export function remainingMs(timer: SleepTimer, now: number, song: { durationSec: number; positionSec: number; crossfadeSec: number }): number {
  if (timer.kind === 'minutes') return timer.endsAt - now;
  if (!Number.isFinite(song.durationSec) || song.durationSec <= 0) return Infinity;
  return (song.durationSec - song.crossfadeSec - song.positionSec) * 1000 - END_MARGIN_MS;
}

/** The volume left at [remaining] ms: 1 until the fade starts, then down to 0. */
export function fadeLevel(remaining: number, fadeMs: number): number {
  if (remaining >= fadeMs) return 1;
  return Math.max(0, remaining / fadeMs);
}

/** "12:05" left, for the player bar. */
export function formatRemaining(ms: number): string {
  const total = Math.max(0, Math.ceil(ms / 1000));
  const h = Math.floor(total / 3600);
  const m = Math.floor((total % 3600) / 60);
  const s = String(total % 60).padStart(2, '0');
  return h ? `${h}:${String(m).padStart(2, '0')}:${s}` : `${m}:${s}`;
}
