import { useEffect } from 'react';
import { useNavigate } from 'react-router';
import { usePlayer } from '@/state/player';
import { useUi } from '@/state/ui';

/** Typing in a field shouldn't trigger the player. */
function typing(target: EventTarget | null): boolean {
  const element = target as HTMLElement | null;
  if (!element) return false;
  return element.isContentEditable || /^(INPUT|TEXTAREA|SELECT)$/.test(element.tagName);
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
      if (!command) return;
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
