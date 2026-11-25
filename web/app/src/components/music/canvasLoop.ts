import { useEffect, useRef, type RefObject } from 'react';
import { useReducedMotion } from '@/data/spotify/canvas';

/** At most this often: a decorative loop needs no more than 30 frames a second (Common.kt MOTION_FRAME_GAP_MS). */
const FRAME_MS = 1000 / 30;

/*
 * One clock for every animated canvas: a single 30-per-second timer while any is on screen,
 * stopped when none is, rather than each canvas waking on every display refresh (9 cards on
 * Home asked for ~340 frames a second at idle and kept a core busy).
 */
const ticking = new Set<(now: number) => void>();
let clockTimer: ReturnType<typeof setInterval> | null = null;

/*
 * The pictures move while someone is looking: after this long with no pointer, wheel, key or
 * scroll, the clock holds (a player left in the background then costs nothing), and the next
 * input starts it again where it stopped.
 */
const IDLE_MS = 20_000;
let lastInput = performance.now();
function start() {
  clockTimer ??= setInterval(() => {
    const now = performance.now();
    if (now - lastInput > IDLE_MS) return stop();
    for (const each of ticking) each(now);
  }, FRAME_MS);
}
function stop() {
  if (clockTimer) clearInterval(clockTimer);
  clockTimer = null;
}
if (typeof window !== 'undefined') {
  const input = () => {
    lastInput = performance.now();
    if (ticking.size && !clockTimer) start();
  };
  for (const type of ['pointermove', 'pointerdown', 'wheel', 'keydown', 'scroll'] as const) {
    window.addEventListener(type, input, { passive: true, capture: true });
  }
}
function join(step: (now: number) => void) {
  ticking.add(step);
  if (performance.now() - lastInput <= IDLE_MS) start();
}
function leave(step: (now: number) => void) {
  ticking.delete(step);
  if (!ticking.size) stop();
}

/**
 * Draws [draw] into [canvas] at its CSS size, and keeps drawing it on a clock
 * while [running] (the phone's loopPhase, TopPicksShelf.kt). The clock counts
 * only while the canvas is on screen, the tab is visible and motion is
 * allowed; anywhere else it holds where it is, so the picture doesn't jump
 * back to its start, and nothing is redrawn. Reduced motion draws one still
 * frame at [stillSeconds].
 */
export function useCanvasLoop(
  canvas: RefObject<HTMLCanvasElement | null>,
  draw: (ctx: CanvasRenderingContext2D, width: number, height: number, seconds: number) => void,
  { running = true, stillSeconds = 0, scale = 1 }: { running?: boolean; stillSeconds?: number; scale?: number } = {},
) {
  const still = useReducedMotion();
  const paint = useRef(draw);
  paint.current = draw;
  const clock = useRef(stillSeconds);

  useEffect(() => {
    const el = canvas.current;
    const ctx = el?.getContext('2d');
    if (!el || !ctx) return;
    const animate = running && !still;
    if (!animate) clock.current = still ? stillSeconds : clock.current;
    let last = 0;
    let visible = false;
    const render = () => {
      // Device pixels, capped at 2×; [scale] < 1 for a picture that is blurred anyway.
      const ratio = Math.min(window.devicePixelRatio || 1, 2) * scale;
      const width = Math.max(1, Math.round(el.clientWidth * ratio));
      const height = Math.max(1, Math.round(el.clientHeight * ratio));
      if (el.width !== width || el.height !== height) {
        el.width = width;
        el.height = height;
      }
      ctx.setTransform(ratio, 0, 0, ratio, 0, 0);
      ctx.clearRect(0, 0, el.clientWidth, el.clientHeight);
      paint.current(ctx, el.clientWidth, el.clientHeight, clock.current);
    };
    const step = (now: number) => {
      // A long gap (a hidden window, a scroll away) isn't counted toward the loop.
      clock.current += last ? Math.min(now - last, 100) / 1000 : 0;
      last = now;
      render();
    };
    const wake = () => {
      if (animate && visible && !document.hidden) {
        if (!ticking.has(step)) last = 0;
        join(step);
      } else leave(step);
    };
    const seen = new IntersectionObserver(([entry]) => {
      visible = !!entry?.isIntersecting;
      wake();
    });
    seen.observe(el);
    const resized = new ResizeObserver(render);
    resized.observe(el);
    document.addEventListener('visibilitychange', wake);
    render();
    return () => {
      leave(step);
      seen.disconnect();
      resized.disconnect();
      document.removeEventListener('visibilitychange', wake);
    };
  }, [canvas, running, still, stillSeconds, scale]);
}
