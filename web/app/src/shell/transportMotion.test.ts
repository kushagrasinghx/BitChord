import { describe, expect, it } from 'vitest';
import { EXPANDED_SPRING, SKIP_MS, skipAt, skipPose, skipRun, skipTarget, springSettled, stepSpring, symbolPose } from './transportMotion';

describe('play/pause motion (ExpandedPlayPauseMotion.kt)', () => {
  it('overshoots a touch and settles, as the 0.68 / 170 spring does', () => {
    let [x, v] = [0, 0];
    let peak = 0;
    let t = 0;
    while (!springSettled(x, v, 1) && t < 3) {
      [x, v] = stepSpring(EXPANDED_SPRING, x, v, 1, 1 / 60);
      peak = Math.max(peak, x);
      t += 1 / 60;
    }
    expect(peak).toBeGreaterThan(1.03);
    expect(peak).toBeLessThan(1.08);
    expect(t).toBeLessThan(1.5);
  });

  it('shows the symbol crisp at rest and hides it shrunk and blurred', () => {
    expect(symbolPose(1)).toEqual({ scale: 1, alpha: 1, blur: 0 });
    expect(symbolPose(0)).toEqual({ scale: 0.35, alpha: 0, blur: 0.16 });
  });
});

describe('skip motion (PlayerSkipMotion.kt)', () => {
  it('runs one cycle in 600 ms, ending where it began', () => {
    const run = skipRun(0, 1, 0, 0);
    expect(run.durationMs).toBe(SKIP_MS);
    expect(skipAt(run, 300).cycles).toBeCloseTo(0.5);
    expect(skipAt(run, SKIP_MS)).toMatchObject({ cycles: 1, done: true });
    expect(skipPose(0)).toMatchObject({ frontScale: 1, frontAlpha: 1, survivorTravel: 0, newScale: 0 });
    expect(skipPose(0.9)).toMatchObject({ frontScale: 0, survivorTravel: 1, newScale: 1 });
  });

  it('adds at most one cycle for presses during one', () => {
    expect(skipTarget(0, 0)).toBe(1);
    expect(skipTarget(0.4, 1)).toBe(2);
    expect(skipTarget(0.6, 2)).toBe(2);
  });

  it('keeps its speed through a retarget without overshooting', () => {
    const first = skipRun(0, 1, 0, 0);
    const mid = skipAt(first, 300);
    const second = skipRun(mid.cycles, 2, mid.velocity, 300);
    let last = mid.cycles;
    for (let t = 300; t <= 300 + second.durationMs; t += 8) {
      const at = skipAt(second, t).cycles;
      expect(at).toBeGreaterThanOrEqual(last - 1e-9);
      expect(at).toBeLessThanOrEqual(2 + 1e-9);
      last = at;
    }
    expect(skipAt(second, 300 + second.durationMs).cycles).toBeCloseTo(2);
  });
});
