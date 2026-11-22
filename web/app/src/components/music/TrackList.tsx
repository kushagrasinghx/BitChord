import { useVirtualizer } from '@tanstack/react-virtual';
import { Check, ListEnd } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { PrefetchLink } from '@/components/PrefetchLink';
import { PauseIcon, PlayIcon } from '@/components/icons/Transport';
import type { ArtistRef, Track } from '@/data/music/types';
import { usePlayer } from '@/state/player';
import { albumHref, artistHref, formatDuration } from './links';
import { playList, playTrack, type ListContext } from './play';
import { PlayingBars } from './PlayingBars';
import { currentRowFill, RowArtwork, rowMoreButton, RowTitle, useRowPlayback } from './SongRows';
import { TrackMenu } from './TrackMenu';
import { contextMenu } from './actionMenu';

type Variant = 'album' | 'playlist';

/** A first guess for the virtualizer; each row is measured once it's drawn (a playlist row is taller below 768px). */
const ROW_HEIGHT: Record<Variant, number> = { album: 60, playlist: 56 };
/** Below this, plain rows: an album or a short playlist gains nothing from virtualizing. */
const VIRTUALIZE_FROM = 80;

interface TrackListProps {
  tracks: Track[];
  variant: Variant;
  /** Album artwork, for rows that have none of their own (album tracks). */
  fallbackArtwork?: string | null;
  /** The album's artists, credited when a track has none of its own. */
  fallbackArtists?: ArtistRef[];
  /** Called when the list nears its end, to load the next page of a long playlist. */
  onNearEnd?: () => void;
  /** Where the list comes from: playing a row queues the list from there. Without it, a row plays alone. */
  source?: ListContext['source'];
}

/*
 * Track rows, the phone's SongRow (sharedUi/.../components/Common.kt): 16px
 * in from the row's edge and 4px above and below, a 16px titleMedium title
 * over a 14px second line, the duration in 12px labelMedium, and the ⋮ always
 * there. An album numbers its rows in a 24px column (TRACK_NUMBER_WIDTH) where
 * a playlist shows 48px artwork; a playlist keeps its Artist, Album and Time
 * columns on a wide window, and below 768px becomes the phone's single column
 * with its 52px artwork.
 *
 * The second line and the duration are `text-secondary`: on an untinted page
 * that is onSurfaceVariant exactly, and on a tinted one it follows the tint,
 * as the phone's palette.onBackgroundVariant follows the artwork.
 */

const cell = 'flex h-full min-w-0 items-center';
// 396 / 274 / 274 / 141 at 1440px (report §14).
const playlistColumns = 'grid-cols-[minmax(0,36.5fr)_minmax(0,25.25fr)_minmax(0,25.25fr)_minmax(0,13fr)]';
// Artist and album: playlists only, and not below 768px.
const secondary = `${cell} pe-2.5 type-callout text-secondary max-md:hidden`;
// A row from another catalogue with no YouTube Music song (yet) is dimmed.
const dimmed = 'group-data-unavailable/row:opacity-45';
const link = 'text-inherit hover:underline';

/**
 * A hairline above every row from its title onwards (Common.kt
 * NUMBERED_ROW_DIVIDER_INSET and ROW_DIVIDER_INSET), hidden on hover and
 * around the playing row, which is washed with the primary colour.
 */
const row = [
  `group/row relative grid items-center rounded-row px-4 hover:bg-tracklist-hover data-unavailable:cursor-default ${currentRowFill}`,
  'before:pointer-events-none before:absolute before:end-0 before:top-0 before:border-t-[0.5px] before:border-t-divider',
  'hover:before:border-t-transparent data-current:before:border-t-transparent [&:hover+&]:before:border-t-transparent [&[data-current]+&]:before:border-t-transparent',
].join(' ');

const rowVariant: Record<Variant, string> = {
  // 52px tall number column + 4 + 4.
  album: 'h-[60px] grid-cols-[minmax(0,1fr)_auto] before:start-14',
  playlist: `h-14 ${playlistColumns} before:start-[76px] max-md:h-[60px] max-md:grid-cols-[minmax(0,1fr)_auto] max-md:before:start-[84px]`,
};

function Artists({ artists }: { artists: ArtistRef[] }) {
  return (
    <>
      {artists.map((artist, i) => {
        const href = artistHref(artist.id);
        return (
          <span key={`${artist.name}-${i}`}>
            {i > 0 && ', '}
            {href ? (
              <PrefetchLink to={href} className={link}>
                {artist.name}
              </PrefetchLink>
            ) : (
              artist.name
            )}
          </span>
        );
      })}
    </>
  );
}

/**
 * The phone's swipe-to-queue, for a mouse: on hover (or focus) a row offers
 * Add to Queue beside its duration, and shows a tick for a moment once it's done.
 */
function QueueButton({ track }: { track: Track }) {
  const [queued, setQueued] = useState(false);
  useEffect(() => {
    if (!queued) return;
    const timer = setTimeout(() => setQueued(false), 1500);
    return () => clearTimeout(timer);
  }, [queued]);
  return (
    <button
      className="grid size-8 flex-none place-items-center rounded-full text-secondary opacity-0 transition-opacity duration-100 group-hover/row:opacity-100 hover:bg-quinary hover:text-primary focus-visible:opacity-100 data-done:opacity-100"
      aria-label={`Add ${track.title} to queue`}
      title="Add to queue"
      data-done={queued || undefined}
      onClick={() => {
        usePlayer.getState().addToQueue([track]);
        setQueued(true);
      }}
    >
      {queued ? <Check size={17} strokeWidth={2.4} /> : <ListEnd size={17} strokeWidth={2.2} />}
    </button>
  );
}

interface RowProps {
  track: Track;
  variant: Variant;
  position: number;
  onPlayRow: () => void;
}

function Row({ track, variant, position, onPlayRow }: RowProps) {
  const playback = useRowPlayback(track, onPlayRow);
  const { current, playing, onPlay } = playback;
  const unavailable = track.unavailable;
  const album = variant === 'album';

  return (
    <div
      className={`${row} ${rowVariant[variant]}`}
      role="listitem"
      data-variant={variant}
      data-current={current || undefined}
      data-unavailable={unavailable}
      title={unavailable === 'matching' ? 'Looking for this on YouTube Music…' : unavailable === 'missing' ? 'Not found on YouTube Music' : undefined}
      onClick={playback.onClick}
      onContextMenu={contextMenu(unavailable ? null : { kind: 'track', track })}
    >
      <div className={`${cell} ${album ? 'gap-4' : 'gap-3 max-md:gap-3.5'}`}>
        {album ? (
          <div className="relative grid h-[52px] w-6 shrink-0 place-items-center">
            {current ? (
              <span className="grid place-items-center group-hover/row:invisible" aria-label="Now playing">
                <PlayingBars playing={playing} />
              </span>
            ) : (
              <span className="type-body text-secondary tabular-nums group-hover/row:invisible">{track.index ?? position}</span>
            )}
            {/* The number turns into play (or pause) on hover or focus. */}
            <button
              className="absolute inset-0 grid place-items-center text-primary opacity-0 group-hover/row:opacity-100 focus-visible:opacity-100 group-data-unavailable/row:hidden"
              aria-label={playing ? `Pause ${track.title}` : `Play ${track.title}`}
              onClick={onPlay}
              disabled={!!unavailable}
            >
              {playing ? <PauseIcon size={18} /> : <PlayIcon size={18} />}
            </button>
          </div>
        ) : (
          <RowArtwork track={track} playback={playback} className="w-12 max-md:w-[52px]" />
        )}
        <div
          className={`flex min-w-0 flex-col gap-0.5 ${dimmed} group-data-[unavailable=matching]/row:animate-matching motion-reduce:group-data-[unavailable=matching]/row:animate-none`}
        >
          <RowTitle track={track} />
          {track.artists.length > 0 && (
            <div className={`truncate type-callout text-secondary ${album ? '' : 'md:hidden'}`}>
              <Artists artists={track.artists} />
            </div>
          )}
        </div>
      </div>
      {!album && (
        <>
          <div className={`${secondary} ${dimmed}`}>
            <span className="truncate">
              <Artists artists={track.artists} />
            </span>
          </div>
          <div className={`${secondary} pe-4 ${dimmed}`}>
            <span className="truncate">
              {track.album &&
                (albumHref(track.album.id) ? (
                  <PrefetchLink to={albumHref(track.album.id)!} className={link}>
                    {track.album.name}
                  </PrefetchLink>
                ) : (
                  track.album.name
                ))}
            </span>
          </div>
        </>
      )}
      <div className={`${cell} justify-end gap-1 ps-2 ${dimmed}`}>
        {!unavailable && <QueueButton track={track} />}
        <span className="type-label-medium text-secondary tabular-nums">
          {unavailable === 'missing' ? 'Not found' : formatDuration(track.durationSec)}
        </span>
        {/* Hanging into the row's padding, so the dots end near its edge. */}
        {!unavailable && <TrackMenu track={track} className={`-me-2.5 ${rowMoreButton}`} />}
      </div>
    </div>
  );
}

function Header() {
  return (
    <div className={`grid h-8 items-center px-4 ${playlistColumns} type-callout-emphasized text-secondary max-md:hidden`} aria-hidden="true">
      <div className={cell}>Song</div>
      <div className={`${cell} pe-2.5`}>Artist</div>
      <div className={`${cell} pe-4`}>Album</div>
      <div className={`${cell} justify-end pe-[30px]`}>Time</div>
    </div>
  );
}

/**
 * Album and playlist track lists: SongRows divided by inset hairlines. Long
 * lists are virtualized against the page's scroll container (prompt §3).
 */
export function TrackList({ tracks, variant, fallbackArtwork, fallbackArtists, onNearEnd, source }: TrackListProps) {
  // An album's tracks carry no artwork or artist of their own: each row (its
  // queue button and menu with it) takes the album's.
  const withFallback = (track: Track): Track => ({
    ...track,
    thumbnail: track.thumbnail ?? fallbackArtwork ?? null,
    artists: track.artists.length ? track.artists : (fallbackArtists ?? []),
  });
  const playRow = (i: number) => {
    // Rows from another catalogue that found no YouTube Music song stay out of the queue.
    if (source) {
      const playable = tracks.filter((t) => !t.unavailable);
      return playList(playable, Math.max(0, playable.indexOf(tracks[i]!)), { source, fallbackArtwork, fallbackArtists });
    }
    playTrack(withFallback(tracks[i]!), fallbackArtwork);
  };
  const container = useRef<HTMLDivElement>(null);
  const [scrollElement, setScrollElement] = useState<HTMLElement | null>(null);
  useEffect(() => setScrollElement(document.getElementById('scrollable-page')), []);
  const virtual = tracks.length >= VIRTUALIZE_FROM;

  const virtualizer = useVirtualizer({
    count: virtual ? tracks.length : 0,
    getScrollElement: () => scrollElement,
    estimateSize: () => ROW_HEIGHT[variant],
    overscan: 10,
    scrollMargin: container.current?.offsetTop ?? 0,
  });

  const items = virtual ? virtualizer.getVirtualItems() : [];
  const last = items[items.length - 1];
  useEffect(() => {
    if (!onNearEnd) return;
    if (virtual ? last && last.index >= tracks.length - 20 : true) onNearEnd();
  }, [last?.index, tracks.length, virtual, onNearEnd]);

  const renderRow = (i: number, key?: string) => (
    <Row key={key} track={withFallback(tracks[i]!)} variant={variant} position={i + 1} onPlayRow={() => playRow(i)} />
  );

  return (
    // Rows reach 16px past the page's gutter, so their text lines up with the header's and their fill overhangs it.
    <div className="mx-[max(0px,calc(var(--body-gutter)-16px))]" data-variant={variant} role="list" aria-label="Songs">
      {variant === 'playlist' && <Header />}
      <div ref={container}>
        {virtual ? (
          <div style={{ height: virtualizer.getTotalSize(), position: 'relative' }}>
            {items.map((item) => (
              <div
                key={item.key}
                data-index={item.index}
                ref={virtualizer.measureElement}
                style={{
                  position: 'absolute',
                  top: 0,
                  left: 0,
                  right: 0,
                  transform: `translateY(${item.start - virtualizer.options.scrollMargin}px)`,
                }}
              >
                {renderRow(item.index)}
              </div>
            ))}
          </div>
        ) : (
          tracks.map((track, i) => renderRow(i, `${track.videoId}-${i}`))
        )}
      </div>
    </div>
  );
}
