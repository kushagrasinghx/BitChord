/** One wheel notch moves the volume by this much (the drag slider is finer). */
export const WHEEL_VOLUME_STEP = 0.05;

/**
 * The volume a wheel step lands on: up (negative deltaY) raises it, down
 * lowers it, clamped to [0, 1] and rounded to whole percents so repeated
 * steps don't accumulate float dust. A zero delta leaves it unchanged.
 * Pure and store-free on purpose: the test suite runs in node, and the
 * player store touches `window` at import time.
 */
export function nextVolumeFromWheel(volume: number, deltaY: number): number {
  if (deltaY === 0) return volume;
  const next = volume + (deltaY < 0 ? WHEEL_VOLUME_STEP : -WHEEL_VOLUME_STEP);
  return Math.round(Math.min(1, Math.max(0, next)) * 100) / 100;
}
