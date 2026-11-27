import { describe, expect, it } from 'vitest';
import { barScale, MINIMUMS } from './playingBarMotion';

describe('the playing bars keep the phone timing', () => {
  it('start at each bar minimum and peak at full height at 260, 310 and 360 ms', () => {
    MINIMUMS.forEach((low, i) => {
      expect(barScale(0, i)).toBeCloseTo(low, 5);
      expect(barScale(0.26 + i * 0.05, i)).toBeCloseTo(1, 5);
    });
  });

  it('play each loop backwards after it, so a bar never jumps', () => {
    const period = 0.52;
    expect(barScale(period - 0.001, 0)).toBeCloseTo(barScale(period + 0.001, 0), 2);
    expect(barScale(period + 0.1, 0)).toBeCloseTo(barScale(period - 0.1, 0), 5);
  });

  it('stay between the minimum and full height', () => {
    for (let t = 0; t < 5; t += 0.037) {
      MINIMUMS.forEach((low, i) => {
        const v = barScale(t, i);
        expect(v).toBeGreaterThanOrEqual(low - 1e-9);
        expect(v).toBeLessThanOrEqual(1 + 1e-9);
      });
    }
  });
});
