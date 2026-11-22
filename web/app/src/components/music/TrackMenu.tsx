import { useQuery } from '@tanstack/react-query';
import {
  AudioLines,
  CircleAlert,
  CircleCheck,
  Disc3,
  Download,
  HeartCrack,
  Info,
  ListEnd,
  ListPlus,
  ListStart,
  Loader2,
  MicVocal,
  MoreHorizontal,
  Radio,
  Share,
  Star,
} from 'lucide-react';
import { useEffect, useId, useState } from 'react';
import { useNavigate } from 'react-router';
import { MenuItem, MenuQuickAction, MenuQuickActions, MenuSeparator, type MenuPlacement } from '@/components/Menu';
import { fetchRadio } from '@/data/music/api';
import { useLikeStatus, useSetDisliked, useSetLiked, useSignedIn } from '@/data/music/queries';
import type { Track } from '@/data/music/types';
import { cancelDownload, dismissFailure, downloadSong, removeDownload, useDownloads } from '@/playback/offline/downloads';
import { offlineSupported, offlineTracks } from '@/playback/offline/store';
import { extendStation } from '@/playback/queue/builder';
import { albumPageFor, artistPageFor } from '@/shell/nowPlayingLinks';
import { LyricsOffsetItems, LyricsOffsetRow } from '@/shell/LyricsOffsetMenu';
import { SleepTimerItems, SleepTimerRow } from '@/shell/SleepTimerMenu';
import { usePlayer } from '@/state/player';
import { useUi } from '@/state/ui';
import { shareLink, useActionMenu, useMenuOwned } from './actionMenu';

/** How many songs a started radio queues behind its seed. */
const RADIO_LENGTH = 50;

/**
 * Start radio: the song, then YouTube Music's station for it, as AutoPlay
 * builds one (fetchRadio, extendStation). If the station won't load, the
 * song plays on its own and AutoPlay carries on from it.
 */
async function startRadio(track: Track) {
  const source = { title: `${track.title} Radio`, type: 'radio' as const, id: `RDAMVM${track.videoId}` };
  const station = await fetchRadio(track.videoId).then(
    ({ candidates, seedIsVideo }) => extendStation([track], candidates, RADIO_LENGTH, seedIsVideo),
    () => [],
  );
  usePlayer.getState().playTracks([track, ...station], 0, source);
}

/** Like, Dislike and Share as tiles: kept up after a rating, so the new state shows in place. */
function QuickTiles({ track, signedIn, onShared }: { track: Track; signedIn: boolean; onShared: () => void }) {
  const status = useLikeStatus(signedIn ? track.videoId : null);
  const setLiked = useSetLiked();
  const setDisliked = useSetDisliked();
  const [copied, setCopied] = useState(false);
  const liked = status.data === 'LIKE';
  const disliked = status.data === 'DISLIKE';
  return (
    <MenuQuickActions>
      {signedIn && (
        <>
          <MenuQuickAction
            icon={<Star fill={liked ? 'currentColor' : 'none'} />}
            label="Like"
            active={liked}
            disabled={status.isPending}
            onSelect={() => setLiked.mutate({ videoId: track.videoId, liked: !liked })}
          />
          <MenuQuickAction
            icon={<HeartCrack fill={disliked ? 'currentColor' : 'none'} />}
            label="Dislike"
            active={disliked}
            disabled={status.isPending}
            onSelect={() => setDisliked.mutate({ videoId: track.videoId, disliked: !disliked })}
          />
        </>
      )}
      <MenuQuickAction
        icon={<Share />}
        label={copied ? 'Copied' : 'Share'}
        onSelect={() =>
          void shareLink(track.title, `https://music.youtube.com/watch?v=${track.videoId}`).then((how) =>
            how === 'copied' ? setCopied(true) : onShared(),
          )
        }
      />
    </MenuQuickActions>
  );
}

/**
 * One row for the whole life of a download (SongActionsMenu.kt DownloadRow):
 * Download, Downloading (tap to cancel), a failure with Retry, then Remove
 * download once the file is kept. The menu stays up so the row can be watched.
 * No percentage: the fetch doesn't report one, and a bare label reads as working.
 */
function DownloadRow({ track }: { track: Track }) {
  const { videoId } = track;
  const state = useDownloads((s) => s.active[videoId]);
  // The same query Library › Downloads lists, so both change together.
  const saved = useQuery({ queryKey: ['offline', 'tracks'], queryFn: offlineTracks, select: (ts) => ts.some((t) => t.videoId === videoId) });
  useEffect(() => () => dismissFailure(videoId), [videoId]);

  if (state?.kind === 'running') {
    return (
      <MenuItem icon={<Loader2 className="animate-spin motion-reduce:animate-none" />} trailing="Cancel" onSelect={() => cancelDownload(videoId)}>
        Downloading
      </MenuItem>
    );
  }
  if (state?.kind === 'failed') {
    return (
      <MenuItem icon={<CircleAlert />} destructive description={state.reason} trailing="Retry" onSelect={() => void downloadSong(track)}>
        Download failed
      </MenuItem>
    );
  }
  if (saved.data) {
    return (
      <MenuItem icon={<CircleCheck />} onSelect={() => void removeDownload(videoId)}>
        Remove download
      </MenuItem>
    );
  }
  // Nothing to fetch offline, and nothing shown until the store has answered (no Download flashing to Remove).
  if (saved.data === undefined || !navigator.onLine) return null;
  return (
    <MenuItem icon={<Download />} onSelect={() => void downloadSong(track)}>
      Download
    </MenuItem>
  );
}

/**
 * A song's menu (SongActionsMenu.kt), in the phone's order: the rating and
 * Share as tiles; Add to playlist; Start radio, Play next, Add to queue,
 * Download (where this device can keep files);
 * Go to album, Go to artist, Song info; and in the player's own menu the
 * sleep timer, AutoMix and lyrics offset. Account rows only signed in.
 */
export function TrackMenuItems({ track, player }: { track: Track; player?: boolean }) {
  const signedIn = useSignedIn();
  const navigate = useNavigate();
  const automix = usePlayer((s) => s.automix);
  const { close, openDialog } = useActionMenu.getState();
  const { playNext, addToQueue, setAutomix } = usePlayer.getState();
  const [page, setPage] = useState<'sleep' | 'offset' | null>(null);
  const artist = track.artists[0];
  const go = (to: Promise<string>) => {
    close();
    void to.then((href) => {
      // The page opens behind the full-screen player otherwise.
      if (player) useUi.getState().setFullScreen(false);
      navigate(href);
    });
  };

  // The player's menu swaps its rows for the timer's (or the offset's) in place, as the phone's does.
  if (page === 'sleep') return <SleepTimerItems onBack={() => setPage(null)} onDone={close} />;
  if (page === 'offset') return <LyricsOffsetItems onBack={() => setPage(null)} />;
  return (
    <>
      <QuickTiles track={track} signedIn={signedIn} onShared={close} />
      {signedIn && (
        <>
          <MenuSeparator />
          <MenuItem icon={<ListPlus />} onSelect={() => openDialog({ kind: 'playlists', tracks: [track] })}>
            Add to playlist
          </MenuItem>
        </>
      )}
      <MenuSeparator />
      <MenuItem icon={<Radio />} onSelect={() => (close(), void startRadio(track))}>
        Start radio
      </MenuItem>
      <MenuItem icon={<ListStart />} onSelect={() => (close(), playNext([track]))}>
        Play next
      </MenuItem>
      <MenuItem icon={<ListEnd />} onSelect={() => (close(), addToQueue([track]))}>
        Add to queue
      </MenuItem>
      {/* The Library hub's check: without Cache Storage there is nowhere to keep the file. */}
      {offlineSupported() && <DownloadRow track={track} />}
      <MenuSeparator />
      {(track.album || player) && (
        <MenuItem
          icon={<Disc3 />}
          description={track.album?.name}
          onSelect={() => go(albumPageFor(track.videoId, track.album, [track.title, artist?.name].filter(Boolean).join(' ')))}
        >
          Go to album
        </MenuItem>
      )}
      {artist && (
        <MenuItem
          icon={<MicVocal />}
          description={track.artists.map((a) => a.name).join(', ')}
          onSelect={() => go(artistPageFor(track.videoId, artist))}
        >
          Go to artist
        </MenuItem>
      )}
      <MenuItem icon={<Info />} onSelect={() => openDialog({ kind: 'info', track })}>
        Song info
      </MenuItem>
      {player && (
        <>
          <MenuSeparator />
          <SleepTimerRow onOpen={() => setPage('sleep')} />
          <MenuItem icon={<AudioLines />} trailing={automix ? 'On' : 'Off'} onSelect={() => (close(), setAutomix(!automix))}>
            AutoMix
          </MenuItem>
          {/* After AutoMix, as the phone orders its player rows. */}
          <LyricsOffsetRow onOpen={() => setPage('offset')} />
        </>
      )}
    </>
  );
}

/**
 * The ⋯ that opens a song's menu, hung off the button (IosMenuPopover).
 * ActionMenuHost draws the menu; the button only asks for it and shows
 * while it's up.
 */
export function TrackMenu({
  track,
  className,
  placement,
  player,
  onOpenChange,
}: {
  track: Track;
  className?: string;
  /** Where the menu opens from the button; the player's sit at the bottom of the window. */
  placement?: MenuPlacement;
  /** The player's own ⋯: adds the sleep timer, AutoMix and lyrics offset. */
  player?: boolean;
  /** Told when the menu, or a dialog it opened, opens and closes; most mounts don't need to know. */
  onOpenChange?: (open: boolean) => void;
}) {
  const owner = useId();
  const open = useMenuOwned(owner);
  const menuUp = useActionMenu((s) => s.menu?.owner === owner);
  // A dialog counts as the menu still open: the search suggestions holding
  // this button stay up under it rather than taking it (and the dialog) away.
  useEffect(() => onOpenChange?.(open), [open, onOpenChange]);
  return (
    <button
      className={className}
      aria-label={`More options for ${track.title}`}
      aria-haspopup="menu"
      aria-expanded={menuUp}
      data-open={open || undefined}
      onClick={(event) => {
        event.stopPropagation();
        const { open: openMenu, close } = useActionMenu.getState();
        if (menuUp) close();
        else openMenu({ target: { kind: 'track', track, player }, anchor: event.currentTarget, placement, owner });
      }}
      onDoubleClick={(event) => event.stopPropagation()}
    >
      <MoreHorizontal size={18} strokeWidth={2} />
    </button>
  );
}
