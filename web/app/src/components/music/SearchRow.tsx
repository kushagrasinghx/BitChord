import { Check, ListEnd } from 'lucide-react';
import { useState, type HTMLAttributes, type ReactNode } from 'react';
import { PrefetchLink } from '@/components/PrefetchLink';
import type { MusicItem } from '@/data/music/types';
import { usePlayer } from '@/state/player';
import { Artwork } from './Artwork';
import { ExplicitBadge } from './ExplicitBadge';
import { toTrack } from './play';
import { itemContextMenu } from './actionMenu';
import { TrackMenu } from './TrackMenu';

export const isPlayable = (item: MusicItem) => item.kind === 'song' || item.kind === 'video';

const KIND_LABEL: Record<MusicItem['kind'], string> = {
  song: 'Song',
  video: 'Video',
  album: 'Album',
  playlist: 'Playlist',
  'spotify-playlist': 'Spotify Playlist',
  artist: 'Artist',
  episode: 'Episode',
  podcast: 'Podcast',
  unknown: '',
};

/** What a row says under its title: YouTube's own line, or what kind of thing it is (SearchScreen.kt BrowseRow). */
export const subtitleOf = (item: MusicItem) => item.subtitle || KIND_LABEL[item.kind];

/*
 * The phone's search rows (sharedUi/.../ui/screens/SearchScreen.kt SongRow,
 * BrowseRow, TypeaheadSongRow): 52px art, r8 (round for an artist), 16px
 * after it, then 16px title over 14px grey. The row is 6px above and below
 * the art, as SongRow pads it. Hover and keyboard selection fill it a neutral
 * grey, a desktop's answer to the phone's press, inset 10px from the gutter so
 * the fill has a margin of its own. Result rows are parted by 0.5px hairlines
 * that start under the text (Common.kt ROW_DIVIDER_INSET: gutter + 68); the
 * suggestions and recent searches have none, as on the phone.
 */
const row =
  'group/row relative mx-[calc(var(--body-gutter)-10px)] grid min-h-16 grid-cols-[52px_minmax(0,1fr)_auto] items-center gap-x-4 rounded-row px-2.5 py-1.5 hover:bg-quinary data-active:bg-quinary';
const divider =
  "after:pointer-events-none after:absolute after:right-2.5 after:bottom-0 after:left-[78px] after:h-px after:scale-y-50 after:bg-outline after:content-[''] last:after:hidden";
/** The row's main hit area: art and text, stretched over the whole row under its trailing buttons. */
const hit = 'col-span-2 grid grid-cols-subgrid items-center text-left outline-none before:absolute before:inset-0 before:rounded-row focus-visible:bg-none focus-visible:before:bg-key/14';

/**
 * A round trailing button: the phone's 40px touch target around an 18px glyph
 * (SearchScreen.kt, the ✕ and ↖ on its rows).
 */
export const rowButton =
  'relative z-1 grid size-10 place-items-center rounded-full text-on-surface-variant hover:bg-quinary hover:text-primary focus-visible:opacity-100 [&_svg]:size-[18px]';
/** The same, shown only while its row is hovered or selected, and kept once used. */
const hoverButton = `${rowButton} opacity-0 group-hover/row:opacity-100 group-data-active/row:opacity-100 data-open:opacity-100`;

/**
 * Add to Queue, the desktop's stand-in for the phone's swipe on a result
 * (SwipeToQueueBox): shown on hover, and a tick once the song is queued.
 */
export function QueueButton({ item }: { item: MusicItem }) {
  const [queued, setQueued] = useState(false);
  return (
    <button
      type="button"
      className={hoverButton}
      aria-label={`Add ${item.title} to queue`}
      title="Add to Queue (Shift+Enter)"
      data-open={queued || undefined}
      onMouseDown={(e) => e.preventDefault()}
      onClick={(e) => {
        e.stopPropagation();
        usePlayer.getState().addToQueue([toTrack(item)]);
        setQueued(true);
      }}
    >
      {queued ? <Check strokeWidth={2.4} /> : <ListEnd />}
    </button>
  );
}

/** A song's queue button and its ⋯, always shown as the phone's ⋮ is. */
export function SongActions({ item, onMenuOpenChange }: { item: MusicItem; onMenuOpenChange?: (open: boolean) => void }) {
  return (
    <>
      <QueueButton item={item} />
      <TrackMenu track={toTrack(item)} onOpenChange={onMenuOpenChange} className={rowButton} />
    </>
  );
}

function RowBody({ item }: { item: MusicItem }) {
  return (
    <>
      <Artwork src={item.thumbnail} displayWidth={52} radius={item.kind === 'artist' ? '50%' : 'var(--radius-row)'} />
      <span className="flex min-w-0 flex-col gap-0.5">
        <span className="flex min-w-0 items-center type-body text-primary">
          {/* The badge leads the title, as SongRow draws it. */}
          {item.explicit && (
            <span className="flex-none [&>svg]:ms-0 [&>svg]:me-1">
              <ExplicitBadge />
            </span>
          )}
          <span className="truncate">{item.title}</span>
        </span>
        <span className="truncate type-callout text-on-surface-variant">{subtitleOf(item)}</span>
      </span>
    </>
  );
}

/**
 * One search row: a result, a suggestion while typing, or a recent search.
 * Picking it opens [href] or runs [onPick]; [trailing] are its own buttons.
 * As a listbox [option] it is the field's to drive, so it takes no focus and
 * is picked by click or Enter.
 */
export function ItemRow({
  item,
  href,
  onPick,
  trailing,
  active,
  option,
  divided,
}: {
  item: MusicItem;
  href?: string | null;
  onPick: () => void;
  trailing?: ReactNode;
  active?: boolean;
  option?: HTMLAttributes<HTMLLIElement>;
  divided?: boolean;
}) {
  return (
    <li className={divided ? `${row} ${divider}` : row} data-active={active || undefined} {...option} onClick={option ? onPick : undefined} onContextMenu={itemContextMenu(item)}>
      {option ? (
        <span className="col-span-2 grid cursor-default grid-cols-subgrid items-center">
          <RowBody item={item} />
        </span>
      ) : href ? (
        <PrefetchLink to={href} className={hit} onClick={onPick}>
          <RowBody item={item} />
        </PrefetchLink>
      ) : (
        <button type="button" className={hit} onClick={onPick}>
          <RowBody item={item} />
        </button>
      )}
      <span className="flex items-center">{trailing}</span>
    </li>
  );
}
