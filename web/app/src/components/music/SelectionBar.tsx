import { ListEnd, ListPlus, ListStart, X } from 'lucide-react';
import { useEffect, useState } from 'react';
import { PlayIcon } from '@/components/icons/Transport';
import { useSignedIn } from '@/data/music/queries';
import type { Track } from '@/data/music/types';
import type { QueueSource } from '@/playback/queue/timeline';
import { usePlayer } from '@/state/player';
import { PlaylistPicker } from './PlaylistPicker';

function typing(element: EventTarget | null): boolean {
  return element instanceof HTMLElement && (element.isContentEditable || /^(INPUT|TEXTAREA|SELECT)$/.test(element.tagName));
}

/** Esc leaves selection, as it does in the queue; a menu, dialog or text field keeps its own Esc. */
export function useEscapeClears(active: boolean, clear: () => void) {
  useEffect(() => {
    if (!active) return;
    const onKey = (event: KeyboardEvent) => {
      if (event.key !== 'Escape' || event.defaultPrevented || typing(event.target)) return;
      if ((event.target as HTMLElement | null)?.closest('[role="menu"], [role="dialog"]')) return;
      event.preventDefault();
      clear();
    };
    // Captured ahead of the app's own Esc (closing a panel), so one press does one thing.
    window.addEventListener('keydown', onKey, true);
    return () => window.removeEventListener('keydown', onKey, true);
  }, [active, clear]);
}

const action =
  'flex items-center gap-1.5 rounded-[8px] px-2 py-1.5 type-callout-medium text-primary hover:bg-quinary focus-visible:bg-quinary disabled:text-tertiary';

/**
 * What a selection can do (the queue's selection bar, PlayerQueue's footer):
 * Play, Play Next, Add to Queue and Add to Playlist, floating over the list
 * above the player pill for as long as anything is selected.
 */
export function SelectionBar({ tracks, source, onClear }: { tracks: Track[]; source: QueueSource | null; onClear: () => void }) {
  const signedIn = useSignedIn();
  const [picking, setPicking] = useState(false);
  const done = (run: () => void) => () => {
    run();
    onClear();
  };
  return (
    // Sticks to the window's foot, over the rows, while the list is on screen; at the list's end it takes its own
    // place under the last row rather than covering it.
    <div className="pointer-events-none sticky bottom-[calc(var(--player-bottom-offset)+var(--player-pill-height)+12px)] z-10 pt-3">
      <div className="flex justify-center px-4">
        <div
          className="glass-pill pointer-events-auto relative flex min-h-11 max-w-full items-center gap-1 overflow-x-auto rounded-[12px] px-2 [scrollbar-width:none]"
          role="toolbar"
          aria-label="Selected songs"
        >
          <span className="flex-none px-2 type-body-emphasized text-primary tabular-nums" aria-live="polite">
            {tracks.length} selected
          </span>
          <button className={action} onClick={done(() => usePlayer.getState().playTracks(tracks, 0, source ?? { title: 'Selection', type: 'other' }))}>
            <PlayIcon size={14} /> Play
          </button>
          <button className={action} onClick={done(() => usePlayer.getState().playNext(tracks, source))}>
            <ListStart size={16} /> Play Next
          </button>
          <button className={action} onClick={done(() => usePlayer.getState().addToQueue(tracks, source))}>
            <ListEnd size={16} /> Add to Queue
          </button>
          {signedIn && (
            <button className={action} aria-haspopup="dialog" onClick={() => setPicking(true)}>
              <ListPlus size={16} /> Add to Playlist
            </button>
          )}
          <button className={`${action} px-1.5`} aria-label="Clear selection" title="Clear selection (Esc)" onClick={onClear}>
            <X size={16} />
          </button>
        </div>
      </div>
      {picking && <PlaylistPicker tracks={tracks} onClose={() => setPicking(false)} />}
    </div>
  );
}
