import { useEffect, useLayoutEffect, useRef } from 'react';
import { ForwardIcon, PauseIcon, PlayIcon } from '@/components/icons/Transport';
import { useReducedMotion } from '@/data/spotify/canvas';
import {
  EXPANDED_SPRING,
  MINI_SPRING,
  PULSE_DIP,
  PULSE_DIP_SPRING,
  PULSE_RETURN_AT,
  PULSE_RETURN_SPRING,
  SKIP_SETTLED,
  skipAt,
  skipPose,
  skipRun,
  skipTarget,
  springSettled,
  stepSpring,
  symbolPose,
  type SkipRun,
} from './transportMotion';

/**
 * Play/pause, handing over as Apple Music's does (the phone's
 * ExpandedPlayPauseGlyph): one spring carries both symbols, the outgoing one
 * shrinking and blurring away as the incoming one grows out of a blur, so two
 * crisp full-size icons are never on screen at once. A reversal mid-way
 * carries on from where the spring is, at its speed.
 *
 * In the mini player ([mini]), the whole glyph also pinches almost to a point
 * and pops back, which is where the phone's mini player hides its morph.
 */
export function PlayPauseGlyph({ playing, size, mini = false }: { playing: boolean; size: number; mini?: boolean }) {
  const box = useRef<HTMLSpanElement>(null);
  const play = useRef<HTMLSpanElement>(null);
  const pause = useRef<HTMLSpanElement>(null);
  const still = useReducedMotion();
  const motion = useRef({ phase: playing ? 1 : 0, velocity: 0, pulse: 1, pulseVelocity: 0, dipping: false, frame: 0, last: 0, shown: playing });

  // Before paint: the symbols' styles are this effect's alone, never React's.
  useLayoutEffect(() => {
    const m = motion.current;
    const target = playing ? 1 : 0;
    const paint = () => {
      for (const [layer, weight] of [
        [pause.current, m.phase],
        [play.current, 1 - m.phase],
      ] as const) {
        if (!layer) continue;
        const pose = symbolPose(weight);
        layer.style.opacity = String(pose.alpha);
        layer.style.transform = `scale(${pose.scale})`;
        layer.style.filter = pose.blur > 0.001 && pose.alpha > 0 ? `blur(${(pose.blur * size).toFixed(2)}px)` : '';
      }
      if (box.current) box.current.style.transform = m.pulse === 1 ? '' : `scale(${m.pulse})`;
    };
    cancelAnimationFrame(m.frame);
    // A hidden page, reduced motion or the first frame never replay a change they didn't show.
    if (still || document.hidden || m.shown === playing) {
      Object.assign(m, { phase: target, velocity: 0, pulse: 1, pulseVelocity: 0, dipping: false, shown: playing });
      paint();
      return;
    }
    m.shown = playing;
    m.dipping = mini;
    m.last = performance.now();
    const tick = (now: number) => {
      const dt = Math.min(0.064, Math.max(0, (now - m.last) / 1000));
      m.last = now;
      [m.phase, m.velocity] = stepSpring(mini ? MINI_SPRING : EXPANDED_SPRING, m.phase, m.velocity, target, dt);
      if (mini) {
        const to = m.dipping ? PULSE_DIP : 1;
        [m.pulse, m.pulseVelocity] = stepSpring(m.dipping ? PULSE_DIP_SPRING : PULSE_RETURN_SPRING, m.pulse, m.pulseVelocity, to, dt);
        if (m.dipping && Math.abs(m.pulse - PULSE_DIP) <= PULSE_RETURN_AT) m.dipping = false;
      }
      const settled = springSettled(m.phase, m.velocity, target) && !m.dipping && springSettled(m.pulse, m.pulseVelocity, 1);
      if (settled) Object.assign(m, { phase: target, velocity: 0, pulse: 1, pulseVelocity: 0 });
      paint();
      if (!settled) m.frame = requestAnimationFrame(tick);
    };
    m.frame = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(m.frame);
  }, [playing, still, mini, size]);

  const layer = 'absolute inset-0 grid place-items-center';
  return (
    <span ref={box} className="relative grid place-items-center" style={{ width: size, height: size }} aria-hidden="true">
      <span ref={play} className={layer}>
        <PlayIcon size={size} />
      </span>
      <span ref={pause} className={layer}>
        <PauseIcon size={size} />
      </span>
    </span>
  );
}

// The leading arrowhead of ForwardIcon on its own, and how far behind it the trailing one sits
// (in the icon's viewBox): the two are the same contour, so one is drawn translated.
const ARROW =
  'M18.14 20.68c.365 0 .672-.107 1.038-.323l8.508-4.997c.623-.365.938-.814.938-1.37 0-.564-.307-.988-.938-1.361l-8.508-4.997c-.366-.216-.68-.324-1.046-.324-.73 0-1.337.556-1.337 1.569V19.111c0 1.013.615 1.569 1.345 1.569z';
const SHIFT = 11.795;
const FRONT_X = 22.71;
const CENTER_Y = 13.994;
const BACK_X = FRONT_X - SHIFT;
const around = (x: number, scale: number) => `translate(${x} ${CENTER_Y}) scale(${scale}) translate(${-x} ${-CENTER_Y})`;

/**
 * Next or previous, with the phone's skip conveyor (PlayerSkipGlyph): on each
 * press the leading arrowhead shrinks away, the trailing one slides into its
 * place and a new one grows in behind, 600 ms a cycle. Presses during one add
 * at most one more cycle. At rest it is the original icon. [taps] counts presses.
 */
export function SkipGlyph({ taps, size, previous = false }: { taps: number; size: number; previous?: boolean }) {
  const rest = useRef<SVGSVGElement>(null);
  const moving = useRef<SVGSVGElement>(null);
  const front = useRef<SVGPathElement>(null);
  const survivor = useRef<SVGPathElement>(null);
  const grown = useRef<SVGPathElement>(null);
  const still = useReducedMotion();
  const motion = useRef<{ cycles: number; target: number; velocity: number; run: SkipRun | null; frame: number; taps: number }>({
    cycles: 0,
    target: 0,
    velocity: 0,
    run: null,
    frame: 0,
    taps,
  });

  useEffect(() => {
    const m = motion.current;
    if (taps === m.taps) return;
    m.taps = taps;
    const paint = () => {
      const p = m.cycles % 1;
      const atRest = p === 0 || p >= SKIP_SETTLED;
      if (rest.current) rest.current.style.visibility = atRest ? '' : 'hidden';
      if (moving.current) moving.current.style.visibility = atRest ? 'hidden' : '';
      if (atRest) return;
      const pose = skipPose(p);
      front.current?.setAttribute('transform', around(FRONT_X, pose.frontScale));
      front.current?.setAttribute('opacity', String(pose.frontAlpha));
      survivor.current?.setAttribute('transform', `translate(${SHIFT * (pose.survivorTravel - 1)} 0)`);
      grown.current?.setAttribute('transform', `${around(BACK_X, pose.newScale)} translate(${-SHIFT} 0)`);
      grown.current?.setAttribute('opacity', String(pose.newAlpha));
    };
    m.target = skipTarget(m.cycles, m.target);
    cancelAnimationFrame(m.frame);
    if (still || document.hidden) {
      Object.assign(m, { cycles: m.target, velocity: 0, run: null });
      paint();
      return;
    }
    const now = performance.now();
    m.run = skipRun(m.cycles, m.target, m.run ? m.velocity : 0, now);
    const tick = (time: number) => {
      const at = skipAt(m.run!, time);
      m.cycles = at.cycles;
      m.velocity = at.velocity;
      if (at.done) m.run = null;
      paint();
      if (!at.done) m.frame = requestAnimationFrame(tick);
    };
    m.frame = requestAnimationFrame(tick);
  }, [taps, still]);

  useEffect(() => () => cancelAnimationFrame(motion.current.frame), []);

  const mirror = previous ? { transform: 'scaleX(-1)' } : undefined;
  return (
    <span className="relative grid place-items-center" style={{ width: size, height: size }} aria-hidden="true">
      <ForwardIcon ref={rest} size={size} style={mirror} />
      <svg ref={moving} className="absolute" width={size} height={size} viewBox="2 0 28 28" fill="currentColor" style={{ ...mirror, visibility: 'hidden' }}>
        <path ref={front} d={ARROW} />
        <path ref={survivor} d={ARROW} transform={`translate(${-SHIFT} 0)`} />
        <path ref={grown} d={ARROW} opacity={0} />
      </svg>
    </span>
  );
}
