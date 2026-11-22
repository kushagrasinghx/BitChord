import { useVirtualizer } from '@tanstack/react-virtual';
import { Check, ListEnd } from 'lucide-react';
import { useCallback, useEffect, useRef, useState, type CSSProperties, type MouseEvent } from 'react';
import { PrefetchLink } from '@/components/PrefetchLink';
import { PauseIcon, PlayIcon } from '@/components/icons/Transport';
import type { ArtistRef, Track } from '@/data/music/types';
import { usePlayer } from '@/state/player';
import { albumHref, artistHref, formatDuration } from './links';
import { playList, playTrack, type ListContext } from './play';
import { PlayingBars } from './PlayingBars';
import { SelectionBar, useEscapeClears } from './SelectionBar';
import { nextSelection, type TrackSelection } from './trackSelection';
import { currentRowFill, RowArtwork, rowMoreButton, RowTitle, useRowPlayback } from './SongRows';
import { TrackMenu } from './TrackMenu';
import { contextMenu } from './actionMenu';

type Variant = 'album' | 'playlist';

/** A first guess for the virtualizer; each row is measured once it's drawn (a playlist row is taller below 768px, an album's shorter from 1000px). */
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
 * with its 52px artwork. From 1000px an album takes columns too, in 48px
 * single-line rows, as a desktop player lists a record.
 *
 * The second line and the duration are `text-secondary`: on an untinted page
 * that is onSurfaceVariant exactly, and on a tinted one it follows the tint,
 * as the phone's palette.onBackgroundVariant follows the artwork.
 */

const cell = 'flex h-full min-w-0 items-center';

/*
 * The columns are set on the list as --cols, so its header and rows agree and
 * a column no row fills is left out (a mix has no Album; an album rarely needs
 * an Artist). A playlist's are 396 / 274 / 274 / 141 at 1440px (report §14).
 * An album's, from 1000px, are Title, an Artist where some song isn't only the
 * album's, and Time at a fixed 120px so the queue button, duration and dots fit.
 */
function columns(variant: Variant, artist: boolean, album: boolean): string {
  if (variant === 'album') return artist ? 'minmax(0,3fr) minmax(0,2fr) 120px' : 'minmax(0,1fr) 120px';
  return ['minmax(0,36.5fr)', artist && 'minmax(0,25.25fr)', album && 'minmax(0,25.25fr)', 'minmax(0,13fr)'].filter(Boolean).join(' ');
}

const secondary = `${cell} pe-2.5 type-callout text-secondary`;
// A row from another catalogue with no YouTube Music song (yet) is dimmed.
const dimmed = 'group-data-unavailable/row:opacity-45';
const link = 'text-inherit hover:underline';

/**
 * A hairline above every row from its title onwards (Common.kt
 * NUMBERED_ROW_DIVIDER_INSET and ROW_DIVIDER_INSET), hidden on hover and
 * around the playing or selected row, which are washed. Keyboard focus takes
 * the hover's fill, with no ring.
 */
const row = [
  `group/row relative grid items-center rounded-row px-4 hover:bg-tracklist-hover focus-visible:bg-tracklist-hover data-unavailable:cursor-default ${currentRowFill} data-selected:bg-primary/10`,
  'before:pointer-events-none before:absolute before:end-0 before:top-0 before:border-t-[0.5px] before:border-t-divider',
  'hover:before:border-t-transparent data-current:before:border-t-transparent data-selected:before:border-t-transparent [&:hover+&]:before:border-t-transparent [&[data-current]+&]:before:border-t-transparent [&[data-selected]+&]:before:border-t-transparent',
].join(' ');

const rowVariant: Record<Variant, string> = {
  // 52px tall number column + 4 + 4; from 1000px one 48px line in the list's columns.
  album: 'h-[60px] grid-cols-[minmax(0,1fr)_auto] before:start-14 lg:h-12 lg:grid-cols-(--cols)',
  playlist: 'h-14 grid-cols-(--cols) before:start-[76px] max-md:h-[60px] max-md:grid-cols-[minmax(0,1fr)_auto] max-md:before:start-[84px]',
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

/** Which of the optional columns the list shows. */
interface Shown {
  artist: boolean;
  album: boolean;
}

interface RowProps {
  track: Track;
  variant: Variant;
  position: number;
  onPlayRow: () => void;
  show: Shown;
  /** An album row names its artist only where it isn't the album's. */
  credited: boolean;
  selecting: boolean;
  selected: boolean;
  /** A click with ⌘/Ctrl or Shift, or any click while something is selected. */
  onSelect: (event: MouseEvent) => void;
}

function Row({ track, variant, position, onPlayRow, show, credited, selecting, selected, onSelect }: RowProps) {
  const playback = useRowPlayback(track, onPlayRow);
  const { current, playing, onPlay } = playback;
  const unavailable = track.unavailable;
  const album = variant === 'album';

  return (
    <div
      className={`${row} ${rowVariant[variant]}`}
      role="listitem"
      tabIndex={0}
      data-variant={variant}
      data-current={current || undefined}
      data-selected={selected || undefined}
      data-unavailable={unavailable}
      title={unavailable === 'matching' ? 'Looking for this on YouTube Music…' : unavailable === 'missing' ? 'Not found on YouTube Music' : undefined}
      // Shift-click picks a run of rows, not a run of text.
      onMouseDown={(e) => e.shiftKey && e.preventDefault()}
      onClick={(e) => {
        if ((e.target as HTMLElement).closest('a, button')) return;
        if (!unavailable && (selecting || e.metaKey || e.ctrlKey || e.shiftKey)) return onSelect(e);
        playback.onClick(e);
      }}
      // Enter on a focused row plays it, as a click does.
      onKeyDown={(e) => {
        if (e.key !== 'Enter' || e.target !== e.currentTarget) return;
        e.preventDefault();
        onPlay();
      }}
      onContextMenu={contextMenu(unavailable ? null : { kind: 'track', track })}
    >
      <div className={`${cell} ${album ? 'gap-4' : 'gap-3 max-md:gap-3.5'}`}>
        {album ? (
          <div className="relative grid h-[52px] w-6 shrink-0 place-items-center lg:h-full">
            {selected ? (
              <Check size={17} strokeWidth={2.6} className="text-primary group-hover/row:invisible" aria-label="Selected" />
            ) : current ? (
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
          <div className="relative shrink-0">
            <RowArtwork track={track} playback={playback} className="w-12 max-md:w-[52px]" />
            {selected && (
              // The queue's selection tick, over the artwork's corner.
              <span
                className="pointer-events-none absolute -top-1.5 -left-1.5 grid size-[22px] place-items-center rounded-full border-[1.5px] border-white bg-white text-black shadow-[0_1px_3px_#0004]"
                aria-label="Selected"
              >
                <Check size={12} strokeWidth={3} />
              </span>
            )}
          </div>
        )}
        <div
          className={`flex min-w-0 flex-col gap-0.5 ${dimmed} group-data-[unavailable=matching]/row:animate-matching motion-reduce:group-data-[unavailable=matching]/row:animate-none`}
        >
          <RowTitle track={track} />
          {track.artists.length > 0 && (
            <div className={`truncate type-callout text-secondary ${album ? 'lg:hidden' : 'md:hidden'}`}>
              <Artists artists={track.artists} />
            </div>
          )}
        </div>
      </div>
      {album && show.artist && (
        <div className={`${secondary} ${dimmed} max-lg:hidden`}>
          {credited && (
            <span className="truncate">
              <Artists artists={track.artists} />
            </span>
          )}
        </div>
      )}
      {!album && show.artist && (
        <div className={`${secondary} ${dimmed} max-md:hidden`}>
          <span className="truncate">
            <Artists artists={track.artists} />
          </span>
        </div>
      )}
      {!album && show.album && (
        <div className={`${secondary} pe-4 ${dimmed} max-md:hidden`}>
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

/** The column heads: a playlist's from 768px, an album's (# · Title · Artist · Time) from 1000px. */
function Header({ variant, show }: { variant: Variant; show: Shown }) {
  const album = variant === 'album';
  return (
    <div
      className={`grid h-8 grid-cols-(--cols) items-center px-4 type-callout-emphasized text-secondary ${album ? 'max-lg:hidden' : 'max-md:hidden'}`}
      aria-hidden="true"
    >
      {album ? (
        <div className={`${cell} gap-4`}>
          <span className="w-6 text-center">#</span>Title
        </div>
      ) : (
        <div className={cell}>Song</div>
      )}
      {show.artist && <div className={`${cell} pe-2.5`}>Artist</div>}
      {show.album && <div className={`${cell} pe-4`}>Album</div>}
      <div className={`${cell} justify-end pe-[30px]`}>Time</div>
    </div>
  );
}

const names = (artists: ArtistRef[]) => artists.map((a) => a.name).join(', ');

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

  // An album names a song's artist only where it isn't the album's own; a column nobody fills is left out.
  const albumArtists = names(fallbackArtists ?? []);
  const credited = (track: Track) => variant !== 'album' || (track.artists.length > 0 && names(track.artists) !== albumArtists);
  const show: Shown = {
    artist: variant === 'album' ? tracks.some(credited) : tracks.some((t) => t.artists.length > 0),
    album: variant === 'playlist' && tracks.some((t) => t.album),
  };

  // Multi-select, as the queue's (UpNext.tsx): ⌘/Ctrl-click toggles a row, Shift-click adds a run, Esc clears.
  const [selection, setSelection] = useState<TrackSelection | null>(null);
  const clear = useCallback(() => setSelection(null), []);
  useEscapeClears(!!selection, clear);
  const selectable = (t: Track) => !t.unavailable;
  // In the list's order; a song the filter hides leaves the selection's actions with it.
  const selected = selection ? tracks.filter((t) => selectable(t) && selection.ids.has(t.videoId)).map(withFallback) : [];
  const select = (track: Track, event: MouseEvent) => {
    const order = tracks.filter(selectable).map((t) => t.videoId);
    const next = nextSelection(selection, order, track.videoId, event.shiftKey);
    setSelection(next.ids.size ? next : null);
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

  const renderRow = (i: number, key?: string) => {
    const track = tracks[i]!;
    return (
      <Row
        key={key}
        track={withFallback(track)}
        variant={variant}
        position={i + 1}
        onPlayRow={() => playRow(i)}
        show={show}
        credited={credited(track)}
        selecting={!!selection}
        selected={!!selection?.ids.has(track.videoId) && selectable(track)}
        onSelect={(event) => select(track, event)}
      />
    );
  };

  return (
    // Rows reach 16px past the page's gutter at the start, so their text lines up with the header's and their
    // fill overhangs it; from 1000px they stop at the gutter at the end, level with the header's controls.
    <div
      className="mx-[max(0px,calc(var(--body-gutter)-16px))] lg:me-(--body-gutter)"
      style={{ '--cols': columns(variant, show.artist, show.album) } as CSSProperties}
      data-variant={variant}
    >
      <Header variant={variant} show={show} />
      <div ref={container} role="list" aria-label="Songs">
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
      {selected.length > 0 && <SelectionBar tracks={selected} source={source ?? null} onClear={clear} />}
    </div>
  );
}
