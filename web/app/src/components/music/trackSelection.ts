/** A track list's selection: the songs picked, and where a Shift-click range starts. */
export interface TrackSelection {
  ids: ReadonlySet<string>;
  anchor: string | null;
}

/**
 * The next selection after a click on [id] in [order], as the queue's
 * (UpNext.tsx): Shift adds the run from the anchor, anything else toggles the
 * one row and makes it the anchor.
 */
export function nextSelection(selection: TrackSelection | null, order: string[], id: string, shift: boolean): TrackSelection {
  const ids = new Set(selection?.ids);
  const from = selection?.anchor ? order.indexOf(selection.anchor) : -1;
  const to = order.indexOf(id);
  if (shift && from >= 0 && to >= 0) {
    for (const key of order.slice(Math.min(from, to), Math.max(from, to) + 1)) ids.add(key);
    return { ids, anchor: selection!.anchor };
  }
  if (ids.has(id)) ids.delete(id);
  else ids.add(id);
  return { ids, anchor: id };
}
