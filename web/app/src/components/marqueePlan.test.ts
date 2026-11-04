import { describe, expect, it } from 'vitest';
import { MARQUEE, marqueePlan } from './marqueePlan';

describe('a scrolling title', () => {
  it("doesn't move when it fits, or misses only a sliver", () => {
    expect(marqueePlan(200, 250)).toBeNull();
    expect(marqueePlan(253, 250)).toBeNull();
  });

  it('holds, then scrolls until the copy behind it stands where the title began', () => {
    const plan = marqueePlan(402, 250)!;
    const distance = 402 + MARQUEE.gapPx; // 450 px: the title's width and the gap
    expect(plan.durationMs).toBe(MARQUEE.holdMs + (distance / MARQUEE.pxPerSecond) * 1000);
    expect(plan.keyframes.map((k) => k.transform)).toEqual(['translateX(0)', 'translateX(0)', `translateX(${-distance}px)`]);
    expect(plan.keyframes[1]!.offset).toBeCloseTo(3 / 18);
  });
});
