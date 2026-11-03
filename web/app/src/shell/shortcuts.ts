/** One line of the keyboard shortcut sheet. */
export interface Shortcut {
  /**
   * The ways to do it, each a set of keys pressed together ("Mod" is ⌘ on
   * Apple devices and Ctrl elsewhere): [['/'], ['Mod', 'F']] reads "/ or ⌘ F".
   */
  keys: string[][];
  label: string;
}

/**
 * What useKeyboardShortcuts answers, for the ? sheet. Kept next to the
 * handler's own comment so a new shortcut is added to both.
 */
export const SHORTCUT_GROUPS: { title: string; shortcuts: Shortcut[] }[] = [
  {
    title: 'Playback',
    shortcuts: [
      { keys: [['Space']], label: 'Play or pause' },
      { keys: [['Mod', '→']], label: 'Next song' },
      { keys: [['Mod', '←']], label: 'Previous song' },
      { keys: [['Mod', '↑']], label: 'Volume up' },
      { keys: [['Mod', '↓']], label: 'Volume down' },
    ],
  },
  {
    title: 'Navigation',
    shortcuts: [
      { keys: [['/'], ['Mod', 'F']], label: 'Search' },
      { keys: [['Esc']], label: 'Close the full-screen player or side panel' },
      { keys: [['?']], label: 'Show keyboard shortcuts' },
    ],
  },
];

/** Whether ⌘ or Ctrl is this device's command key. */
export function isApple(platform: string): boolean {
  return /mac|iphone|ipad|ipod/i.test(platform);
}

/** A key as this device labels it. */
export function keyLabel(key: string, apple: boolean): string {
  return key === 'Mod' ? (apple ? '⌘' : 'Ctrl') : key;
}
