import { describe, expect, it } from 'vitest';
import { stretchAllowed } from './transition-scheduler';

describe('stretchAllowed', () => {
  it('slows the next song down by at most 10%', () => {
    // 120 BPM out, 130 BPM in: played at 120/130 = 0.923, 7.7% slower
    expect(stretchAllowed(120 / 130)).toBe(true);
    // 120 out, 152 in: 0.79, 21% slower, too far (the 0.792x the user saw)
    expect(stretchAllowed(120 / 152)).toBe(false);
  });

  it('speeds the next song up by at most 25%', () => {
    // 120 out, 100 in: played at 1.2
    expect(stretchAllowed(120 / 100)).toBe(true);
    // 120 out, 92 in: 1.304, too far
    expect(stretchAllowed(120 / 92)).toBe(false);
  });
});
