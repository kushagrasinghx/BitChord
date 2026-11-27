import { MoreHorizontal } from 'lucide-react';
import { useCallback, useState, type ReactNode } from 'react';
import { Menu, MenuHeader, MenuItem, MenuSeparator } from '@/components/Menu';
import { SONG_SORTS, type SongSort } from '@/data/music/library';
import { detailButton } from './DetailHeader';

interface DetailMenuProps {
  /** Names the menu for assistive tech: the album's or playlist's title. */
  label: string;
  sort: SongSort;
  onSort: (sort: SongSort) => void;
  /** The page's own actions, above the sort orders; given the way to close the menu. */
  children?: (close: () => void) => ReactNode;
}

/**
 * The ⋯ beside an album's or playlist's Play: the phone's sort menu
 * (MainActivity FrostedSortMenu), and on the account's own playlist its
 * rename, reorder and delete above it.
 */
export function DetailMenu({ label, sort, onSort, children }: DetailMenuProps) {
  const [anchor, setAnchor] = useState<HTMLElement | null>(null);
  const close = useCallback(() => setAnchor(null), []);
  const actions = children?.(close);
  return (
    <>
      <button
        className={detailButton.circle}
        aria-label="More"
        title="More"
        aria-haspopup="menu"
        aria-expanded={anchor !== null}
        onClick={(event) => setAnchor(anchor ? null : event.currentTarget)}
      >
        <MoreHorizontal size={18} strokeWidth={2.2} />
      </button>
      {anchor && (
        <Menu anchor={anchor} onClose={close} label={label}>
          {actions && (
            <>
              {actions}
              <MenuSeparator />
            </>
          )}
          <MenuHeader>Sort Songs</MenuHeader>
          {SONG_SORTS.map((option) => (
            <MenuItem
              key={option.value}
              checked={option.value === sort}
              onSelect={() => {
                close();
                onSort(option.value);
                anchor.focus();
              }}
            >
              {option.label}
            </MenuItem>
          ))}
        </Menu>
      )}
    </>
  );
}
