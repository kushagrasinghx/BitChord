/**
 * The playback shortcuts (Settings › Keybinds): one definition per action,
 * with its default bindings. Listed for reference; not user-rebindable.
 */

export type ShortcutAction =
  | 'playPause'
  | 'next'
  | 'previous'
  | 'seekForward'
  | 'seekBackward'
  | 'volumeUp'
  | 'volumeDown'
  | 'fullScreen'
  | 'favorite'
  | 'queue'
  | 'repeat';

export interface ShortcutBinding {
  /** KeyboardEvent.key as pressed, normalized: lowercase letters, named keys kept. */
  key: string;
  /** Whether Shift must be held. */
  shift: boolean;
}

export interface ShortcutDefinition {
  action: ShortcutAction;
  label: string;
  bindings: ShortcutBinding[];
}

export const SHORTCUT_DEFINITIONS: ShortcutDefinition[] = [
  { action: 'playPause', label: 'Play / Pause', bindings: [{ key: ' ', shift: false }] },
  { action: 'next', label: 'Next Track', bindings: [{ key: 'n', shift: true }, { key: 'End', shift: false }] },
  { action: 'previous', label: 'Previous Track', bindings: [{ key: 'p', shift: true }, { key: 'Home', shift: false }] },
  { action: 'seekForward', label: 'Seek Forward 10s', bindings: [{ key: 'ArrowRight', shift: false }] },
  { action: 'seekBackward', label: 'Seek Backward 10s', bindings: [{ key: 'ArrowLeft', shift: false }] },
  { action: 'volumeUp', label: 'Volume Up', bindings: [{ key: 'ArrowUp', shift: false }] },
  { action: 'volumeDown', label: 'Volume Down', bindings: [{ key: 'ArrowDown', shift: false }] },
  { action: 'fullScreen', label: 'Now Playing (Full Screen)', bindings: [{ key: 'f', shift: false }] },
  { action: 'favorite', label: 'Add to Favorites', bindings: [{ key: 'l', shift: false }] },
  { action: 'queue', label: 'Current Queue (Up Next)', bindings: [{ key: 'q', shift: false }] },
  { action: 'repeat', label: 'Cycle Repeat Mode', bindings: [{ key: 'r', shift: false }] },
];

/** Human-readable label for one binding, e.g. "Shift+N", "Space", "→". */
export function bindingLabel(binding: ShortcutBinding): string {
  const key = keyLabel(binding.key);
  return binding.shift ? `Shift+${key}` : key;
}

function keyLabel(key: string): string {
  switch (key) {
    case ' ':
      return 'Space';
    case 'ArrowUp':
      return '↑';
    case 'ArrowDown':
      return '↓';
    case 'ArrowLeft':
      return '←';
    case 'ArrowRight':
      return '→';
    default:
      return key.length === 1 ? key.toUpperCase() : key;
  }
}

/** Whether a KeyboardEvent matches a binding (key already normalized). */
export function matchesBinding(event: { key: string; shiftKey: boolean }, binding: ShortcutBinding): boolean {
  return event.key === binding.key && event.shiftKey === binding.shift;
}
