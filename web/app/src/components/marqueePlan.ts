/** How a title too long for its space scrolls: the Apple Music loop. */
export const MARQUEE = {
  /** Read the start first, and again each time the title comes round. */
  holdMs: 3_000,
  /** Slow enough to read while it moves. */
  pxPerSecond: 30,
  /** Between the title and the copy that follows it in. */
  gapPx: 48,
  /** Overflow under this is a clipped letter, not worth moving for. */
  minOverflowPx: 4,
} as const;

/**
 * One loop for a [textWidth] px title in a [boxWidth] px space, or null when
 * it fits. The title holds, then scrolls left with a copy following [gapPx]
 * behind; the loop ends with the copy exactly where the title began, so the
 * next one starts from a frame that looks the same: no jump back.
 */
export function marqueePlan(textWidth: number, boxWidth: number) {
  if (textWidth - boxWidth < MARQUEE.minOverflowPx) return null;
  const distance = textWidth + MARQUEE.gapPx;
  const durationMs = MARQUEE.holdMs + (distance / MARQUEE.pxPerSecond) * 1000;
  return {
    durationMs,
    keyframes: [
      { transform: 'translateX(0)', offset: 0 },
      { transform: 'translateX(0)', offset: MARQUEE.holdMs / durationMs },
      { transform: `translateX(${-distance}px)`, offset: 1 },
    ],
  };
}
