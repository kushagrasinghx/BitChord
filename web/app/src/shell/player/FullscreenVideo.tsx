import { Maximize, Minimize } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { VideoSurface } from '@/components/music/VideoSurface';
import { usePlayer } from '@/state/player';
import { VideoQuality } from '../VideoQuality';
import { Scrubber } from './Scrubber';
import { Transport } from './Transport';

/** MusicVideoLayout.kt VIDEO_CONTROLS_IDLE_MS: how long the controls stay over a playing video untouched. */
const IDLE_MS = 3500;

/**
 * The two boxes on a music video's top-right corner (MusicVideoLayout.kt
 * VideoCornerControls): the quality, and the button into or out of the
 * video's own full screen, 30px, 8px corners, black at 38%, 6px apart.
 */
export function VideoCornerControls({ fullScreen, onToggle, className = '' }: { fullScreen: boolean; onToggle: () => void; className?: string }) {
  const label = fullScreen ? 'Exit full screen' : 'Full screen video';
  return (
    <div className={`flex items-center gap-1.5 ${className}`}>
      <VideoQuality />
      <button
        className="grid size-[30px] place-items-center rounded-[8px] bg-black/38 text-white transition-colors hover:bg-black/50"
        aria-label={label}
        title={`${label} (F)`}
        aria-keyshortcuts="F"
        onClick={(e) => {
          e.stopPropagation();
          onToggle();
        }}
      >
        {fullScreen ? <Minimize size={18} /> : <Maximize size={18} />}
      </button>
    </div>
  );
}

/**
 * The music video on its own (MusicVideoLayout.kt FullscreenVideoPlayer):
 * the picture is the whole window, letterboxed in black, with the browser's
 * own full screen asked for, as the phone puts its system bars away. The
 * title, the transport and the seek bar sit over it on a dark wash and stand
 * down 3.5s after the last movement while it plays, the pointer with them;
 * moving or tabbing brings them back. A click on the picture plays or
 * pauses, as in every desktop video player, and a double click, Esc, F or
 * the corner button goes back to the player.
 */
export function FullscreenVideo({ onExit }: { onExit: () => void }) {
  const nowPlaying = usePlayer((s) => s.nowPlaying);
  const playing = usePlayer((s) => s.status === 'playing' || s.status === 'buffering');
  const [shown, setShown] = useState(true);
  const [moves, setMoves] = useState(0);
  const root = useRef<HTMLDivElement>(null);
  const exit = useRef(onExit);
  exit.current = onExit;

  useEffect(() => {
    if (!shown || !playing) return;
    const timer = setTimeout(() => setShown(false), IDLE_MS);
    return () => clearTimeout(timer);
  }, [shown, playing, moves]);

  // The browser's full screen for as long as this is up; leaving it (the browser's own Esc) leaves this too.
  useEffect(() => {
    let entered = false;
    const onChange = () => {
      if (entered && !document.fullscreenElement) exit.current();
    };
    if (!document.fullscreenElement && document.fullscreenEnabled) {
      void document.documentElement.requestFullscreen().then(
        () => (entered = true),
        () => undefined,
      );
    }
    document.addEventListener('fullscreenchange', onChange);
    // Ahead of the app's Esc, which would close the whole player.
    const onKey = (event: KeyboardEvent) => {
      if ((event.key !== 'Escape' && event.key.toLowerCase() !== 'f') || event.metaKey || event.ctrlKey || event.altKey) return;
      if (event.target instanceof Element && event.target.closest('input, textarea, [role="menu"]')) return;
      event.preventDefault();
      event.stopImmediatePropagation();
      exit.current();
    };
    window.addEventListener('keydown', onKey, true);
    root.current?.focus({ preventScroll: true });
    return () => {
      document.removeEventListener('fullscreenchange', onChange);
      window.removeEventListener('keydown', onKey, true);
      if (entered && document.fullscreenElement) void document.exitFullscreen();
    };
  }, []);

  const wake = () => {
    setShown(true);
    setMoves((n) => n + 1);
  };

  return (
    <div
      ref={root}
      className={`absolute inset-0 z-2 bg-black outline-none ${shown ? '' : 'cursor-none'}`}
      role="dialog"
      aria-label="Music video"
      tabIndex={-1}
      data-no-sheet-drag
      onPointerMove={wake}
      onFocus={wake}
      onClick={(e) => {
        if (!(e.target as Element).closest('button, a, [role="slider"], [role="menu"]')) usePlayer.getState().togglePlay();
        wake();
      }}
      onDoubleClick={(e) => {
        if (!(e.target as Element).closest('button, [role="slider"]')) onExit();
      }}
    >
      <VideoSurface fit="contain" className="pointer-events-none absolute inset-0" />
      <div
        className={`absolute inset-0 flex flex-col bg-[linear-gradient(rgb(0_0_0/0.62),transparent_30%,transparent_55%,rgb(0_0_0/0.78))] px-8 py-6 transition-opacity motion-reduce:transition-none ${shown ? 'opacity-100 duration-200' : 'pointer-events-none opacity-0 duration-300'}`}
      >
        <div className="flex items-center gap-4">
          <div className="flex min-w-0 flex-1 flex-col">
            <span className="truncate type-[600_18px/22px] text-white">{nowPlaying?.title}</span>
            <span className="truncate type-[400_14px/18px] text-white/70">{nowPlaying?.artist}</span>
          </div>
          <VideoCornerControls fullScreen onToggle={onExit} />
        </div>
        <div className="pointer-events-none flex flex-1 items-center justify-center">
          <div className="pointer-events-auto w-full max-w-[420px]">
            <Transport compact={false} />
          </div>
        </div>
        <Scrubber />
      </div>
    </div>
  );
}
