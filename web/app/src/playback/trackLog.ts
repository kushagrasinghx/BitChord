import { create } from 'zustand';

/**
 * The web counterpart of the app's TrackLog: every step of resolving and
 * playing a track, tagged with the track it's about, for the debug panel
 * (prompt §5.2). Kept in memory only, capped.
 */
export type LogLevel = 'debug' | 'info' | 'warn' | 'error';

export interface LogLine {
  id: number;
  /** ms since the page loaded (performance.now), for readable relative timing. */
  t: number;
  level: LogLevel;
  /** The videoId the line is about, so a track's lines can be filtered out of the stream. */
  about: string | null;
  message: string;
}

const MAX_LINES = 1500;

interface TrackLogState {
  lines: LogLine[];
  clear: () => void;
}

export const useTrackLog = create<TrackLogState>()((set) => ({
  lines: [],
  clear: () => set({ lines: [] }),
}));

let nextId = 0;

function push(level: LogLevel, message: string, about: string | null) {
  const line: LogLine = { id: nextId++, t: performance.now(), level, about, message };
  useTrackLog.setState((s) => ({
    lines: s.lines.length >= MAX_LINES ? [...s.lines.slice(-MAX_LINES + 1), line] : [...s.lines, line],
  }));
  const tag = about ? `[${about}] ` : '';
  const out = level === 'error' ? console.error : level === 'warn' ? console.warn : console.debug;
  out(`BitChord ${tag}${message}`);
}

export const trackLog = {
  d: (message: string, about: string | null = null) => push('debug', message, about),
  i: (message: string, about: string | null = null) => push('info', message, about),
  w: (message: string, about: string | null = null) => push('warn', message, about),
  e: (message: string, about: string | null = null) => push('error', message, about),
};

/** Runs [block], logging how long it took whatever the outcome (StreamResolver's `timed`). */
export async function timed<T>(label: string, about: string | null, block: () => Promise<T>): Promise<T> {
  const start = performance.now();
  try {
    return await block();
  } finally {
    trackLog.d(`TIMING ${label}: ${Math.round(performance.now() - start)}ms`, about);
  }
}
