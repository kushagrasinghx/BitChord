/**
 * The full-screen player's springs and curves, taken from the phone's.
 *
 * Compose springs become CSS `linear()` curves where the motion is a plain
 * transition (sampled from the same spring, to its 0.2% settle), and a small
 * critically damped integrator where a gesture hands its speed over (the
 * sheet).
 */

/**
 * PlayerSheetMotion.kt SLIDE_STIFFNESS: the sheet's spring, critically damped
 * (Spring.DampingRatioNoBouncy), so it leaves briskly and spends most of its
 * time settling, about 0.45s of response.
 */
export const SHEET_STIFFNESS = 195;

/** PlayerSheetMotion.kt SLIDE_SETTLED_PX: close enough to stop, rather than the spring's long tail. */
export const SHEET_SETTLED_PX = 0.5;

/** MainActivity.kt PLAYER_PULL_FLING_VELOCITY: a flick this fast decides on its own, either way. */
export const SHEET_FLING_PX_PER_SEC = 400;

/** MainActivity.kt PLAYER_SCRIM_ALPHA: the dim behind the player, as deep as it is open. */
export const SHEET_SCRIM_ALPHA = 0.32;

/**
 * One step of a critically damped spring toward `to`, `dt` seconds on from
 * `x` moving at `v` (px/s). Solved exactly rather than integrated, so a long
 * frame never overshoots: x(t) = to + (d + (v + ωd)t)e^(−ωt), d = x − to.
 */
export function stepCriticalSpring(x: number, v: number, to: number, dt: number, stiffness = SHEET_STIFFNESS) {
  const omega = Math.sqrt(stiffness);
  const d = x - to;
  const c = v + omega * d;
  const decay = Math.exp(-omega * dt);
  return { x: to + (d + c * dt) * decay, v: (c - omega * (d + c * dt)) * decay };
}

/** Where a released drag goes: a flick decides by its direction, otherwise whichever side of halfway it was let go. */
export function sheetSettlesOpen(offset: number, travel: number, velocity: number): boolean {
  if (Math.abs(velocity) >= SHEET_FLING_PX_PER_SEC) return velocity < 0;
  return offset < travel / 2;
}

/**
 * ThinSlider.kt's height spring (Spring.DampingRatioMediumBouncy, StiffnessMediumLow):
 * the bar swells under the pointer with a little bounce.
 */
export const SLIDER_SPRING =
  'duration-[613ms] ease-[linear(0,0.109,0.352,0.627,0.867,1.038,1.132,1.163,1.148,1.11,1.065,1.025,0.996,0.979,0.974,0.975,0.981,0.989,0.995,1,1.003,1.004,1.004,1.003,1)]';

/** PlayerControls.kt Pill: animateContentSize on spring(.82, StiffnessMediumLow). */
export const PILL_SPRING =
  'duration-[405ms] ease-[linear(0,0.097,0.293,0.499,0.674,0.806,0.897,0.954,0.987,1.003,1.01,1.011,1.01,1.007,1.005,1.003,1)]';

/** Compose's FastOutSlowInEasing, for the tweens that name no curve of their own. */
export const FAST_OUT_SLOW_IN = 'cubic-bezier(0.4, 0, 0.2, 1)';
