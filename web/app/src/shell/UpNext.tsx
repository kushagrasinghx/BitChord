import { party, useParty } from '@/party/listenTogether';
import { Infinity as InfinityIcon, MoreHorizontal, X } from 'lucide-react';
import { useCallback, useState } from 'react';
import { Menu, MenuHeader, MenuItem, MenuSeparator } from '@/components/Menu';
import { Artwork } from '@/components/music/Artwork';
import { formatDuration } from '@/components/music/links';
import { autoplaySectionStart, upcomingUserQueueIndices, type QueueEntry } from '@/playback/queue/timeline';
import { CROSSFADE_CHOICES, usePlayer } from '@/state/player';

// The panel's square buttons: AutoPlay (the key colour when on) and the options menu.
const square = 'grid size-7 place-items-center rounded-[6px] text-secondary hover:bg-quinary aria-pressed:text-key';
const list = 'flex flex-col';

/** A row: 55px, [art] 40px [title] 1fr [right] 40px, gap 12px, radius 6px; a key-colour line where a drag would drop. */
function Row({
  entry,
  at,
  onDragStart,
  onDropAt,
  dragging,
}: {
  entry: QueueEntry;
  at: number;
  onDragStart: (at: number) => void;
  onDropAt: (at: number) => void;
  dragging: boolean;
}) {
  const { jumpTo, removeAt, playNext } = usePlayer.getState();
  const [anchor, setAnchor] = useState<HTMLElement | null>(null);
  const [over, setOver] = useState(false);
  const close = useCallback(() => setAnchor(null), []);
  const { track } = entry;

  return (
    <li
      className="group/row relative grid h-[55px] grid-cols-[40px_minmax(0,1fr)_40px] items-center gap-x-3 rounded-row px-2.5 hover:bg-quinary data-dragging:opacity-40 data-over:before:absolute data-over:before:inset-x-2.5 data-over:before:-top-px data-over:before:border-t-2 data-over:before:border-t-key"
      data-over={over || undefined}
      data-dragging={dragging || undefined}
      draggable
      onDragStart={(e) => {
        e.dataTransfer.effectAllowed = 'move';
        onDragStart(at);
      }}
      onDragOver={(e) => {
        e.preventDefault();
        setOver(true);
      }}
      onDragLeave={() => setOver(false)}
      onDrop={(e) => {
        e.preventDefault();
        setOver(false);
        onDropAt(at);
      }}
      onDoubleClick={() => jumpTo(at)}
    >
      <div className="relative size-10">
        <Artwork src={track.thumbnail} displayWidth={40} radius="var(--radius-artwork-small)" />
        {/* The ✕ over the artwork's top-left, on hover. */}
        <button
          className="absolute -top-1.5 -left-1.5 hidden size-[22px] place-items-center rounded-full bg-thick-material text-primary shadow-[0_1px_3px_#0004] [backdrop-filter:blur(10px)] group-hover/row:grid"
          aria-label={`Remove ${track.title}`}
          onClick={() => removeAt(at)}
        >
          <X size={12} strokeWidth={3} />
        </button>
      </div>
      <button className="flex min-w-0 flex-col text-start" onClick={() => jumpTo(at)} aria-label={`Play ${track.title}`}>
        <span className="truncate type-body text-primary">{track.title}</span>
        <span className="truncate type-body text-secondary">{track.artists.map((a) => a.name).join(', ')}</span>
      </button>
      <div className="relative grid items-center justify-items-end">
        <span className="type-body tabular-nums text-secondary group-hover/row:invisible group-has-[[data-open]]/row:invisible">{formatDuration(track.durationSec)}</span>
        <button
          className="absolute inset-y-0 right-0 my-auto hidden size-7 place-items-center rounded-full text-key group-hover/row:grid data-open:grid"
          aria-label={`More options for ${track.title}`}
          aria-haspopup="menu"
          data-open={anchor !== null || undefined}
          onClick={(e) => setAnchor(anchor ? null : e.currentTarget)}
        >
          <MoreHorizontal size={16} />
        </button>
      </div>
      {anchor && (
        <Menu anchor={anchor} onClose={close} label={track.title}>
          <MenuItem onSelect={() => (close(), jumpTo(at))}>Play Now</MenuItem>
          <MenuItem
            onSelect={() => {
              close();
              removeAt(at);
              playNext([track], entry.source);
            }}
          >
            Play Next
          </MenuItem>
          <MenuSeparator />
          <MenuItem destructive onSelect={() => (close(), removeAt(at))}>
            Remove from Up Next
          </MenuItem>
        </Menu>
      )}
    </li>
  );
}

/** Apple's Up Next (report §7), over the three-tier queue: what's next, then AutoPlay's section. */
export function UpNext() {
  const queue = usePlayer((s) => s.queue);
  const index = usePlayer((s) => s.index);
  const localAutoplay = usePlayer((s) => s.autoplay);
  // In a party AutoPlay is the party's setting (Autoplay.autoplayEnabledFor).
  const inParty = useParty((s) => !!s.code);
  const partyAutoplay = useParty((s) => s.playback.autoplayEnabled);
  const autoplay = inParty ? partyAutoplay : localAutoplay;
  const repeat = usePlayer((s) => s.repeat);
  const crossfadeSec = usePlayer((s) => s.crossfadeSec);
  const automix = usePlayer((s) => s.automix);
  const { clearUpNext, setAutoplay, setCrossfade, setAutomix, move } = usePlayer.getState();
  const [anchor, setAnchor] = useState<HTMLElement | null>(null);
  const [dragFrom, setDragFrom] = useState<number | null>(null);
  const close = useCallback(() => setAnchor(null), []);

  const tiers = queue.map((e) => e.tier);
  const autoplayAt = autoplaySectionStart(tiers, index);
  const canClear = upcomingUserQueueIndices(tiers, index).length > 0;
  const upcoming = queue.map((entry, at) => ({ entry, at })).filter(({ at }) => at > index);
  const beforeAutoplay = upcoming.filter(({ at }) => at < autoplayAt);
  const afterAutoplay = upcoming.filter(({ at }) => at >= autoplayAt);
  const playingFrom = queue[index]?.source?.title;

  const rows = (items: typeof upcoming) =>
    items.map(({ entry, at }) => (
      <Row
        key={entry.entryId}
        entry={entry}
        at={at}
        dragging={dragFrom === at}
        onDragStart={setDragFrom}
        onDropAt={(to) => {
          if (dragFrom !== null) move(dragFrom, to);
          setDragFrom(null);
        }}
      />
    ));

  return (
    <>
      <div className="flex items-center gap-1.5 px-5 pt-[23px] pb-3">
        <h3 className="flex-1 type-title-2-emphasized">Up Next</h3>
        {canClear && (
          <button className="mr-1 type-title-3 text-key" onClick={clearUpNext}>
            Clear
          </button>
        )}
        <button
          className={square}
          aria-label="AutoPlay"
          title="AutoPlay: keep playing similar music when the queue ends"
          aria-pressed={autoplay}
          onClick={() => (inParty ? party.setAutoplay(!autoplay) : setAutoplay(!autoplay))}
        >
          <InfinityIcon size={16} strokeWidth={2.4} />
        </button>
        <button className={square} aria-label="Playback options" aria-haspopup="menu" onClick={(e) => setAnchor(e.currentTarget)}>
          <MoreHorizontal size={16} />
        </button>
        {anchor && (
          <Menu anchor={anchor} onClose={close} label="Playback options">
            <MenuHeader>Crossfade</MenuHeader>
            {CROSSFADE_CHOICES.map((seconds) => (
              <MenuItem key={seconds} checked={!automix && crossfadeSec === seconds} onSelect={() => (close(), setCrossfade(seconds))}>
                {seconds === 0 ? 'Off' : `${seconds} seconds`}
              </MenuItem>
            ))}
            <MenuItem checked={automix} onSelect={() => (close(), setAutomix(true))}>
              Automix
            </MenuItem>
          </Menu>
        )}
      </div>
      {playingFrom && <p className="truncate px-5 pb-2 type-callout text-secondary">Playing from {playingFrom}</p>}

      {upcoming.length === 0 ? (
        <div className="flex flex-1 flex-col items-center justify-center gap-3 px-6 pb-20 text-center type-callout text-secondary">
          <p className="type-callout-emphasized">Nothing in Up Next</p>
          <p>
            {autoplay && repeat === 'off' && index >= 0
              ? 'AutoPlay is finding similar music.'
              : 'Songs you play next appear here.'}
          </p>
        </div>
      ) : (
        <div className="min-h-0 flex-1 overflow-y-auto px-2 pb-[120px] [scrollbar-width:thin]">
          <ol className={list}>{rows(beforeAutoplay)}</ol>
          {afterAutoplay.length > 0 && (
            <>
              <h4 className="mx-2.5 mt-[18px] mb-0.5 flex items-center gap-1.5 type-callout-emphasized">
                <InfinityIcon size={14} strokeWidth={2.4} /> AutoPlay
              </h4>
              <p className="mx-2.5 mb-2 type-callout text-secondary">Similar music, from YouTube Music’s radio.</p>
              <ol className={list}>{rows(afterAutoplay)}</ol>
            </>
          )}
        </div>
      )}
    </>
  );
}
