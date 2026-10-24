import {
  ChevronLeft,
  ChevronRight,
  Disc3,
  ListEnd,
  ListPlus,
  ListStart,
  MoreHorizontal,
  Star,
} from 'lucide-react';
import { useCallback, useState } from 'react';
import { useNavigate } from 'react-router';
import { Menu, MenuHeader, MenuItem, MenuSeparator, type MenuPlacement } from '@/components/Menu';
import {
  useAddToPlaylist,
  useLikeStatus,
  usePlaylistOptions,
  useSetLiked,
  useSignedIn,
} from '@/data/music/queries';
import type { Track } from '@/data/music/types';
import { usePlayer } from '@/state/player';

function PlaylistChoices({ videoId, onDone, onBack }: { videoId: string; onDone: () => void; onBack: () => void }) {
  const options = usePlaylistOptions(videoId, true);
  const add = useAddToPlaylist();
  return (
    <>
      <MenuItem icon={<ChevronLeft size={15} />} onSelect={onBack}>
        Back
      </MenuItem>
      <MenuSeparator />
      <MenuHeader>Add to Playlist</MenuHeader>
      {options.isPending && (
        <MenuItem disabled onSelect={() => {}}>
          Loading…
        </MenuItem>
      )}
      {options.isError && (
        <MenuItem disabled onSelect={() => {}}>
          Couldn’t load playlists
        </MenuItem>
      )}
      {options.data?.length === 0 && (
        <MenuItem disabled onSelect={() => {}}>
          No playlists yet
        </MenuItem>
      )}
      {options.data?.map((option) => (
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

function TrackMenuItems({ track, onClose }: { track: Track; onClose: () => void }) {
  const [view, setView] = useState<'main' | 'playlists'>('main');
  const navigate = useNavigate();
  const signedIn = useSignedIn();
  const likeStatus = useLikeStatus(track.videoId);
  const setLiked = useSetLiked();

  const liked = likeStatus.data === 'LIKE';

  if (view === 'playlists') {
    return <PlaylistChoices videoId={track.videoId} onDone={onClose} onBack={() => setView('main')} />;
  }

  const { playNext, addToQueue } = usePlayer.getState();

  const handleToggleLike = () => {
    setLiked.mutate({ videoId: track.videoId, liked: !liked });
  };

  const handlePlayNext = () => {
    onClose();
    playNext([track]);
  };

  const handleAddToQueue = () => {
    onClose();
    addToQueue([track]);
  };

  const handleOpenAlbum = () => {
    onClose();
    if (track.album?.id) {
      navigate(`/album/${encodeURIComponent(track.album.id)}`);
    } else {
      navigate(`/search?q=${encodeURIComponent(track.album?.name || track.title)}`);
    }
  };

  return (
    <>
      {/* 1. Favorite / Unfavorite */}
      <MenuItem
        icon={<Star size={15} fill={liked ? 'currentColor' : 'none'} className={liked ? 'text-key' : ''} />}
        disabled={!signedIn || likeStatus.isPending}
        onSelect={handleToggleLike}
      >
        {liked ? 'Unfavorite' : 'Favorite'}
      </MenuItem>

      {/* 2. Add to playlist */}
      <MenuItem
        icon={<ListPlus size={15} />}
        trailing={<ChevronRight size={14} />}
        disabled={!signedIn}
        onSelect={() => setView('playlists')}
      >
        Add to playlist
      </MenuItem>

      <MenuSeparator />

      {/* 3. Play next */}
      <MenuItem icon={<ListStart size={15} />} onSelect={handlePlayNext}>
        Play next
      </MenuItem>

      {/* 4. Add to queue */}
      <MenuItem icon={<ListEnd size={15} />} onSelect={handleAddToQueue}>
        Add to queue
      </MenuItem>

      <MenuSeparator />

      {/* 5. Open album */}
      <MenuItem icon={<Disc3 size={15} />} onSelect={handleOpenAlbum}>
        Open album
      </MenuItem>
    </>
  );
}

/**
 * The ⋯ button offering track options:
 * Favorite, Add to playlist, Play next, Add to queue, Open album.
 */
export function TrackMenu({
  track,
  className,
  placement = 'auto',
  iconSize = 18,
}: {
  track: Track;
  className?: string;
  placement?: MenuPlacement;
  iconSize?: number;
}) {
  const [anchor, setAnchor] = useState<HTMLElement | null>(null);
  const close = useCallback(() => setAnchor(null), []);

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
        <MoreHorizontal size={iconSize} strokeWidth={2} />
      </button>
      {anchor && (
        <Menu anchor={anchor} placement={placement} onClose={close} label={track.title}>
          <TrackMenuItems track={track} onClose={close} />
        </Menu>
      )}
    </>
  );
}
