// @vitest-environment happy-dom
import { describe, expect, it } from 'vitest';
import { seekTarget } from './useScrubber';

const press = (key: string, mods: Partial<Record<'altKey' | 'ctrlKey' | 'metaKey' | 'shiftKey', boolean>> = {}) => ({ key, altKey: false, ctrlKey: false, metaKey: false, shiftKey: false, ...mods });

describe('seekTarget (the focused seek line)', () => {
  it('steps ten seconds on the arrows and goes to either end on Home and End', () => {
    expect(seekTarget(press('ArrowRight'), 30, 200)).toBe(40);
    expect(seekTarget(press('ArrowDown'), 30, 200)).toBe(20);
    expect(seekTarget(press('Home'), 30, 200)).toBe(0);
    expect(seekTarget(press('End'), 30, 200)).toBe(200);
  });

  it('stays inside the song', () => {
    expect(seekTarget(press('ArrowLeft'), 4, 200)).toBe(0);
    expect(seekTarget(press('ArrowUp'), 195, 200)).toBe(200);
  });

  it("leaves modified arrows to the app's next, previous and volume", () => {
    for (const mod of ['shiftKey', 'ctrlKey', 'altKey', 'metaKey'] as const) expect(seekTarget(press('ArrowRight', { [mod]: true }), 30, 200), mod).toBeNull();
  });

  it("ignores other keys, and a song whose length isn't known yet", () => {
    expect(seekTarget(press(' '), 30, 200)).toBeNull();
    expect(seekTarget(press('ArrowRight'), 30, Number.NaN)).toBeNull();
  });
});
