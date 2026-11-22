import { party, useParty } from '@/party/listenTogether';
import { Check, Infinity as InfinityIcon, ListChecks, ListEnd, ListPlus, MoreHorizontal, Trash2, X } from 'lucide-react';
import { lazy, Suspense, useCallback, useEffect, useLayoutEffect, useRef, useState, type MouseEvent } from 'react';
import { Menu, MenuHeader, MenuItem, MenuSeparator } from '@/components/Menu';
import { Artwork } from '@/components/music/Artwork';
import { PlaylistPicker } from '@/components/music/PlaylistPicker';
import { formatDuration } from '@/components/music/links';
import { useSignedIn } from '@/data/music/queries';
import { autoplaySectionStart, splitQueue, upcomingUserQueueIndices, type QueueRow } from '@/playback/queue/timeline';
import { CROSSFADE_CHOICES, usePlayer } from '@/state/player';

// The sleep timer brings the Kotlin it shares with the phone; it loads with the menu, not the page.
const SleepTimerRow = lazy(() => import('./SleepTimerMenu').then((m) => ({ default: m.SleepTimerRow })));
const SleepTimerItems = lazy(() => import('./SleepTimerMenu').then((m) => ({ default: m.SleepTimerItems })));

// The panel's square buttons: AutoPlay (the key colour when on) and the options menu.
const square = 'grid size-7 place-items-center rounded-[6px] text-secondary hover:bg-quinary aria-pressed:text-key';
const list = 'flex flex-col';
const heading = 'mx-2.5 mt-[18px] mb-0.5 flex items-center gap-1.5 type-callout-emphasized';
// The undo notice and the selection's actions, floating over the list above the player pill.
const bar = 'glass-pill pointer-events-auto relative flex min-h-11 items-center gap-2 rounded-[12px] px-3.5 type-body';

/** QueueSwipeRow.kt: a swipe commits past 45% of the row's width, and lets go of it again below 36%. */
const SWIPE_COMMIT = 0.45;
const SWIPE_DISARM = 0.36;
const SWIPE_SLOP_PX = 10;
type SwipeAction = 'remove' | 'queue';

interface RowProps {
  row: QueueRow;
  /** Upcoming rows reorder by drag; history rows don't. */
  draggable: boolean;
  dragging: boolean;
  onDragStart: (at: number) => void;
  onDropAt: (at: number) => void;
  /** Selection mode: the row shows whether it's in the selection, and a click toggles it. */
  selecting: boolean;
  selected: boolean;
  onActivate: (row: QueueRow, event: MouseEvent) => void;
  /** Null when that swipe has nowhere to go: the last queued row can't be queued again. */
  canSwipe: (action: SwipeAction) => boolean;
  onSwipe: (row: QueueRow, action: SwipeAction) => void;
}

/**
 * A touch swipe on a row (QueueSwipeRow.kt): left removes, right sends it to
 * the end of the hand-queued songs. Touch only: a mouse drags rows to reorder
 * them instead, and has the ✕ and the menu.
 */
function useSwipe(row: QueueRow, canSwipe: RowProps['canSwipe'], onSwipe: RowProps['onSwipe'], enabled: boolean) {
  const [offset, setOffset] = useState(0);
  const [settling, setSettling] = useState(false);
  const [armed, setArmed] = useState(false);
  const gesture = useRef<{ id: number; x: number; y: number; width: number; dragging: boolean; offset: number } | null>(null);
  const actionAt = (x: number): SwipeAction => (x < 0 ? 'remove' : 'queue');

  const release = (cancelled: boolean) => {
    const g = gesture.current;
    gesture.current = null;
    if (!g?.dragging) return;
    setArmed(false);
    setSettling(true);
    const action = actionAt(g.offset);
    if (!cancelled && Math.abs(g.offset) >= g.width * SWIPE_COMMIT && canSwipe(action)) {
      // Off the edge, then out of the queue; the row leaves the list with its entry.
      setOffset(Math.sign(g.offset) * g.width);
      const reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
      setTimeout(() => onSwipe(row, action), reduce ? 0 : 180);
    } else setOffset(0);
  };

  const handlers = enabled
    ? {
        onPointerDown: (e: React.PointerEvent<HTMLElement>) => {
          if (e.pointerType !== 'touch') return;
          gesture.current = { id: e.pointerId, x: e.clientX, y: e.clientY, width: e.currentTarget.offsetWidth, dragging: false, offset: 0 };
        },
        onPointerMove: (e: React.PointerEvent<HTMLElement>) => {
          const g = gesture.current;
          if (!g || g.id !== e.pointerId) return;
          const dx = e.clientX - g.x;
          const dy = e.clientY - g.y;
          if (!g.dragging) {
            // Mostly vertical: the list is being scrolled.
            if (Math.abs(dy) > SWIPE_SLOP_PX && Math.abs(dy) > Math.abs(dx)) return void (gesture.current = null);
            if (Math.abs(dx) < SWIPE_SLOP_PX) return;
            g.dragging = true;
            setSettling(false);
            e.currentTarget.setPointerCapture(e.pointerId);
          }
          g.offset = Math.max(-g.width, Math.min(g.width, dx));
          setOffset(g.offset);
          const eligible = canSwipe(actionAt(g.offset));
          if (eligible && Math.abs(g.offset) >= g.width * SWIPE_COMMIT) setArmed(true);
          else if (!eligible || Math.abs(g.offset) < g.width * SWIPE_DISARM) setArmed(false);
        },
        onPointerUp: () => release(false),
        onPointerCancel: () => release(true),
      }
    : {};
  return { offset, settling, armed, action: actionAt(offset), handlers, onSettled: () => setSettling(false) };
}

/** A row: 55px, [art] 40px [title] 1fr [right] 40px, gap 12px, radius 6px; a key-colour line where a drag would drop. */
function Row({ row, draggable, dragging, onDragStart, onDropAt, selecting, selected, onActivate, canSwipe, onSwipe }: RowProps) {
  const { entry, at } = row;
  const { jumpTo, removeAt, removeEntries, playNext } = usePlayer.getState();
  const [anchor, setAnchor] = useState<HTMLElement | null>(null);
  const [over, setOver] = useState(false);
  const close = useCallback(() => setAnchor(null), []);
  const swipe = useSwipe(row, canSwipe, onSwipe, !selecting);
  const { track } = entry;

  return (
    <li
      className="group/row relative touch-pan-y rounded-row select-none not-data-selected:hover:bg-quinary data-dragging:opacity-40 data-over:before:absolute data-over:before:inset-x-2.5 data-over:before:-top-px data-over:before:border-t-2 data-over:before:border-t-key data-selected:bg-key-bg"
      data-over={over || undefined}
      data-dragging={dragging || undefined}
      data-selected={selected || undefined}
      draggable={draggable && !selecting}
      onDragStart={(e) => {
        e.dataTransfer.effectAllowed = 'move';
        onDragStart(at);
      }}
      onDragOver={(e) => {
        if (!draggable) return;
        e.preventDefault();
        setOver(true);
      }}
      onDragLeave={() => setOver(false)}
      onDrop={(e) => {
        e.preventDefault();
        setOver(false);
        onDropAt(at);
      }}
      // Anywhere on the row but its own buttons: play it, or select it (Shift for a range, ⌘/Ctrl to add one).
      onClick={(e) => {
        if (!(e.target as HTMLElement).closest('[data-row-action]')) onActivate(row, e);
      }}
      {...swipe.handlers}
    >
      {swipe.offset !== 0 && (
        <div
          className="absolute inset-0 flex items-center overflow-hidden rounded-row px-5 data-[action=queue]:bg-key/20 data-[action=queue]:text-key data-[action=remove]:justify-end data-[action=remove]:bg-red/20 data-[action=remove]:text-red"
          data-action={swipe.action}
          aria-hidden="true"
        >
          <span className="transition-transform duration-150 data-armed:scale-125" data-armed={swipe.armed || undefined}>
            {swipe.action === 'remove' ? <Trash2 size={20} /> : <ListEnd size={20} />}
          </span>
        </div>
      )}
      <div
        className="relative grid h-[55px] grid-cols-[40px_minmax(0,1fr)_40px] items-center gap-x-3 px-2.5 data-settling:transition-transform data-settling:duration-200 data-settling:ease-out motion-reduce:transition-none"
        data-settling={swipe.settling || undefined}
        style={swipe.offset ? { transform: `translateX(${swipe.offset}px)` } : undefined}
        onTransitionEnd={swipe.onSettled}
      >
        <div className="relative size-10">
          <Artwork src={track.thumbnail} displayWidth={40} radius="var(--radius-artwork-small)" />
          {selecting ? (
            // Selection's tick over the artwork's top-left, where the ✕ sits otherwise.
            <span
              className="absolute -top-1.5 -left-1.5 grid size-[22px] place-items-center rounded-full border-[1.5px] border-white bg-black/30 text-key shadow-[0_1px_3px_#0004] data-selected:bg-white"
              data-selected={selected || undefined}
              aria-hidden="true"
            >
              {selected && <Check size={12} strokeWidth={3} />}
            </span>
          ) : (
            // The ✕ over the artwork's top-left, on hover.
            <button
              className="absolute -top-1.5 -left-1.5 hidden size-[22px] place-items-center rounded-full bg-thick-material text-primary shadow-[0_1px_3px_#0004] [backdrop-filter:blur(10px)] group-hover/row:grid"
              aria-label={`Remove ${track.title}`}
              data-row-action
              onClick={() => removeEntries([entry.entryId])}
            >
              <X size={12} strokeWidth={3} />
            </button>
          )}
        </div>
        <button
          className="flex min-w-0 flex-col text-start"
          aria-label={selecting ? `Select ${track.title}` : `Play ${track.title}`}
          aria-pressed={selecting ? selected : undefined}
        >
          <span className="truncate type-body text-primary group-data-selected/row:text-white">{track.title}</span>
          <span className="truncate type-body text-secondary group-data-selected/row:text-white/80">
            {track.artists.map((a) => a.name).join(', ')}
          </span>
        </button>
        <div className="relative grid items-center justify-items-end">
          <span className="type-body tabular-nums text-secondary group-data-selected/row:text-white/80 group-hover/row:invisible group-has-[[data-open]]/row:invisible">{formatDuration(track.durationSec)}</span>
          {!selecting && (
            <button
              className="absolute inset-y-0 right-0 my-auto hidden size-7 place-items-center rounded-full text-key group-hover/row:grid data-open:grid"
              aria-label={`More options for ${track.title}`}
              aria-haspopup="menu"
              data-row-action
              data-open={anchor !== null || undefined}
              onClick={(e) => setAnchor(anchor ? null : e.currentTarget)}
            >
              <MoreHorizontal size={16} />
            </button>
          )}
        </div>
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
          <MenuItem destructive onSelect={() => (close(), removeEntries([entry.entryId]))}>
            Remove from Up Next
          </MenuItem>
        </Menu>
      )}
    </li>
  );
}

/** The selection's Add to Playlist: the same picker as track, album and playlist menus (search, several at once). */
function AddSelectionToPlaylist({ rows }: { rows: QueueRow[] }) {
  const [open, setOpen] = useState(false);
  return (
    <>
      <button
        className="flex items-center gap-1.5 rounded-[6px] px-1.5 py-1 text-key enabled:hover:bg-quinary disabled:text-tertiary"
        disabled={!rows.length}
        aria-haspopup="dialog"
        onClick={() => setOpen(true)}
      >
        <ListPlus size={16} /> Add to Playlist
      </button>
      {open && <PlaylistPicker tracks={rows.map(({ entry }) => entry.track)} onClose={() => setOpen(false)} />}
    </>
  );
}

/** Typing in a field isn't a queue shortcut. */
const typing = (target: EventTarget | null) => {
  const element = target as HTMLElement | null;
  return !!element && (element.isContentEditable || /^(INPUT|TEXTAREA|SELECT)$/.test(element.tagName));
};

/** Apple's Up Next (report §7), over the three-tier queue: what's played (optionally), what's next, then AutoPlay's section. */
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
  const showHistory = usePlayer((s) => s.showQueueHistory);
  const undoNotice = usePlayer((s) => s.undoNotice);
  const signedIn = useSignedIn();
  const { clearUpNext, setAutoplay, setCrossfade, setAutomix, move, jumpTo, removeAt, removeEntries, addToQueue, undo } =
    usePlayer.getState();
  const [anchor, setAnchor] = useState<HTMLElement | null>(null);
  const [menuView, setMenuView] = useState<'main' | 'sleep'>('main');
  const [dragFrom, setDragFrom] = useState<number | null>(null);
  /** Selection mode (QueueSelectionState): the selected entry ids, and where a Shift-click range starts. */
  const [selection, setSelection] = useState<{ ids: ReadonlySet<string>; anchor: string | null } | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const close = useCallback(() => (setAnchor(null), setMenuView('main')), []);
  const scroller = useRef<HTMLDivElement>(null);
  const upcomingStart = useRef<HTMLHeadingElement>(null);
  const replayedHistory = useRef(false);

  const tiers = queue.map((e) => e.tier);
  const autoplayAt = autoplaySectionStart(tiers, index);
  const canClear = upcomingUserQueueIndices(tiers, index).length > 0;
  const { history, upcoming } = splitQueue(queue, index, showHistory);
  const beforeAutoplay = upcoming.filter(({ at }) => at < autoplayAt);
  const afterAutoplay = upcoming.filter(({ at }) => at >= autoplayAt);
  const playingFrom = queue[index]?.source?.title;
  const playingId = queue[index]?.entryId;
  // What a selection can hold, in the order the panel lists it; a row that leaves the queue leaves the selection with it.
  const rows = [...history, ...upcoming];
  const selected = selection ? rows.filter(({ entry }) => selection.ids.has(entry.entryId)) : [];
  // A swipe right sends a row to the end of the hand-queued songs, unless it's already the last of them.
  const lastQueued = upcoming.filter(({ entry }) => entry.tier === 'user').at(-1)?.entry.entryId;

  const onActivate = (row: QueueRow, event: MouseEvent) => {
    const id = row.entry.entryId;
    const command = event.metaKey || event.ctrlKey;
    if (!selection && !command && !event.shiftKey) {
      // A replayed history row trades places with the song that was playing; the list stays where it is.
      replayedHistory.current = row.at < index;
      return jumpTo(row.at);
    }
    const ids = new Set(selection?.ids);
    const from = selection?.anchor ? rows.findIndex(({ entry }) => entry.entryId === selection.anchor) : -1;
    const to = rows.indexOf(row);
    if (event.shiftKey && from >= 0) {
      for (const { entry } of rows.slice(Math.min(from, to), Math.max(from, to) + 1)) ids.add(entry.entryId);
      return setSelection({ ids, anchor: selection!.anchor });
    }
    if (ids.has(id)) ids.delete(id);
    else ids.add(id);
    setSelection({ ids, anchor: id });
  };

  const removeSelected = () => {
    if (!selected.length) return;
    removeEntries(selected.map(({ entry }) => entry.entryId));
    setSelection(null);
  };

  // Delete removes the selection, ⌘/Ctrl+Z takes back the last removal or Clear, Esc leaves selection.
  // Captured ahead of the app's shortcuts, so Esc here doesn't also close the panel.
  const keys = useRef({ removeSelected, selecting: !!selection });
  keys.current = { removeSelected, selecting: !!selection };
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.defaultPrevented || event.altKey || typing(event.target)) return;
      if ((event.target as HTMLElement | null)?.closest('[role="menu"], [role="dialog"]')) return;
      const command = event.metaKey || event.ctrlKey;
      if (command && !event.shiftKey && (event.key === 'z' || event.key === 'Z')) {
        if (!usePlayer.getState().undoNotice) return;
        event.preventDefault();
        return usePlayer.getState().undo();
      }
      if (!keys.current.selecting) return;
      if (event.key === 'Escape') {
        event.preventDefault();
        setSelection(null);
      } else if (event.key === 'Delete' || event.key === 'Backspace') {
        event.preventDefault();
        keys.current.removeSelected();
      }
    };
    window.addEventListener('keydown', onKey, true);
    return () => window.removeEventListener('keydown', onKey, true);
  }, []);

  useEffect(() => {
    if (!notice) return;
    const id = setTimeout(() => setNotice(null), 3000);
    return () => clearTimeout(id);
  }, [notice]);

  // The queue opens at what plays next, with history above it to scroll back to
  // (PlayerQueue.upcomingStartIndex); a new song brings the list back there.
  const scrolledOnce = useRef(false);
  useLayoutEffect(() => {
    const list = scroller.current;
    if (!list) return;
    const target = upcomingStart.current?.offsetTop ?? 0;
    if (replayedHistory.current) replayedHistory.current = false;
    else list.scrollTo({ top: target, behavior: scrolledOnce.current ? 'smooth' : 'instant' });
    scrolledOnce.current = true;
  }, [playingId, showHistory]);

  const rowFor = (row: QueueRow, draggable: boolean) => (
    <Row
      key={row.entry.entryId}
      row={row}
      draggable={draggable}
      dragging={dragFrom === row.at}
      onDragStart={setDragFrom}
      onDropAt={(to) => {
        if (dragFrom !== null) move(dragFrom, to);
        setDragFrom(null);
      }}
      selecting={!!selection}
      selected={!!selection?.ids.has(row.entry.entryId)}
      onActivate={onActivate}
      canSwipe={(action) => action === 'remove' || row.entry.entryId !== lastQueued}
      onSwipe={({ entry }, action) => {
        if (action === 'remove') return removeEntries([entry.entryId]);
        // Found again by its entry: the queue may have moved while the row slid away.
        const { queue: now, index: playing } = usePlayer.getState();
        const at = now.findIndex((e) => e.entryId === entry.entryId);
        if (at < 0 || at === playing) return;
        removeAt(at);
        addToQueue([entry.track], entry.source);
      }}
    />
  );

  return (
    <>
      {selection ? (
        <div className="flex items-center gap-1.5 px-5 pt-[23px] pb-3">
          <h3 className="flex-1 type-title-2-emphasized" aria-live="polite">
            {selected.length} Selected
          </h3>
          <button
            className="mr-1 type-title-3 text-key disabled:text-tertiary"
            disabled={!rows.length}
            onClick={() => setSelection({ ids: new Set(rows.map(({ entry }) => entry.entryId)), anchor: selection.anchor })}
          >
            Select All
          </button>
          <button className={square} aria-label="Close selection" onClick={() => setSelection(null)}>
            <X size={16} />
          </button>
        </div>
      ) : (
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
              <Suspense fallback={null}>
                {menuView === 'sleep' ? (
                  <SleepTimerItems onBack={() => setMenuView('main')} onDone={close} />
                ) : (
                  <>
                    <MenuHeader>Crossfade</MenuHeader>
                    {CROSSFADE_CHOICES.map((seconds) => (
                      <MenuItem key={seconds} checked={!automix && crossfadeSec === seconds} onSelect={() => (close(), setCrossfade(seconds))}>
                        {seconds === 0 ? 'Off' : `${seconds} seconds`}
                      </MenuItem>
                    ))}
                    <MenuItem checked={automix} onSelect={() => (close(), setAutomix(true))}>
                      Automix
                    </MenuItem>
                    <MenuSeparator />
                    <SleepTimerRow onOpen={() => setMenuView('sleep')} />
                    {rows.length > 0 && (
                      <MenuItem icon={<ListChecks size={15} />} onSelect={() => (close(), setSelection({ ids: new Set(), anchor: null }))}>
                        Select Songs
                      </MenuItem>
                    )}
                  </>
                )}
              </Suspense>
            </Menu>
          )}
        </div>
      )}
      {playingFrom && <p className="truncate px-5 pb-2 type-callout text-secondary">Playing from {playingFrom}</p>}

      {rows.length === 0 ? (
        <div className="flex flex-1 flex-col items-center justify-center gap-3 px-6 pb-20 text-center type-callout text-secondary">
          <p className="type-callout-emphasized">Nothing in Up Next</p>
          <p>
            {autoplay && repeat === 'off' && index >= 0
              ? 'AutoPlay is finding similar music.'
              : 'Songs you play next appear here.'}
          </p>
        </div>
      ) : (
        <div ref={scroller} className="relative min-h-0 flex-1 overflow-y-auto px-2 pb-[120px] [scrollbar-width:thin]">
          {history.length > 0 && (
            <>
              <h4 className={`${heading} mt-0`}>Previously Played</h4>
              <ol className={list}>{history.map((row) => rowFor(row, false))}</ol>
              <h4 ref={upcomingStart} className={heading}>
                Playing Next
              </h4>
            </>
          )}
          {upcoming.length === 0 && <p className="mx-2.5 my-3 type-callout text-secondary">Nothing in Up Next</p>}
          <ol className={list}>{beforeAutoplay.map((row) => rowFor(row, true))}</ol>
          {afterAutoplay.length > 0 && (
            <>
              <h4 className={heading}>
                <InfinityIcon size={14} strokeWidth={2.4} /> AutoPlay
              </h4>
              <p className="mx-2.5 mb-2 type-callout text-secondary">Similar music, from YouTube Music’s radio.</p>
              <ol className={list}>{afterAutoplay.map((row) => rowFor(row, true))}</ol>
            </>
          )}
        </div>
      )}

      {/* The undo notice and the selection's actions (PlayerQueue's footer), over the list's bottom. */}
      <div className="pointer-events-none absolute inset-x-3 bottom-[calc(var(--player-bottom-offset)+var(--player-pill-height)+12px)] flex flex-col gap-2 max-sm:bottom-4">
        {(undoNotice || notice) && (
          <div className={bar} role="status">
            <span className="min-w-0 flex-1 truncate">{undoNotice?.message ?? notice}</span>
            {undoNotice && (
              <button className="type-body-emphasized text-key" onClick={undo}>
                Undo
              </button>
            )}
          </div>
        )}
        {selection && (
          <div className={`${bar} justify-between`}>
            {signedIn ? (
              <AddSelectionToPlaylist rows={selected} />
            ) : (
              <span />
            )}
            <button
              className="flex items-center gap-1.5 rounded-[6px] px-1.5 py-1 text-destructive enabled:hover:bg-quinary disabled:text-tertiary"
              disabled={!selected.length}
              onClick={removeSelected}
            >
              <Trash2 size={16} /> Remove
            </button>
          </div>
        )}
      </div>
    </>
  );
}
