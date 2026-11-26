import { useEffect, useRef } from 'react';
import { usePlayer } from '@/state/player';
import { barScale, joinBarClock, MINIMUMS } from './playingBarMotion';

/**
 * The playing row's three bars on a dark 24px plate (sharedUi/.../components/
 * Common.kt SearchPlayingBars), moved by the shared bar clock (playingBarMotion.ts).
 * Paused, they hold where they are, as the phone's do; under reduced motion
 * they stand still.
 */
export function PlayingBars({ playing = true, className = '' }: { playing?: boolean; className?: string }) {
  const bars = useRef<(HTMLSpanElement | null)[]>([]);
  const clock = useRef(0);

  useEffect(() => {
    if (!playing || matchMedia('(prefers-reduced-motion: reduce)').matches) return;
    let last = performance.now();
    return joinBarClock(() => {
      const now = performance.now();
      clock.current += Math.min(now - last, 250) / 1000;
      last = now;
      bars.current.forEach((bar, i) => {
        if (bar) bar.style.transform = `scaleY(${barScale(clock.current, i).toFixed(3)})`;
      });
    });
  }, [playing]);

  return (
    // The plate is a fixed square so only the bars move, never the box around them.
    <span className={`grid size-6 place-items-center rounded-[6px] bg-[rgb(0_0_0/0.52)] ${className}`} aria-hidden="true">
      <span className="flex h-3.5 w-[13px] items-end gap-0.5">
        {MINIMUMS.map((minimum, i) => (
          <span
            key={i}
            ref={(el) => {
              bars.current[i] = el;
            }}
            className="h-full w-[3px] origin-bottom rounded-[1.5px] bg-white"
            style={{ transform: `scaleY(${minimum})` }}
          />
        ))}
      </span>
    </span>
  );
}

/** True while [videoId] is the track playing (or buffering to play): a card's bars show only then, as on the phone. */
export function useIsPlaying(videoId: string | null): boolean {
  return usePlayer((s) => !!videoId && s.nowPlaying?.videoId === videoId && (s.status === 'playing' || s.status === 'buffering'));
}
