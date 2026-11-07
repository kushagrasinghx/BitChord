import { create } from 'zustand';

const KEY = 'bitchord:pinned-playlists';
/** Spotify allows four pins; so does the sidebar here. */
export const MAX_PINS = 4;

function load(): string[] {
  try {
    const parsed: unknown = JSON.parse(localStorage.getItem(KEY) ?? '[]');
    return Array.isArray(parsed) ? parsed.filter((id): id is string => typeof id === 'string').slice(0, MAX_PINS) : [];
  } catch {
    return [];
  }
}

interface PinState {
  /** Playlist ids pinned to the top of the sidebar, oldest pin first. Kept in this browser: YouTube has no pins. */
  pinned: string[];
  /** Pins or unpins; false when pinning is refused because MAX_PINS are pinned already. */
  toggle: (id: string) => boolean;
}

export const usePins = create<PinState>()((set, get) => ({
  pinned: load(),
  toggle: (id) => {
    const { pinned } = get();
    const next = pinned.includes(id) ? pinned.filter((p) => p !== id) : pinned.length < MAX_PINS ? [...pinned, id] : null;
    if (!next) return false;
    try {
      localStorage.setItem(KEY, JSON.stringify(next));
    } catch {
      // Storage unavailable: the pin lasts for this visit.
    }
    set({ pinned: next });
    return true;
  },
}));
