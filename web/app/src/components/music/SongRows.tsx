import { Plus } from 'lucide-react';
import type { MouseEvent } from 'react';
import { PauseIcon, PlayIcon } from '@/components/icons/Transport';
import type { Track } from '@/data/music/types';
import { usePlayer } from '@/state/player';
import { Artwork } from './Artwork';
import { ExplicitBadge } from './ExplicitBadge';
import { contextMenu } from './actionMenu';
import { PlayingBars } from './PlayingBars';
import { TrackMenu } from './TrackMenu';

/**
 * What every track row does with the pointer: one click plays it, as a tap
 * does on the phone, and on the playing row pauses or resumes. The second
 * click of a double click is let go, so a double click still plays once
 * rather than playing and then pausing; a click on a link or button inside
 * the row is that control's own.
 */
export function useRowPlayback(track: Track, onPlayRow: () => void) {
  const current = usePlayer((s) => s.nowPlaying?.videoId === track.videoId);
  const playing = usePlayer((s) => s.nowPlaying?.videoId === track.videoId && (s.status === 'playing' || s.status === 'buffering'));
  const togglePlay = usePlayer((s) => s.togglePlay);
  const onPlay = () => (track.unavailable ? undefined : current ? togglePlay() : onPlayRow());
  const onClick = (event: MouseEvent) => {
    if (event.detail > 1 || (event.target as HTMLElement).closest('a, button')) return;
    onPlay();
  };
  return { current, playing, onPlay, onClick };
}

/** The playing row's wash: 14% of the primary colour (Common.kt SongRow activeBackground), fading as animateColorAsState does. */
export const currentRowFill = 'transition-colors duration-200 data-current:bg-[color-mix(in_srgb,var(--systemPrimary)_14%,transparent)]';

/** The phone's row menu button (RowMoreButton), as a ⋯ like the player's and search's: one overflow glyph across the app. */
export const rowMoreButton =
  'grid size-9 flex-none place-items-center rounded-full text-secondary hover:bg-quinary hover:text-primary data-open:text-primary';

/** A title with the explicit mark leading it, as ExplicitSongTitle sets it. */
export function RowTitle({ track }: { track: Track }) {
  return (
    <div className="flex min-w-0 items-center">
      {track.explicit && (
        <span className="me-1 flex [&>svg]:ms-0">
          <ExplicitBadge />
        </span>
      )}
      <span className="truncate type-title-3-emphasized text-primary">{track.title}</span>
    </div>
  );
}

/**
 * Artwork with the playing bars over it while it plays, turning into play (or
 * pause) on hover or focus. Sized by the caller's width class; r7 for the 48px
 * compact rows, r8 (SongRow) otherwise.
 */
export function RowArtwork({
  track,
  playback,
  className,
  compact,
}: {
  track: Track;
  playback: ReturnType<typeof useRowPlayback>;
  className: string;
  compact?: boolean;
}) {
  const radius = compact ? 'var(--radius-artwork-small)' : 'var(--radius-track-row)';
  return (
    <div className={`relative shrink-0 ${className}`}>
      <Artwork src={track.thumbnail} displayWidth={52} radius={radius} />
      {playback.current && (
        <span className="absolute inset-0 grid place-items-center group-hover/row:invisible" aria-label="Now playing">
          <PlayingBars playing={playback.playing} />
        </span>
      )}
      {!track.unavailable && (
        <button
          className="absolute inset-0 grid place-items-center bg-lockup-hover text-white opacity-0 group-hover/row:opacity-100 focus-visible:opacity-100"
          style={{ borderRadius: radius }}
          aria-label={playback.playing ? `Pause ${track.title}` : `Play ${track.title}`}
          onClick={playback.onPlay}
        >
          {playback.playing ? <PauseIcon size={18} /> : <PlayIcon size={18} />}
        </button>
      )}
    </div>
  );
}

function Byline({ track }: { track: Track }) {
  return <div className="truncate type-callout text-secondary">{track.artists.map((a) => a.name).join(', ')}</div>;
}

/**
 * A row in an artist's Top Songs (DetailScreen.kt CompactSongRow): 48px
 * artwork at r7, 12px to the title, 4px above and below, the ⋮ at the end.
 */
export function CompactSongRow({ track, onPlayRow }: { track: Track; onPlayRow: () => void }) {
  const playback = useRowPlayback(track, onPlayRow);
  return (
    <div
      className={`group/row -mx-2 flex items-center gap-3 rounded-row px-2 py-1 hover:bg-tracklist-hover ${currentRowFill}`}
      data-current={playback.current || undefined}
      onClick={playback.onClick}
      onContextMenu={contextMenu({ kind: 'track', track })}
    >
      <RowArtwork track={track} playback={playback} className="w-12" compact />
      <div className="flex min-w-0 flex-1 flex-col gap-0.5">
        <RowTitle track={track} />
        <Byline track={track} />
      </div>
      <TrackMenu track={track} className={`-me-1 ${rowMoreButton}`} />
    </div>
  );
}

/**
 * A row under a playlist's Suggested (DetailScreen.kt SuggestedSongRow): a
 * song YouTube offers that was never added, so adding it takes the place a
 * row's ⋮ would have, as a 36px circle in a 16% wash of the primary colour.
 */
export function SuggestedSongRow({
  track,
  onPlayRow,
  onAdd,
  adding,
}: {
  track: Track;
  onPlayRow: () => void;
  onAdd: () => void;
  adding: boolean;
}) {
  const playback = useRowPlayback(track, onPlayRow);
  return (
    <div
      className={`group/row flex h-[60px] items-center gap-3.5 rounded-row px-4 hover:bg-tracklist-hover ${currentRowFill}`}
      data-current={playback.current || undefined}
      onClick={playback.onClick}
      onContextMenu={contextMenu({ kind: 'track', track })}
    >
      <RowArtwork track={track} playback={playback} className="w-[52px]" />
      <div className="flex min-w-0 flex-1 flex-col gap-0.5">
        <RowTitle track={track} />
        <Byline track={track} />
      </div>
      <button
        className="ms-2 grid size-9 flex-none place-items-center rounded-full bg-[color-mix(in_srgb,var(--systemPrimary)_16%,transparent)] text-primary transition-[scale,opacity] duration-150 enabled:hover:bg-[color-mix(in_srgb,var(--systemPrimary)_24%,transparent)] enabled:active:scale-[.92] disabled:opacity-40"
        aria-label={`Add ${track.title} to this playlist`}
        title="Add to playlist"
        disabled={adding}
        onClick={onAdd}
      >
        <Plus size={20} strokeWidth={2.4} />
      </button>
    </div>
  );
}
