/*
 * The phone's play/pause and skip button motion (sharedUi ExpandedPlayPauseMotion.kt,
 * PlayerPlayPauseMotion.kt, PlayerSkipMotion.kt), as pure functions the transport
 * glyphs step every frame. Ported rather than shared: on the phone these live in
 * Compose UI files, tangled with its springs and drawing, not in shared logic.
 */

export const clamp01 = (x: number) => Math.min(1, Math.max(0, x));
const smoothstep = (x: number) => {
  const t = clamp01(x);
  return t * t * (3 - 2 * t);
};

// ---- Play/pause: two silhouettes handing over, shrunk and blurred, on one spring ----------

/** A damped spring (Compose's spring(): mass 1). */
export interface Spring {
  damping: number;
  stiffness: number;
}

/** ExpandedPlayPauseSpec: fitted to Apple Music's own play/pause, frame by frame. */
export const EXPANDED_SPRING: Spring = { damping: 0.68, stiffness: 170 };
/** PlayPauseMotionSpec's morph: Spring.DampingRatioLowBouncy, Spring.StiffnessLow. */
export const MINI_SPRING: Spring = { damping: 0.75, stiffness: 200 };
/** The mini player's pulse: a pinch almost to a point, then a pop back (APPROX fits on the phone too). */
export const PULSE_DIP = 0.12;
export const PULSE_DIP_SPRING: Spring = { damping: 1, stiffness: 2500 };
export const PULSE_RETURN_SPRING: Spring = { damping: 0.68, stiffness: 322 };
export const PULSE_RETURN_AT = 0.03;

const MIN_SCALE = 0.35;
const MAX_SCALE = 1.08;
const BLUR_FRACTION = 0.16;

/** expandedSymbolScale, expandedSymbolAlpha and expandedSymbolBlur for a symbol at [weight] (1 = fully shown). */
export function symbolPose(weight: number): { scale: number; alpha: number; blur: number } {
  return {
    scale: Math.min(MAX_SCALE, Math.max(MIN_SCALE, weight)),
    alpha: smoothstep(weight),
    // A share of the icon's size, before the symbol's own scale.
    blur: BLUR_FRACTION * (1 - clamp01(weight)),
  };
}

/** One frame of [spring] toward [target], in small fixed steps so a long frame can't blow it up. */
export function stepSpring(spring: Spring, x: number, v: number, target: number, dtSec: number): [number, number] {
  const damping = 2 * spring.damping * Math.sqrt(spring.stiffness);
  const steps = Math.max(1, Math.ceil(dtSec / (1 / 240)));
  const h = dtSec / steps;
  for (let i = 0; i < steps; i++) {
    v += (-spring.stiffness * (x - target) - damping * v) * h;
    x += v * h;
  }
  return [x, v];
}

/** Close enough to [target], and slow enough, to stop (the phone's visibilityThreshold). */
export const springSettled = (x: number, v: number, target: number) => Math.abs(x - target) < 1e-4 && Math.abs(v) < 1e-3;

// ---- Skip: a conveyor of arrowheads, 600 ms a cycle -------------------------------------

export const SKIP_MS = 600;
const FRONT_GONE = 0.35;
/** From here to the end of a cycle, the glyph is the original icon again. */
export const SKIP_SETTLED = 0.9;
const NEW_ONSET = 0.28;
const MAX_START_SLOPE = 2;

/** playerSkipTarget: keep the interrupted cycle and at most one pending, never a backlog of taps. */
export function skipTarget(cycles: number, target: number): number {
  const boundary = cycles - (cycles % 1) + 1;
  return cycles < target ? boundary + 1 : boundary;
}

/** playerSkipPose: the leading arrow shrinks away, the trailing one slides into its place, a new one grows behind. */
export function skipPose(progress: number) {
  const p = clamp01(progress);
  const contraction = clamp01(p / FRONT_GONE);
  const t = clamp01(p / SKIP_SETTLED);
  const growth = smoothstep((p - NEW_ONSET) / (SKIP_SETTLED - NEW_ONSET));
  return {
    frontScale: 1 - smoothstep(contraction),
    // Fades later than it shrinks, so it reads as a triangle through the middle of it.
    frontAlpha: 1 - smoothstep(contraction * contraction),
    survivorTravel: t + t * t * (1 - t),
    newScale: growth,
    newAlpha: growth,
  };
}

/** A run of skip cycles from [from] to [to]: the phone's tween, its Hermite easing carrying the speed it had. */
export interface SkipRun {
  from: number;
  distance: number;
  durationMs: number;
  slope: number;
  startedAt: number;
}

export function skipRun(from: number, to: number, velocityPerSec: number, now: number): SkipRun {
  const distance = to - from;
  const velocity = velocityPerSec > 0 ? velocityPerSec : (distance * 1000) / SKIP_MS;
  // Finish within one tap's time even when taps pile up; shorter only to keep the speed without overshoot.
  const durationMs = Math.max(1, Math.min(SKIP_MS, (MAX_START_SLOPE * distance * 1000) / velocity));
  const slope = Math.min(MAX_START_SLOPE, Math.max(0, (velocity * durationMs) / (distance * 1000)));
  return { from, distance, durationMs, slope, startedAt: now };
}

/** Where [run] is at [now], in cycles, and how fast it is moving (cycles a second). */
export function skipAt(run: SkipRun, now: number): { cycles: number; velocity: number; done: boolean } {
  const t = clamp01((now - run.startedAt) / run.durationMs);
  const s = run.slope;
  const eased = (s - 1) * t * t * t + 2 * (1 - s) * t * t + s * t;
  const rate = 3 * (s - 1) * t * t + 4 * (1 - s) * t + s;
  return { cycles: run.from + run.distance * eased, velocity: t < 1 ? (run.distance * rate * 1000) / run.durationMs : 0, done: t >= 1 };
}
