import './PlayingBars.css';
import type { CSSProperties } from 'react';
import { usePlayer } from '@/state/player';

const BARS = ['playing-bar-0 1040ms', 'playing-bar-1 1300ms', 'playing-bar-2 1560ms'];

/**
 * The animated bars over the artwork of whatever is playing (Common.kt
 * SearchPlayingBars): a fixed 24px plate, radius 6, black at 52%, so only the
 * bars move and never the box around them. Three white bars 3px wide, 2px apart.
 */
export function PlayingBars({ className = '' }: { className?: string }) {
  return (
    <span
      className={`grid size-6 place-items-center rounded-[6px] bg-[rgb(0_0_0/0.52)] ${className}`}
      aria-hidden="true"
    >
      <span className="flex h-3.5 w-[13px] items-end gap-0.5">
        {BARS.map((bar) => (
          <span key={bar} className="playing-bar h-full w-[3px] rounded-[1.5px] bg-white" style={{ '--bar': bar } as CSSProperties} />
        ))}
      </span>
    </span>
  );
}

/** True while [videoId] is the track playing (or buffering to play): the bars show only then, as on the phone. */
export function useIsPlaying(videoId: string | null): boolean {
  return usePlayer((s) => !!videoId && s.nowPlaying?.videoId === videoId && (s.status === 'playing' || s.status === 'buffering'));
}
