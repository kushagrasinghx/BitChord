import { party, useParty } from '@/party/listenTogether';
import { Check, Infinity as InfinityIcon, ListEnd, ListPlus, MoreHorizontal, Trash2, X } from 'lucide-react';
import { lazy, Suspense, useCallback, useEffect, useLayoutEffect, useRef, useState, type MouseEvent, type ReactNode } from 'react';
import { Menu, MenuHeader, MenuItem, MenuSeparator } from '@/components/Menu';
import { Artwork } from '@/components/music/Artwork';
import { PlaylistPicker } from '@/components/music/PlaylistPicker';
import { useReducedMotion } from '@/data/spotify/canvas';
import { useSignedIn } from '@/data/music/queries';
import { autoplaySectionStart, splitQueue, upcomingUserQueueIndices, type QueueRow } from '@/playback/queue/timeline';
import { CROSSFADE_CHOICES, usePlayer } from '@/state/player';

// The sleep timer brings the Kotlin it shares with the phone; it loads with the menu, not the page.
const SleepTimerRow = lazy(() => import('./SleepTimerMenu').then((m) => ({ default: m.SleepTimerRow })));
const SleepTimerItems = lazy(() => import('./SleepTimerMenu').then((m) => ({ default: m.SleepTimerItems })));

// The header's round buttons: AutoPlay (lit when on) and the options menu.
const square = 'grid size-8 flex-none place-items-center rounded-full text-secondary hover:bg-quinary aria-pressed:text-primary';
const list = 'flex flex-col';
/* PlayerQueue.kt QueueHeading: titleMedium at 75%, 16px above and 6 below (12 above the first). */
const heading = 'flex min-h-8 items-center gap-1 pt-4 pb-1.5 type-title-2-emphasized text-primary/75 first:pt-3';
/* The text actions (QueueClearButton, the Select pill): 75%, in a capsule 8/4 around them. */
const pill = 'flex-none rounded-pill px-2 py-1 type-callout-medium text-primary/75 hover:bg-quinary disabled:text-primary/25';
/** QueueRow placement: a row that moves slides to its new place over 200ms rather than jumping. */
const PLACEMENT_MS = 200;
// Material's rounded DragHandle, the row's 20px handle at 40%.
const DRAG_HANDLE =
  'M19 9H5c-.55 0-1 .45-1 1s.45 1 1 1h14c.55 0 1-.45 1-1s-.45-1-1-1zM5 15h14c.55 0 1-.45 1-1s-.45-1-1-1H5c-.55 0-1 .45-1 1s.45 1 1 1z';
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

/**
 * A row (PlayerQueue.kt InlineQueueRow): 44px art with 6px corners and the
 * artwork hairline, 12px to the title (titleMedium at 92%) over the artist
 * (bodyMedium, secondary), 6px above and below, 8px corners; the 20px drag
 * handle at 40% at its end where it can move. Selected, a 6% wash. A line
 * where a drag would drop it.
 */
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
      className="group/row relative touch-pan-y rounded-row select-none not-data-selected:hover:bg-primary/4 data-dragging:opacity-40 data-over:before:absolute data-over:before:inset-x-0 data-over:before:-top-px data-over:before:border-t-2 data-over:before:border-t-primary/75 data-selected:bg-primary/6"
      data-entry={entry.entryId}
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
        className="relative grid grid-cols-[44px_minmax(0,1fr)_auto] items-center gap-x-3 py-1.5 data-settling:transition-transform data-settling:duration-200 data-settling:ease-out motion-reduce:transition-none"
        data-settling={swipe.settling || undefined}
        style={swipe.offset ? { transform: `translateX(${swipe.offset}px)` } : undefined}
        onTransitionEnd={swipe.onSettled}
      >
        <div className="relative size-11">
          <Artwork src={track.thumbnail} displayWidth={44} radius="6px" />
          {selecting ? (
            // Selection's tick over the artwork's top-left, where the ✕ sits otherwise.
            <span
              className="absolute -top-1.5 -left-1.5 grid size-[22px] place-items-center rounded-full border-[1.5px] border-white bg-black/30 text-black shadow-[0_1px_3px_#0004] data-selected:bg-white"
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
          <span className="truncate type-body-emphasized text-primary/92">{track.title}</span>
          <span className="truncate type-callout text-secondary">
            {track.artists.map((a) => a.name).join(', ')}
          </span>
        </button>
        <div className="flex items-center gap-1">
          {!selecting && (
            <button
              className="invisible grid size-7 place-items-center rounded-full text-primary/75 group-hover/row:visible hover:bg-quinary data-open:visible"
              aria-label={`More options for ${track.title}`}
              aria-haspopup="menu"
              data-row-action
              data-open={anchor !== null || undefined}
              onClick={(e) => setAnchor(anchor ? null : e.currentTarget)}
            >
              <MoreHorizontal size={16} />
            </button>
          )}
          {draggable && !selecting ? (
            <svg className="size-5 translate-x-1 cursor-grab text-primary/40" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
              <path d={DRAG_HANDLE} />
            </svg>
          ) : (
            <span className="w-6" />
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

/** A section's Add all to playlist (PlayerQueue.kt QueueAddAllButton): a 20px glyph at 75%. */
function AddAllToPlaylist({ rows }: { rows: QueueRow[] }) {
  const [open, setOpen] = useState(false);
  return (
    <>
      <button
        className="grid size-8 flex-none place-items-center rounded-full text-primary/75 hover:bg-quinary"
        aria-label="Add all to playlist"
        title="Add all to playlist"
        aria-haspopup="dialog"
        onClick={() => setOpen(true)}
      >
        <ListPlus size={20} />
      </button>
      {open && <PlaylistPicker tracks={rows.map(({ entry }) => entry.track)} onClose={() => setOpen(false)} />}
    </>
  );
}

/**
 * The queue's placement animation (PlayerQueue.kt queueMotion): when the
 * list changes, a row that moved slides from where it was to where it is
 * over 200ms rather than jumping there. Rows are found by data-entry.
 */
function usePlacement(list: React.RefObject<HTMLElement | null>, key: unknown) {
  const tops = useRef(new Map<string, number>());
  const still = useReducedMotion();
  useLayoutEffect(() => {
    const root = list.current;
    if (!root) return;
    const next = new Map<string, number>();
    for (const row of root.querySelectorAll<HTMLElement>('[data-entry]')) {
      const id = row.dataset.entry!;
      const top = row.offsetTop;
      next.set(id, top);
      const was = tops.current.get(id);
      if (!still && was !== undefined && was !== top) {
        row.animate([{ transform: `translateY(${was - top}px)` }, { transform: 'none' }], { duration: PLACEMENT_MS, easing: 'cubic-bezier(0.4, 0, 0.2, 1)' });
      }
    }
    tops.current = next;
  }, [key, still, list]);
}

/** Typing in a field isn't a queue shortcut. */
const typing = (target: EventTarget | null) => {
  const element = target as HTMLElement | null;
  return !!element && (element.isContentEditable || /^(INPUT|TEXTAREA|SELECT)$/.test(element.tagName));
};

/**
 * The queue (sharedUi/.../ui/player/PlayerQueue.kt), over the three-tier
 * queue: what's played (optionally), what was queued by hand, the rest of
 * what's playing, then AutoPlay's section. `player` is the full-screen
 * player's queue page, which brings its own margins and has AutoPlay in its
 * capsule; the side panel is the default.
 */
export function UpNext({ variant = 'panel' }: { variant?: 'panel' | 'player' }) {
  const player = variant === 'player';
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
  // PlayerQueue.kt's sections: what was hand-queued, the rest of what's playing, then AutoPlay's.
  const userRows = upcoming.filter(({ at, entry }) => at < autoplayAt && entry.tier === 'user');
  const contextRows = upcoming.filter(({ at, entry }) => at < autoplayAt && entry.tier !== 'user');
  const autoplayRows = upcoming.filter(({ at }) => at >= autoplayAt);
  const contextTitle = contextRows[0]?.entry.source?.title ?? queue[index]?.source?.title;
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
      // A menu or dialog has its own keys; the full-screen player, a dialog itself, is where this list lives.
      if ((event.target as HTMLElement | null)?.closest('[role="menu"], [role="dialog"]:not([data-full-screen])')) return;
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

  usePlacement(scroller, queue);

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

  const sectionHeading = (title: string, rowsIn: QueueRow[], extra?: ReactNode) => (
    <h4 className={heading}>
      <span className="min-w-0 flex-1 truncate">{title}</span>
      {!selection && (
        <>
          {signedIn && rowsIn.length > 0 && <AddAllToPlaylist rows={rowsIn} />}
          {extra}
        </>
      )}
    </h4>
  );

  return (
    <>
      {/* PlayerQueue.kt's header: "Queue" in titleLarge and the Select pill; selecting, the count, Select All and close. */}
      <div className={`flex items-center gap-1 ${player ? 'pb-1' : 'px-5 pt-[23px] pb-1'}`}>
        <h3 className="min-w-0 flex-1 truncate type-title-large text-primary" aria-live={selection ? 'polite' : undefined}>
          {selection ? `${selected.length} Selected` : 'Queue'}
        </h3>
        {selection ? (
          <>
            <button
              className={`${pill} type-title-2-emphasized`}
              disabled={!rows.length}
              onClick={() => setSelection({ ids: new Set(rows.map(({ entry }) => entry.entryId)), anchor: selection.anchor })}
            >
              Select All
            </button>
            <button className={square} aria-label="Close selection" onClick={() => setSelection(null)}>
              <X size={18} />
            </button>
          </>
        ) : (
          <>
            {rows.length > 0 && (
              <button className={`${pill} type-title-2-emphasized`} onClick={() => setSelection({ ids: new Set(), anchor: null })}>
                Select
              </button>
            )}
            {/* The full-screen player has AutoPlay in its capsule; the side panel keeps it here. */}
            {!player && (
              <button
                className={square}
                aria-label="AutoPlay"
                title="AutoPlay: keep playing similar music when the queue ends"
                aria-pressed={autoplay}
                onClick={() => (inParty ? party.setAutoplay(!autoplay) : setAutoplay(!autoplay))}
              >
                <InfinityIcon size={18} strokeWidth={2.4} />
              </button>
            )}
            <button className={square} aria-label="Playback options" aria-haspopup="menu" onClick={(e) => setAnchor(e.currentTarget)}>
              <MoreHorizontal size={18} />
            </button>
          </>
        )}
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
                </>
              )}
            </Suspense>
          </Menu>
        )}
      </div>

      {rows.length === 0 && !autoplay ? (
        <div className="flex flex-1 flex-col items-center justify-center gap-3 px-6 pb-20 text-center type-callout text-secondary">
          <p className="type-body-emphasized text-primary">Nothing in the queue</p>
          <p>Songs you play next appear here.</p>
        </div>
      ) : (
        <div
          ref={scroller}
          // PlayerQueue.kt fadingEdges: the list fades out over 28px at either end.
          className={`relative min-h-0 flex-1 overflow-y-auto [mask-image:linear-gradient(transparent,#000_28px,#000_calc(100%-28px),transparent)] [scrollbar-width:none] ${player ? 'pb-24' : 'px-3 pb-[120px]'}`}
        >
          {history.length > 0 && (
            <>
              {sectionHeading('Previously played', history)}
              <ol className={list}>{history.map((row) => rowFor(row, false))}</ol>
            </>
          )}
          <div ref={upcomingStart} />
          {userRows.length > 0 && (
            <>
              {sectionHeading(
                'Next in queue',
                userRows,
                canClear && (
                  <button className={pill} onClick={clearUpNext}>
                    Clear
                  </button>
                ),
              )}
              <ol className={list}>{userRows.map((row) => rowFor(row, true))}</ol>
            </>
          )}
          {contextRows.length > 0 && (
            <>
              {sectionHeading(contextTitle ? `Next from: ${contextTitle}` : 'Queue', contextRows)}
              <ol className={list}>{contextRows.map((row) => rowFor(row, true))}</ol>
            </>
          )}
          {(autoplay || autoplayRows.length > 0) && (
            <>
              {/* PlayerQueue.kt AutoplayHeading: the infinity glyph, "Autoplay" in white, and what it does under it. */}
              <div className="flex items-center gap-2 py-3.5">
                <InfinityIcon size={18} strokeWidth={2.4} className="flex-none text-primary/75" aria-hidden="true" />
                <div className="min-w-0 flex-1">
                  <h4 className="type-title-2-emphasized text-primary">Autoplay</h4>
                  <p className="type-callout text-secondary">
                    {autoplayRows.length ? 'Similar music, selected to play next' : 'Similar music will keep playing'}
                  </p>
                </div>
                {!selection && signedIn && autoplayRows.length > 0 && <AddAllToPlaylist rows={autoplayRows} />}
              </div>
              <ol className={list}>{autoplayRows.map((row) => rowFor(row, true))}</ol>
            </>
          )}
        </div>
      )}

      {/* The undo notice and the selection's actions (PlayerQueue's footer), over the list's bottom. */}
      <div
        className={`pointer-events-none absolute flex flex-col gap-2 ${player ? 'inset-x-0 bottom-2' : 'inset-x-3 bottom-[calc(var(--player-bottom-offset)+var(--player-pill-height)+12px)] max-sm:bottom-4'}`}
      >
        {(undoNotice || notice) && (
          <div className={bar} role="status">
            <span className="min-w-0 flex-1 truncate">{undoNotice?.message ?? notice}</span>
            {undoNotice && (
              <button className="type-body-emphasized text-primary" onClick={undo}>
                Undo
              </button>
            )}
          </div>
        )}
        {selection && (
          <div className={`${bar} justify-between`}>
            {signedIn ? <AddSelectionToPlaylist rows={selected} /> : <span />}
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
