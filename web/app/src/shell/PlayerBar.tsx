import { useEffect, useRef, useState, type PointerEvent } from 'react';
import { LyricsActiveIcon, LyricsIcon, UpNextIcon } from '@/components/icons/Transport';
import { ActionMenuHost } from '@/components/music/ActionMenuHost';
import { contextMenu } from '@/components/music/actionMenu';
import { TrackMenu } from '@/components/music/TrackMenu';
import { usePlayer } from '@/state/player';
import { useUi } from '@/state/ui';
import { Lcd } from './Lcd';
import { ThinSlider } from './player/ThinSlider';
import { ShortcutsHost } from './ShortcutsSheet';
import { PlayPauseGlyph, SkipGlyph } from './TransportGlyphs';
import { useScrubber } from './useScrubber';
import { fadeForVolume, VolumeControl } from './VolumeControl';

// MiniPlayer.kt GLYPH_SLOT: the transport's 40px targets, with the phone's press disc filling them.
const slot = 'press relative grid size-10 flex-none place-items-center rounded-full text-primary [--press-size:40px] disabled:opacity-30';
const action = 'press relative grid size-8 flex-none place-items-center rounded-full text-primary [--press-size:32px] aria-pressed:bg-primary/12';
// The actions the volume level slides in over: they fade out while it's open.
const fading = `${action} ${fadeForVolume}`;

/** MiniPlayer.kt TRACK_SWIPE_THRESHOLD: how far sideways a swipe goes to change track. */
const SWIPE = 72;
/** MiniPlayer.kt PULL_BIAS: steeper than ~34° above level is a pull up, not a swipe. */
const PULL_BIAS = 1.5;
/** How far a pull goes before it opens the player rather than springing back. */
const PULL_OPENS = 40;
/** MiniPlayer.kt pullLift: the bar rises half as far as the pointer, and less the further it goes. */
const pullLift = (pulled: number) => (pulled <= 0 ? 0 : 0.5 * 2 * 72 * (Math.sqrt(1 + pulled / 72) - 1));
/** The bar's spring home, spring(dampingRatio 0.65, StiffnessMediumLow). */
const SETTLE =
  'linear(0, 0.122, 0.371, 0.628, 0.833, 0.968, 1.04, 1.066, 1.065, 1.05, 1.032, 1.016, 1.005, 0.999, 0.996, 0.995, 0.996, 0.997, 0.999, 0.999, 1)';

/**
 * The bar's drags (MiniPlayer.kt miniPlayerGestures), for a mouse and a
 * trackpad: a swipe sideways, by drag or by a horizontal wheel, changes
 * track (leftwards is next, as the phone's is), and a drag upwards pulls
 * the bar up a little and opens the player. A drag that did either isn't
 * also a click.
 */
function useBarGestures(onOpen: () => void) {
  const bar = useRef<HTMLDivElement>(null);
  const drag = useRef<{ x: number; y: number; id: number; axis: 'x' | 'y' | null; lift: number } | null>(null);
  const dragged = useRef(false);
  const skip = (forward: boolean) => {
    const { next, previous, nowPlaying } = usePlayer.getState();
    if (nowPlaying) (forward ? next : previous)();
  };

  useEffect(() => {
    const el = bar.current!;
    let sum = 0;
    let fired = false;
    let idle = 0;
    const onWheel = (event: WheelEvent) => {
      if (Math.abs(event.deltaX) <= Math.abs(event.deltaY)) return;
      // Kept from the browser, which would take a horizontal swipe as back or forward.
      event.preventDefault();
      clearTimeout(idle);
      idle = window.setTimeout(() => ((sum = 0), (fired = false)), 250);
      sum += event.deltaX;
      // One skip per swipe, however far it goes, as the phone's waits for the finger to lift.
      if (!fired && Math.abs(sum) >= SWIPE) {
        fired = true;
        skip(sum > 0);
      }
    };
    el.addEventListener('wheel', onWheel, { passive: false });
    return () => el.removeEventListener('wheel', onWheel);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const settle = (lift: number) => {
    const el = bar.current!;
    el.style.translate = '';
    if (lift > 0 && !window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      el.animate([{ translate: `0 ${-lift}px` }, { translate: '0 0' }], { duration: 560, easing: SETTLE });
    }
  };

  const handlers = {
    onPointerDown: (event: PointerEvent<HTMLDivElement>) => {
      dragged.current = false;
      if (event.button !== 0 || (event.target as Element).closest('button:not([data-open-player]),input,a,[data-scrubber]')) return;
      drag.current = { x: event.clientX, y: event.clientY, id: event.pointerId, axis: null, lift: 0 };
    },
    onPointerMove: (event: PointerEvent<HTMLDivElement>) => {
      const d = drag.current;
      if (!d || d.id !== event.pointerId) return;
      const dx = event.clientX - d.x;
      const dy = event.clientY - d.y;
      if (!d.axis) {
        if (Math.hypot(dx, dy) < 6) return;
        // Decided once, by the way it's heading as it passes the slop.
        d.axis = dy < 0 && Math.abs(dx) < -dy * PULL_BIAS ? 'y' : Math.abs(dx) >= Math.abs(dy) ? 'x' : null;
        if (!d.axis) return void (drag.current = null);
        dragged.current = true;
        event.currentTarget.setPointerCapture(event.pointerId);
      }
      if (d.axis === 'y' && !window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
        d.lift = pullLift(-dy);
        event.currentTarget.style.translate = `0 ${-d.lift}px`;
      }
    },
    onPointerUp: (event: PointerEvent<HTMLDivElement>) => {
      const d = drag.current;
      drag.current = null;
      if (!d?.axis || d.id !== event.pointerId) return;
      const dx = event.clientX - d.x;
      const dy = event.clientY - d.y;
      settle(d.lift);
      if (d.axis === 'x' && Math.abs(dx) >= SWIPE) skip(dx < 0);
      if (d.axis === 'y' && -dy >= PULL_OPENS && usePlayer.getState().nowPlaying) onOpen();
    },
    onPointerCancel: () => {
      settle(drag.current?.lift ?? 0);
      drag.current = null;
    },
  };
  return { bar, dragged, handlers };
}

/**
 * The capsule's seek line, for a desktop window (lg, 1000px and up): the full
 * player's ThinSlider laid along the straight part of the capsule's bottom
 * edge, 3px at rest and 5px under the pointer or while held, played in the
 * theme's text colour rather than the player's white since the capsule
 * follows the theme. Its hit area is 16px, reaching a little past the rim so
 * the edge itself is easy to catch without covering the buttons above it.
 */
function BarScrubber({ scrubber }: { scrubber: ReturnType<typeof useScrubber> }) {
  const [over, setOver] = useState(false);
  return (
    <ThinSlider
      fill={scrubber.bar}
      active={over || scrubber.dragging}
      idle={3}
      pressed={5}
      data-scrubber
      aria-hidden="true"
      className="absolute inset-x-7 -bottom-[5px] [--slider-fill:color-mix(in_srgb,var(--color-primary)_72%,transparent)] [--slider-track:color-mix(in_srgb,var(--color-primary)_14%,transparent)] max-lg:hidden"
      style={{ height: 16 }}
      onPointerEnter={() => setOver(true)}
      onPointerLeave={() => setOver(false)}
      {...scrubber.handlers}
    />
  );
}

/**
 * The mini player (MiniPlayer.kt), as a floating capsule: cover and names,
 * play and next, then the desktop's own lyrics, queue, ⋯ and volume. A thin
 * frosted glass with a 0.5px rim and no shadow, 56px tall. Clicking anywhere
 * on it that isn't a button opens the full-screen player; right-click opens
 * the song's menu.
 *
 * A desktop window (1000px and up) has the mouse and room the phone hasn't:
 * Previous before Play, the seek line along the bottom edge, and the elapsed
 * and remaining times beside the artist while the pointer is over the capsule.
 * The swipes and the pull up still work there, as extras.
 */
export function PlayerBar() {
  const sidePanel = useUi((s) => s.sidePanel);
  const toggleSidePanel = useUi((s) => s.toggleSidePanel);
  const setFullScreen = useUi((s) => s.setFullScreen);
  const hasTrack = usePlayer((s) => s.nowPlaying !== null);
  // The queue's entry for what's playing: the track menu needs the full track.
  const track = usePlayer((s) => {
    const entry = s.queue[s.index]?.track;
    return entry && entry.videoId === s.nowPlaying?.videoId ? entry : null;
  });
  const loading = usePlayer((s) => s.status === 'loading');
  const playing = usePlayer((s) => s.status === 'playing' || s.status === 'buffering');
  const hasNext = usePlayer((s) => s.index + 1 < s.queue.length || s.repeat !== 'off' || (s.autoplay && s.index >= 0));
  const { togglePlay, next, previous } = usePlayer.getState();
  const [volumeOpen, setVolumeOpen] = useState(false);
  // Presses of previous and next, for the skip glyphs' conveyor.
  const [skips, setSkips] = useState({ previous: 0, next: 0 });
  const scrubber = useScrubber();
  const { bar, dragged, handlers } = useBarGestures(() => setFullScreen(true));

  return (
    // The side panel overlays the page; the pill re-centres in what's left.
    <div
      className="pointer-events-none absolute right-0 bottom-(--player-bottom-offset) left-(--web-navigation-width) z-[calc(var(--z-web-chrome)-1)] flex justify-center px-5 transition-[padding-inline-end] duration-(--duration-panel) ease-panel data-[panel-open=true]:pe-[calc(var(--side-panel-width)+20px)] max-sm:hidden"
      data-panel-open={sidePanel !== null}
    >
      {/* The app's one song and release menu, here because the bar is up for the life of the shell. */}
      <ActionMenuHost />
      <ShortcutsHost />
      <div
        ref={bar}
        className="group/bar pointer-events-auto relative flex h-(--player-pill-height) w-[min(668px,100%)] cursor-default touch-none items-center rounded-pill glass-material px-3 shadow-[inset_0_0_0_0.5px_rgb(255_255_255/0.1)] select-none"
        role="region"
        aria-label="Player"
        data-scrubbing={scrubber.dragging || undefined}
        onClick={(event) => {
          if (dragged.current || !hasTrack || (event.target as Element).closest('button,input,a,[data-scrubber]')) return;
          setFullScreen(true);
        }}
        onContextMenu={contextMenu(track && { kind: 'track', track, player: true })}
        {...handlers}
      >
        <Lcd
          times={
            // Shown while the pointer is over the capsule or the line is held; the hook writes them every frame.
            <span className="hidden flex-none ps-2 type-footnote text-on-surface-variant tabular-nums lg:group-hover/bar:inline lg:group-data-scrubbing/bar:inline">
              <span ref={scrubber.elapsed} />
              <span aria-hidden="true"> · </span>
              <span ref={scrubber.remaining} />
            </span>
          }
        />
        {/* MiniPlayer.kt draws no previous; a desktop window has the room, mirrored on next's gap. */}
        <button
          className={`${slot} me-2 max-lg:hidden`}
          disabled={!hasTrack}
          aria-label="Previous"
          onClick={() => {
            setSkips((s) => ({ ...s, previous: s.previous + 1 }));
            previous();
          }}
        >
          <span className="scale-y-[0.85]">
            <SkipGlyph taps={skips.previous} size={25} previous />
          </span>
        </button>
        {loading ? (
          // MiniPlayer.kt SPINNER_SIZE: a 22px spinner in the play glyph's place.
          <span className="grid size-10 flex-none place-items-center" role="status" aria-label="Loading">
            <span className="size-[22px] animate-spin rounded-full border-2 border-primary/20 border-t-primary" />
          </span>
        ) : (
          <button className={slot} disabled={!hasTrack} aria-label={playing ? 'Pause' : 'Play'} onClick={togglePlay}>
            <PlayPauseGlyph playing={playing} size={32} mini />
          </button>
        )}
        {/* MiniPlayer.kt TRANSPORT_GAP, then next at 25px squashed to 0.85 as the full player's is. */}
        <button
          className={`${slot} ms-2`}
          disabled={!hasTrack || !hasNext}
          aria-label="Next"
          onClick={() => {
            setSkips((s) => ({ ...s, next: s.next + 1 }));
            next();
          }}
        >
          <span className="scale-y-[0.85]">
            <SkipGlyph taps={skips.next} size={25} />
          </span>
        </button>

        <div
          className="group/actions relative ms-3 flex items-center gap-1 [--volume-gap:36px]"
          data-volume-open={volumeOpen || undefined}
          onPointerLeave={() => setVolumeOpen(false)}
          onBlur={(e) => {
            if (!e.currentTarget.contains(e.relatedTarget as Node | null)) setVolumeOpen(false);
          }}
        >
          {/* Always here: the panel itself says whether this track has lyrics, and from where. */}
          <button className={fading} aria-label="Lyrics" aria-pressed={sidePanel === 'lyrics'} onClick={() => toggleSidePanel('lyrics')}>
            {sidePanel === 'lyrics' ? <LyricsActiveIcon size={18} /> : <LyricsIcon size={18} />}
          </button>
          <button className={fading} aria-label="Up Next" aria-pressed={sidePanel === 'up-next'} onClick={() => toggleSidePanel('up-next')}>
            <UpNextIcon size={18} />
          </button>
          {track ? <TrackMenu track={track} className={fading} placement="above-end" player /> : <span className="size-8" />}
          <VolumeControl open={volumeOpen} onOpen={() => setVolumeOpen(true)} className={action} />
        </div>
        <BarScrubber scrubber={scrubber} />
      </div>
    </div>
  );
}
