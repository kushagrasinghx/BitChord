import { useEffect, useLayoutEffect, useRef, type RefObject } from 'react';
import { useReducedMotion } from '@/data/spotify/canvas';
import { SHEET_SCRIM_ALPHA, SHEET_SETTLED_PX, sheetSettlesOpen, stepCriticalSpring } from './motion';

/** How far a press has to travel before it is a drag on the sheet rather than a click. */
const DRAG_SLOP_PX = 6;
/** How old the last movement can be and still count as the release's speed (SHEET_VELOCITY_FRESH_MS). */
const VELOCITY_FRESH_MS = 80;
/** The mini player's cover and the sleeve's corners, docked and at rest (PlayerDock, LANDSCAPE_ART_CORNER). */
const MINI_RADIUS = 8;
const SLEEVE_RADIUS = 10;
/** Reduced motion: a short fade instead of the slide and the flight. */
const FADE_MS = 200;

/*
 * The mini player's cover (Lcd.tsx marks it), where the sleeve docks.
 */
const MINI_COVER = '[data-player-cover]';

interface Parts {
  sheet: RefObject<HTMLDivElement | null>;
  scrim: RefObject<HTMLDivElement | null>;
  /** The sleeve's outer layer, which flies between the mini player's cover and its place. */
  dock: RefObject<HTMLDivElement | null>;
}

/**
 * The player as a sheet over the app (MainActivity.kt PlayerSheetMotion):
 * it slides up from the bottom on a critically damped spring, and back down
 * the same way to close; dragged, it follows the pointer, and on release a
 * flick decides by its direction, otherwise whichever side of halfway it was
 * let go. The dim behind it deepens to 32% as it opens.
 *
 * While it travels, the sleeve flies between the mini player's cover and its
 * own place in step with it, its corners rounding from the cover's 8px to its
 * own 10 (PlayerDock), so the artwork is seen to travel home.
 *
 * `open` says where it is going; a drag let go to close asks `onRequestClose`
 * to set it false, and `onClosed` fires once it is all the way down.
 * Returns the pointer handler that starts a drag.
 */
export function useSheet(open: boolean, onRequestClose: () => void, onClosed: () => void, { sheet, scrim, dock }: Parts) {
  const still = useReducedMotion();
  const state = useRef({ y: Number.NaN, v: 0, frame: 0, target: 0, dragging: false, mini: null as DOMRect | null, rest: null as DOMRect | null });
  const closed = useRef(onClosed);
  closed.current = onClosed;

  const travel = () => window.innerHeight;

  /** Lays the sheet, the dim and the sleeve out for an offset of `y` below open. */
  const place = (y: number) => {
    const s = state.current;
    s.y = y;
    const t = travel();
    const open = Math.min(1, Math.max(0, 1 - y / t));
    if (sheet.current) sheet.current.style.transform = y > 0.01 ? `translate3d(0, ${y}px, 0)` : '';
    if (scrim.current) scrim.current.style.opacity = String(SHEET_SCRIM_ALPHA * open);
    const el = dock.current;
    if (!el) return;
    if (!s.mini || !s.rest || open >= 1) {
      el.style.transform = '';
      el.style.removeProperty('--dock-radius');
      return;
    }
    // Where the sleeve should be on screen: from the cover to its own place, as far as the sheet is open.
    const lerp = (a: number, b: number) => a + (b - a) * open;
    const left = lerp(s.mini.left, s.rest.left);
    const top = lerp(s.mini.top, s.rest.top);
    const sx = lerp(s.mini.width, s.rest.width) / s.rest.width;
    const sy = lerp(s.mini.height, s.rest.height) / s.rest.height;
    // ...less where the sheet has carried it.
    el.style.transform = `translate3d(${left - s.rest.left}px, ${top - (s.rest.top + y)}px, 0) scale(${sx}, ${sy})`;
    el.style.setProperty('--dock-radius', `${lerp(MINI_RADIUS, SLEEVE_RADIUS) / sx}px / ${lerp(MINI_RADIUS, SLEEVE_RADIUS) / sy}px`);
  };

  /** The mini player's cover and the sleeve's resting place, measured as a trip begins. */
  const measure = () => {
    const s = state.current;
    const el = dock.current;
    const cover = document.querySelector<HTMLElement>(MINI_COVER)?.getBoundingClientRect();
    s.mini = cover && cover.width > 0 && cover.height > 0 ? cover : null;
    if (!el || !s.mini) return (s.rest = null);
    const transform = el.style.transform;
    el.style.transform = '';
    const rect = el.getBoundingClientRect();
    el.style.transform = transform;
    // Its place with the sheet open: take off the sheet's own offset.
    const offset = Number.isFinite(s.y) ? s.y : 0;
    s.rest = rect.width > 0 ? new DOMRect(rect.left, rect.top - offset, rect.width, rect.height) : null;
  };

  const run = (target: number) => {
    const s = state.current;
    s.target = target;
    cancelAnimationFrame(s.frame);
    let last = performance.now();
    const tick = (now: number) => {
      const dt = Math.min(0.064, (now - last) / 1000);
      last = now;
      const next = stepCriticalSpring(s.y, s.v, target, dt);
      s.v = next.v;
      // Thrown hard at its end it can pass it once: never past open or past the bottom.
      const y = Math.min(travel(), Math.max(0, next.x));
      if (Math.abs(y - target) < SHEET_SETTLED_PX && Math.abs(s.v) < 10) {
        place(target);
        s.v = 0;
        s.frame = 0;
        if (target > 0) closed.current();
        return;
      }
      place(y);
      s.frame = requestAnimationFrame(tick);
    };
    s.frame = requestAnimationFrame(tick);
  };

  // Placed below the window before the first paint, so the opening slide starts from the bottom.
  useLayoutEffect(() => {
    if (still) return;
    place(travel());
    return () => cancelAnimationFrame(state.current.frame);
    // Once, on mount.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    const s = state.current;
    const root = sheet.current;
    if (still) {
      // A short fade either way, nothing moving.
      if (!root) return;
      const fade = root.animate([{ opacity: open ? 0 : 1 }, { opacity: open ? 1 : 0 }], { duration: FADE_MS, fill: 'forwards' });
      if (scrim.current) scrim.current.style.opacity = String(open ? SHEET_SCRIM_ALPHA : 0);
      if (!open) fade.onfinish = () => closed.current();
      return () => fade.cancel();
    }
    if (s.dragging) return;
    measure();
    run(open ? 0 : travel());
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, still]);

  /** A press anywhere the sheet answers to: past the slop downward, the sheet follows it. */
  const onPointerDown = (event: React.PointerEvent<HTMLElement>) => {
    if (still || event.button !== 0 || !open) return;
    const s = state.current;
    const startY = event.clientY;
    const startX = event.clientX;
    const from = Number.isFinite(s.y) ? s.y : 0;
    const target = event.currentTarget;
    const id = event.pointerId;
    let samples: { t: number; y: number }[] = [];
    let dragging = false;

    const move = (e: PointerEvent) => {
      if (e.pointerId !== id) return;
      const dy = e.clientY - startY;
      if (!dragging) {
        if (dy < DRAG_SLOP_PX || Math.abs(e.clientX - startX) > Math.abs(dy)) {
          if (Math.abs(e.clientX - startX) > DRAG_SLOP_PX || dy < -DRAG_SLOP_PX) end();
          return;
        }
        dragging = true;
        s.dragging = true;
        cancelAnimationFrame(s.frame);
        measure();
        target.setPointerCapture(id);
      }
      const y = Math.min(travel(), Math.max(0, from + dy));
      place(y);
      samples = [...samples.filter((p) => e.timeStamp - p.t < VELOCITY_FRESH_MS), { t: e.timeStamp, y }];
    };
    const up = (e: PointerEvent) => {
      if (e.pointerId !== id) return;
      end();
      if (!dragging) return;
      s.dragging = false;
      const first = samples[0];
      const lastSample = samples.at(-1);
      const seconds = first && lastSample ? (lastSample.t - first.t) / 1000 : 0;
      s.v = seconds > 0 ? (lastSample!.y - first!.y) / seconds : 0;
      // Closing goes through the app's own state; the effect above then carries the sheet down at this speed.
      if (sheetSettlesOpen(s.y, travel(), s.v)) run(0);
      else onRequestClose();
      // The click that ends a drag is not a click on what was under it.
      const swallow = (c: MouseEvent) => c.stopPropagation();
      window.addEventListener('click', swallow, { capture: true, once: true });
      setTimeout(() => window.removeEventListener('click', swallow, { capture: true }), 0);
    };
    const end = () => {
      window.removeEventListener('pointermove', move);
      window.removeEventListener('pointerup', up);
      window.removeEventListener('pointercancel', up);
    };
    window.addEventListener('pointermove', move);
    window.addEventListener('pointerup', up);
    window.addEventListener('pointercancel', up);
  };

  return { onPointerDown };
}
