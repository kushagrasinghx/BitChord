/** How a title too long for its space scrolls: Spotify's rhythm. */
export const MARQUEE = {
  /** Read the start first. */
  holdStartMs: 3_000,
  /** Then the end, before going back. */
  holdEndMs: 2_000,
  /** Slow enough to read while it moves. */
  pxPerSecond: 30,
  /** Overflow under this is a clipped letter, not worth moving for. */
  minOverflowPx: 4,
} as const;

/**
 * One cycle for [overflow] px of hidden text: hold, scroll to the end, hold,
 * back to the start. Null when it fits. Offsets are fractions of [durationMs].
 */
export function marqueePlan(overflow: number) {
  if (overflow < MARQUEE.minOverflowPx) return null;
  const scrollMs = (overflow / MARQUEE.pxPerSecond) * 1000;
  const durationMs = MARQUEE.holdStartMs + scrollMs + MARQUEE.holdEndMs;
  const scrolledAt = (MARQUEE.holdStartMs + scrollMs) / durationMs;
  return {
    durationMs,
    keyframes: [
      { transform: 'translateX(0)', offset: 0 },
      { transform: 'translateX(0)', offset: MARQUEE.holdStartMs / durationMs },
      { transform: `translateX(${-overflow}px)`, offset: scrolledAt },
      { transform: `translateX(${-overflow}px)`, offset: 1 },
    ],
  };
}
