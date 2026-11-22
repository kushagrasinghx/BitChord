export type MenuPlacement = 'above-start' | 'above-end' | 'below-end';

/** The panel's distance from the window's edges (IosMenuPopover's margin). */
export const MENU_MARGIN = 8;
/** Between a button and the menu hung off it. */
const GAP = 6;
/** The tallest anchor counted as a row rather than a card. */
const ROW_HEIGHT = 72;

interface Box {
  left: number;
  top: number;
  right: number;
  bottom: number;
}

/**
 * Where the menu panel goes and the corner it grows from. Hung off a button,
 * it lines up with the button's edge as IosMenuPopover does; opened at a
 * point (a right-click), its corner sits on the pointer, flipped to whichever
 * side has room.
 */
export function placeMenu(
  anchor: Box,
  size: { width: number; height: number },
  viewport: { width: number; height: number },
  placement: MenuPlacement,
  point?: { x: number; y: number },
): { left: number; top: number; maxHeight: number; origin: string } {
  const clampX = (x: number) => Math.min(Math.max(MENU_MARGIN, x), Math.max(MENU_MARGIN, viewport.width - MENU_MARGIN - size.width));
  if (point) {
    // A row (or the player bar) is short enough to hang the menu off, above or
    // below it, as a held row's menu hangs on the phone; a card is not.
    const row = anchor.bottom - anchor.top <= ROW_HEIGHT;
    const below = row ? anchor.bottom + GAP : point.y;
    const above = row ? anchor.top - GAP : point.y;
    const right = point.x + size.width > viewport.width - MENU_MARGIN;
    const roomBelow = viewport.height - MENU_MARGIN - below;
    const roomAbove = Math.min(above, viewport.height - MENU_MARGIN) - MENU_MARGIN;
    // Below if it fits, else above if it fits, else the roomier side, scrolling there: never over the row.
    const up = size.height > roomBelow && (size.height <= roomAbove || roomAbove > roomBelow);
    const maxHeight = Math.max(0, up ? roomAbove : roomBelow);
    const height = Math.min(size.height, maxHeight);
    return {
      left: clampX(right ? point.x - size.width : point.x),
      top: Math.max(MENU_MARGIN, up ? MENU_MARGIN + roomAbove - height : below),
      maxHeight,
      origin: `${right ? 'right' : 'left'} ${up ? 'bottom' : 'top'}`,
    };
  }
  if (placement === 'below-end') {
    const top = anchor.bottom + GAP;
    return { left: clampX(anchor.right - size.width), top, maxHeight: viewport.height - top - MENU_MARGIN, origin: 'right top' };
  }
  const maxHeight = anchor.top - GAP - MENU_MARGIN;
  const top = Math.max(MENU_MARGIN, anchor.top - GAP - size.height);
  return placement === 'above-start'
    ? { left: clampX(anchor.left), top, maxHeight, origin: 'left bottom' }
    : { left: clampX(anchor.right - size.width), top, maxHeight, origin: 'right bottom' };
}
