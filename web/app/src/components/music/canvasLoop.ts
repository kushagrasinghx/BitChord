import { useEffect, useRef, type RefObject } from 'react';
import { useReducedMotion } from '@/data/spotify/canvas';

/** At most this often: a decorative loop needs no more than 30 frames a second (Common.kt MOTION_FRAME_GAP_MS). */
const FRAME_MS = 1000 / 30;

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
    let frame = 0;
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
    const tick = (now: number) => {
      frame = 0;
      if (!visible || document.hidden) return;
      if (now - last >= FRAME_MS - 1) {
        // A long gap (a hidden tab, a scroll away) isn't counted toward the loop.
        clock.current += last ? Math.min(now - last, 100) / 1000 : 0;
        last = now;
        render();
      }
      frame = requestAnimationFrame(tick);
    };
    const wake = () => {
      if (animate && visible && !document.hidden && !frame) {
        last = 0;
        frame = requestAnimationFrame(tick);
      }
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
      cancelAnimationFrame(frame);
      seen.disconnect();
      resized.disconnect();
      document.removeEventListener('visibilitychange', wake);
    };
  }, [canvas, running, still, stillSeconds, scale]);
}
