// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { adjustedLyricsPosition, adjustedLyricsSeekTarget, formatLyricsOffset } from './offset';

// LyricsOffsetTest.kt's cases, then the web's own storage.
describe('lyrics offset', () => {
  beforeEach(() => {
    vi.resetModules();
    localStorage.clear();
  });

  it('shows lyrics later for a positive offset and earlier for a negative one', () => {
    expect(adjustedLyricsPosition(10_000, 1_500)).toBe(8_500);
    expect(adjustedLyricsPosition(10_000, -1_500)).toBe(11_500);
  });

  it('never reads before the start of the song', () => {
    expect(adjustedLyricsPosition(500, 1_500)).toBe(0);
  });

  it('seeks a tapped line to where the offset shows it', () => {
    expect(adjustedLyricsSeekTarget(10_000, 1_500)).toBe(11_500);
    expect(adjustedLyricsSeekTarget(10_000, -1_500)).toBe(8_500);
  });

  it('is written as the sheet writes it', () => {
    expect(formatLyricsOffset(0)).toBe('+0.0 s');
    expect(formatLyricsOffset(1_500)).toBe('+1.5 s');
    expect(formatLyricsOffset(-300)).toBe('−0.3 s');
  });

  it('is kept across reloads, clamped to ±5 s', async () => {
    const { useLyricsOffset } = await import('./offset');
    expect(useLyricsOffset.getState().offsetMs).toBe(0);
    useLyricsOffset.getState().setOffsetMs(1_200);
    vi.resetModules();
    expect((await import('./offset')).useLyricsOffset.getState().offsetMs).toBe(1_200);

    useLyricsOffset.getState().setOffsetMs(9_000);
    expect(useLyricsOffset.getState().offsetMs).toBe(5_000);
    useLyricsOffset.getState().setOffsetMs(-9_000);
    expect(useLyricsOffset.getState().offsetMs).toBe(-5_000);
  });

  it('ignores a stored value it cannot use', async () => {
    localStorage.setItem('bitchord:lyrics-offset', JSON.stringify({ state: { offsetMs: 'soon' }, version: 1 }));
    expect((await import('./offset')).useLyricsOffset.getState().offsetMs).toBe(0);
    localStorage.setItem('bitchord:lyrics-offset', JSON.stringify({ state: { offsetMs: 60_000 }, version: 1 }));
    vi.resetModules();
    expect((await import('./offset')).useLyricsOffset.getState().offsetMs).toBe(5_000);
  });
});
