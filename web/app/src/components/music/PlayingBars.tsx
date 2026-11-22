import { useEffect, useRef } from 'react';
import { usePlayer } from '@/state/player';

/** Each bar's lowest point; all three peak at full height (Common.kt PLAYING_BAR_MINIMUMS). */
const MINIMUMS = [0.38, 0.78, 0.52];

/**
 * The playing row's three bars on a dark 24px plate (sharedUi/.../components/
 * Common.kt SearchPlayingBars). Each rises from its minimum to full height and
 * falls again over its own period (520, 650 and 780ms, peaking at 260, 310 and
 * 360ms), then plays that backwards, so the three drift in and out of step
 * rather than marching. Paused, they hold where they are, as the phone's do;
 * under reduced motion they stand still.
 */
export function PlayingBars({ playing = true, className = '' }: { playing?: boolean; className?: string }) {
  const bars = useRef<(HTMLSpanElement | null)[]>([]);
  const animations = useRef<Animation[]>([]);

  useEffect(() => {
    if (matchMedia('(prefers-reduced-motion: reduce)').matches) return;
    animations.current = bars.current.flatMap((bar, i) => {
      const duration = 520 + i * 130;
      const low = `scaleY(${MINIMUMS[i]})`;
      const animation = bar?.animate?.(
        [{ transform: low }, { transform: 'scaleY(1)', offset: (260 + i * 50) / duration }, { transform: low }],
        { duration, iterations: Infinity, direction: 'alternate' },
      );
      return animation ? [animation] : [];
    });
    return () => animations.current.forEach((animation) => animation.cancel());
  }, []);

  useEffect(() => {
    animations.current.forEach((animation) => (playing ? animation.play() : animation.pause()));
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
