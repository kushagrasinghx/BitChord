import './stories.css';
import { bestPart, type ReplayYearStory } from '@bitchord/shared-kotlin';
import { ChevronLeft, ChevronRight, Share, Volume2, VolumeX, X } from 'lucide-react';
import { useEffect, useRef, useState, type CSSProperties, type PointerEvent } from 'react';
import { createPortal } from 'react-dom';
import { meshPalette } from '@/lib/meshPalette';
import { MeshBackdrop } from '@/shell/MeshBackdrop';
import { usePlayer } from '@/state/player';
import { accentOf } from '../colors';
import { heatmapBestPart, type BestPartLookup } from './bestPart';
import { YearClipPlayer, type Clip } from './clipPlayer';
import { PAGE_MS, revealAtMs, slideHue, type YearSlide } from './content';
import { HeldContext, SlideContent } from './slides';

/** The "building your year" screen stays at least this long, so it reads as a beat rather than a flash… */
const BUILD_MIN_MS = 1_200;
/** …and never longer than this, whatever is still being looked up. */
const BUILD_MAX_MS = 2_500;
const LOOKUPS_AT_ONCE = 3;
const PRIME_AFTER_MS = 1_200;
/** A best part starts a quarter-second before its line is sung (BestPart.snap). */
const SNAP_LEAD_MS = 250;
/** A press held this long holds the card instead of turning it. */
const HOLD_MS = 250;
const MUTED_KEY = 'bitchord:replay-year-muted';
const YOUTUBE_ID = /^[A-Za-z0-9_-]{11}$/;
/** Where a song with no best part found yet starts (BestPart.fallbackMs of an unknown length). */
const FALLBACK_MS = bestPart(null, [], 0).startMs;

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

function readMuted(): boolean {
  try {
    return localStorage.getItem(MUTED_KEY) === '1';
  } catch {
    return false;
  }
}

/**
 * The Replay year story (ReplayYearStories.kt): the year told as a run of
 * loud, animated cards, each scored with the best part of a song that belongs
 * to it, in the fixed 9:16 frame the phone uses, centred in the window.
 *
 * Click the right of a card (or →) for the next, the left (or ←) for the last;
 * hold the mouse down, or press Space, to stop the clock, music and all. Every
 * card's motion is a CSS animation, so holding pauses them and the progress
 * bar together, and the card turns when its bar ends.
 */
export function ReplayYearStories({
  story,
  year,
  onClose,
  lookup = heatmapBestPart,
}: {
  story: ReplayYearStory;
  year: number;
  onClose: () => void;
  lookup?: BestPartLookup;
}) {
  const slides = story.slides as YearSlide[];
  const [index, setIndex] = useState(0);
  const [forward, setForward] = useState(true);
  const [pressing, setPressing] = useState(false);
  const [paused, setPaused] = useState(false);
  const [sharing, setSharing] = useState(false);
  const [hidden, setHidden] = useState(() => document.visibilityState === 'hidden');
  const [ready, setReady] = useState(false);
  const [muted, setMuted] = useState(readMuted);
  const [accent, setAccent] = useState('hsl(350 90% 70%)');
  const player = useRef<YearClipPlayer | null>(null);
  const starts = useRef(new Map<string, number>());
  const close = useRef<HTMLButtonElement>(null);
  const held = pressing || paused || sharing || hidden;
  const slide = slides[Math.min(index, slides.length - 1)]!;
  const art = story.clips[index]?.art ?? story.charts.songs[0]?.art ?? null;
  const hue = slideHue(slide);

  /** [i]'s clip, started early enough that the chorus's first word lands on the card's reveal. */
  const clipFor = (i: number): Clip | null => {
    const song = story.clips[i];
    const card = slides[i];
    if (!song || !card || !YOUTUBE_ID.test(song.videoId)) return null;
    const best = starts.current.get(song.videoId) ?? FALLBACK_MS;
    const startMs = Math.max(0, best + SNAP_LEAD_MS - revealAtMs(card));
    return { key: `${song.videoId}@${startMs}`, videoId: song.videoId, startMs };
  };

  // The soundtrack's players, and whatever was playing stops for the story and stays stopped after (MainActivity).
  useEffect(() => {
    const clips = new YearClipPlayer();
    player.current = clips;
    const { status, togglePlay } = usePlayer.getState();
    if (status === 'playing') togglePlay();
    const opener = document.activeElement as HTMLElement | null;
    close.current?.focus();
    return () => {
      clips.release();
      player.current = null;
      opener?.focus();
    };
  }, []);

  // Every card's best part, three at a time in the order the cards are told, so the first are ready first.
  // The story waits for the opening card's (or BUILD_MAX_MS) behind "Building your year…".
  useEffect(() => {
    let live = true;
    const songs = [...new Set(story.clips.map((s) => s?.videoId).filter((id): id is string => !!id && YOUTUBE_ID.test(id)))];
    let next = 0;
    const work = async () => {
      while (live && next < songs.length) {
        const id = songs[next++]!;
        const ms = await lookup(id).catch(() => null);
        if (live && ms !== null) starts.current.set(id, ms);
      }
    };
    for (let i = 0; i < LOOKUPS_AT_ONCE; i++) void work();
    void (async () => {
      const began = performance.now();
      const first = story.clips[0]?.videoId;
      while (live && first && songs.includes(first) && !starts.current.has(first) && performance.now() - began < BUILD_MAX_MS) await sleep(50);
      if (!live) return;
      player.current?.prime(clipFor(0));
      await sleep(BUILD_MIN_MS - (performance.now() - began));
      if (live) setReady(true);
    })();
    return () => {
      live = false;
    };
    // clipFor reads the story and refs only.
  }, [story, lookup]);

  // This card's clip in, and the next one loaded while it plays.
  useEffect(() => {
    if (!ready) return;
    player.current?.play(clipFor(index));
    const timer = setTimeout(() => player.current?.prime(clipFor(index + 1)), PRIME_AFTER_MS);
    return () => clearTimeout(timer);
  }, [index, ready]);

  useEffect(() => player.current?.hold(held), [held]);

  useEffect(() => {
    player.current?.setMuted(muted);
    try {
      localStorage.setItem(MUTED_KEY, muted ? '1' : '0');
    } catch {
      // Private windows: mute just isn't remembered.
    }
  }, [muted]);

  // Off screen the story stops where it is, as if held, and carries on from there on coming back.
  useEffect(() => {
    const onVisibility = () => setHidden(document.visibilityState === 'hidden');
    document.addEventListener('visibilitychange', onVisibility);
    return () => document.removeEventListener('visibilitychange', onVisibility);
  }, []);

  // The card's highlight, from its artwork, turned with the backdrop.
  useEffect(() => {
    if (!art) return;
    let live = true;
    void meshPalette(art).then(
      (cells) => live && setAccent(accentOf(cells, hue)),
      () => undefined,
    );
    return () => {
      live = false;
    };
  }, [art, hue]);

  const goTo = (target: number) => {
    if (target >= slides.length) return onClose();
    if (target < 0) return;
    setForward(target > index);
    setIndex(target);
  };

  // Ahead of the app's own shortcuts: Space here holds the story, not the player underneath.
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      const keys: Record<string, () => void> = {
        ArrowRight: () => goTo(index + 1),
        ArrowLeft: () => goTo(index - 1),
        ' ': () => setPaused((p) => !p),
        Escape: onClose,
        m: () => setMuted((m) => !m),
      };
      const run = keys[event.key];
      if (!run || event.metaKey || event.ctrlKey || event.altKey) return;
      if (event.key === ' ' && event.target instanceof HTMLButtonElement) return;
      event.preventDefault();
      event.stopPropagation();
      run();
    };
    window.addEventListener('keydown', onKey, true);
    return () => window.removeEventListener('keydown', onKey, true);
  });

  // A short press turns the card (left third back, the rest on); a long one holds it until let go.
  const press = useRef<{ timer: ReturnType<typeof setTimeout>; long: boolean } | null>(null);
  const onPointerDown = (event: PointerEvent) => {
    if (event.button !== 0 || (event.target as HTMLElement).closest('button')) return;
    const state = { long: false, timer: setTimeout(() => ((state.long = true), setPressing(true)), HOLD_MS) };
    press.current = state;
  };
  const onPointerUp = (event: PointerEvent<HTMLDivElement>) => {
    const state = press.current;
    press.current = null;
    if (!state) return;
    clearTimeout(state.timer);
    setPressing(false);
    if (state.long) return;
    const box = event.currentTarget.getBoundingClientRect();
    goTo(event.clientX - box.left < box.width / 3 ? index - 1 : index + 1);
  };
  const onPointerCancel = () => {
    if (press.current) clearTimeout(press.current.timer);
    press.current = null;
    setPressing(false);
  };

  const share = async () => {
    setSharing(true);
    try {
      const { renderYearCard, sharePicture } = await import('../poster');
      await sharePicture(await renderYearCard(story, slide, year, art), 'replay-year.png', 'Share your Replay');
    } finally {
      setSharing(false);
    }
  };

  const chrome = 'flex size-9 items-center justify-center rounded-full text-white transition-[background-color] hover:bg-white/15';
  const arrow = 'flex size-12 flex-none items-center justify-center rounded-full bg-white/10 text-white transition-[background-color,opacity] hover:bg-white/20 disabled:opacity-0 max-md:hidden';
  // How each card arrives (transitionInto): zooming through, rising up or sweeping across, in turn.
  const from = [
    forward ? 'scale(0.82)' : 'scale(1.18)',
    forward ? 'translateY(50%)' : 'translateY(-50%)',
    forward ? 'translateX(100%)' : 'translateX(-100%)',
  ][index % 3];

  return createPortal(
    <div
      className="ry-story fixed inset-0 z-[calc(var(--z-contextual-menus)-1)] isolate flex items-center justify-center gap-6 bg-black text-white select-none"
      role="dialog"
      aria-modal="true"
      aria-label={`Your Replay ${year}`}
      data-held={held || undefined}
    >
      <button className={arrow} aria-label="Previous card" disabled={index === 0 || !ready} onClick={() => goTo(index - 1)}>
        <ChevronLeft size={26} />
      </button>
      <div
        className="relative isolate aspect-[9/16] h-[min(calc(100dvh-48px),calc((100vw-32px)*16/9))] overflow-hidden rounded-[22px] [container-type:size] shadow-[0_30px_120px_rgb(0_0_0/0.6)] max-md:h-[min(100dvh,calc(100vw*16/9))] max-md:rounded-none"
        onPointerDown={onPointerDown}
        onPointerUp={onPointerUp}
        onPointerCancel={onPointerCancel}
        onPointerLeave={onPointerCancel}
      >
        {/* The artwork mesh, turned to a new colour per card, as the Replay story does. */}
        <div className="absolute inset-0 -z-1 transition-[filter] duration-700" style={{ filter: `hue-rotate(${hue}deg)` }}>
          <MeshBackdrop artworkUrl={art} playing={!held} />
        </div>
        <div className="absolute inset-0 -z-1 bg-black/30" />

        <div className="absolute inset-x-0 top-0 z-1 px-[4.9cqw] pt-[1.4cqh]">
          <div className="flex items-center justify-end gap-1">
            <button className={chrome} aria-label={muted ? 'Unmute the story' : 'Mute the story'} onClick={() => setMuted((m) => !m)}>
              {muted ? <VolumeX size={20} /> : <Volume2 size={20} />}
            </button>
            <button ref={close} className={chrome} aria-label="Close Replay" onClick={onClose}>
              <X size={22} />
            </button>
          </div>
          <div className="mt-1 flex gap-1" aria-hidden="true">
            {slides.map((s, i) => (
              <div key={s} className="h-[3px] flex-1 overflow-hidden rounded-full bg-white/30">
                {i < index && <div className="size-full bg-white" />}
                {i === index && ready && (
                  <div
                    key={index}
                    className="ry-progress size-full bg-white"
                    style={{ animationDuration: `${PAGE_MS}ms` }}
                    onAnimationEnd={() => goTo(index + 1)}
                  />
                )}
              </div>
            ))}
          </div>
          <p className="mt-[1.8cqh] text-[2.2cqh] font-bold">Replay {year % 100}</p>
        </div>

        <HeldContext.Provider value={held}>
          {ready ? (
            <div key={index} className="ry-card-in absolute inset-0" style={{ '--ry-from': from } as CSSProperties}>
              <SlideContent story={story} slide={slide} year={year} accent={accent} />
            </div>
          ) : (
            <div className="absolute inset-0 flex flex-col items-center justify-center gap-5">
              <div className="size-[18cqh] animate-spin rounded-full border-[0.6cqh] border-white/20 border-t-white" />
              <p className="ry-breathe text-[3cqh] font-extrabold">Building your year…</p>
            </div>
          )}
        </HeldContext.Provider>

        {ready && (
          <button
            className="absolute right-[4.9cqw] bottom-[3cqh] z-1 flex h-10 items-center gap-2 rounded-pill bg-white/15 px-4 text-[2cqh] font-bold text-white backdrop-blur-md transition-[background-color] hover:bg-white/25 disabled:opacity-50"
            disabled={sharing}
            onClick={() => void share()}
          >
            <Share size={16} strokeWidth={2.4} />
            Share
          </button>
        )}
      </div>
      <button className={arrow} aria-label="Next card" disabled={!ready} onClick={() => goTo(index + 1)}>
        <ChevronRight size={26} />
      </button>
    </div>,
    document.body,
  );
}
