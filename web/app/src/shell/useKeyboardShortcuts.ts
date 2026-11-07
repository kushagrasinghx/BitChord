import { useEffect } from 'react';
import { useNavigate } from 'react-router';
import { LIKED_MUSIC_PLAYLIST } from '@/data/music/library';
import { setTrackLiked, type LikeStatus } from '@/data/music/api';
import { queryClient } from '@/lib/queryClient';
import { getPlayhead, usePlayer } from '@/state/player';
import { useUi } from '@/state/ui';
import { SHORTCUT_DEFINITIONS, matchesBinding } from './keybinds';

/** Typing in a field shouldn't trigger the player. */
function typing(target: EventTarget | null): boolean {
  const element = target as HTMLElement | null;
  if (!element) return false;
  return element.isContentEditable || /^(INPUT|TEXTAREA|SELECT)$/.test(element.tagName);
}

const likeKey = (videoId: string) => ['music', 'like', videoId] as const;

/**
 * Toggles the now-playing track's favorite (YouTube Music like) from outside
 * React, mirroring useSetLiked's optimistic cache update in data/music/queries.
 */
function toggleFavorite(videoId: string, liked: boolean) {
  const key = likeKey(videoId);
  const previous = queryClient.getQueryData<LikeStatus>(key);
  queryClient.setQueryData<LikeStatus>(key, liked ? 'LIKE' : 'INDIFFERENT');
  void setTrackLiked(videoId, liked)
    .catch(() => queryClient.setQueryData<LikeStatus>(key, previous))
    .finally(() => {
      void queryClient.invalidateQueries({ queryKey: ['music', 'library', 'songs'] });
      void queryClient.invalidateQueries({ queryKey: ['music', 'playlist', LIKED_MUSIC_PLAYLIST] });
      void queryClient.invalidateQueries({ queryKey: ['music', 'playlist', `VL${LIKED_MUSIC_PLAYLIST}`] });
    });
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
 * The web player's shortcuts: Space play/pause and the rebindable playback
 * shortcuts in ./keybinds (Shift+N / End next, Shift+P / Home previous, ←/→
 * seek, ↑/↓ volume, F full screen, L favorite, Q queue, R repeat by default),
 * alongside the web player's own ⌘/Ctrl+→ / ← next/previous, ⌘/Ctrl+↑ / ↓
 * volume, ⌘/Ctrl+F or "/" focuses search, and Esc closes the full-screen
 * player, else the side panel.
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

      // Space is the default play/pause binding, but a focused button answers
      // it natively — so only rebind-aware handling below, never double-fire.
      if (event.key === ' ' && !command) {
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
      if (command) {
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
        return;
      }

      const key = event.key.length === 1 ? event.key.toLowerCase() : event.key;
      const pressed = (action: string) =>
        SHORTCUT_DEFINITIONS.find((d) => d.action === action)?.bindings.some((binding) =>
          matchesBinding({ key, shiftKey: event.shiftKey }, binding),
        );

      if (pressed('next')) {
        event.preventDefault();
        return player.next();
      }
      if (pressed('previous')) {
        event.preventDefault();
        return player.previous();
      }
      // Seek ±10s; the store clamps to the track's bounds.
      if (pressed('seekForward')) {
        event.preventDefault();
        return player.seek((getPlayhead()?.sec ?? 0) + 10);
      }
      if (pressed('seekBackward')) {
        event.preventDefault();
        return player.seek((getPlayhead()?.sec ?? 0) - 10);
      }
      // Volume ±0.1.
      if (pressed('volumeUp')) {
        event.preventDefault();
        return player.setVolume(player.volume + 0.1);
      }
      if (pressed('volumeDown')) {
        event.preventDefault();
        return player.setVolume(player.volume - 0.1);
      }
      if (pressed('fullScreen')) {
        // Opens the now-playing (full-screen) view.
        event.preventDefault();
        const ui = useUi.getState();
        return ui.setFullScreen(!ui.fullScreen);
      }
      if (pressed('favorite')) {
        const videoId = player.nowPlaying?.videoId;
        if (!videoId) return;
        event.preventDefault();
        const liked = queryClient.getQueryData<LikeStatus>(likeKey(videoId)) === 'LIKE';
        return toggleFavorite(videoId, !liked);
      }
      if (pressed('queue')) {
        event.preventDefault();
        return useUi.getState().toggleSidePanel('up-next');
      }
      if (pressed('repeat')) {
        event.preventDefault();
        return player.cycleRepeat();
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [navigate]);
}
