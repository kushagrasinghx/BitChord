import type { MouseEvent } from 'react';
import { create } from 'zustand';
import type { MenuPlacement } from '@/components/Menu';
import type { MusicItem, Track } from '@/data/music/types';
import { toTrack } from './play';

/** What a menu acts on: a song (SongActionsMenu.kt), or an album or playlist (BrowseActionsMenu.kt). */
export type MenuTarget =
  | {
      kind: 'track';
      track: Track;
      /** The player's own menu: it adds the sleep timer and AutoMix, as the phone's does. */
      player?: boolean;
    }
  | { kind: 'browse'; item: MusicItem };

export interface OpenMenu {
  target: MenuTarget;
  anchor: HTMLElement;
  placement?: MenuPlacement;
  point?: { x: number; y: number };
  lift?: HTMLElement;
  /** The ⋯ button that opened it, so the button can show it's open. */
  owner?: string;
  /** Tells each opening apart: a right-click elsewhere opens a fresh menu. */
  key: number;
}

/** What a menu row opens once the menu itself has closed. */
export type DialogRequest = { kind: 'playlists'; tracks: Track[] } | { kind: 'info'; track: Track };
export type MenuDialog = DialogRequest & {
  owner?: string;
  /** Where focus goes back to once it closes. */
  returnFocus: HTMLElement;
};

interface ActionMenuState {
  menu: OpenMenu | null;
  dialog: MenuDialog | null;
  open: (menu: Omit<OpenMenu, 'key'>) => void;
  close: () => void;
  openDialog: (dialog: DialogRequest) => void;
  closeDialog: () => void;
}

let opened = 0;

/**
 * The app's one song/album menu, wherever it was asked for: a row's ⋯, the
 * player's ⋯, or a right-click. One at a time, as on the phone, and drawn by
 * ActionMenuHost.
 */
export const useActionMenu = create<ActionMenuState>()((set, get) => ({
  menu: null,
  dialog: null,
  open: (menu) => set({ menu: { ...menu, key: ++opened } }),
  close: () => set({ menu: null }),
  openDialog: (dialog) => {
    const menu = get().menu;
    if (!menu) return;
    set({ menu: null, dialog: { ...dialog, owner: menu.owner, returnFocus: menu.anchor } });
  },
  closeDialog: () => {
    get().dialog?.returnFocus.focus();
    set({ dialog: null });
  },
}));

/**
 * Share: the system's share sheet where there is one, as the phone's Share
 * opens Android's; else the link is copied. Says which, so the menu can show
 * "Copied" rather than vanish without a word.
 */
export async function shareLink(title: string, url: string): Promise<'shared' | 'copied'> {
  if (navigator.share) {
    try {
      await navigator.share({ title, url });
      return 'shared';
    } catch (error) {
      if ((error as Error).name === 'AbortError') return 'shared';
    }
  }
  await navigator.clipboard.writeText(url);
  return 'copied';
}

/** Whether the ⋯ button [owner] has its menu, or a dialog from it, up. */
export const useMenuOwned = (owner: string) => useActionMenu((s) => s.menu?.owner === owner || s.dialog?.owner === owner);

/**
 * A right-click handler that opens [target]'s menu at the pointer, lifting
 * the element it's on (ContextClick.kt: the window's secondary click opens
 * what a hold opens on the phone). Shift keeps the browser's own menu.
 *
 *   <div onContextMenu={contextMenu({ kind: 'track', track })}>
 */
export function contextMenu(target: MenuTarget | null) {
  return (event: MouseEvent<HTMLElement>) => {
    if (!target || event.shiftKey) return;
    event.preventDefault();
    event.stopPropagation();
    const element = event.currentTarget;
    useActionMenu.getState().open({ target, anchor: element, lift: element, point: { x: event.clientX, y: event.clientY } });
  };
}

/** A card's right-click: songs and videos get the song's menu, albums and playlists the release's, anything else none. */
export function itemContextMenu(item: MusicItem) {
  const playable = item.kind === 'song' || item.kind === 'video';
  const release = item.kind === 'album' || item.kind === 'playlist';
  return contextMenu(playable ? { kind: 'track', track: toTrack(item) } : release ? { kind: 'browse', item } : null);
}
