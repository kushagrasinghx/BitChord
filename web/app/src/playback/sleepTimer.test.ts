import { describe, expect, it } from 'vitest';
import { fadeLevel, formatRemaining, remainingMs } from './sleepTimerPlan';

describe('the sleep timer', () => {
  const song = { durationSec: 200, positionSec: 150, crossfadeSec: 0 };

  it('counts down to its time', () => {
    expect(remainingMs({ kind: 'minutes', endsAt: 61_000, minutes: 1 }, 1_000, song)).toBe(60_000);
  });

  it('counts down to the end of the song, or to where a crossfade would start the next', () => {
    const track = { kind: 'track', videoId: 'a' } as const;
    expect(remainingMs(track, 0, song)).toBe(49_500);
    expect(remainingMs(track, 0, { ...song, crossfadeSec: 6 })).toBe(43_500);
  });

  it("waits when the song's length isn't known yet", () => {
    expect(remainingMs({ kind: 'track', videoId: 'a' }, 0, { ...song, durationSec: NaN })).toBe(Infinity);
  });

  it('fades only over the last stretch', () => {
    expect(fadeLevel(20_000, 10_000)).toBe(1);
    expect(fadeLevel(5_000, 10_000)).toBe(0.5);
    expect(fadeLevel(-100, 10_000)).toBe(0);
  });

  it('shows the time left', () => {
    expect(formatRemaining(65_000)).toBe('1:05');
    expect(formatRemaining(3_725_000)).toBe('1:02:05');
    expect(formatRemaining(-5)).toBe('0:00');
  });
});
