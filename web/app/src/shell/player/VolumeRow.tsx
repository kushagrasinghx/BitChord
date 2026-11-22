import { useEffect, useRef, useState } from 'react';
import { usePlayer } from '@/state/player';
import { nextVolumeFromWheel } from '../wheelVolume';
import { ThinSlider } from './ThinSlider';

// Material's rounded VolumeDown and VolumeUp, the glyphs the phone draws either side (PlayerControls.kt VolumeRow).
const DOWN =
  'M18.5 12c0-1.77-1.02-3.29-2.5-4.03v8.05c1.48-.73 2.5-2.25 2.5-4.02zM5 10v4c0 .55.45 1 1 1h3l3.29 3.29c.63.63 1.71.18 1.71-.71V6.41c0-.89-1.08-1.34-1.71-.71L9 9H6c-.55 0-1 .45-1 1z';
const UP =
  'M3 10v4c0 .55.45 1 1 1h3l3.29 3.29c.63.63 1.71.18 1.71-.71V6.41c0-.89-1.08-1.34-1.71-.71L7 9H4c-.55 0-1 .45-1 1zm13.5 2c0-1.77-1.02-3.29-2.5-4.03v8.05c1.48-.73 2.5-2.25 2.5-4.02zM14 4.45v.2c0 .38.25.71.6.85C17.18 6.53 19 9.06 19 12s-1.82 5.47-4.4 6.5c-.36.14-.6.47-.6.85v.2c0 .63.63 1.07 1.21.85C18.6 19.11 21 15.84 21 12s-2.4-7.11-5.79-8.4c-.58-.23-1.21.22-1.21.85z';

const Speaker = ({ d }: { d: string }) => (
  <svg className="size-5 flex-none text-white/50" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
    <path d={d} />
  </svg>
);

/**
 * The volume bar between its two speakers (PlayerControls.kt VolumeRow): the
 * pane's full width, 20px glyphs 10px off a ThinSlider that rests at 6px and
 * swells to 10. Drag or click to set it, arrow keys step it, and the wheel
 * turns it as it does over the mini player's speaker.
 */
export function VolumeRow() {
  const volume = usePlayer((s) => (s.muted ? 0 : s.volume));
  const setVolume = usePlayer((s) => s.setVolume);
  const [held, setHeld] = useState(false);
  const slider = useRef<HTMLDivElement>(null);
  const at = (event: React.PointerEvent<HTMLElement>) => {
    const rect = event.currentTarget.getBoundingClientRect();
    const x = (event.clientX - rect.left) / rect.width;
    return Math.min(1, Math.max(0, getComputedStyle(event.currentTarget).direction === 'rtl' ? 1 - x : x));
  };

  // A native listener: React's onWheel is passive and can't keep the page from scrolling.
  useEffect(() => {
    const el = slider.current;
    if (!el) return;
    const onWheel = (e: WheelEvent) => {
      const { volume: now, setVolume: set } = usePlayer.getState();
      const next = nextVolumeFromWheel(now, e.deltaY);
      if (next === now) return;
      e.preventDefault();
      set(next);
    };
    el.addEventListener('wheel', onWheel, { passive: false });
    return () => el.removeEventListener('wheel', onWheel);
  }, []);

  return (
    <div className="flex items-center gap-2.5">
      <Speaker d={DOWN} />
      <div ref={slider} className="min-w-0 flex-1">
        <ThinSlider
          progress={volume}
          active={held}
          idle={6}
          pressed={10}
          role="slider"
          tabIndex={0}
          aria-label="Volume"
          aria-valuemin={0}
          aria-valuemax={100}
          aria-valuenow={Math.round(volume * 100)}
          className="rounded-row focus-visible:bg-none focus-visible:bg-white/14"
          onPointerDown={(e) => {
            e.currentTarget.setPointerCapture(e.pointerId);
            setHeld(true);
            setVolume(at(e));
          }}
          onPointerMove={(e) => held && setVolume(at(e))}
          onPointerUp={() => setHeld(false)}
          onPointerCancel={() => setHeld(false)}
          onKeyDown={(e) => {
            const step = { ArrowRight: 0.05, ArrowUp: 0.05, ArrowLeft: -0.05, ArrowDown: -0.05 }[e.key];
            if (step === undefined) return;
            e.preventDefault();
            e.stopPropagation();
            setVolume(Math.min(1, Math.max(0, volume + step)));
          }}
        />
      </div>
      <Speaker d={UP} />
    </div>
  );
}
