import { useEffect } from 'react';
import { useNavigate } from 'react-router';
import { getPlayhead, usePlayer } from '@/state/player';
import { useUi } from '@/state/ui';
import { handleShortcutKey, type ShortcutActions } from './keyboardShortcuts';

export { isInteractive, isMenuOrDialog, isTyping } from './keyboardShortcuts';
export type { ShortcutActions, ShortcutKeyEvent } from './keyboardShortcuts';

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
 * The web player's shortcuts:
 * - Play/Pause: Space, K
 * - Next / Previous: ⌘/Ctrl+→ / ←, Shift+N / P
 * - Seeking: J / L (10s), bare Left / Right arrows (5s)
 * - Volume: ⌘/Ctrl+↑ / ↓
 * - Mute: M
 * - Shuffle / Repeat: S, R
 * - Search: ⌘/Ctrl+F or "/"
 * - Close: Esc
 */
export function useKeyboardShortcuts() {
  const navigate = useNavigate();

  useEffect(() => {
    const actions: ShortcutActions = {
      togglePlay: () => usePlayer.getState().togglePlay(),
      next: () => usePlayer.getState().next(),
      previous: () => usePlayer.getState().previous(),
      seekBy: (deltaSeconds) => {
        const playhead = getPlayhead();
        if (playhead) usePlayer.getState().seek(playhead.sec + deltaSeconds);
      },
      setVolumeDelta: (delta) => {
        const player = usePlayer.getState();
        const nextVol = Math.round(Math.min(1, Math.max(0, player.volume + delta)) * 100) / 100;
        player.setVolume(nextVol);
      },
      toggleMute: () => usePlayer.getState().toggleMute(),
      toggleShuffle: () => usePlayer.getState().toggleShuffle(),
      cycleRepeat: () => usePlayer.getState().cycleRepeat(),
      focusSearch: () => focusSearch(navigate),
      closeOverlay: () => {
        const ui = useUi.getState();
        if (ui.fullScreen) ui.setFullScreen(false);
        else if (ui.sidePanel) ui.closeSidePanel();
      },
    };

    const onKey = (event: KeyboardEvent) => {
      if (handleShortcutKey(event, actions)) {
        event.preventDefault();
      }
    };

    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [navigate]);
}
