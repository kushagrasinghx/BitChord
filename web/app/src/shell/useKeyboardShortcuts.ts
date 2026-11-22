import { useEffect } from 'react';
import { useNavigate } from 'react-router';
import { getAudio, getPlayhead, usePlayer } from '@/state/player';
import { useUi } from '@/state/ui';
import { useShortcutsSheet } from './ShortcutsSheet';

/** Typing in a field shouldn't trigger the player. */
function typing(target: EventTarget | null): boolean {
  const element = target as HTMLElement | null;
  if (!element) return false;
  return element.isContentEditable || /^(INPUT|TEXTAREA|SELECT)$/.test(element.tagName);
}

/** Seconds ← and → move the playhead, as YouTube Music's own web player steps. */
export const SEEK_STEP = 10;

/**
 * Whether a bare arrow or letter belongs to what has focus rather than to the
 * player: a field, or a widget that steps with the arrows (a slider, a menu,
 * a list box, tabs), or a dialog over the app (the player's own sheet aside).
 */
export function keyBelongsToFocus(target: EventTarget | null): boolean {
  const element = target as HTMLElement | null;
  if (typing(element)) return true;
  return !!element?.closest?.(
    '[role="slider"], [role="menu"], [role="menubar"], [role="listbox"], [role="tablist"], [role="radiogroup"], [role="grid"], [role="tree"], [role="dialog"]:not([data-full-screen])',
  );
}

function seekBy(seconds: number) {
  const audio = getAudio();
  if (!audio || !Number.isFinite(audio.duration)) return;
  // A loop mix plays from its decks with the element paused: its own playhead is the position.
  const now = getPlayhead()?.sec ?? audio.currentTime;
  usePlayer.getState().seek(Math.min(audio.duration, Math.max(0, now + seconds)));
}

function focusSearch(navigate: ReturnType<typeof useNavigate>) {
  const field = document.querySelector<HTMLInputElement>('input[type="search"]');
  if (field) return field.focus();
  navigate('/search');
  // The search page is lazy: focus its field once it has rendered (for up to 3 s).
  const started = performance.now();
  const attempt = () => {
    const field = document.querySelector<HTMLInputElement>('input[type="search"]');
    if (field) field.focus();
    else if (performance.now() - started < 3000) setTimeout(attempt, 50);
  };
  attempt();
}

/**
 * The web player's shortcuts, which BitChord matches (report §8): Space
 * play/pause; ⌘/Ctrl+→ / ← next/previous; ⌘/Ctrl+↑ / ↓ volume; ⌘/Ctrl+F or
 * "/" focuses search; Esc closes the full-screen player, else the side panel.
 *
 * And a desktop app's: ← / → back and forward 10 seconds, Shift+← / → the
 * previous and next song, M mute, L lyrics, Q the queue (the full-screen
 * player takes L and Q for its own panes while it is up), and ? the sheet
 * listing all of them (ShortcutsSheet.tsx).
 */
export function useKeyboardShortcuts() {
  const navigate = useNavigate();
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.defaultPrevented || event.altKey) return;
      const player = usePlayer.getState();
      const command = event.metaKey || event.ctrlKey;

      if (command && (event.key === 'f' || event.key === 'F')) {
        event.preventDefault();
        return focusSearch(navigate);
      }
      if (typing(event.target)) return;

      if (event.key === ' ' && !command) {
        // A focused button already answers Space itself.
        if ((event.target as HTMLElement | null)?.closest('button, a, [role="slider"]')) return;
        event.preventDefault();
        return player.togglePlay();
      }
      if (event.key === '/' && !command) {
        event.preventDefault();
        return focusSearch(navigate);
      }
      if (event.key === 'Escape') {
        const ui = useUi.getState();
        // A menu or dialog over the player closes first, on its own.
        if ((event.target as HTMLElement | null)?.closest('[role="menu"], [role="dialog"]:not([data-full-screen])')) return;
        if (ui.fullScreen) ui.setFullScreen(false);
        else if (ui.sidePanel) ui.closeSidePanel();
        return;
      }
      if (!command) {
        if (keyBelongsToFocus(event.target)) return;
        const ui = useUi.getState();
        const run: (() => void) | undefined = {
          ArrowLeft: () => (event.shiftKey ? player.previous() : seekBy(-SEEK_STEP)),
          ArrowRight: () => (event.shiftKey ? player.next() : seekBy(SEEK_STEP)),
          m: player.toggleMute,
          M: player.toggleMute,
          l: ui.fullScreen ? undefined : () => ui.toggleSidePanel('lyrics'),
          L: ui.fullScreen ? undefined : () => ui.toggleSidePanel('lyrics'),
          q: ui.fullScreen ? undefined : () => ui.toggleSidePanel('up-next'),
          Q: ui.fullScreen ? undefined : () => ui.toggleSidePanel('up-next'),
          '?': () => useShortcutsSheet.getState().toggle(),
        }[event.key];
        if (!run) return;
        event.preventDefault();
        return run();
      }
      switch (event.key) {
        case 'ArrowRight':
          event.preventDefault();
          return player.next();
        case 'ArrowLeft':
          event.preventDefault();
          return player.previous();
        case 'ArrowUp':
          event.preventDefault();
          return player.setVolume(player.volume + 0.1);
        case 'ArrowDown':
          event.preventDefault();
          return player.setVolume(player.volume - 0.1);
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [navigate]);
}
