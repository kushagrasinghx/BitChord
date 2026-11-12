import { useVirtualizer } from '@tanstack/react-virtual';
import { useEffect, useRef, useState } from 'react';
import { PrefetchLink } from '@/components/PrefetchLink';
import { PauseIcon, PlayIcon } from '@/components/icons/Transport';
import type { ArtistRef, Track } from '@/data/music/types';
import { usePlayer } from '@/state/player';
import { Artwork } from './Artwork';
import { ExplicitBadge } from './ExplicitBadge';
import { albumHref, artistHref, formatDuration } from './links';
import { playList, playTrack, type ListContext } from './play';
import { TrackMenu } from './TrackMenu';

type Variant = 'album' | 'playlist';

const ROW_HEIGHT: Record<Variant, number> = { album: 46, playlist: 54 };
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

/* Track lists (report §3 album rows, §14 playlist rows). */

const cell = 'flex h-full min-w-0 items-center';
// 396 / 274 / 274 / 141 at 1440px (report §14).
const playlistColumns = 'grid-cols-[minmax(0,36.5fr)_minmax(0,25.25fr)_minmax(0,25.25fr)_minmax(0,13fr)]';
// Artist and album: playlists only, and not below 768px.
const secondary = `${cell} pe-2.5 max-md:hidden`;
const tertiary = `${cell} pe-4 max-md:hidden`;
// A row from another catalogue with no YouTube Music song (yet) is dimmed.
const dimmed = 'group-data-unavailable/row:opacity-45';
const link = 'text-inherit hover:underline group-data-current/row:text-primary';

/**
 * A hairline on top of every row, hidden on hover and around the playing row;
 * a fill on hover, a stronger one for the playing row.
 */
const row = [
  'group/row relative grid items-center bg-transparent hover:bg-tracklist-hover data-unavailable:cursor-default',
  'data-current:bg-[color-mix(in_srgb,var(--systemPrimary)_12%,transparent)]',
  'before:pointer-events-none before:absolute before:inset-x-0 before:top-0 before:border-t-[0.5px] before:border-t-divider',
  'hover:before:border-t-transparent data-current:before:border-t-transparent [&:hover+&]:before:border-t-transparent [&[data-current]+&]:before:border-t-transparent',
].join(' ');

const rowVariant: Record<Variant, string> = {
  album: 'h-[46px] grid-cols-[minmax(0,1fr)_auto] rounded-row',
  playlist: `h-[54px] ${playlistColumns} rounded-[12px] max-md:grid-cols-[minmax(0,1fr)_auto]`,
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

const time: Record<Variant, string> = {
  album: 'relative justify-end pe-3 type-body tabular-nums',
  playlist: 'relative justify-end pe-[18px] type-body tabular-nums',
};

interface RowProps {
  track: Track;
  variant: Variant;
  position: number;
  fallbackArtwork: string | null | undefined;
  onPlayRow: () => void;
}

function Row({ track, variant, position, fallbackArtwork, onPlayRow }: RowProps) {
  const current = usePlayer((s) => s.nowPlaying?.videoId === track.videoId);
  const playing = usePlayer((s) => s.nowPlaying?.videoId === track.videoId && (s.status === 'playing' || s.status === 'buffering'));
  const togglePlay = usePlayer((s) => s.togglePlay);
  const unavailable = track.unavailable;
  const onPlay = () => (unavailable ? undefined : current ? togglePlay() : onPlayRow());

  return (
    <div
      className={`${row} ${rowVariant[variant]}`}
      data-variant={variant}
      data-current={current || undefined}
      data-unavailable={unavailable}
      title={unavailable === 'matching' ? 'Looking for this on YouTube Music…' : unavailable === 'missing' ? 'Not found on YouTube Music' : undefined}
      onDoubleClick={onPlay}
    >
      <div className={`${cell} gap-3`}>
        <div className={`relative grid w-10 shrink-0 place-items-center${variant === 'playlist' ? ' ms-[7px]' : ''}`}>
          {variant === 'playlist' ? (
            <>
              <Artwork src={track.thumbnail ?? fallbackArtwork ?? null} displayWidth={40} radius="var(--radius-artwork-small)" />
              {current && (
                // The playing row's marker: a still play glyph over the artwork (or where the number was).
                <span
                  className="absolute inset-0 grid place-items-center rounded-artwork-sm bg-[rgb(0_0_0/0.45)] text-white group-hover/row:invisible"
                  aria-label="Now playing"
                >
                  <PlayIcon size={18} />
                </span>
              )}
            </>
          ) : (
            <span className="type-body text-secondary group-hover/row:invisible">
              {current ? (
                <span className="grid place-items-center text-primary group-hover/row:invisible" aria-label="Now playing">
                  <PlayIcon size={14} />
                </span>
              ) : (
                (track.index ?? position)
              )}
            </span>
          )}
          <button
            className={`absolute inset-0 grid place-items-center rounded-artwork-sm opacity-0 group-hover/row:opacity-100 focus-visible:opacity-100 group-data-unavailable/row:hidden ${
              variant === 'playlist' ? 'bg-lockup-hover text-white' : 'text-primary'
            }`}
            aria-label={playing ? `Pause ${track.title}` : `Play ${track.title}`}
            onClick={onPlay}
            disabled={!!unavailable}
          >
            {playing ? <PauseIcon size={variant === 'playlist' ? 18 : 16} /> : <PlayIcon size={variant === 'playlist' ? 18 : 16} />}
          </button>
        </div>
        <div
          className={`flex min-w-0 flex-col ${dimmed} group-data-[unavailable=matching]/row:animate-matching motion-reduce:group-data-[unavailable=matching]/row:animate-none`}
        >
          <div className="flex min-w-0 items-center">
            <span className="truncate type-body text-primary">{track.title}</span>
            {track.explicit && <ExplicitBadge />}
          </div>
          {variant === 'album' && track.artists.length > 0 && (
            <div className="truncate type-callout">
              <Artists artists={track.artists} />
            </div>
          )}
        </div>
      </div>
      {variant === 'playlist' && (
        <>
          <div className={`${secondary} ${dimmed}`}>
            <span className="truncate type-body">
              <Artists artists={track.artists} />
            </span>
          </div>
          <div className={`${tertiary} ${dimmed}`}>
            <span className="truncate type-body">
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
      <div className={`${cell} ${time[variant]} ${dimmed}`}>
        {/* The ⋯ button (TrackMenu) takes the time's place on hover, as Apple's rows do. */}
        <span className={unavailable ? undefined : 'group-focus-within/row:invisible group-hover/row:invisible group-has-[[data-open]]/row:invisible'}>
          {unavailable === 'missing' ? 'Not found' : formatDuration(track.durationSec)}
        </span>
        {!unavailable && (
          <TrackMenu
            track={track}
            className="absolute end-2.5 hidden size-7 place-items-center rounded-full text-key group-focus-within/row:grid group-hover/row:grid hover:bg-quinary data-open:grid"
          />
        )}
      </div>
    </div>
  );
}

function Header() {
  return (
    <div className={`grid h-8 items-center ${playlistColumns} type-callout-emphasized text-secondary max-md:hidden`} aria-hidden="true">
      <div className={`${cell} gap-3 ps-[7px]`}>Song</div>
      <div className={secondary}>Artist</div>
      <div className={tertiary}>Album</div>
      <div className={`${cell} ${time.playlist}`}>Time</div>
    </div>
  );
}

/**
 * Album and playlist track lists (report §3, §14): hairline-divided rows,
 * hover fill, the number (or artwork) turning into a play button. Long lists
 * are virtualized against the page's scroll container (prompt §3).
 */
export function TrackList({ tracks, variant, fallbackArtwork, fallbackArtists, onNearEnd, source }: TrackListProps) {
  const playRow = (i: number) => {
    // Rows from another catalogue that found no YouTube Music song stay out of the queue.
    if (source) {
      const playable = tracks.filter((t) => !t.unavailable);
      return playList(playable, Math.max(0, playable.indexOf(tracks[i]!)), { source, fallbackArtwork, fallbackArtists });
    }
    const track = tracks[i]!;
    playTrack(track.artists.length || !fallbackArtists ? track : { ...track, artists: fallbackArtists }, fallbackArtwork);
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

  return (
    <div className="mx-(--body-gutter) type-callout text-secondary" data-variant={variant} role="table" aria-label="Songs">
      {variant === 'playlist' && <Header />}
      <div ref={container}>
        {virtual ? (
          <div style={{ height: virtualizer.getTotalSize(), position: 'relative' }}>
            {items.map((item) => (
              <div
                key={item.key}
                style={{
                  position: 'absolute',
                  top: 0,
                  left: 0,
                  right: 0,
                  transform: `translateY(${item.start - virtualizer.options.scrollMargin}px)`,
                }}
              >
                <Row
                  track={tracks[item.index]!}
                  variant={variant}
                  position={item.index + 1}
                  fallbackArtwork={fallbackArtwork}
                  onPlayRow={() => playRow(item.index)}
                />
              </div>
            ))}
          </div>
        ) : (
          tracks.map((track, i) => (
            <Row
              key={`${track.videoId}-${i}`}
              track={track}
              variant={variant}
              position={i + 1}
              fallbackArtwork={fallbackArtwork}
              onPlayRow={() => playRow(i)}
            />
          ))
        )}
      </div>
    </div>
  );
}
