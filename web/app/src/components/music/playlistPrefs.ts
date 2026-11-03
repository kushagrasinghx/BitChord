import { useState } from 'react';
import { TRACK_SORTS, type TrackSort } from './playlistView';

const COMPACT_KEY = 'bitchord:playlist-compact';
const SORT_KEY = 'bitchord:playlist-sort';

function read<T>(key: string, fallback: T): T {
  try {
    const raw = localStorage.getItem(key);
    return raw == null ? fallback : (JSON.parse(raw) as T);
  } catch {
    return fallback;
  }
}

function write(key: string, value: unknown): void {
  try {
    localStorage.setItem(key, JSON.stringify(value));
  } catch {
    // Private mode or full storage: the choice lasts for this visit only.
  }
}

/** List or compact rows: one choice for every playlist, as Spotify keeps it. */
export function useCompactView(): [boolean, (compact: boolean) => void] {
  const [compact, setCompact] = useState(() => read<unknown>(COMPACT_KEY, false) === true);
  return [compact, (next) => (setCompact(next), write(COMPACT_KEY, next))];
}

/** Each playlist remembers its own sort, as Spotify does. */
export function usePlaylistSort(playlistId: string): [TrackSort, (sort: TrackSort) => void] {
  const [sorts, setSorts] = useState(() => read<Record<string, TrackSort>>(SORT_KEY, {}));
  const saved = sorts[playlistId];
  const sort = TRACK_SORTS.some((s) => s.value === saved) ? saved! : 'custom';
  return [
    sort,
    (next) => {
      const all = { ...read<Record<string, TrackSort>>(SORT_KEY, {}) };
      if (next === 'custom') delete all[playlistId];
      else all[playlistId] = next;
      write(SORT_KEY, all);
      setSorts(all);
    },
  ];
}
