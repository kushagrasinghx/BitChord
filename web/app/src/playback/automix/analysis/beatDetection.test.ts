import { describe, expect, it } from 'vitest';
import { beatChunks, gridConfidence, pickPeaks, snapDownbeats, tempoFromBeats } from './beatDetection';

const beats = Array.from({ length: 48 }, (_, i) => i * 0.5);
describe('Beat This! evidence', () => {
  it('requires decisive peaks and enough beats before authorizing a beatmatch', () => {
    expect(gridConfidence(beats, beats.map(() => 0.01))).toBeLessThan(0.1);
    expect(gridConfidence(beats, beats.map(() => 3))).toBeGreaterThan(0.55);
    expect(gridConfidence(beats.slice(0, 8), Array(8).fill(3))).toBeLessThan(0.55);
  });
  it('keeps the tempo across a beatless break', () => {
    const withBreak = beats.filter((_, i) => i < 12 || i > 17);
    expect(tempoFromBeats(withBreak)).toBeCloseTo(120, 9);
    expect(gridConfidence(withBreak, withBreak.map(() => 3))).toBeGreaterThan(0.55);
  });
  it('reduces confidence when the song changes tempo', () => {
    const changing = Array.from({ length: 40 }, (_, i) => i < 20 ? i * 0.5 : 10 + (i - 20) * 0.4);
    expect(gridConfidence(changing, changing.map(() => 4))).toBeLessThan(0.55);
  });
  it('rejects malformed timelines and logits', () => {
    for (const bad of [[...beats].reverse(), beats.map((b, i) => i === 9 ? beats[8]! : b), beats.map((b, i) => i === 9 ? NaN : b)]) {
      expect(tempoFromBeats(bad)).toBe(0);
      expect(gridConfidence(bad, bad.map(() => 3))).toBe(0);
    }
    expect(gridConfidence(beats, [3])).toBe(0);
    expect(gridConfidence(beats, beats.map(() => NaN))).toBe(0);
  });
  it('collapses a flat run into one peak and ignores non-finite logits', () => {
    const logits = new Float32Array(20).fill(-2);
    logits.fill(2, 5, 9);
    logits[15] = NaN;
    logits[19] = Infinity;
    expect(pickPeaks(logits)).toEqual([6.5]);
  });
  it('retains sub-frame timing', () => {
    const logits = new Float32Array(10).fill(-2);
    logits.set([0.5, 2, 1.5], 3);
    expect(pickPeaks(logits)).toEqual([4.25]);
  });
  it('does not pull stray downbeats onto distant beats', () => {
    expect(snapDownbeats(beats, [0.02, 2.03, 2.04, 6, 8.25, NaN])).toEqual([0, 2, 6]);
  });
  it.each([1, 12, 1488, 1489, 1500, 2951, 2976, 3010, 4510])('covers every real frame with trained predictions (%i frames)', (frames) => {
    const chunks = beatChunks(frames);
    expect(chunks[0]!.startFrame).toBe(-6);
    if (frames > 1488) expect(chunks.at(-1)!.frameCount).toBe(1500);
    const covered = new Set(chunks.flatMap((c) => Array.from({ length: c.frameCount - 12 }, (_, i) => c.startFrame + 6 + i)));
    expect([...covered].sort((a, b) => a - b)).toEqual(Array.from({ length: frames }, (_, i) => i));
    expect(chunks.every((c) => c.frameCount <= 1500)).toBe(true);
  });
});
