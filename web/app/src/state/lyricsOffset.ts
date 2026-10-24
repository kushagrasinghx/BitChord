import { create } from 'zustand';

interface LyricsOffsetState {
  offsetMs: number;
  setOffset: (offsetMs: number) => void;
  adjustOffset: (deltaMs: number) => void;
  reset: () => void;
}

const KEY = 'bitchord:lyrics_offset';

function loadOffset(): number {
  try {
    const val = localStorage.getItem(KEY);
    return val ? Number(val) || 0 : 0;
  } catch {
    return 0;
  }
}

export const useLyricsOffset = create<LyricsOffsetState>()((set) => ({
  offsetMs: loadOffset(),
  setOffset: (offsetMs) => {
    localStorage.setItem(KEY, String(offsetMs));
    set({ offsetMs });
  },
  adjustOffset: (deltaMs) => {
    set((state) => {
      const next = state.offsetMs + deltaMs;
      localStorage.setItem(KEY, String(next));
      return { offsetMs: next };
    });
  },
  reset: () => {
    localStorage.removeItem(KEY);
    set({ offsetMs: 0 });
  },
}));
