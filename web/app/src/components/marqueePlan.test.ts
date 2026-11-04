import { describe, expect, it } from 'vitest';
import { MARQUEE, marqueePlan } from './marqueePlan';

describe('a scrolling title', () => {
  it("doesn't move when it fits, or misses only a sliver", () => {
    expect(marqueePlan(0)).toBeNull();
    expect(marqueePlan(3)).toBeNull();
  });

  it('holds the start, scrolls at a reading pace, holds the end', () => {
    const plan = marqueePlan(90)!;
    // 3 s hold + 90 px at 30 px/s + 2 s hold
    expect(plan.durationMs).toBe(MARQUEE.holdStartMs + 3_000 + MARQUEE.holdEndMs);
    expect(plan.keyframes.map((k) => k.transform)).toEqual(['translateX(0)', 'translateX(0)', 'translateX(-90px)', 'translateX(-90px)']);
    expect(plan.keyframes[1]!.offset).toBeCloseTo(3 / 8);
    expect(plan.keyframes[2]!.offset).toBeCloseTo(6 / 8);
  });
});
