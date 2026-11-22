import { useState } from 'react';
import { usePlayer } from '@/state/player';
import { PlayPauseGlyph, SkipGlyph } from '../TransportGlyphs';

/*
 * PlayerControls.kt TransportGlyph: the glyph's own white, faded to 30% while
 * there is nowhere to go (faded rather than hidden, so the row keeps its
 * shape), and the phone's press disc behind it (the `press` utility).
 */
const glyph = 'press relative grid place-items-center text-white transition-opacity duration-200 disabled:opacity-30';

/**
 * Previous, play/pause, next, spread across the pane (PlayerControls.kt
 * TransportRow, SpaceAround). Play is 74px in a 92px target (58 in 76 on a
 * short window) and sinks to 90% while pressed; the skips are 53px drawn at
 * 85% of their height, so the pair reads lower and longer beside it. While
 * the stream resolves, a 38px spinner holds the play button's place.
 */
export function Transport({ compact }: { compact: boolean }) {
  const hasTrack = usePlayer((s) => s.nowPlaying !== null);
  const status = usePlayer((s) => s.status);
  const hasNext = usePlayer((s) => s.index + 1 < s.queue.length || s.repeat !== 'off' || (s.autoplay && s.index >= 0));
  const { togglePlay, next, previous } = usePlayer.getState();
  // Presses of previous and next, for the skip glyphs' conveyor.
  const [skips, setSkips] = useState({ previous: 0, next: 0 });
  const playing = status === 'playing' || status === 'buffering';
  const playSize = compact ? 58 : 74;
  const playTouch = compact ? 76 : 92;
  const skipSize = compact ? 44 : 53;

  return (
    <div className="flex items-center justify-around">
      <button
        className={`${glyph} size-[53px]`}
        disabled={!hasTrack}
        aria-label="Previous"
        onClick={() => {
          setSkips((s) => ({ ...s, previous: s.previous + 1 }));
          previous();
        }}
      >
        <span className="grid scale-y-85">
          <SkipGlyph taps={skips.previous} size={skipSize} previous />
        </span>
      </button>
      {status === 'loading' ? (
        // The play target's own footprint, so nothing below moves on every load.
        <span className="grid place-items-center" style={{ width: playTouch, height: playTouch }} role="status" aria-label="Loading">
          <span
            className="animate-spin rounded-full border-3 border-white border-t-transparent motion-reduce:animate-[spin_3s_linear_infinite]"
            style={{ width: compact ? 30 : 38, height: compact ? 30 : 38 }}
          />
        </span>
      ) : (
        <button
          className={`${glyph} transition-[scale,opacity] duration-150 ease-out [--press-size:60px] enabled:active:scale-90`}
          style={{ width: playTouch, height: playTouch }}
          disabled={!hasTrack}
          aria-label={playing ? 'Pause' : 'Play'}
          onClick={togglePlay}
        >
          <PlayPauseGlyph playing={playing} size={playSize} />
        </button>
      )}
      <button
        className={`${glyph} size-[53px]`}
        disabled={!hasTrack || !hasNext}
        aria-label="Next"
        onClick={() => {
          setSkips((s) => ({ ...s, next: s.next + 1 }));
          next();
        }}
      >
        <span className="grid scale-y-85">
          <SkipGlyph taps={skips.next} size={skipSize} />
        </span>
      </button>
    </div>
  );
}
