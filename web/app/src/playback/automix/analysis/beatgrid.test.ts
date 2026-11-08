import { describe, expect, it } from 'vitest';
import { fitGrid, normalizeTempo } from './beatgrid';

/** Ticks on a [bpm] grid from [from] to [to] seconds, shifted by [phaseSec], with essentia-like ±[jitterMs] wobble. */
function ticks(bpm: number, from: number, to: number, phaseSec = 0, jitterMs = 0): number[] {
  const beat = 60 / bpm;
  const out: number[] = [];
  let seed = 7;
  for (let t = from + phaseSec; t < to; t += beat) {
    seed = (seed * 1103515245 + 12345) & 0x7fffffff;
    out.push(t + ((seed / 0x7fffffff) * 2 - 1) * (jitterMs / 1000));
  }
  return out;
}

describe('fitGrid', () => {
  it('fits a clean grid exactly', () => {
    const fit = normalizeTempo(fitGrid(ticks(124, 0.3, 120))!);
    expect(fit.bpm).toBeCloseTo(124, 2);
    expect(fit.residualMs).toBeLessThan(1);
  });

  // AutoMix numbered ticks consecutively, so a beatless breakdown (no ticks)
  // put every later tick on the wrong beat: One More Time scored 4e-8.
  it('holds the grid across a breakdown with no ticks', () => {
    const t = [...ticks(123, 0.4, 100), ...ticks(123, 0.4, 300).filter((x) => x > 160)];
    const fit = normalizeTempo(fitGrid(t)!);
    expect(fit.bpm).toBeCloseTo(123, 1);
    expect(fit.residualMs).toBeLessThan(2);
  });

  it('keeps essentia-sized jitter from moving the tempo', () => {
    const fit = normalizeTempo(fitGrid(ticks(115, 0.1, 100, 0, 23))!);
    expect(fit.bpm).toBeCloseTo(115, 1);
    expect(fit.residualMs).toBeLessThan(20);
  });

  it('caps a stray tick at a quarter beat in the residual', () => {
    const t = ticks(120, 0, 60);
    t.splice(40, 0, t[40]! - 0.25); // one tick on the off-beat
    const fit = fitGrid(t)!;
    expect(fit.bpm).toBeCloseTo(120, 1);
    expect(fit.residualMs).toBeLessThan(0.125 * 1000 / Math.sqrt(t.length) + 1);
  });

  it('needs at least eight ticks', () => {
    expect(fitGrid([0, 0.5, 1, 1.5])).toBeNull();
  });
});
