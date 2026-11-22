import { Info, ListEnd, ListPlus, ListStart, MoreHorizontal, Star } from 'lucide-react';
import { useCallback, useEffect, useRef, useState } from 'react';
import { Menu, MenuItem, MenuSeparator, type MenuPlacement } from '@/components/Menu';
import { useLikeStatus, useSetLiked, useSignedIn } from '@/data/music/queries';
import type { Track } from '@/data/music/types';
import { usePlayer } from '@/state/player';
import { PlaylistPicker } from './PlaylistPicker';
import { SongCredits } from './SongCredits';

/** What a menu item opens once the menu itself has closed. */
type MenuDialog = 'playlists' | 'credits';

/** Favorite and Add to Playlist: the account's actions, signed in only. */
function AccountItems({ track, onClose, onPlaylists }: { track: Track; onClose: () => void; onPlaylists: () => void }) {
  const status = useLikeStatus(track.videoId);
  const setLiked = useSetLiked();
  const liked = status.data === 'LIKE';
  return (
    <>
      <MenuSeparator />
      <MenuItem
        icon={<Star size={15} fill={liked ? 'currentColor' : 'none'} />}
        disabled={status.isPending}
        onSelect={() => {
          setLiked.mutate({ videoId: track.videoId, liked: !liked });
          onClose();
        }}
      >
        {liked ? 'Unfavorite' : 'Favorite'}
      </MenuItem>
      <MenuItem icon={<ListPlus size={15} />} onSelect={onPlaylists}>
        Add to Playlist…
      </MenuItem>
    </>
  );
}

function TrackMenuItems({ track, onClose, onDialog }: { track: Track; onClose: () => void; onDialog: (dialog: MenuDialog) => void }) {
  const signedIn = useSignedIn();
  const { playNext, addToQueue } = usePlayer.getState();
  return (
    <>
      <MenuItem icon={<ListStart size={15} />} onSelect={() => (onClose(), playNext([track]))}>
        Play Next
      </MenuItem>
      <MenuItem icon={<ListEnd size={15} />} onSelect={() => (onClose(), addToQueue([track]))}>
        Add to Queue
      </MenuItem>
      {signedIn && <AccountItems track={track} onClose={onClose} onPlaylists={() => onDialog('playlists')} />}
      <MenuSeparator />
      <MenuItem icon={<Info size={15} />} onSelect={() => onDialog('credits')}>
        View Credits
      </MenuItem>
    </>
  );
}

/**
 * The ⋯ button at the end of a track row (report §14): Play Next, Add to
 * Queue and View Credits for everyone; Favorite and Add to Playlist signed in.
 */
export function TrackMenu({
  track,
  className,
  placement,
  onOpenChange,
}: {
  track: Track;
  className?: string;
  /** Where the menu opens from the button; the player's sit at the bottom of the window. */
  placement?: MenuPlacement;
  /** Told when the menu, or a dialog it opened, opens and closes; most mounts don't need to know. */
  onOpenChange?: (open: boolean) => void;
}) {
  const [anchor, setAnchor] = useState<HTMLElement | null>(null);
  const [dialog, setDialog] = useState<MenuDialog | null>(null);
  const button = useRef<HTMLButtonElement>(null);
  const close = useCallback(() => setAnchor(null), []);
  const openDialog = useCallback((next: MenuDialog) => (setAnchor(null), setDialog(next)), []);
  const closeDialog = useCallback(() => (setDialog(null), button.current?.focus()), []);
  // A dialog counts as the menu still open: the search suggestions holding
  // this button stay up under it rather than taking it (and the dialog) away.
  useEffect(() => {
    onOpenChange?.(anchor !== null || dialog !== null);
  }, [anchor, dialog, onOpenChange]);
  return (
    <>
      <button
        ref={button}
        className={className}
        aria-label={`More options for ${track.title}`}
        aria-haspopup="menu"
        aria-expanded={anchor !== null}
        data-open={anchor !== null || undefined}
        onClick={(event) => {
          event.stopPropagation();
          setAnchor(anchor ? null : event.currentTarget);
        }}
        onDoubleClick={(event) => event.stopPropagation()}
      >
        <MoreHorizontal size={18} strokeWidth={2} />
      </button>
      {anchor && (
        <Menu anchor={anchor} placement={placement} onClose={close} label={track.title}>
          <TrackMenuItems track={track} onClose={close} onDialog={openDialog} />
        </Menu>
      )}
      {dialog === 'playlists' && <PlaylistPicker tracks={[track]} onClose={closeDialog} />}
      {dialog === 'credits' && <SongCredits track={track} onClose={closeDialog} />}
    </>
  );
}
