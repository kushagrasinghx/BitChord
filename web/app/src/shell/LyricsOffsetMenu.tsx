import { ChevronLeft, ChevronRight, Minus, Plus, SlidersHorizontal } from 'lucide-react';
import { MenuHeader, MenuItem, MenuSeparator } from '@/components/Menu';
import {
  formatLyricsOffset,
  LYRICS_OFFSET_STEP_MS as STEP,
  MAX_LYRICS_OFFSET_MS as MAX,
  MIN_LYRICS_OFFSET_MS as MIN,
  useLyricsOffset,
} from '@/lyrics/offset';

/** The player menu's Lyrics offset row (SongActionsMenu.kt onLyricsOffset), with the shift when there is one. */
export function LyricsOffsetRow({ onOpen }: { onOpen: () => void }) {
  const offsetMs = useLyricsOffset((s) => s.offsetMs);
  return (
    <MenuItem
      icon={<SlidersHorizontal />}
      trailing={
        <span className="inline-flex items-center gap-1 tabular-nums">
          {offsetMs !== 0 && formatLyricsOffset(offsetMs)}
          <ChevronRight size={14} />
        </span>
      }
      onSelect={onOpen}
    >
      Lyrics offset
    </MenuItem>
  );
}

/**
 * The offset's page of the player menu: LyricsOffsetSheet.kt's controls (the
 * value, −, a slider snapping to 0.1 s, +, Reset), drawn in the menu in place
 * of its rows as the sleep timer's page is, rather than as a second drawer.
 * Each change applies at once, so the lyrics can be watched settling into time.
 */
export function LyricsOffsetItems({ onBack }: { onBack: () => void }) {
  const offsetMs = useLyricsOffset((s) => s.offsetMs);
  const set = useLyricsOffset((s) => s.setOffsetMs);
  const stepper = 'grid size-7 flex-none place-items-center rounded-full text-key enabled:hover:bg-quinary disabled:text-tertiary';
  return (
    <>
      <MenuItem icon={<ChevronLeft size={14} />} onSelect={onBack}>
        Back
      </MenuItem>
      <MenuSeparator />
      <MenuHeader>Lyrics offset</MenuHeader>
      <p className="px-5 pb-1 type-callout text-secondary">Positive values show lyrics later</p>
      <p className="px-2 pb-1 text-center type-title-3 tabular-nums">{formatLyricsOffset(offsetMs)}</p>
      <div className="flex items-center gap-1 px-1 pb-1">
        <button className={stepper} aria-label="Decrease lyrics offset" disabled={offsetMs <= MIN} onClick={() => set(offsetMs - STEP)}>
          <Minus size={14} />
        </button>
        <input
          type="range"
          className="min-w-0 flex-1 accent-key"
          aria-label="Lyrics offset"
          aria-valuetext={formatLyricsOffset(offsetMs)}
          min={MIN}
          max={MAX}
          step={STEP}
          value={offsetMs}
          onChange={(e) => set(Number(e.currentTarget.value))}
        />
        <button className={stepper} aria-label="Increase lyrics offset" disabled={offsetMs >= MAX} onClick={() => set(offsetMs + STEP)}>
          <Plus size={14} />
        </button>
      </div>
      <MenuSeparator />
      <MenuItem disabled={offsetMs === 0} onSelect={() => set(0)}>
        Reset
      </MenuItem>
    </>
  );
}
