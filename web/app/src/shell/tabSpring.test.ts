import { describe, expect, it } from 'vitest';
import { pillFrames, spring, SPRING_MS } from './tabSpring';

describe('tab spring', () => {
  it('starts at rest, overshoots once, and has settled by its duration', () => {
    expect(spring(0)).toBe(0);
    const peak = Math.max(...Array.from({ length: 100 }, (_, i) => spring((i / 100) * (SPRING_MS / 1000))));
    expect(peak).toBeGreaterThan(1.02);
    expect(peak).toBeLessThan(1.06);
    expect(spring(SPRING_MS / 1000)).toBeCloseTo(1, 1);
  });

  it('stretches the pill while it travels and lands it in its own shape on the target', () => {
    const frames = pillFrames(0, 76, 38);
    expect(frames[0]!.transform).toBe('translateY(0px) scale(0.92, 1.16)');
    expect(frames.at(-1)!.transform).toBe('translateY(76px) scale(1, 1)');
  });
});
