// @vitest-environment happy-dom
import { describe, expect, it } from 'vitest';
import { act, createElement } from 'react';
import { createRoot } from 'react-dom/client';
import { MemoryRouter } from 'react-router';
import { usePlayer } from '@/state/player';
import { keyBelongsToFocus, useKeyboardShortcuts } from './useKeyboardShortcuts';

function Shortcuts() {
  useKeyboardShortcuts();
  return null;
}

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

describe('useKeyboardShortcuts with the seek slider focused (as after clicking it)', () => {
  it('still plays and pauses on Space, mutes on M and skips on Shift+→', async () => {
    (globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
    const calls: string[] = [];
    usePlayer.setState({ togglePlay: () => calls.push('play'), toggleMute: () => calls.push('mute'), next: () => calls.push('next') } as never);
    const slider = Object.assign(document.createElement('div'), { tabIndex: 0 });
    slider.setAttribute('role', 'slider');
    const host = document.createElement('div');
    document.body.append(slider, host);
    const root = createRoot(host);
    await act(async () => root.render(createElement(MemoryRouter, null, createElement(Shortcuts))));
    slider.focus();
    for (const init of [{ key: ' ' }, { key: 'm' }, { key: 'ArrowRight', shiftKey: true }]) {
      slider.dispatchEvent(new KeyboardEvent('keydown', { ...init, bubbles: true, cancelable: true }));
    }
    expect(calls).toEqual(['play', 'mute', 'next']);
    await act(async () => root.unmount());
  });
});
