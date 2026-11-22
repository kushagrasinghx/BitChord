import { ChevronLeft, ChevronRight, Minus, Moon, Plus } from 'lucide-react';
import { useEffect, useState } from 'react';
import { MenuHeader, MenuItem, MenuSeparator } from '@/components/Menu';
import { remainingMs, SLEEP_TIMER, useSleepTimer } from '@/playback/sleepTimer';

/** "89:59", minutes and seconds, as the phone counts down (sleepTimerCountdown). */
function countdown(ms: number): string {
  const seconds = Math.floor(ms / 1000);
  return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`;
}

/** "1h 30m" or "45 min" (SongActionsMenu.sleepDuration). */
const duration = (minutes: number) => (minutes >= 60 ? `${Math.floor(minutes / 60)}h ${minutes % 60}m` : `${minutes} min`);

/** The running timer's time left, redrawn once a second; null when none runs. */
function useCountdown(): string | null {
  const deadline = useSleepTimer((s) => s.deadline);
  const [, redraw] = useState(0);
  useEffect(() => {
    if (deadline === null) return;
    const id = setInterval(() => redraw((n) => n + 1), 1000);
    return () => clearInterval(id);
  }, [deadline]);
  const left = remainingMs();
  return left === null ? null : countdown(left);
}

/** The Playback options menu's Sleep Timer row, with the time left while one runs. */
export function SleepTimerRow({ onOpen }: { onOpen: () => void }) {
  const left = useCountdown();
  const afterTrack = useSleepTimer((s) => s.afterTrack);
  return (
    <MenuItem
      icon={<Moon size={15} />}
      trailing={
        <span className="inline-flex items-center gap-1 tabular-nums">
          {left ?? (afterTrack ? 'End of Song' : null)}
          <ChevronRight size={14} />
        </span>
      }
      onSelect={onOpen}
    >
      Sleep Timer
    </MenuItem>
  );
}

/** The custom duration: 5-minute steps up to 12 hours (SongActionsMenu.CustomSleepTimerPage). */
function CustomItems({ initial, onBack, onStart }: { initial: number; onBack: () => void; onStart: (minutes: number) => void }) {
  const { stepMinutes: step, maxMinutes: max } = SLEEP_TIMER;
  const [minutes, setMinutes] = useState(initial);
  const stepper = 'grid size-7 flex-none place-items-center rounded-full text-key enabled:hover:bg-quinary disabled:text-tertiary';
  return (
    <>
      <MenuItem icon={<ChevronLeft size={14} />} onSelect={onBack}>
        Back
      </MenuItem>
      <MenuSeparator />
      <MenuHeader>Custom</MenuHeader>
      <p className="px-2 pb-1 text-center type-title-3 tabular-nums">{duration(minutes)}</p>
      <div className="flex items-center gap-1 px-1 pb-1">
        <button className={stepper} aria-label={`${step} minutes less`} disabled={minutes <= step} onClick={() => setMinutes(minutes - step)}>
          <Minus size={14} />
        </button>
        <input
          type="range"
          className="min-w-0 flex-1 accent-key"
          aria-label="Pause after"
          aria-valuetext={duration(minutes)}
          min={step}
          max={max}
          step={step}
          value={minutes}
          onChange={(e) => setMinutes(Number(e.currentTarget.value))}
        />
        <button className={stepper} aria-label={`${step} minutes more`} disabled={minutes >= max} onClick={() => setMinutes(minutes + step)}>
          <Plus size={14} />
        </button>
      </div>
      <MenuSeparator />
      <MenuItem onSelect={() => onStart(minutes)}>Start Timer</MenuItem>
    </>
  );
}

/**
 * The sleep timer's page of the Playback options menu (SongActionsMenu.SleepTimerPicker):
 * after this song, the presets, a custom length, and the fade.
 */
export function SleepTimerItems({ onBack, onDone }: { onBack: () => void; onDone: () => void }) {
  const minutes = useSleepTimer((s) => s.minutes);
  const afterTrack = useSleepTimer((s) => s.afterTrack);
  const fadeOut = useSleepTimer((s) => s.fadeOut);
  const { start, startAfterTrack, cancel, setFadeOut } = useSleepTimer.getState();
  const left = useCountdown();
  const [custom, setCustom] = useState<number | null>(null);
  // A running timer no preset matches is a custom one: its row ticks and names it.
  const runningCustom = minutes !== null && !SLEEP_TIMER.presets.includes(minutes) ? minutes : null;

  if (custom !== null) {
    return <CustomItems initial={custom} onBack={() => setCustom(null)} onStart={(m) => (start(m), onDone())} />;
  }
  return (
    <>
      <MenuItem icon={<ChevronLeft size={14} />} onSelect={onBack}>
        Back
      </MenuItem>
      <MenuSeparator />
      <MenuHeader>Sleep Timer</MenuHeader>
      <p className="px-2 pb-1 type-callout text-secondary">
        {left ? `${left} until playback pauses` : afterTrack ? 'Pausing when this song ends' : 'Pause playback after a while'}
      </p>
      {/* Finishing the song is the one people reach for at the end of a listen, so it leads. */}
      <MenuItem checked={afterTrack} onSelect={() => (startAfterTrack(), onDone())}>
        After This Song
      </MenuItem>
      {SLEEP_TIMER.presets.map((m) => (
        <MenuItem key={m} checked={minutes === m} onSelect={() => (start(m), onDone())}>
          {m === 60 ? '1 hour' : `${m} minutes`}
        </MenuItem>
      ))}
      <MenuItem checked={runningCustom !== null} trailing={<ChevronRight size={14} />} onSelect={() => setCustom(runningCustom ?? 90)}>
        {runningCustom === null ? 'Custom' : `Custom · ${duration(runningCustom)}`}
      </MenuItem>
      <MenuSeparator />
      <MenuItem checked={fadeOut} onSelect={() => setFadeOut(!fadeOut)}>
        Fade Out Before Pausing
      </MenuItem>
      {(minutes !== null || afterTrack) && <MenuItem onSelect={() => (cancel(), onDone())}>Turn Off Timer</MenuItem>}
    </>
  );
}
