import { Pin, PinOff } from 'lucide-react';
import { useCallback, useState, type MouseEvent, type ReactNode } from 'react';
import { Menu, MenuItem } from '@/components/Menu';
import type { MusicItem } from '@/data/music/types';
import { MAX_PINS, usePins } from '@/state/pins';
import { toast } from '@/state/toasts';

/**
 * Right-click on a playlist (the sidebar, All Playlists): Pin to Top or
 * Unpin, at the pointer. Spread [onContextMenu] on the element and render [menu].
 */
export function usePinMenu(item: Pick<MusicItem, 'id' | 'title'> | null): { onContextMenu: ((event: MouseEvent) => void) | undefined; menu: ReactNode } {
  const [at, setAt] = useState<{ x: number; y: number } | null>(null);
  const close = useCallback(() => setAt(null), []);
  const pinned = usePins((state) => (item ? state.pinned.includes(item.id) : false));
  return {
    onContextMenu: item
      ? (event) => {
          event.preventDefault();
          setAt({ x: event.clientX, y: event.clientY });
        }
      : undefined,
    menu:
      item && at ? (
        <div className="contents" onClick={(e) => e.stopPropagation()} onContextMenu={(e) => (e.preventDefault(), e.stopPropagation())}>
          <Menu anchor={at} onClose={close} label={item.title}>
            <MenuItem
              icon={pinned ? <PinOff size={15} /> : <Pin size={15} />}
              onSelect={() => {
                close();
                if (!usePins.getState().toggle(item.id)) toast(`You can pin up to ${MAX_PINS} playlists`, { error: true });
              }}
            >
              {pinned ? 'Unpin' : 'Pin to Top'}
            </MenuItem>
          </Menu>
        </div>
      ) : null,
  };
}
