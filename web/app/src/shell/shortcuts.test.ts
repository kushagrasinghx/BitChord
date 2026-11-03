import { describe, expect, it } from 'vitest';
import { isApple, keyLabel, SHORTCUT_GROUPS } from './shortcuts';

describe('the shortcut sheet', () => {
  it('names the command key as the device does', () => {
    expect(isApple('MacIntel')).toBe(true);
    expect(isApple('Win32')).toBe(false);
    expect(keyLabel('Mod', true)).toBe('⌘');
    expect(keyLabel('Mod', false)).toBe('Ctrl');
    expect(keyLabel('Space', true)).toBe('Space');
  });

  it('lists itself', () => {
    expect(SHORTCUT_GROUPS.flatMap((g) => g.shortcuts).some((s) => s.keys.some((combo) => combo.join() === '?'))).toBe(true);
  });
});
