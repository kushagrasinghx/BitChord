// @vitest-environment happy-dom
import { describe, expect, it } from 'vitest';
import { keyBelongsToFocus } from './useKeyboardShortcuts';

/** Focus inside an element matching only [role]: enough for keyBelongsToFocus's closest() checks. */
const inside = (role: string) => ({ tagName: 'DIV', isContentEditable: false, closest: (selector: string) => (selector.includes(`[role="${role}"]`) ? {} : null) }) as unknown as EventTarget;
const key = (target: EventTarget, k: string, shiftKey = false) => keyBelongsToFocus({ target, key: k, shiftKey });

describe('keyBelongsToFocus', () => {
  it("leaves the player's letters and Shift+arrows to the player while the seek slider has focus", () => {
    const slider = inside('slider');
    for (const k of ['m', 'L', 'q', '?', ' ']) expect(key(slider, k), k).toBe(false);
    expect(key(slider, 'ArrowRight', true)).toBe(false);
  });

  it('gives a slider its own stepping keys', () => {
    for (const k of ['ArrowLeft', 'ArrowRight', 'Home', 'End']) expect(key(inside('slider'), k), k).toBe(true);
  });

  it('still gives a menu every key', () => {
    expect(key(inside('menu'), 'm')).toBe(true);
  });
});
