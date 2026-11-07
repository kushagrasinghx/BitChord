/** Where a right-click happened: a context menu opens there instead of from a button. */
export interface MenuPoint {
  x: number;
  y: number;
}

/**
 * A context menu's place: its top-left corner at the click, moved left or up
 * as far as it has to be to stay 8px inside the window.
 */
export function pointPosition(point: MenuPoint, size: { width: number; height: number }, viewport: { width: number; height: number }) {
  const margin = 8;
  return {
    left: Math.max(margin, Math.min(point.x, viewport.width - size.width - margin)),
    top: Math.max(margin, Math.min(point.y, viewport.height - size.height - margin)),
    maxHeight: viewport.height - 2 * margin,
  };
}
