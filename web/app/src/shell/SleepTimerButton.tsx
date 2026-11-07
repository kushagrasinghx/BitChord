import { Moon } from 'lucide-react';
import { useCallback, useState } from 'react';
import { Menu, MenuHeader, MenuItem, MenuSeparator } from '@/components/Menu';
import { useSleepTimer } from '@/playback/sleepTimer';
import { formatRemaining, SLEEP_CHOICES } from '@/playback/sleepTimerPlan';

/**
 * The player bar's sleep timer: a moon, in the key colour while it runs. The
 * time left is in its tooltip and atop its menu, not in the bar, which has no
 * room to spare.
 */
export function SleepTimerButton({ className }: { className: string }) {
  const timer = useSleepTimer((s) => s.timer);
  const remaining = useSleepTimer((s) => s.remaining);
  const { start, cancel } = useSleepTimer.getState();
  const [anchor, setAnchor] = useState<HTMLElement | null>(null);
  const close = useCallback(() => setAnchor(null), []);
  const left = timer?.kind === 'minutes' ? formatRemaining(remaining) : timer ? 'End of song' : null;
  return (
    <>
      <button
        className={className}
        aria-label={left ? `Sleep timer, ${left} left` : 'Sleep timer'}
        title={left ? `Sleep timer: ${left}` : 'Sleep timer'}
        aria-pressed={timer !== null}
        aria-haspopup="menu"
        aria-expanded={anchor !== null}
        onClick={(e) => setAnchor(anchor ? null : e.currentTarget)}
      >
        <Moon size={16} strokeWidth={2} />
      </button>
      {anchor && (
        <Menu anchor={anchor} placement="above-end" onClose={close} label="Sleep timer">
          <MenuHeader>{timer?.kind === 'minutes' ? `Stops in ${left}` : timer ? 'Stops at the end of this song' : 'Stop playing in'}</MenuHeader>
          {SLEEP_CHOICES.map((minutes) => (
            <MenuItem key={minutes} checked={timer?.kind === 'minutes' && timer.minutes === minutes} onSelect={() => (close(), start(minutes))}>
              {minutes} minutes
            </MenuItem>
          ))}
          <MenuItem checked={timer?.kind === 'track'} onSelect={() => (close(), start('track'))}>
            End of song
          </MenuItem>
          {timer && (
            <>
              <MenuSeparator />
              <MenuItem destructive onSelect={() => (close(), cancel())}>
                Turn Off Timer
              </MenuItem>
            </>
          )}
        </Menu>
      )}
    </>
  );
}
