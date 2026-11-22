import { describe, expect, it } from 'vitest';
import { boxBlur } from './boxBlur';
import { currentLineIndex } from './lyricLines';
import { sheetSettlesOpen, stepCriticalSpring } from './motion';
import { line } from '@/lyrics/model';

describe('stepCriticalSpring', () => {
  it('settles on its target without passing it from rest', () => {
    let s = { x: 900, v: 0 };
    let lowest = Infinity;
    for (let i = 0; i < 120; i++) {
      s = stepCriticalSpring(s.x, s.v, 0, 1 / 60);
      lowest = Math.min(lowest, s.x);
    }
    expect(lowest).toBeGreaterThanOrEqual(0);
    expect(s.x).toBeLessThan(0.5);
  });

  it('gives the same answer in one long step as in many short ones', () => {
    let s = { x: 500, v: -1200 };
    for (let i = 0; i < 30; i++) s = stepCriticalSpring(s.x, s.v, 0, 0.01);
    const once = stepCriticalSpring(500, -1200, 0, 0.3);
    expect(once.x).toBeCloseTo(s.x, 6);
    expect(once.v).toBeCloseTo(s.v, 6);
  });
});

describe('sheetSettlesOpen', () => {
  it('lets a flick decide, whatever the position', () => {
    expect(sheetSettlesOpen(100, 1000, 600)).toBe(false);
    expect(sheetSettlesOpen(900, 1000, -600)).toBe(true);
  });
  it('otherwise goes to the nearer end', () => {
    expect(sheetSettlesOpen(400, 1000, 50)).toBe(true);
    expect(sheetSettlesOpen(600, 1000, -50)).toBe(false);
  });
});

describe('boxBlur', () => {
  it('keeps a flat colour flat, out to the clamped edges', () => {
    const size = 24;
    const pixels = new Uint8ClampedArray(size * size * 4);
    for (let i = 0; i < pixels.length; i += 4) pixels.set([40, 80, 120, 255], i);
    boxBlur(pixels, size, size);
    expect([...pixels.slice(0, 4)]).toEqual([40, 80, 120, 255]);
    expect([...pixels.slice(-4)]).toEqual([40, 80, 120, 255]);
  });

  it('spreads a bright spot into its neighbours', () => {
    const size = 24;
    const pixels = new Uint8ClampedArray(size * size * 4);
    const centre = (12 * size + 12) * 4;
    pixels[centre] = 255;
    boxBlur(pixels, size, size);
    expect(pixels[centre]).toBeLessThan(255);
    expect(pixels[(12 * size + 14) * 4]).toBeGreaterThan(0);
  });
});

describe('currentLineIndex', () => {
  const lines = [line(1000, 'one'), line(3000, ''), line(5000, 'three')];
  it('is the last line started', () => {
    expect(currentLineIndex(lines, 500)).toBe(-1);
    expect(currentLineIndex(lines, 1000)).toBe(0);
    expect(currentLineIndex(lines, 4000)).toBe(1);
    expect(currentLineIndex(lines, 9000)).toBe(2);
  });
});
