import { Pin } from 'lucide-react';
import { PrefetchLink } from '@/components/PrefetchLink';
import { PlayIcon } from '@/components/icons/Transport';
import type { MusicItem } from '@/data/music/types';
import { Artwork } from './Artwork';
import { ExplicitBadge } from './ExplicitBadge';
import { artistHref, hrefFor } from './links';
import { playTrack, toTrack } from './play';
import { PlayingBars, useIsPlaying } from './PlayingBars';
import { TrackMenu } from './TrackMenu';
import { itemContextMenu } from './actionMenu';

const isPlayable = (item: MusicItem) => item.kind === 'song' || item.kind === 'video';

/* Lockups (report §2). */
const lockup = 'flex min-w-0 flex-col';
const caption = 'mt-2 flex min-w-0 flex-col gap-px';
const title = 'flex min-w-0 items-center type-callout [&>:first-child]:truncate';
const subtitle = 'truncate type-callout text-secondary';
const overlayLink = 'absolute inset-0 z-1 rounded-[inherit]';

/** Artist names as links where YouTube links them, else the given subtitle. */
function Byline({ item }: { item: MusicItem }) {
  const linked = item.artists.some((a) => a.id);
  if (!linked || item.kind === 'album' || item.kind === 'playlist') return <>{item.subtitle}</>;
  return (
    <>
      {item.artists.map((artist, i) => {
        const href = artistHref(artist.id);
        return (
          <span key={`${artist.name}-${i}`}>
            {i > 0 && ', '}
            {href ? (
              <PrefetchLink to={href} className="hover:underline">
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

function PlayPlatter({ item }: { item: MusicItem }) {
  return (
    <button
      className="pointer-events-auto absolute bottom-2.5 left-2.5 grid size-[30px] place-items-center rounded-full bg-thin-material text-white [backdrop-filter:saturate(180%)_blur(60px)] hover:bg-key-bg hover:text-on-key"
      aria-label={`Play ${item.title}`}
      onClick={(e) => {
        e.preventDefault();
        e.stopPropagation();
        playTrack(item);
      }}
    >
      <PlayIcon size={16} />
    </button>
  );
}

/** The artwork with its hover scrim, linking to the item or playing it. */
function LockupArtwork({ item, width, aspect }: { item: MusicItem; width: number; aspect: number }) {
  const href = hrefFor(item);
  const playing = useIsPlaying(isPlayable(item) ? item.id : null);
  return (
    <div className="group/art relative rounded-artwork-md">
      <Artwork src={item.thumbnail} displayWidth={width} aspect={aspect} radius="var(--radius-artwork-medium)" />
      {playing && <PlayingBars className="absolute top-1/2 left-1/2 z-1 -translate-1/2" />}
      {href ? (
        <PrefetchLink to={href} className={overlayLink} aria-label={item.title} />
      ) : (
        <button className={overlayLink} aria-label={`Play ${item.title}`} onClick={() => playTrack(item)} />
      )}
      {/* Hover scrim and platter (report §2: --lockupHoverBGColor, opacity .1s ease-in). */}
      <div className="pointer-events-none absolute inset-0 z-2 rounded-[inherit] bg-lockup-hover opacity-0 transition-opacity duration-100 ease-in group-focus-visible/art:opacity-100 group-hover/art:opacity-100">{isPlayable(item) && <PlayPlatter item={item} />}</div>
      {/* The track menu (report §14) for playable items: top-right, the
          platter's counterpart in the same glass, revealed with the scrim
          and held visible while its menu is open. */}
      {isPlayable(item) && (
        <TrackMenu
          track={toTrack(item)}
          className="absolute top-2 right-2 z-3 grid size-7 place-items-center rounded-full bg-thin-material text-white opacity-0 transition-opacity duration-100 ease-in [backdrop-filter:saturate(180%)_blur(60px)] group-hover/art:opacity-100 focus-visible:opacity-100 hover:bg-key-bg hover:text-on-key data-open:opacity-100"
        />
      )}
    </div>
  );
}

/** Square lockup (report §2): artwork, hover scrim with play platter, title and subtitle. */
export function SquareLockup({ item, width = 220, pinned = false }: { item: MusicItem; width?: number; pinned?: boolean }) {
  const href = hrefFor(item);
  return (
    <div className={lockup} onContextMenu={itemContextMenu(item)}>
      <LockupArtwork item={item} width={width} aspect={1} />
      <div className={caption}>
        <div className={title}>
          {href ? (
            <PrefetchLink to={href} className="hover:underline">
              {item.title}
            </PrefetchLink>
          ) : (
            <span>{item.title}</span>
          )}
          {item.explicit && <ExplicitBadge />}
          {/* A pinned Library playlist's 14px pin before its title (HomeScreen.kt ShelfCard isPinned); last in the DOM so the title stays the child that truncates. */}
          {pinned && <Pin className="order-first mr-1 size-3.5 flex-none text-on-surface-variant" aria-label="Pinned" role="img" />}
        </div>
        <div className={subtitle}>
          <Byline item={item} />
        </div>
      </div>
    </div>
  );
}

/** 16:9 video lockup (report §2, type C). */
export function VideoLockup({ item, width = 300 }: { item: MusicItem; width?: number }) {
  return (
    <div className={lockup} onContextMenu={itemContextMenu(item)}>
      <LockupArtwork item={item} width={width} aspect={16 / 9} />
      <div className={caption}>
        <div className={title}>
          <span>{item.title}</span>
          {item.explicit && <ExplicitBadge />}
        </div>
        <div className={subtitle}>{item.subtitle}</div>
      </div>
    </div>
  );
}

/** Circular artist lockup (report §2, type H). */
export function CircleLockup({ item, width = 160 }: { item: MusicItem; width?: number }) {
  return (
    <PrefetchLink to={hrefFor(item) ?? '#'} className="group/circle flex min-w-0 flex-col items-center gap-2.5">
      <Artwork src={item.thumbnail} displayWidth={width} radius="50%" />
      <span className="max-w-full truncate text-center type-title-2-emphasized group-hover/circle:underline">{item.title}</span>
    </PrefetchLink>
  );
}

/**
 * A song row inside a shelf, as the phone's CompactTrackRow (HomeScreen.kt):
 * 48px art at radius 7, 12px to the text, title 16 titleMedium over a 14
 * credit, 4px above and below.
 */
export function TrackLockup({ item, onPlay }: { item: MusicItem; onPlay?: () => void }) {
  const play = onPlay ?? (() => playTrack(item));
  const playing = useIsPlaying(item.id);
  return (
    // 48px art column, 12px gap, content, menu; 56px tall.
    <div onContextMenu={itemContextMenu(item)} className="group/track grid h-14 grid-cols-[48px_minmax(0,1fr)_auto] items-center gap-x-3 py-1 pr-1 pl-0">
      <button className="group/art relative w-12 rounded-artwork-sm" aria-label={`Play ${item.title}`} onClick={play}>
        <Artwork src={item.thumbnail} displayWidth={48} radius="var(--radius-artwork-small)" />
        {playing && <PlayingBars className="absolute top-1/2 left-1/2 -translate-1/2" />}
        <span
          className="absolute inset-0 grid place-items-center rounded-[inherit] bg-lockup-hover text-white opacity-0 transition-opacity duration-100 ease-in group-hover/track:opacity-100 group-focus-visible/art:opacity-100"
          aria-hidden="true"
        >
          <PlayIcon size={16} />
        </span>
      </button>
      <div className="flex min-w-0 flex-col">
        <div className="flex min-w-0 items-center type-title-2-emphasized">
          <button className="min-w-0 truncate text-left" onClick={play}>
            {item.title}
          </button>
          {item.explicit && <ExplicitBadge />}
        </div>
        <div className="truncate type-callout text-on-surface-variant">
          <Byline item={item} />
        </div>
      </div>
      {/* The track row's ⋯ (report §14), at the card's end: revealed on hover
          or focus, and held visible while its menu is open. The space is
          always reserved so the title doesn't jump when it appears. */}
      {isPlayable(item) && (
        <TrackMenu
          track={toTrack(item)}
          className="grid size-7 place-items-center rounded-full text-key opacity-0 transition-opacity duration-100 ease-in group-hover/track:opacity-100 focus-visible:opacity-100 hover:bg-quinary data-open:opacity-100"
        />
      )}
    </div>
  );
}
