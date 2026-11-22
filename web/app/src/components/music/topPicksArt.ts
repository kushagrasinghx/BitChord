import type { MoodId } from '@/data/music/moods';

/*
 * Top Picks' drawn artwork, ported from sharedUi/.../screens/TopPicksShelf.kt:
 * the two Made for You stations (the folded chevrons, the fan of shards) and
 * every mood's pale outline emblem, each a function of its loop's phase (0→1).
 * Shapes and timings are the phone's, number for number.
 */

const TAU = Math.PI * 2;
const wave = (phase: number, offset: number) => Math.sin(TAU * (phase + offset));
/** Fades a ring in at the outer edge and out at the centre. */
const edgeFade = (share: number) => Math.min(1, Math.max(0, share > 0.85 ? (1 - share) / 0.15 : share < 0.15 ? share / 0.15 : 1));
const easeInOut = (t: number) => 0.5 - 0.5 * Math.cos(Math.PI * Math.min(1, Math.max(0, t)));
const rad = (degrees: number) => (degrees * Math.PI) / 180;

function mixHex(hex: string, toward: string, t: number): string {
  const a = Number.parseInt(hex.slice(1), 16);
  const b = Number.parseInt(toward.slice(1), 16);
  const channel = (shift: number) => Math.round(((a >> shift) & 255) + (((b >> shift) & 255) - ((a >> shift) & 255)) * t);
  return `rgb(${channel(16)} ${channel(8)} ${channel(0)})`;
}

/** One full breath of the stations' motion. Slow on purpose: it is a cover, not a spinner. */
export const STATION_PERIOD_MS = 9000;

// ---- Made for You stations ---------------------------------------------------

/** Outermost to innermost: where each arrow's tip rests, and its fold's two tones. */
const PERSONAL_LAYERS: [number, string, string][] = [
  [1.0, '#FE5E83', '#FA6196'],
  [0.66, '#FE5065', '#FC657C'],
  [0.43, '#FB594C', '#FC776D'],
  [0.21, '#FB6729', '#F98A48'],
];

/**
 * The personal station: arrows nested in one another on the left edge,
 * pointing right, warm orange in the middle out to pink. Their tips drift in
 * and out of step, so the stack seems to fold and unfold.
 */
export function drawPersonalStation(ctx: CanvasRenderingContext2D, w: number, h: number, phase: number) {
  const background = ctx.createLinearGradient(0, 0, w, 0);
  background.addColorStop(0, '#F7334F');
  background.addColorStop(0.38, '#FA6094');
  background.addColorStop(0.75, '#F02A4A');
  background.addColorStop(1, '#EE213D');
  ctx.fillStyle = background;
  ctx.fillRect(0, 0, w, h);
  PERSONAL_LAYERS.forEach(([rest, deep, light], index) => {
    // The outer arrow is pinned to the card's edge; the inner ones swing.
    const tip = index === 0 ? rest : rest + 0.07 * wave(phase, index * 0.19);
    const fold = ctx.createLinearGradient(0, 0, w * rest, 0);
    fold.addColorStop(0, deep);
    fold.addColorStop(1, light);
    ctx.fillStyle = fold;
    ctx.beginPath();
    ctx.moveTo(0, 0);
    ctx.lineTo(w * tip, h / 2);
    ctx.lineTo(0, h);
    ctx.closePath();
    ctx.fill();
  });
}

const DISCOVERY_TOP = ['#3A2496', '#2F4FB0', '#3B30A0', '#2E7FB8', '#27988A', '#3C8DA8'];
const DISCOVERY_BOTTOM = ['#3A2496', '#333EA4', '#2F62B6', '#288B99', '#3477BA', '#42A296'];
/** Where each shard meets the edge, as a share of the width; the inner ones swing. */
const DISCOVERY_EDGES = [0, 0.24, 0.44, 0.62, 0.78, 0.91, 1.02];

/**
 * The discovery station: a violet arrow across the card with a fan of blue,
 * indigo and teal shards opening round its tip, sweeping slowly to and fro.
 */
export function drawDiscoveryStation(ctx: CanvasRenderingContext2D, w: number, h: number, phase: number) {
  const background = ctx.createLinearGradient(0, 0, w, h);
  background.addColorStop(0, '#2A869F');
  background.addColorStop(1, '#3477BA');
  ctx.fillStyle = background;
  ctx.fillRect(0, 0, w, h);
  const fan = (edgeY: number, colors: string[], offset: number) => {
    let previous = DISCOVERY_EDGES[0]!;
    for (let i = 1; i < DISCOVERY_EDGES.length; i++) {
      const rest = DISCOVERY_EDGES[i]!;
      const edge = i === DISCOVERY_EDGES.length - 1 ? rest : rest + 0.06 * wave(phase, offset + i * 0.11);
      const mid = (w * (DISCOVERY_EDGES[i - 1]! + rest)) / 2;
      const base = colors[(i - 1) % colors.length]!;
      const shard = ctx.createLinearGradient(w, h / 2, mid, edgeY);
      shard.addColorStop(0, mixHex(base, '#FFFFFF', 0.1));
      shard.addColorStop(1, base);
      ctx.fillStyle = shard;
      ctx.beginPath();
      ctx.moveTo(w, h / 2);
      ctx.lineTo(w * previous, edgeY);
      ctx.lineTo(w * edge, edgeY);
      ctx.closePath();
      ctx.fill();
      previous = edge;
    }
  };
  fan(0, DISCOVERY_TOP, 0);
  fan(h, DISCOVERY_BOTTOM, 0.5);
  // The arrow itself, with a darker inner arrow that breathes.
  const arrow = ctx.createLinearGradient(0, 0, w, 0);
  arrow.addColorStop(0, '#502798');
  arrow.addColorStop(0.55, '#421C88');
  arrow.addColorStop(1, '#4A2292');
  ctx.fillStyle = arrow;
  ctx.beginPath();
  ctx.moveTo(0, 0);
  ctx.lineTo(w, h / 2);
  ctx.lineTo(0, h);
  ctx.closePath();
  ctx.fill();
  const inner = 0.08 + 0.04 * wave(phase, 0.25);
  ctx.fillStyle = 'rgb(60 25 129 / 0.35)';
  ctx.beginPath();
  ctx.moveTo(0, h * inner);
  ctx.lineTo(w * (0.9 - inner), h / 2);
  ctx.lineTo(0, h * (1 - inner));
  ctx.closePath();
  ctx.fill();
}

// ---- Moods ---------------------------------------------------------------------

/** The emblem's square, in card pixels; x and y take shares of its side. */
interface Frame {
  left: number;
  top: number;
  side: number;
}
const fx = (f: Frame, v: number) => f.left + f.side * v;
const fy = (f: Frame, v: number) => f.top + f.side * v;

/** A polyline with its length measured, so a stretch of it can be drawn (PathMeasure.getSegment). */
interface Measured {
  points: [number, number][];
  lengths: number[];
  total: number;
}
function measured(points: [number, number][]): Measured {
  const lengths = [0];
  for (let i = 1; i < points.length; i++) {
    const [ax, ay] = points[i - 1]!;
    const [bx, by] = points[i]!;
    lengths.push(lengths[i - 1]! + Math.hypot(bx - ax, by - ay));
  }
  return { points, lengths, total: lengths[lengths.length - 1]! };
}
function strokeStretch(ctx: CanvasRenderingContext2D, line: Measured, from: number, to: number) {
  if (to <= from) return;
  const at = (d: number): [number, number] => {
    const i = Math.max(1, line.lengths.findIndex((l) => l >= d));
    const [ax, ay] = line.points[i - 1]!;
    const [bx, by] = line.points[i]!;
    const span = line.lengths[i]! - line.lengths[i - 1]! || 1;
    const t = (d - line.lengths[i - 1]!) / span;
    return [ax + (bx - ax) * t, ay + (by - ay) * t];
  };
  ctx.beginPath();
  ctx.moveTo(...at(from));
  line.points.forEach(([x, y], i) => {
    if (line.lengths[i]! > from && line.lengths[i]! < to) ctx.lineTo(x, y);
  });
  ctx.lineTo(...at(to));
  ctx.stroke();
}

function line(ctx: CanvasRenderingContext2D, f: Frame, x0: number, y0: number, x1: number, y1: number) {
  ctx.beginPath();
  ctx.moveTo(fx(f, x0), fy(f, y0));
  ctx.lineTo(fx(f, x1), fy(f, y1));
  ctx.stroke();
}
function circle(ctx: CanvasRenderingContext2D, x: number, y: number, r: number, fill = false) {
  ctx.beginPath();
  ctx.arc(x, y, r, 0, TAU);
  if (fill) ctx.fill();
  else ctx.stroke();
}
function polyline(ctx: CanvasRenderingContext2D, f: Frame, points: [number, number][], close = false) {
  ctx.beginPath();
  points.forEach(([x, y], i) => (i === 0 ? ctx.moveTo(fx(f, x), fy(f, y)) : ctx.lineTo(fx(f, x), fy(f, y))));
  if (close) ctx.closePath();
}
/** Rotates and scales about ([cx], [cy]) for [draw]. */
function about(ctx: CanvasRenderingContext2D, cx: number, cy: number, degrees: number, scale: number, draw: () => void) {
  ctx.save();
  ctx.translate(cx, cy);
  ctx.rotate(rad(degrees));
  ctx.scale(scale, scale);
  ctx.translate(-cx, -cy);
  draw();
  ctx.restore();
}

/** Apple's slanted bolt, corner by corner off their artwork. */
const BOLT: [number, number][] = [
  [0.486, 0.123], [0.886, 0.123], [0.532, 0.509], [0.877, 0.509], [0.114, 0.895], [0.409, 0.614], [0.105, 0.614],
];

/** Four points joined by inward curves. */
function star(ctx: CanvasRenderingContext2D, f: Frame) {
  ctx.beginPath();
  ctx.moveTo(fx(f, 0.5), fy(f, 0.06));
  ctx.quadraticCurveTo(fx(f, 0.56), fy(f, 0.44), fx(f, 0.94), fy(f, 0.5));
  ctx.quadraticCurveTo(fx(f, 0.56), fy(f, 0.56), fx(f, 0.5), fy(f, 0.94));
  ctx.quadraticCurveTo(fx(f, 0.44), fy(f, 0.56), fx(f, 0.06), fy(f, 0.5));
  ctx.quadraticCurveTo(fx(f, 0.44), fy(f, 0.44), fx(f, 0.5), fy(f, 0.06));
  ctx.closePath();
}

/** The same four-pointed star, small, around (cx, cy). */
function sparkle(ctx: CanvasRenderingContext2D, cx: number, cy: number, r: number) {
  const k = r * 0.12;
  ctx.beginPath();
  ctx.moveTo(cx, cy - r);
  ctx.quadraticCurveTo(cx + k, cy - k, cx + r, cy);
  ctx.quadraticCurveTo(cx + k, cy + k, cx, cy + r);
  ctx.quadraticCurveTo(cx - k, cy + k, cx - r, cy);
  ctx.quadraticCurveTo(cx - k, cy - k, cx, cy - r);
  ctx.closePath();
}

function fivePointStar(ctx: CanvasRenderingContext2D, cx: number, cy: number, r: number) {
  ctx.beginPath();
  for (let i = 0; i < 10; i++) {
    const radius = i % 2 === 0 ? r : r * 0.45;
    const angle = (i * Math.PI) / 5 - Math.PI / 2;
    const x = cx + radius * Math.cos(angle);
    const y = cy + radius * Math.sin(angle);
    if (i === 0) ctx.moveTo(x, y);
    else ctx.lineTo(x, y);
  }
  ctx.closePath();
}

/** Two round lobes and straight sides down to a point. */
function heart(ctx: CanvasRenderingContext2D, f: Frame) {
  const r = 0.24;
  ctx.beginPath();
  ctx.moveTo(fx(f, 0.5), fy(f, 0.92));
  ctx.lineTo(fx(f, 0.28 - r * 0.707), fy(f, 0.3 + r * 0.707));
  ctx.arc(fx(f, 0.28), fy(f, 0.3), f.side * r, rad(135), rad(330));
  ctx.arc(fx(f, 0.72), fy(f, 0.3), f.side * r, rad(210), rad(405));
  ctx.closePath();
}

/** A coil that narrows as it falls, like Apple's Feeling Blue twister: 320 steps, 5.5 turns. */
function spiral(f: Frame): Measured {
  const points: [number, number][] = [];
  for (let k = 0; k <= 320; k++) {
    const t = k / 320;
    const angle = Math.PI + t * 5.5 * TAU;
    const rx = 0.38 * (1 - 0.72 * t);
    const ry = 0.075 * (1 - 0.55 * t);
    points.push([fx(f, 0.5 + rx * Math.cos(angle)), fy(f, 0.1 + 0.78 * t + ry * Math.sin(angle))]);
  }
  return measured(points);
}

const FITNESS_TRACE: [number, number][] = [[0, 0.55], [0.28, 0.55], [0.36, 0.42], [0.44, 0.76], [0.52, 0.18], [0.6, 0.84], [0.67, 0.55], [1, 0.55]];

/**
 * Where in its loop a mood's emblem is drawn when it holds still: its start,
 * where nearly every one is whole, but for the two only part-way drawn there.
 */
export function stillPhase(mood: MoodId): number {
  return mood === 'fitness' ? 0.55 : mood === 'work' ? 0.6 : 0;
}

/**
 * A mood's artwork (MoodArt): a flat colour and one pale outline emblem.
 * [square] is a Find Your Mood tile, the emblem in the middle; otherwise it's a
 * tall Top Picks card and keeps to the part above the caption.
 */
export function drawMood(
  ctx: CanvasRenderingContext2D,
  w: number,
  h: number,
  phase: number,
  mood: { id: MoodId; background: string; stroke: string },
  square: boolean,
) {
  ctx.fillStyle = mood.background;
  ctx.fillRect(0, 0, w, h);
  // A landscape card (Top Picks on a wide window) keeps the emblem to the right, clear of the caption at the start.
  const landscape = !square && w > h * 1.1;
  const side = square ? w * 0.7 : landscape ? h * 0.66 : Math.min(w * 0.84, h * 0.62);
  const f: Frame = square
    ? { left: (w - side) / 2, top: (h - side) / 2, side }
    : landscape
      ? { left: w * 0.72 - side / 2, top: (h - side) / 2, side }
      : { left: (w - side) / 2, top: h * 0.37 - side / 2, side };
  ctx.strokeStyle = mood.stroke;
  ctx.fillStyle = mood.stroke;
  ctx.lineWidth = square ? 2 : 2.5;
  ctx.lineCap = 'round';
  ctx.lineJoin = 'round';
  const cx = fx(f, 0.5);
  const cy = fy(f, 0.5);
  const alpha = (a: number, draw: () => void) => {
    ctx.globalAlpha = Math.max(0, Math.min(1, a));
    draw();
    ctx.globalAlpha = 1;
  };

  switch (mood.id) {
    case 'energy': {
      // Every loop the bolt flickers: an echo jumps out to either side and fades, like a strobe.
      const t = (phase - 0.28) / 0.26;
      const flash = t >= 0 && t <= 1 ? Math.sin(Math.PI * t) : 0;
      const bolt = () => {
        polyline(ctx, f, BOLT, true);
        ctx.stroke();
      };
      if (flash > 0) {
        [[0.07, 0.55], [-0.05, 0.35]].forEach(([dx, a]) => {
          ctx.save();
          ctx.translate(side * dx! * flash, 0);
          alpha(a! * flash, bolt);
          ctx.restore();
        });
      }
      bolt();
      break;
    }
    case 'relax':
      // Three stones, balanced and swaying a little out of step.
      [[0.17, 0.44, 0.18], [0.38, 0.68, 0.28], [0.66, 0.84, 0.36]].forEach(([y, sw, sh], index) => {
        const sway = wave(phase, index * 0.22);
        const x = fx(f, 0.5 + 0.015 * sway);
        about(ctx, x, fy(f, y!), 4 * sway, 1, () => {
          ctx.beginPath();
          ctx.ellipse(x, fy(f, y!), (side * sw!) / 2, (side * sh!) / 2, 0, 0, TAU);
          ctx.stroke();
        });
      });
      break;
    case 'feeling-blue': {
      // Whole, then unwinding from the top, then drawn back in.
      const coil = spiral(f);
      const length = coil.total;
      const [from, to] =
        phase < 0.35 ? [0, length] : phase < 0.8 ? [length * easeInOut((phase - 0.35) / 0.45), length] : [0, length * easeInOut((phase - 0.8) / 0.2)];
      strokeStretch(ctx, coil, from, to);
      break;
    }
    case 'feel-good': {
      const pulse = 0.9 + 0.1 * (0.5 + 0.5 * Math.cos(TAU * phase));
      about(ctx, cx, cy, 8 * wave(phase, 0), pulse, () => {
        star(ctx, f);
        ctx.stroke();
      });
      break;
    }
    case 'focus':
      // Rings drawn in toward the centre: each shrinks, the innermost fades and a new one fades in at the edge.
      for (let i = 0; i < 4; i++) {
        const share = (i + 1 - phase) / 4;
        alpha(edgeFade(share), () => circle(ctx, cx, cy, side * 0.5 * share));
      }
      break;
    case 'heartbreak': {
      const dash = side * 0.075;
      const gap = side * 0.055;
      ctx.setLineDash([dash, gap]);
      ctx.lineDashOffset = -phase * (dash + gap) * 2;
      heart(ctx, f);
      ctx.stroke();
      ctx.setLineDash([]);
      break;
    }
    case 'love': {
      // Lub-dub: two quick swells, then rest.
      const bump = (at: number) => Math.exp(-(((phase - at) / 0.06) ** 2));
      const beat = 1 + 0.07 * (bump(0.15) + 0.7 * bump(0.38));
      about(ctx, cx, cy, 0, beat, () => {
        heart(ctx, f);
        ctx.stroke();
      });
      break;
    }
    case 'after-hours': {
      // A crescent: the disc's rim outside the bite, and the bite's rim inside the disc.
      const [dx, dy, dr] = [fx(f, 0.42), fy(f, 0.52), side * 0.34];
      const [bx, by, br] = [fx(f, 0.42 + 0.17), fy(f, 0.52 - 0.102), side * 0.34 * 0.88];
      ctx.save();
      ctx.beginPath();
      ctx.rect(0, 0, w, h);
      ctx.arc(bx, by, br, 0, TAU);
      ctx.clip('evenodd');
      circle(ctx, dx, dy, dr);
      ctx.restore();
      ctx.save();
      ctx.beginPath();
      ctx.arc(dx, dy, dr, 0, TAU);
      ctx.clip();
      circle(ctx, bx, by, br);
      ctx.restore();
      [[0.8, 0.22, 0.07], [0.86, 0.58, 0.05], [0.68, 0.82, 0.04]].forEach(([x, y, r], index) => {
        const twinkle = 0.25 + 0.75 * (0.5 + 0.5 * wave(phase, index / 3));
        sparkle(ctx, fx(f, x!), fy(f, y!), side * r!);
        alpha(twinkle, () => ctx.stroke());
      });
      break;
    }
    case 'alone-time':
      // A drop's ripples spreading out across still water.
      for (let i = 0; i < 4; i++) {
        const share = (i + phase) / 4;
        const rx = side * 0.5 * share;
        alpha(edgeFade(share), () => {
          ctx.beginPath();
          ctx.ellipse(cx, fy(f, 0.55), rx, rx * 0.42, 0, 0, TAU);
          ctx.stroke();
        });
      }
      break;
    case 'chill':
      // Three waves rolling slowly sideways.
      ctx.save();
      ctx.beginPath();
      ctx.rect(f.left, f.top, side, side);
      ctx.clip();
      [0.32, 0.5, 0.68].forEach((y, row) => {
        const points: [number, number][] = [];
        for (let k = 0; k <= 80; k++) {
          const u = k / 80;
          points.push([u, y + 0.05 * Math.sin(TAU * (u / 0.33 - phase - row * 0.15))]);
        }
        polyline(ctx, f, points);
        ctx.stroke();
      });
      ctx.restore();
      break;
    case 'commuting':
      // A road to the horizon, its centre line rushing toward you.
      line(ctx, f, 0.06, 0.95, 0.44, 0.14);
      line(ctx, f, 0.94, 0.95, 0.56, 0.14);
      line(ctx, f, 0.15, 0.14, 0.85, 0.14);
      for (let k = 0; k < 5; k++) {
        const u = (k / 5 + phase) % 1;
        const y0 = 0.18 + 0.77 * u * u;
        alpha(edgeFade(u), () => line(ctx, f, 0.5, y0, 0.5, Math.min(0.95, y0 + 0.02 + 0.08 * u)));
      }
      break;
    case 'eating-cooking':
      // A bowl with steam curling up off it.
      ctx.beginPath();
      ctx.ellipse(cx, fy(f, 0.6), side * 0.38, side * 0.35, 0, 0, Math.PI);
      ctx.stroke();
      line(ctx, f, 0.05, 0.6, 0.95, 0.6);
      [0.36, 0.5, 0.64].forEach((x, index) => {
        const rise = (phase + index / 3) % 1;
        const points: [number, number][] = [];
        for (let k = 0; k <= 30; k++) {
          const u = k / 30;
          points.push([x + 0.035 * Math.sin(TAU * (u * 1.2 + rise)), 0.5 - 0.3 * u - 0.1 * rise]);
        }
        polyline(ctx, f, points);
        alpha(Math.sin(Math.PI * rise), () => ctx.stroke());
      });
      break;
    case 'evening': {
      // The sun sinking behind the horizon and rising back.
      const horizon = 0.62;
      const sink = 0.1 * (0.5 - 0.5 * Math.cos(TAU * phase));
      ctx.save();
      ctx.beginPath();
      ctx.rect(f.left - side, f.top - side, side * 3, side * (1 + horizon));
      ctx.clip();
      circle(ctx, cx, fy(f, horizon - 0.02 + sink), side * 0.26);
      ctx.restore();
      line(ctx, f, 0.04, horizon, 0.96, horizon);
      line(ctx, f, 0.2, horizon + 0.11, 0.8, horizon + 0.11);
      line(ctx, f, 0.36, horizon + 0.22, 0.64, horizon + 0.22);
      break;
    }
    case 'fitness': {
      // A heartbeat trace, a bright pulse running along it.
      const trace = measured(FITNESS_TRACE.map(([x, y]) => [fx(f, x), fy(f, y)]));
      alpha(0.3, () => strokeStretch(ctx, trace, 0, trace.total));
      const head = phase * 1.4 * trace.total;
      strokeStretch(ctx, trace, Math.max(0, head - 0.45 * trace.total), Math.min(head, trace.total));
      break;
    }
    case 'gaming':
      // Squares inside squares, turning against each other.
      [0.9, 0.62, 0.34].forEach((s, index) => {
        about(ctx, cx, cy, (index % 2 === 0 ? 1 : -1) * phase * 90, 1, () => {
          ctx.strokeRect(fx(f, 0.5 - s / 2), fy(f, 0.5 - s / 2), side * s, side * s);
        });
      });
      break;
    case 'home': {
      polyline(ctx, f, [[0.5, 0.1], [0.94, 0.5], [0.82, 0.5], [0.82, 0.92], [0.18, 0.92], [0.18, 0.5], [0.06, 0.5]], true);
      ctx.stroke();
      ctx.strokeRect(fx(f, 0.41), fy(f, 0.66), side * 0.18, side * 0.26);
      // A lamp coming on and off in the window.
      const glow = 0.5 + 0.5 * wave(phase, 0);
      alpha(0.15 + 0.45 * glow, () => ctx.fillRect(fx(f, 0.27), fy(f, 0.56), side * 0.1, side * 0.1));
      ctx.strokeRect(fx(f, 0.27), fy(f, 0.56), side * 0.1, side * 0.1);
      break;
    }
    case 'morning': {
      // The sun coming up, its rays turning.
      const horizon = 0.72;
      const sx = cx;
      const sy = fy(f, horizon - 0.03 - 0.05 * (0.5 + 0.5 * wave(phase, 0)));
      ctx.save();
      ctx.beginPath();
      ctx.rect(f.left - side, f.top - side, side * 3, side * (1 + horizon));
      ctx.clip();
      circle(ctx, sx, sy, side * 0.2);
      for (let k = 0; k < 9; k++) {
        const angle = Math.PI + ((k + 0.5) * Math.PI) / 9 + phase * 0.35;
        ctx.beginPath();
        ctx.moveTo(sx + side * 0.28 * Math.cos(angle), sy + side * 0.28 * Math.sin(angle));
        ctx.lineTo(sx + side * 0.38 * Math.cos(angle), sy + side * 0.38 * Math.sin(angle));
        ctx.stroke();
      }
      ctx.restore();
      line(ctx, f, 0.04, horizon, 0.96, horizon);
      break;
    }
    case 'motivation':
      // Chevrons climbing up and out of the frame.
      for (let k = 0; k < 3; k++) {
        const u = (k / 3 + phase) % 1;
        const y = 0.82 - 0.66 * u;
        polyline(ctx, f, [[0.22, y + 0.17], [0.5, y], [0.78, y + 0.17]]);
        alpha(Math.sin(Math.PI * u), () => ctx.stroke());
      }
      break;
    case 'outdoors':
      polyline(ctx, f, [[0.02, 0.86], [0.36, 0.36], [0.55, 0.62], [0.7, 0.44], [0.98, 0.86]]);
      ctx.stroke();
      line(ctx, f, 0.02, 0.86, 0.98, 0.86);
      circle(ctx, fx(f, 0.78 + 0.02 * wave(phase, 0.25)), fy(f, 0.2 + 0.02 * wave(phase, 0)), side * 0.09);
      break;
    case 'party':
      // Confetti stars, spinning and popping.
      [[0.24, 0.24, 0.14], [0.72, 0.18, 0.11], [0.52, 0.5, 0.17], [0.2, 0.72, 0.1], [0.8, 0.66, 0.13], [0.56, 0.88, 0.08]].forEach(
        ([x, y, r], index) => {
          const px = fx(f, x!);
          const py = fy(f, y!);
          about(ctx, px, py, (index % 2 === 0 ? 1 : -1) * phase * 360, 0.85 + 0.15 * wave(phase, index / 6), () => {
            fivePointStar(ctx, px, py, side * r!);
            ctx.stroke();
          });
        },
      );
      break;
    case 'sleep':
      // A closed eye, breathing.
      about(ctx, cx, cy, 0, 1 + 0.04 * wave(phase, 0), () => {
        ctx.beginPath();
        ctx.ellipse(cx, fy(f, 0.43), side * 0.38, side * 0.23, 0, rad(10), rad(170));
        ctx.stroke();
        for (let k = 0; k < 5; k++) {
          const angle = rad(30 + k * 30);
          const bx = fx(f, 0.5 + 0.38 * Math.cos(angle));
          const by = fy(f, 0.43 + 0.23 * Math.sin(angle));
          ctx.beginPath();
          ctx.moveTo(bx, by);
          ctx.lineTo(bx + side * 0.08 * Math.cos(angle), by + side * 0.1 * Math.sin(angle));
          ctx.stroke();
        }
      });
      break;
    case 'social': {
      // Two circles drifting together and apart.
      const gap = 0.12 + 0.07 * wave(phase, 0);
      circle(ctx, fx(f, 0.5 - gap), cy, side * 0.27);
      circle(ctx, fx(f, 0.5 + gap), cy, side * 0.27);
      break;
    }
    case 'sports': {
      // A ball, spinning.
      const r = side * 0.44;
      circle(ctx, cx, cy, r);
      ctx.save();
      ctx.beginPath();
      ctx.arc(cx, cy, r, 0, TAU);
      ctx.clip();
      about(ctx, cx, cy, phase * 360, 1, () => {
        circle(ctx, cx - r * 1.25, cy, r * 0.95);
        circle(ctx, cx + r * 1.25, cy, r * 0.95);
      });
      ctx.restore();
      break;
    }
    case 'summertime':
      circle(ctx, cx, cy, side * 0.22);
      about(ctx, cx, cy, phase * 30, 1, () => {
        for (let k = 0; k < 12; k++) {
          const angle = (k * TAU) / 12;
          ctx.beginPath();
          ctx.moveTo(cx + side * 0.31 * Math.cos(angle), cy + side * 0.31 * Math.sin(angle));
          ctx.lineTo(cx + side * 0.46 * Math.cos(angle), cy + side * 0.46 * Math.sin(angle));
          ctx.stroke();
        }
      });
      break;
    case 'holiday':
      // A snowflake, turning.
      about(ctx, cx, cy, phase * 60, 1, () => {
        for (let k = 0; k < 6; k++) {
          const angle = (k * TAU) / 6;
          const point = (d: number): [number, number] => [cx + side * d * Math.cos(angle), cy + side * d * Math.sin(angle)];
          ctx.beginPath();
          ctx.moveTo(cx, cy);
          ctx.lineTo(...point(0.46));
          ctx.stroke();
          [0.22, 0.34].forEach((d) => {
            const [bx, by] = point(d);
            [-0.785, 0.785].forEach((turn) => {
              ctx.beginPath();
              ctx.moveTo(bx, by);
              ctx.lineTo(bx + side * 0.1 * Math.cos(angle + turn), by + side * 0.1 * Math.sin(angle + turn));
              ctx.stroke();
            });
          });
        }
      });
      break;
    case 'weekend':
      // A record spinning.
      circle(ctx, cx, cy, side * 0.46);
      circle(ctx, cx, cy, side * 0.16);
      circle(ctx, cx, cy, side * 0.025, true);
      about(ctx, cx, cy, phase * 360, 1, () => {
        [0.36, 0.27].forEach((r) => {
          [200, 20].forEach((start) => {
            ctx.beginPath();
            ctx.arc(cx, cy, side * r, rad(start), rad(start + 70));
            ctx.stroke();
          });
        });
      });
      break;
    case 'wellbeing': {
      // A lotus, its petals opening with each breath.
      const open = 0.85 + 0.15 * wave(phase, 0);
      const bx = cx;
      const by = fy(f, 0.84);
      [-62, -31, 0, 31, 62].forEach((angle) => {
        about(ctx, bx, by, angle * open, 1, () => {
          ctx.beginPath();
          ctx.moveTo(bx, by);
          ctx.quadraticCurveTo(fx(f, 0.66), fy(f, 0.52), fx(f, 0.5), fy(f, 0.14 + (1 - open) * 0.3));
          ctx.quadraticCurveTo(fx(f, 0.34), fy(f, 0.52), bx, by);
          ctx.stroke();
        });
      });
      break;
    }
    case 'work': {
      // A laptop, a line of text being typed and the cursor blinking.
      ctx.beginPath();
      ctx.roundRect(fx(f, 0.16), fy(f, 0.18), side * 0.68, side * 0.48, side * 0.03);
      ctx.stroke();
      line(ctx, f, 0.04, 0.78, 0.96, 0.78);
      line(ctx, f, 0.16, 0.66, 0.04, 0.78);
      line(ctx, f, 0.84, 0.66, 0.96, 0.78);
      const typed = 0.28 + 0.36 * Math.min(1, phase * 1.25);
      line(ctx, f, 0.28, 0.36, 0.64, 0.36);
      line(ctx, f, 0.28, 0.48, typed, 0.48);
      if (Math.floor(phase * 4) % 2 === 0) line(ctx, f, typed + 0.04, 0.43, typed + 0.04, 0.53);
      break;
    }
  }
}
