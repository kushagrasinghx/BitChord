import { ChevronLeft, ChevronRight, ListEnd, ListPlus, ListStart, MoreHorizontal, Plus, Search, Star } from 'lucide-react';
import { useCallback, useEffect, useRef, useState } from 'react';
import { Menu, MenuHeader, MenuItem, MenuSeparator, type MenuPlacement } from '@/components/Menu';
import {
  useAddToPlaylist,
  useCreatePlaylist,
  useLikeStatus,
  usePlaylistOptions,
  useSetLiked,
  useSignedIn,
} from '@/data/music/queries';
import type { Track } from '@/data/music/types';
import { usePlayer } from '@/state/player';
import { filterPlaylists } from './playlistFilter';

const menuField =
  'h-8 w-full rounded-[7px] border-0 bg-quinary pe-2 type-body text-primary outline-none placeholder:text-tertiary focus:bg-quaternary';

/**
 * Add to Playlist: a field to find one of the user's playlists by name, and
 * New Playlist, which names a new private playlist holding the song.
 */
function PlaylistChoices({ videoId, onDone, onBack }: { videoId: string; onDone: () => void; onBack: () => void }) {
  const options = usePlaylistOptions(videoId, true);
  const add = useAddToPlaylist();
  const create = useCreatePlaylist();
  const [query, setQuery] = useState('');
  const [naming, setNaming] = useState(false);
  const [name, setName] = useState('');
  const field = useRef<HTMLInputElement>(null);
  useEffect(() => field.current?.focus({ preventScroll: true }), [naming]);
  const shown = options.data ? filterPlaylists(options.data, query) : [];

  const createNamed = () => {
    const title = name.trim();
    if (!title || create.isPending) return;
    create.mutate({ title, videoId }, { onSuccess: onDone });
  };

  if (naming) {
    return (
      <>
        <MenuItem icon={<ChevronLeft size={14} />} onSelect={() => setNaming(false)}>
          Back
        </MenuItem>
        <MenuSeparator />
        <MenuHeader>New Playlist</MenuHeader>
        <form
          className="px-1 pb-1"
          onSubmit={(e) => {
            e.preventDefault();
            createNamed();
          }}
        >
          <input
            ref={field}
            className={`${menuField} ps-2.5`}
            value={name}
            maxLength={150}
            placeholder="Playlist name"
            aria-label="New playlist name"
            onChange={(e) => setName(e.target.value)}
          />
          {create.isError && <p className="px-1.5 pt-1.5 type-callout text-destructive">Couldn’t create the playlist</p>}
          <button
            type="submit"
            className="mt-1.5 h-7 w-full rounded-[7px] bg-key type-body-emphasized text-white enabled:hover:bg-key-rollover disabled:bg-quinary disabled:text-tertiary"
            disabled={!name.trim() || create.isPending}
          >
            {create.isPending ? 'Creating…' : 'Create and Add'}
          </button>
        </form>
      </>
    );
  }

  return (
    <>
      <MenuItem icon={<ChevronLeft size={14} />} onSelect={onBack}>
        Back
      </MenuItem>
      <MenuSeparator />
      <div className="relative px-1 pb-1">
        <Search size={15} className="pointer-events-none absolute start-3.5 top-2 text-secondary" aria-hidden="true" />
        <input
          ref={field}
          className={`${menuField} ps-8`}
          type="search"
          value={query}
          placeholder="Find a playlist"
          aria-label="Find a playlist"
          onChange={(e) => setQuery(e.target.value)}
          onKeyDown={(e) => {
            // Enter adds to the only (or first) playlist the search left.
            if (e.key === 'Enter' && query.trim() && shown[0]) {
              e.preventDefault();
              add.mutate({ playlistId: shown[0].playlistId, videoId });
              onDone();
            }
          }}
        />
      </div>
      <MenuItem
        icon={<Plus size={15} />}
        onSelect={() => {
          setName(query.trim());
          setNaming(true);
        }}
      >
        New Playlist
      </MenuItem>
      <MenuSeparator />
      {options.isPending && (
        <MenuItem disabled onSelect={() => {}}>
          Loading…
        </MenuItem>
      )}
      {options.isError && (
        <MenuItem disabled onSelect={() => {}}>
          Couldn’t load your playlists
        </MenuItem>
      )}
      {options.data && shown.length === 0 && (
        <MenuItem disabled onSelect={() => {}}>
          {query.trim() ? 'No matching playlists' : 'No playlists yet'}
        </MenuItem>
      )}
      {shown.map((option) => (
        <MenuItem
          key={option.playlistId}
          onSelect={() => {
            add.mutate({ playlistId: option.playlistId, videoId });
            onDone();
          }}
        >
          {option.title}
        </MenuItem>
      ))}
    </>
  );
}

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
      <MenuItem icon={<ListPlus size={15} />} trailing={<ChevronRight size={14} />} onSelect={onPlaylists}>
        Add to Playlist
      </MenuItem>
    </>
  );
}

function TrackMenuItems({ track, onClose }: { track: Track; onClose: () => void }) {
  const [view, setView] = useState<'main' | 'playlists'>('main');
  const signedIn = useSignedIn();
  if (view === 'playlists') {
    return <PlaylistChoices videoId={track.videoId} onDone={onClose} onBack={() => setView('main')} />;
  }
  const { playNext, addToQueue } = usePlayer.getState();
  return (
    <>
      <MenuItem icon={<ListStart size={15} />} onSelect={() => (onClose(), playNext([track]))}>
        Play Next
      </MenuItem>
      <MenuItem icon={<ListEnd size={15} />} onSelect={() => (onClose(), addToQueue([track]))}>
        Add to Queue
      </MenuItem>
      {signedIn && <AccountItems track={track} onClose={onClose} onPlaylists={() => setView('playlists')} />}
    </>
  );
}

/**
 * The ⋯ button at the end of a track row (report §14): Play Next and Add to
 * Queue for everyone; Favorite and Add to Playlist signed in.
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
  /** Told when the menu opens and closes; most mounts don't need to know. */
  onOpenChange?: (open: boolean) => void;
}) {
  const [anchor, setAnchor] = useState<HTMLElement | null>(null);
  const close = useCallback(() => setAnchor(null), []);
  useEffect(() => {
    onOpenChange?.(anchor !== null);
  }, [anchor, onOpenChange]);
  return (
    <>
      <button
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
          <TrackMenuItems track={track} onClose={close} />
        </Menu>
      )}
    </>
  );
}
