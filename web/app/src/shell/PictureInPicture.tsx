import { useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { PictureInPicture2, X } from 'lucide-react';
import {
  BackwardIcon,
  ForwardIcon,
  PauseIcon,
  PlayIcon,
  RepeatIcon,
  RepeatOneIcon,
  ShuffleIcon,
} from '@/components/icons/Transport';
import { sizedArtwork } from '@/lib/artwork';
import { usePlayer } from '@/state/player';
import { useUi } from '@/state/ui';
import { useScrubber } from './useScrubber';

interface DocumentPictureInPictureOptions {
  width?: number;
  height?: number;
  disallowReturnToOpener?: boolean;
}

interface DocumentPictureInPictureApi {
  requestWindow: (options?: DocumentPictureInPictureOptions) => Promise<Window>;
  window: Window | null;
  addEventListener?: (type: string, listener: EventListenerOrEventListenerObject) => void;
  removeEventListener?: (type: string, listener: EventListenerOrEventListenerObject) => void;
}

declare global {
  interface Window {
    documentPictureInPicture?: DocumentPictureInPictureApi;
  }
}

/** Track the single active Document PiP window instance. */
let pipWindowInstance: Window | null = null;

/** Check whether Document Picture-in-Picture API is supported by the current browser. */
export function isDocumentPipSupported(): boolean {
  return typeof window !== 'undefined' && 'documentPictureInPicture' in window && Boolean(window.documentPictureInPicture);
}

/** Copies styles and links from the parent window to the PiP window. */
export function copyStylesToWindow(targetDoc: Document): void {
  // Transfer stylesheet links and style tags
  const styleElements = Array.from(
    document.querySelectorAll<HTMLLinkElement | HTMLStyleElement>('link[rel="stylesheet"], style'),
  );

  for (const el of styleElements) {
    if (el.tagName === 'LINK') {
      const link = targetDoc.createElement('link');
      link.rel = 'stylesheet';
      link.href = (el as HTMLLinkElement).href;
      targetDoc.head.appendChild(link);
    } else if (el.tagName === 'STYLE') {
      const style = targetDoc.createElement('style');
      style.textContent = el.textContent;
      targetDoc.head.appendChild(style);
    }
  }

  // Also replicate CSS rules from styleSheets for dynamic or CSS module rules
  try {
    for (const sheet of Array.from(document.styleSheets)) {
      try {
        if (sheet.cssRules && sheet.cssRules.length > 0) {
          if (sheet.ownerNode && targetDoc.head.contains(sheet.ownerNode as Node)) {
            continue;
          }
          const style = targetDoc.createElement('style');
          for (const rule of Array.from(sheet.cssRules)) {
            style.appendChild(targetDoc.createTextNode(rule.cssText));
          }
          targetDoc.head.appendChild(style);
        }
      } catch {
        // Cross-origin stylesheet access restriction - already covered via <link>
      }
    }
  } catch {
    // Ignore cross-origin access errors
  }

  // Copy html/body styling and design tokens
  targetDoc.title = document.title || 'BitChord Mini Player';
  targetDoc.documentElement.setAttribute('data-theme', 'dark');
  targetDoc.documentElement.className = document.documentElement.className;
  targetDoc.body.className = 'bg-[#141416] text-primary antialiased select-none overflow-hidden m-0 p-0 h-full w-full';
}

/** Closes the active Document Picture-in-Picture window if open. */
export function closePictureInPicture(): void {
  if (pipWindowInstance && !pipWindowInstance.closed) {
    pipWindowInstance.close();
  }
  pipWindowInstance = null;
  useUi.getState().setPipOpen(false);
}

/** Requests a new Document Picture-in-Picture window and mounts the mini player. */
export async function openPictureInPicture(): Promise<void> {
  if (!isDocumentPipSupported() || !window.documentPictureInPicture) return;

  if (pipWindowInstance && !pipWindowInstance.closed) {
    pipWindowInstance.focus();
    return;
  }

  try {
    const pipWin = await window.documentPictureInPicture.requestWindow({
      width: 340,
      height: 460,
    });

    pipWindowInstance = pipWin;
    copyStylesToWindow(pipWin.document);
    useUi.getState().setPipOpen(true);

    pipWin.addEventListener('pagehide', () => {
      pipWindowInstance = null;
      useUi.getState().setPipOpen(false);
    });
  } catch (error) {
    console.error('Failed to open Document Picture-in-Picture:', error);
    pipWindowInstance = null;
    useUi.getState().setPipOpen(false);
  }
}

/** Toggles Document Picture-in-Picture window. */
export async function togglePictureInPicture(): Promise<void> {
  if (pipWindowInstance && !pipWindowInstance.closed) {
    closePictureInPicture();
  } else {
    await openPictureInPicture();
  }
}

/** Scrubber bar specifically sized for the compact mini player. */
function MiniPlayerScrubber() {
  const { bar, elapsed, remaining, dragging, handlers } = useScrubber();
  return (
    <div className="group/scrub w-full" data-dragging={dragging || undefined}>
      <div className="group/hit flex h-3.5 cursor-pointer touch-none items-center" {...handlers}>
        <div className="relative h-1 w-full overflow-hidden rounded-[999px] bg-quaternary transition-[height] duration-120 ease-in group-hover/hit:h-1.5 group-data-dragging/scrub:h-1.5">
          <div
            ref={bar}
            className="relative size-full origin-left scale-x-(--progress) bg-primary transition-[background-color] duration-120 ease-in [--progress:0]"
          />
        </div>
      </div>
      <div className="flex justify-between type-[500_10px/12px] tabular-nums text-tertiary">
        <span ref={elapsed}>0:00</span>
        <span ref={remaining}>-0:00</span>
      </div>
    </div>
  );
}

/** Floating mini-player content rendered inside the Document PiP window. */
export function MiniPlayerContent() {
  const nowPlaying = usePlayer((s) => s.nowPlaying);
  const track = usePlayer((s) => {
    const entry = s.queue[s.index]?.track;
    return entry && entry.videoId === s.nowPlaying?.videoId ? entry : null;
  });
  const playing = usePlayer((s) => s.status === 'playing' || s.status === 'buffering');
  const hasNext = usePlayer((s) => s.index + 1 < s.queue.length || s.repeat !== 'off' || (s.autoplay && s.index >= 0));
  const shuffle = usePlayer((s) => s.shuffle);
  const repeat = usePlayer((s) => s.repeat);
  const { togglePlay, next, previous, toggleShuffle, cycleRepeat } = usePlayer.getState();

  if (!nowPlaying) {
    return (
      <div className="flex h-full w-full flex-col items-center justify-center p-6 text-center bg-[#141416] text-secondary">
        <p className="type-title-3 font-semibold text-primary">No track playing</p>
        <p className="mt-1 type-footnote text-tertiary">Select a track in BitChord to start playback</p>
      </div>
    );
  }

  const { title, artist, artworkUrl } = nowPlaying;
  const album = track?.album?.name ?? null;

  return (
    <div className="relative flex h-full w-full flex-col justify-between overflow-hidden bg-[#141416] p-4 text-primary select-none font-sans box-border">
      {/* Top Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-1.5">
          <span className="size-2 rounded-full bg-key animate-pulse" />
          <span className="type-[700_11px/14px] tracking-wider text-secondary uppercase">BitChord</span>
        </div>
        <button
          onClick={closePictureInPicture}
          className="grid size-6 place-items-center rounded-full text-secondary hover:bg-quaternary hover:text-primary transition-colors cursor-pointer"
          aria-label="Close Mini Player"
          title="Return to BitChord"
        >
          <X size={15} />
        </button>
      </div>

      {/* Middle: Artwork & Metadata */}
      <div className="my-auto flex flex-col items-center text-center">
        <div className="relative mb-3.5 size-40 overflow-hidden rounded-[8px] bg-quaternary shadow-[0_8px_24px_rgba(0,0,0,0.5)]">
          {artworkUrl && (
            <img
              src={sizedArtwork(artworkUrl, 320)}
              alt={title}
              className="size-full object-cover"
              referrerPolicy="no-referrer"
            />
          )}
        </div>

        <div className="w-full px-2">
          <div className="truncate type-[700_15px/20px] text-primary" title={title}>
            {title}
          </div>
          <div className="mt-0.5 truncate type-[400_12px/16px] text-secondary" title={artist || undefined}>
            {artist}
            {album && ` — ${album}`}
          </div>
        </div>
      </div>

      {/* Bottom: Progress Scrubber and Transport Controls */}
      <div className="flex flex-col gap-2">
        <MiniPlayerScrubber />

        <div className="flex items-center justify-between px-2 pt-0.5">
          <button
            className="grid size-7 place-items-center rounded-full text-secondary aria-pressed:text-key hover:text-primary transition-colors cursor-pointer"
            aria-label="Shuffle"
            aria-pressed={shuffle}
            onClick={toggleShuffle}
          >
            <ShuffleIcon size={18} />
          </button>

          <div className="flex items-center gap-3">
            <button
              className="grid size-8 place-items-center rounded-full text-primary hover:bg-quaternary transition-colors cursor-pointer disabled:opacity-40"
              disabled={!nowPlaying}
              aria-label="Previous"
              onClick={previous}
            >
              <BackwardIcon size={22} />
            </button>

            <button
              className="grid size-10 place-items-center rounded-full bg-primary text-black hover:scale-105 active:scale-95 transition-transform cursor-pointer disabled:opacity-40"
              disabled={!nowPlaying}
              aria-label={playing ? 'Pause' : 'Play'}
              onClick={togglePlay}
            >
              {playing ? <PauseIcon size={22} /> : <PlayIcon size={22} />}
            </button>

            <button
              className="grid size-8 place-items-center rounded-full text-primary hover:bg-quaternary transition-colors cursor-pointer disabled:opacity-40"
              disabled={!nowPlaying || !hasNext}
              aria-label="Next"
              onClick={next}
            >
              <ForwardIcon size={22} />
            </button>
          </div>

          <button
            className="grid size-7 place-items-center rounded-full text-secondary aria-pressed:text-key hover:text-primary transition-colors cursor-pointer"
            aria-label={repeat === 'one' ? 'Repeat one' : repeat === 'all' ? 'Repeat all' : 'Repeat'}
            aria-pressed={repeat !== 'off'}
            onClick={cycleRepeat}
          >
            {repeat === 'one' ? <RepeatOneIcon size={18} /> : <RepeatIcon size={18} />}
          </button>
        </div>
      </div>
    </div>
  );
}

/** Portals the MiniPlayerContent into the active PiP window document. */
export function PictureInPicturePortal() {
  const pipOpen = useUi((s) => s.pipOpen);
  const [mountNode, setMountNode] = useState<HTMLElement | null>(null);

  useEffect(() => {
    if (pipOpen && pipWindowInstance && !pipWindowInstance.closed) {
      setMountNode(pipWindowInstance.document.body);
    } else {
      setMountNode(null);
    }
  }, [pipOpen]);

  // Clean up PiP window when parent window unloads
  useEffect(() => {
    const handleUnload = () => {
      closePictureInPicture();
    };
    window.addEventListener('beforeunload', handleUnload);
    return () => {
      window.removeEventListener('beforeunload', handleUnload);
    };
  }, []);

  if (!pipOpen || !mountNode) return null;

  return createPortal(<MiniPlayerContent />, mountNode);
}

/** Button to toggle the Document Picture-in-Picture mini player. */
export function PictureInPictureButton({ className, iconSize = 18 }: { className?: string; iconSize?: number }) {
  const supported = isDocumentPipSupported();
  const pipOpen = useUi((s) => s.pipOpen);

  if (!supported) return null;

  return (
    <button
      className={className}
      aria-label={pipOpen ? 'Close Mini Player' : 'Open Mini Player'}
      aria-pressed={pipOpen}
      title={pipOpen ? 'Close Mini Player' : 'Open Mini Player'}
      onClick={() => void togglePictureInPicture()}
    >
      <PictureInPicture2 size={iconSize} />
    </button>
  );
}
