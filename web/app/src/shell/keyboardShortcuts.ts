export interface ShortcutActions {
  togglePlay: () => void;
  next: () => void;
  previous: () => void;
  seekBy: (deltaSeconds: number) => void;
  setVolumeDelta: (delta: number) => void;
  toggleMute: () => void;
  toggleShuffle: () => void;
  cycleRepeat: () => void;
  focusSearch: () => void;
  closeOverlay: () => void;
}

export interface ShortcutKeyEvent {
  key: string;
  metaKey?: boolean;
  ctrlKey?: boolean;
  altKey?: boolean;
  shiftKey?: boolean;
  defaultPrevented?: boolean;
  target?: EventTarget | null;
}

/** Typing in an input, textarea, or contenteditable field shouldn't trigger player shortcuts. */
export function isTyping(target: EventTarget | null | undefined): boolean {
  const element = target as HTMLElement | null;
  if (!element) return false;
  return element.isContentEditable || /^(INPUT|TEXTAREA|SELECT)$/.test(element.tagName);
}

/** Elements with their own keyboard behavior (sliders, buttons, links) shouldn't be overridden by basic keys. */
export function isInteractive(target: EventTarget | null | undefined): boolean {
  const element = target as HTMLElement | null;
  if (!element || typeof element.closest !== 'function') return false;
  return !!element.closest('button, a, [role="slider"]');
}

/** Popups, menus or non-fullscreen dialogs manage Escape themselves. */
export function isMenuOrDialog(target: EventTarget | null | undefined): boolean {
  const element = target as HTMLElement | null;
  if (!element || typeof element.closest !== 'function') return false;
  return !!element.closest('[role="menu"], [role="dialog"]:not([data-full-screen])');
}

/**
 * Pure shortcut handler dispatching supported keys to player and shell actions.
 * Returns true if the event was handled and default should be prevented.
 */
export function handleShortcutKey(event: ShortcutKeyEvent, actions: ShortcutActions): boolean {
  if (event.defaultPrevented || event.altKey) return false;

  const command = !!(event.metaKey || event.ctrlKey);

  // Search shortcut with Command/Ctrl works everywhere
  if (command && (event.key === 'f' || event.key === 'F')) {
    actions.focusSearch();
    return true;
  }

  // Never trigger player controls while the user is typing in a text field
  if (isTyping(event.target)) return false;

  // Global command combinations
  if (command) {
    switch (event.key) {
      case 'ArrowRight':
        actions.next();
        return true;
      case 'ArrowLeft':
        actions.previous();
        return true;
      case 'ArrowUp':
        actions.setVolumeDelta(0.1);
        return true;
      case 'ArrowDown':
        actions.setVolumeDelta(-0.1);
        return true;
      default:
        return false;
    }
  }

  // Shift combinations (YouTube Music style navigation)
  if (event.shiftKey) {
    if (event.key === 'N' || event.key === 'n') {
      actions.next();
      return true;
    }
    if (event.key === 'P' || event.key === 'p') {
      actions.previous();
      return true;
    }
    return false;
  }

  // Single-key shortcuts
  if (event.key === ' ') {
    if (isInteractive(event.target)) return false;
    actions.togglePlay();
    return true;
  }

  if (event.key === 'k' || event.key === 'K') {
    actions.togglePlay();
    return true;
  }

  if (event.key === 'm' || event.key === 'M') {
    actions.toggleMute();
    return true;
  }

  if (event.key === 'j' || event.key === 'J') {
    actions.seekBy(-10);
    return true;
  }

  if (event.key === 'l' || event.key === 'L') {
    actions.seekBy(10);
    return true;
  }

  if (event.key === 's' || event.key === 'S') {
    actions.toggleShuffle();
    return true;
  }

  if (event.key === 'r' || event.key === 'R') {
    actions.cycleRepeat();
    return true;
  }

  if (event.key === '/') {
    actions.focusSearch();
    return true;
  }

  if (event.key === 'Escape') {
    if (isMenuOrDialog(event.target)) return false;
    actions.closeOverlay();
    return true;
  }

  if (event.key === 'ArrowRight') {
    if (isInteractive(event.target)) return false;
    actions.seekBy(5);
    return true;
  }

  if (event.key === 'ArrowLeft') {
    if (isInteractive(event.target)) return false;
    actions.seekBy(-5);
    return true;
  }

  return false;
}
