import { useQuery } from '@tanstack/react-query';
import { useSyncExternalStore } from 'react';
import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { useSpotifySignedIn } from '@/extension/useSpotifyAuth';
import { usePlayer } from '@/state/player';
import { fetchCanvas } from './api';

/** Spotify settings kept by this page: whether to show Canvas at all. */
export const useSpotifySettings = create<{ canvas: boolean; setCanvas: (on: boolean) => void }>()(
  persist((set) => ({ canvas: true, setCanvas: (canvas) => set({ canvas }) }), { name: 'bitchord:spotify', version: 1 }),
);

const reducedMotion = typeof matchMedia === 'function' ? matchMedia('(prefers-reduced-motion: reduce)') : null;
export function useReducedMotion(): boolean {
  return useSyncExternalStore(
    (onChange) => {
      reducedMotion?.addEventListener('change', onChange);
      return () => reducedMotion?.removeEventListener('change', onChange);
    },
    () => reducedMotion?.matches ?? false,
  );
}

/** Save-Data, or a cellular link where the browser says so (Network Information API, Chromium only). */
export function metered(): boolean {
  const connection = (navigator as Navigator & { connection?: { saveData?: boolean; type?: string } }).connection;
  return !!connection?.saveData || connection?.type === 'cellular';
}

/**
 * The playing track's Canvas (prompt §7.1): looked up only when it can be
 * shown — signed in to Spotify, the setting on, no reduced motion, and not on
 * a metered connection — and only once per track.
 */
export function useCanvas(): string | null {
  const signedIn = useSpotifySignedIn();
  const enabled = useSpotifySettings((s) => s.canvas);
  const still = useReducedMotion();
  const track = usePlayer((s) => s.nowPlaying);
  const allowed = signedIn && enabled && !still && !metered();
  const query = useQuery({
    queryKey: ['spotify', 'data', 'canvas', track?.videoId ?? ''],
    queryFn: () => fetchCanvas(track!),
    enabled: allowed && !!track?.title,
    staleTime: Infinity,
    gcTime: 30 * 60_000,
    retry: false,
  });
  return allowed && track ? (query.data ?? null) : null;
}
