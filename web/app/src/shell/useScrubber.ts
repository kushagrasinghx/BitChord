import { useEffect, useRef, useState } from 'react';
import { getAudio, getMixBeat, getPlayhead, usePlayer } from '@/state/player';

export function clock(seconds: number): string {
  if (!Number.isFinite(seconds) || seconds < 0) return '0:00';
  const s = Math.floor(seconds);
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
}

// ---- The Automix pulse (the phone's MixPulse, ThinSlider.kt) -------------------------------

/** How long the fill takes to glide out to full width, and back, around a blend. */
const COVER_SEC = 0.7;
/** How long the beat pulse takes to swell in as a blend starts and settle as it ends. */
const PULSE_FADE_SEC = 0.9;
/** Opacity between beats, as a share of the fill's own: low enough to read, never gone. */
const PULSE_REST = 0.1;
/** Share of the remaining phase error corrected each frame; small enough to be invisible. */
const PHASE_PULL = 0.06;
/** Breathing period when the mix's tempo isn't known. */
const BREATH_SEC = 1.8;

/** The FastOutSlowIn-like ease the phone's tweens use, over a linear 0..1. */
const ease = (x: number) => x * x * (3 - 2 * x);
/** A breath on each beat: a raised cosine squared, peaking on the beat. */
const beatShape = (phase: number) => {
  const c = 0.5 + 0.5 * Math.cos(2 * Math.PI * phase);
  return c * c;
};

interface Pulse {
  cover: number;
  depth: number;
  phase: number;
  locked: boolean;
  last: number;
}

/**
 * One frame of the pulse: the fill claims the whole bar while a blend runs
 * (through the outgoing song and the incoming one) and its brightness
 * breathes on the blend's beat. The phase runs free at the beat and only
 * leans toward the mix's clock, so the clock's wobble never shows as a lurch.
 */
function stepPulse(p: Pulse, now: number, beat: { phase: number | null } | null, paused: boolean) {
  const dt = Math.min(0.1, Math.max(0, (now - p.last) / 1000));
  p.last = now;
  p.cover = Math.min(1, Math.max(0, p.cover + (beat ? dt : -dt) / COVER_SEC));
  p.depth = Math.min(1, Math.max(0, p.depth + (beat && !paused ? dt : -dt) / PULSE_FADE_SEC));
  if (beat?.phase != null) {
    const error = ((((beat.phase - p.phase) % 1) + 1.5) % 1) - 0.5;
    p.phase += p.locked ? error * PHASE_PULL : error;
    p.locked = true;
  } else {
    p.phase += dt / BREATH_SEC;
    if (beat) p.locked = false;
  }
  p.phase -= Math.floor(p.phase);
  const cover = ease(p.cover);
  const depth = ease(p.depth);
  const pulsed = PULSE_REST + (1 - PULSE_REST) * beatShape(p.phase);
  return { cover, alpha: 1 + (pulsed - 1) * depth, active: p.cover > 0 || p.depth > 0 };
}

/**
 * A seek line drawn from the media clock every frame while playing, by writing
 * `--progress` on the bar and the times into their spans: no React state, so
 * nothing re-renders. Pressing or dragging on the scrubber seeks on release.
 * Shared by the LCD and the full-screen player.
 */
export function useScrubber() {
  const bar = useRef<HTMLDivElement>(null);
  const elapsed = useRef<HTMLSpanElement>(null);
  const remaining = useRef<HTMLSpanElement>(null);
  /** "Mixing", in the times' place while an Automix blend runs (the phone's MixingLabel). */
  const mixLabel = useRef<HTMLSpanElement>(null);
  const pulse = useRef<Pulse>({ cover: 0, depth: 0, phase: 0, locked: false, last: performance.now() });
  const status = usePlayer((s) => s.status);
  const deck = usePlayer((s) => s.deck);
  const mixing = usePlayer((s) => s.mixing);
  const seek = usePlayer((s) => s.seek);
  const [dragging, setDragging] = useState<number | null>(null);

  useEffect(() => {
    const audio = getAudio();
    const element = bar.current;
    if (!audio || !element) return;
    let frame = 0;
    const draw = () => {
      const duration = audio.duration;
      // during a loop mix the element is paused under the decks: the mix's own playhead
      const head = getPlayhead();
      const paused = head?.paused ?? audio.paused;
      const time = dragging !== null && Number.isFinite(duration) ? dragging * duration : (head?.sec ?? audio.currentTime);
      const base = Math.min(1, Math.max(0, duration > 0 ? time / duration : 0));
      const beat = dragging === null ? getMixBeat() : null;
      const mix = stepPulse(pulse.current, performance.now(), beat, paused);
      element.style.setProperty('--progress', String(base + (1 - base) * mix.cover));
      element.style.opacity = mix.active ? String(mix.alpha) : '';
      if (elapsed.current) elapsed.current.textContent = clock(time);
      if (remaining.current) remaining.current.textContent = `-${clock((duration || 0) - time)}`;
      for (const span of [elapsed.current, remaining.current]) {
        if (span) span.style.opacity = mix.active ? String(1 - mix.cover) : '';
      }
      if (mixLabel.current) mixLabel.current.style.opacity = mix.active ? String(mix.cover * (0.35 + 0.65 * mix.alpha)) : '0';
      if (!paused || dragging !== null || mix.active || beat) frame = requestAnimationFrame(draw);
    };
    draw();
    const onSeeked = () => draw();
    audio.addEventListener('seeked', onSeeked);
    return () => {
      cancelAnimationFrame(frame);
      audio.removeEventListener('seeked', onSeeked);
    };
  }, [status, deck, dragging, mixing]);

  const fractionAt = (event: React.PointerEvent<HTMLElement>) => {
    const rect = event.currentTarget.getBoundingClientRect();
    return Math.min(1, Math.max(0, (event.clientX - rect.left) / rect.width));
  };

  const handlers = {
    onPointerDown: (e: React.PointerEvent<HTMLElement>) => {
      if (!getAudio()) return;
      e.currentTarget.setPointerCapture(e.pointerId);
      setDragging(fractionAt(e));
    },
    onPointerMove: (e: React.PointerEvent<HTMLElement>) => {
      if (dragging !== null) setDragging(fractionAt(e));
    },
    onPointerUp: (e: React.PointerEvent<HTMLElement>) => {
      if (dragging === null) return;
      const audio = getAudio();
      if (audio && Number.isFinite(audio.duration)) seek(fractionAt(e) * audio.duration);
      setDragging(null);
    },
    onPointerCancel: () => setDragging(null),
  };

  return { bar, elapsed, remaining, mixLabel, dragging: dragging !== null, handlers };
}
