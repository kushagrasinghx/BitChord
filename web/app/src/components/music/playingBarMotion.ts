/** Each bar's lowest point; all three peak at full height (Common.kt PLAYING_BAR_MINIMUMS). */
export const MINIMUMS = [0.38, 0.78, 0.52];

/**
 * Bar [i]'s height at [seconds] into its loop (sharedUi/.../components/Common.kt
 * SearchPlayingBars): it rises from its minimum to full height and falls again
 * over its own period (520, 650 and 780 ms, peaking at 260, 310 and 360 ms),
 * then plays that backwards, so the three drift in and out of step.
 */
export function barScale(seconds: number, i: number): number {
  const period = 0.52 + i * 0.13;
  const peak = (0.26 + i * 0.05) / period;
  const cycle = Math.floor(seconds / period);
  let f = (seconds - cycle * period) / period;
  if (cycle % 2 === 1) f = 1 - f;
  const low = MINIMUMS[i] ?? 0.5;
  return f < peak ? low + (1 - low) * (f / peak) : 1 - (1 - low) * ((f - peak) / (1 - peak));
}

/*
 * Every playing indicator on screen shares one timer at this rate. A per-frame
 * animation made WebView2 compose and present the whole window at the display's
 * refresh rate for three 3px bars: ~10% of a core on the Windows app while a
 * song played. At 12 changes a second the bars look the same and cost nothing
 * measurable.
 */
const STEP_MS = 1000 / 12;
const groups = new Set<() => void>();
let timer: ReturnType<typeof setInterval> | null = null;

export function joinBarClock(step: () => void): () => void {
  groups.add(step);
  timer ??= setInterval(() => {
    if (document.hidden) return;
    for (const each of groups) each();
  }, STEP_MS);
  return () => {
    groups.delete(step);
    if (!groups.size && timer) {
      clearInterval(timer);
      timer = null;
    }
  };
}
