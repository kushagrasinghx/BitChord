import { describe, expect, it } from 'vitest';
import { nextVolumeFromWheel, WHEEL_VOLUME_STEP } from './wheelVolume';

describe('nextVolumeFromWheel', () => {
  it('raises the volume one step on wheel up (negative deltaY)', () => {
    expect(nextVolumeFromWheel(0.5, -100)).toBe(0.5 + WHEEL_VOLUME_STEP);
  });

  it('lowers the volume one step on wheel down (positive deltaY)', () => {
    expect(nextVolumeFromWheel(0.5, 100)).toBe(0.5 - WHEEL_VOLUME_STEP);
  });

  it('depends on the direction, not the delta magnitude', () => {
    expect(nextVolumeFromWheel(0.5, -1)).toBe(nextVolumeFromWheel(0.5, -120));
    expect(nextVolumeFromWheel(0.5, 3)).toBe(nextVolumeFromWheel(0.5, 240));
  });

  it('clamps at full volume', () => {
    expect(nextVolumeFromWheel(1, -100)).toBe(1);
    expect(nextVolumeFromWheel(0.98, -100)).toBe(1);
  });

  it('clamps at silence', () => {
    expect(nextVolumeFromWheel(0, 100)).toBe(0);
    expect(nextVolumeFromWheel(0.02, 100)).toBe(0);
  });

  it('leaves the volume unchanged for a zero delta', () => {
    expect(nextVolumeFromWheel(0.42, 0)).toBe(0.42);
  });

  it('lands on exact percents with no float dust', () => {
    expect(nextVolumeFromWheel(0.07, -100)).toBe(0.12);
    expect(nextVolumeFromWheel(0.29, 100)).toBe(0.24);
  });
});
