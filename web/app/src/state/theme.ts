import { create } from 'zustand';

/** AppSettings.ThemeMode: follow the system, or hold light or dark. */
export type ThemeMode = 'system' | 'light' | 'dark';

export const THEME_MODES: { value: ThemeMode; label: string }[] = [
  { value: 'system', label: 'System' },
  { value: 'light', label: 'Light' },
  { value: 'dark', label: 'Dark' },
];

// index.html reads this key too, to put the theme on before the first paint.
const KEY = 'bitchord:theme';

function load(): ThemeMode {
  try {
    const saved = localStorage.getItem(KEY);
    if (saved === 'system' || saved === 'light' || saved === 'dark') return saved;
  } catch {
    // Storage blocked: the default stands.
  }
  // Dark, as the phone ships (AppSettings.themeMode).
  return 'dark';
}

/**
 * tokens.css reads data-theme on <html>; with none it follows the system's
 * appearance through prefers-color-scheme.
 */
function apply(mode: ThemeMode) {
  if (mode === 'system') delete document.documentElement.dataset.theme;
  else document.documentElement.dataset.theme = mode;
}

interface ThemeSettings {
  mode: ThemeMode;
  setMode: (mode: ThemeMode) => void;
}

export const useTheme = create<ThemeSettings>()((set) => ({
  mode: load(),
  setMode: (mode) => {
    set({ mode });
    apply(mode);
    try {
      localStorage.setItem(KEY, mode);
    } catch {
      // Storage blocked: the theme holds for this visit.
    }
  },
}));

apply(useTheme.getState().mode);
