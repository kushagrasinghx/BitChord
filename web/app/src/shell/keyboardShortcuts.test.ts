import { beforeEach, describe, expect, it, vi } from 'vitest';
import { handleShortcutKey, isInteractive, isMenuOrDialog, isTyping, type ShortcutActions } from './keyboardShortcuts';

describe('keyboardShortcuts', () => {
  let actions: ShortcutActions;

  beforeEach(() => {
    actions = {
      togglePlay: vi.fn(),
      next: vi.fn(),
      previous: vi.fn(),
      seekBy: vi.fn(),
      setVolumeDelta: vi.fn(),
      toggleMute: vi.fn(),
      toggleShuffle: vi.fn(),
      cycleRepeat: vi.fn(),
      focusSearch: vi.fn(),
      closeOverlay: vi.fn(),
    };
  });

  describe('typing and input protection', () => {
    it('detects input, textarea, and select elements', () => {
      expect(isTyping({ tagName: 'INPUT', isContentEditable: false } as unknown as HTMLElement)).toBe(true);
      expect(isTyping({ tagName: 'TEXTAREA', isContentEditable: false } as unknown as HTMLElement)).toBe(true);
      expect(isTyping({ tagName: 'SELECT', isContentEditable: false } as unknown as HTMLElement)).toBe(true);
      expect(isTyping({ tagName: 'DIV', isContentEditable: true } as unknown as HTMLElement)).toBe(true);
      expect(isTyping({ tagName: 'DIV', isContentEditable: false } as unknown as HTMLElement)).toBe(false);
      expect(isTyping(null)).toBe(false);
      expect(isTyping(undefined)).toBe(false);
    });

    it('does not trigger player shortcuts while typing in a text field', () => {
      const inputTarget = { tagName: 'INPUT', isContentEditable: false } as unknown as HTMLElement;

      expect(handleShortcutKey({ key: ' ', target: inputTarget }, actions)).toBe(false);
      expect(handleShortcutKey({ key: 'k', target: inputTarget }, actions)).toBe(false);
      expect(handleShortcutKey({ key: 'm', target: inputTarget }, actions)).toBe(false);
      expect(handleShortcutKey({ key: 'j', target: inputTarget }, actions)).toBe(false);
      expect(handleShortcutKey({ key: 'l', target: inputTarget }, actions)).toBe(false);
      expect(handleShortcutKey({ key: 's', target: inputTarget }, actions)).toBe(false);
      expect(handleShortcutKey({ key: 'r', target: inputTarget }, actions)).toBe(false);
      expect(handleShortcutKey({ key: '/', target: inputTarget }, actions)).toBe(false);

      expect(actions.togglePlay).not.toHaveBeenCalled();
      expect(actions.toggleMute).not.toHaveBeenCalled();
      expect(actions.seekBy).not.toHaveBeenCalled();
    });
  });

  describe('playback controls (play/pause)', () => {
    it('toggles playback with Space bar', () => {
      const handled = handleShortcutKey({ key: ' ' }, actions);
      expect(handled).toBe(true);
      expect(actions.togglePlay).toHaveBeenCalledTimes(1);
    });

    it('does not intercept Space if focused on a button or slider', () => {
      const buttonTarget = {
        tagName: 'BUTTON',
        closest: (selector: string) => selector.includes('button'),
      } as unknown as HTMLElement;

      const handled = handleShortcutKey({ key: ' ', target: buttonTarget }, actions);
      expect(handled).toBe(false);
      expect(actions.togglePlay).not.toHaveBeenCalled();
    });

    it('toggles playback with K key', () => {
      expect(handleShortcutKey({ key: 'k' }, actions)).toBe(true);
      expect(handleShortcutKey({ key: 'K' }, actions)).toBe(true);
      expect(actions.togglePlay).toHaveBeenCalledTimes(2);
    });
  });

  describe('mute toggle', () => {
    it('toggles mute with M key', () => {
      expect(handleShortcutKey({ key: 'm' }, actions)).toBe(true);
      expect(handleShortcutKey({ key: 'M' }, actions)).toBe(true);
      expect(actions.toggleMute).toHaveBeenCalledTimes(2);
    });
  });

  describe('seeking', () => {
    it('seeks backward 10s with J and forward 10s with L', () => {
      expect(handleShortcutKey({ key: 'j' }, actions)).toBe(true);
      expect(actions.seekBy).toHaveBeenCalledWith(-10);

      expect(handleShortcutKey({ key: 'l' }, actions)).toBe(true);
      expect(actions.seekBy).toHaveBeenCalledWith(10);
    });

    it('seeks with bare Left and Right arrows by 5s', () => {
      expect(handleShortcutKey({ key: 'ArrowLeft' }, actions)).toBe(true);
      expect(actions.seekBy).toHaveBeenCalledWith(-5);

      expect(handleShortcutKey({ key: 'ArrowRight' }, actions)).toBe(true);
      expect(actions.seekBy).toHaveBeenCalledWith(5);
    });

    it('leaves bare arrow keys to slider scrubbers when interactive element has focus', () => {
      const sliderTarget = {
        closest: (selector: string) => selector.includes('slider'),
      } as unknown as HTMLElement;

      expect(handleShortcutKey({ key: 'ArrowRight', target: sliderTarget }, actions)).toBe(false);
      expect(handleShortcutKey({ key: 'ArrowLeft', target: sliderTarget }, actions)).toBe(false);
      expect(actions.seekBy).not.toHaveBeenCalled();
    });
  });

  describe('track navigation', () => {
    it('triggers next and previous with Command/Ctrl + Arrow keys', () => {
      expect(handleShortcutKey({ key: 'ArrowRight', ctrlKey: true }, actions)).toBe(true);
      expect(actions.next).toHaveBeenCalledTimes(1);

      expect(handleShortcutKey({ key: 'ArrowLeft', metaKey: true }, actions)).toBe(true);
      expect(actions.previous).toHaveBeenCalledTimes(1);
    });

    it('triggers next and previous with Shift + N and Shift + P', () => {
      expect(handleShortcutKey({ key: 'N', shiftKey: true }, actions)).toBe(true);
      expect(actions.next).toHaveBeenCalledTimes(1);

      expect(handleShortcutKey({ key: 'P', shiftKey: true }, actions)).toBe(true);
      expect(actions.previous).toHaveBeenCalledTimes(1);
    });
  });

  describe('volume control', () => {
    it('raises and lowers volume with Command/Ctrl + Up/Down arrows', () => {
      expect(handleShortcutKey({ key: 'ArrowUp', ctrlKey: true }, actions)).toBe(true);
      expect(actions.setVolumeDelta).toHaveBeenCalledWith(0.1);

      expect(handleShortcutKey({ key: 'ArrowDown', metaKey: true }, actions)).toBe(true);
      expect(actions.setVolumeDelta).toHaveBeenCalledWith(-0.1);
    });
  });

  describe('shuffle and repeat', () => {
    it('toggles shuffle with S key', () => {
      expect(handleShortcutKey({ key: 's' }, actions)).toBe(true);
      expect(actions.toggleShuffle).toHaveBeenCalledTimes(1);
    });

    it('cycles repeat with R key', () => {
      expect(handleShortcutKey({ key: 'r' }, actions)).toBe(true);
      expect(actions.cycleRepeat).toHaveBeenCalledTimes(1);
    });

    it('does not intercept browser Ctrl+R or Ctrl+S commands', () => {
      expect(handleShortcutKey({ key: 'r', ctrlKey: true }, actions)).toBe(false);
      expect(handleShortcutKey({ key: 's', ctrlKey: true }, actions)).toBe(false);
      expect(actions.cycleRepeat).not.toHaveBeenCalled();
      expect(actions.toggleShuffle).not.toHaveBeenCalled();
    });
  });

  describe('search and overlays', () => {
    it('focuses search with Command+F and "/"', () => {
      expect(handleShortcutKey({ key: 'f', metaKey: true }, actions)).toBe(true);
      expect(handleShortcutKey({ key: '/' }, actions)).toBe(true);
      expect(actions.focusSearch).toHaveBeenCalledTimes(2);
    });

    it('closes overlays with Escape', () => {
      expect(handleShortcutKey({ key: 'Escape' }, actions)).toBe(true);
      expect(actions.closeOverlay).toHaveBeenCalledTimes(1);
    });

    it('leaves Escape to menus and non-fullscreen dialogs', () => {
      const menuTarget = {
        closest: (selector: string) => selector.includes('menu'),
      } as unknown as HTMLElement;

      expect(handleShortcutKey({ key: 'Escape', target: menuTarget }, actions)).toBe(false);
      expect(actions.closeOverlay).not.toHaveBeenCalled();
    });
  });

  describe('guards and helpers', () => {
    it('ignores defaultPrevented or altKey events', () => {
      expect(handleShortcutKey({ key: ' ', defaultPrevented: true }, actions)).toBe(false);
      expect(handleShortcutKey({ key: ' ', altKey: true }, actions)).toBe(false);
      expect(actions.togglePlay).not.toHaveBeenCalled();
    });

    it('correctly detects interactive and menu elements in helper functions', () => {
      expect(isInteractive(null)).toBe(false);
      expect(isMenuOrDialog(null)).toBe(false);

      const div = { closest: () => null } as unknown as HTMLElement;
      expect(isInteractive(div)).toBe(false);
      expect(isMenuOrDialog(div)).toBe(false);
    });
  });
});
