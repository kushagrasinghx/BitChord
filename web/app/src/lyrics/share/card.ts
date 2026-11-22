import type { ShareLine } from '@bitchord/shared-kotlin';
import { endMs, isRtl, type LyricLine } from '../model';

/**
 * The picked lines as one picture: LyricsShareCard.kt's story card, drawn on a
 * 2D canvas. The whole frame is the song's blurred colours, and the passage
 * sits in a frosted card in the middle with the song under the words, so a
 * story app's own furniture at the top and bottom never lands on them.
 *
 * Laid out once; [LyricsCard.draw] then draws the passage at any moment of the
 * song, the way the lyrics panel lights the same lines: the words not yet sung
 * dim, the sung ones white, the sweep crossing each timed word at its own pace
 * with a soft leading edge, and lines further from the one being sung fading
 * down the panel's falloff. With no moment (the still card) every line is lit.
 */

export type ShareFormat = 'story' | 'square';

export const FRAME_W = 1080;
export const frameHeight = (format: ShareFormat) => (format === 'story' ? 1920 : 1080);

/** LyricsPanel's LYRIC_FONT: the phone's lyric typeface, then the app's. */
const FONT = "'BitChord SF Pro Display', 'DM Sans', system-ui, sans-serif";

export interface CardInput {
  /** From pickShareLines: what goes on the card. */
  lines: ShareLine[];
  /** The lyrics the lines were picked from, for their word timings (ShareLine.source). */
  lyrics: LyricLine[];
  title: string;
  artist: string;
  /** The sleeve, loaded CORS-clean so the canvas can still be recorded; null draws a plain tile. */
  cover: HTMLImageElement | null;
  format: ShareFormat;
  /** "BitChord" under the credit. */
  logo: boolean;
}

interface Row {
  text: string;
  font: string;
  size: number;
  height: number;
  gapBefore: number;
  isGap: boolean;
  /** Which of the card's lines this row belongs to, and where it starts in that line's text. */
  line: number;
  start: number;
  rtl: boolean;
  /** Its own font's ascent: the marker's is smaller than the words'. */
  ascent: number;
  baseline: number;
}

export class LyricsCard {
  readonly width = FRAME_W;
  readonly height: number;
  /** The panel, the band and the credit: everything that never moves. */
  private readonly chrome: HTMLCanvasElement;
  private readonly rows: Row[];
  private readonly x: number;
  private readonly contentW: number;
  /** Each line's place among the sung lines (gaps don't count), for the falloff. */
  private readonly rank: number[];

  constructor(private readonly input: CardInput) {
    this.height = frameHeight(input.format);
    this.chrome = document.createElement('canvas');
    this.chrome.width = this.width;
    this.chrome.height = this.height;
    const ctx = this.chrome.getContext('2d')!;

    const square = input.format === 'square';
    const panelW = square ? SQUARE_PANEL_W : PANEL_W;
    const maxPanelH = square ? SQUARE_PANEL_MAX_H : PANEL_MAX_H;
    const left = (this.width - panelW) / 2;
    this.contentW = panelW - PAD * 2;
    this.x = left + PAD;

    const bandH = BAND_PAD * 2 + THUMB;
    const plan = fittestPlan(ctx, input.lines, maxPanelH - PAD * 2 - bandH, this.contentW);
    const panelH = PAD + plan.content + PAD + bandH;
    const top = (this.height - panelH) / 2;

    // Frosted glass, faked: the backdrop is already a blur, so a light veil
    // and a hairline rim read as the same material (LyricsShareCard.kt).
    roundRect(ctx, left, top, panelW, panelH);
    ctx.fillStyle = 'rgba(0, 0, 0, 0.07)';
    ctx.fill();
    ctx.fillStyle = 'rgba(255, 255, 255, 0.12)';
    ctx.fill();
    const bandTop = top + panelH - bandH;
    ctx.save();
    ctx.clip();
    ctx.fillStyle = 'rgba(255, 255, 255, 0.1)';
    ctx.fillRect(left, bandTop, panelW, bandH);
    ctx.restore();
    roundRect(ctx, left, top, panelW, panelH);
    ctx.strokeStyle = 'rgba(255, 255, 255, 0.12)';
    ctx.lineWidth = 2;
    ctx.stroke();

    // The song, and under it the app it came from.
    const thumbY = bandTop + BAND_PAD;
    roundRect(ctx, this.x, thumbY, THUMB, THUMB, THUMB_RADIUS);
    ctx.save();
    ctx.clip();
    if (input.cover) ctx.drawImage(input.cover, this.x, thumbY, THUMB, THUMB);
    else {
      ctx.fillStyle = 'rgba(255, 255, 255, 0.15)';
      ctx.fillRect(this.x, thumbY, THUMB, THUMB);
    }
    ctx.restore();
    const textX = this.x + THUMB + 28;
    const room = left + panelW - PAD - textX;
    const lineH = 44;
    const credits = input.logo ? 3 : 2;
    let baseline = thumbY + (THUMB - lineH * credits) / 2 + lineH - 10;
    ctx.textBaseline = 'alphabetic';
    ctx.font = `700 36px ${FONT}`;
    ctx.fillStyle = '#fff';
    ctx.fillText(ellipsised(input.title, room, (s) => ctx.measureText(s).width), textX, baseline);
    baseline += lineH;
    ctx.font = `400 36px ${FONT}`;
    ctx.fillStyle = 'rgba(255, 255, 255, 0.9)';
    ctx.fillText(ellipsised(input.artist, room, (s) => ctx.measureText(s).width), textX, baseline);
    if (input.logo) {
      baseline += lineH;
      ctx.font = `700 30px ${FONT}`;
      ctx.fillStyle = 'rgba(255, 255, 255, 0.6)';
      ctx.fillText('BitChord', textX, baseline);
    }

    // The rows from the top of the panel down.
    let y = top + PAD;
    this.rows = plan.rows.map((row) => {
      const placed = { ...row, baseline: y + row.gapBefore + row.ascent + (row.isGap ? row.size * GAP_MARKER_OFFSET : 0) };
      y += row.gapBefore + row.height;
      return placed;
    });

    let next = 0;
    this.rank = input.lines.map((line) => (line.isGap ? -1 : next++));
  }

  /** The card over whatever [ctx] already holds, at track time [positionMs] (null: the still card). */
  draw(ctx: CanvasRenderingContext2D, positionMs: number | null): void {
    ctx.drawImage(this.chrome, 0, 0);
    const active = positionMs === null ? -1 : this.activeLine(positionMs);
    ctx.textBaseline = 'alphabetic';
    for (const row of this.rows) this.drawRow(ctx, row, positionMs, active);
    ctx.direction = 'ltr';
    ctx.textAlign = 'left';
  }

  /** The line being sung at [positionMs]: the last one to have started, or the first. */
  private activeLine(positionMs: number): number {
    let first = -1;
    let last = -1;
    this.input.lines.forEach((line, index) => {
      if (line.isGap || line.startMs == null) return;
      if (first < 0) first = index;
      if (positionMs >= line.startMs) last = index;
    });
    return last >= 0 ? last : first;
  }

  private drawRow(ctx: CanvasRenderingContext2D, row: Row, positionMs: number | null, active: number) {
    ctx.font = row.font;
    setTracking(ctx, row.isGap ? 0 : row.size * TRACKING);
    // A right-to-left line reads from the right edge, as the panel sets it.
    ctx.direction = row.rtl ? 'rtl' : 'ltr';
    ctx.textAlign = row.rtl ? 'right' : 'left';
    const x = row.rtl ? this.x + this.contentW : this.x;
    if (row.isGap) {
      ctx.fillStyle = 'rgba(255, 255, 255, 0.4)';
      ctx.fillText(row.text, x, row.baseline);
      return;
    }
    const line = this.input.lines[row.line]!;
    if (positionMs === null || active < 0 || line.startMs == null) {
      ctx.fillStyle = '#fff';
      ctx.fillText(row.text, x, row.baseline);
      return;
    }
    const alpha = LINE_FALLOFF[Math.min(Math.abs(this.rank[row.line]! - this.rank[active]!), LINE_FALLOFF.length - 1)]!;
    const timing = line.source >= 0 ? this.input.lyrics[line.source] : undefined;
    const lit = (fraction: number) => `rgba(255, 255, 255, ${alpha * fraction})`;

    // No word timings (or a script the sweep can't follow): the line lights
    // whole the moment it starts, as the panel's does.
    if (!timing?.words.length || row.rtl) {
      ctx.fillStyle = lit(positionMs >= line.startMs ? 1 : UNSUNG_ALPHA);
      ctx.fillText(row.text, x, row.baseline);
      return;
    }
    if (positionMs >= endMs(timing)) {
      ctx.fillStyle = lit(1);
      ctx.fillText(row.text, x, row.baseline);
      return;
    }
    const revealed = Math.min(Math.max(revealedChars(timing, positionMs) - row.start, 0), row.text.length);
    if (revealed <= 0) {
      ctx.fillStyle = lit(UNSUNG_ALPHA);
      ctx.fillText(row.text, x, row.baseline);
      return;
    }
    // The sweep's edge, and a feather running ahead of it into what's still to come.
    const whole = Math.floor(revealed);
    const here = ctx.measureText(row.text.slice(0, whole)).width;
    const nextW = ctx.measureText(row.text.slice(0, Math.min(whole + 1, row.text.length))).width;
    const edge = x + here + (nextW - here) * (revealed - whole);
    const feather = Math.min(row.size * FEATHER_RATIO, edge - x);
    const sweep = ctx.createLinearGradient(edge, 0, edge + Math.max(feather, 0.5), 0);
    sweep.addColorStop(0, lit(1));
    sweep.addColorStop(1, lit(UNSUNG_ALPHA));
    ctx.fillStyle = sweep;
    ctx.fillText(row.text, x, row.baseline);
  }
}

/**
 * How many characters of [line]'s text the sweep has crossed at [positionMs]:
 * each word (or each syllable of a split one) at a steady pace from its start
 * to its end, nothing between words.
 */
export function revealedChars(line: LyricLine, positionMs: number): number {
  let cursor = 0;
  let revealed = 0;
  for (const word of line.words) {
    const at = line.text.indexOf(word.text, cursor);
    if (at < 0) continue;
    cursor = at + word.text.length;
    if (positionMs < word.startMs) break;
    if (positionMs >= word.endMs) {
      revealed = cursor;
      continue;
    }
    const parts = word.syllables.length
      ? word.syllables
      : [{ startMs: word.startMs, endMs: word.endMs, startChar: 0, endChar: word.text.length }];
    for (const part of parts) {
      if (positionMs < part.startMs) break;
      const span = part.endChar - part.startChar;
      const done = positionMs >= part.endMs || part.endMs <= part.startMs ? 1 : (positionMs - part.startMs) / (part.endMs - part.startMs);
      revealed = at + part.startChar + span * done;
    }
    break;
  }
  return revealed;
}

/**
 * LyricsShareCard.kt `wrap`: greedy, breaking wherever the line has to. The
 * longest prefix that fits is found first and only then walked back to a
 * space, so Japanese, Chinese and Korean lines (no spaces at all) still wrap.
 */
export function wrap(text: string, width: number, measure: (s: string) => number): string[] {
  const out: string[] = [];
  let rest = text.trim();
  while (rest) {
    let low = 0;
    let high = rest.length;
    while (low < high) {
      const mid = Math.ceil((low + high) / 2);
      if (measure(rest.slice(0, mid)) <= width) low = mid;
      else high = mid - 1;
    }
    const fit = low;
    if (fit <= 0) {
      out.push(rest);
      break;
    }
    const space = rest.lastIndexOf(' ', fit - 1);
    const take = space > 0 && fit < rest.length ? space : fit;
    out.push(rest.slice(0, take).trimEnd());
    rest = rest.slice(take).trimStart();
  }
  const rows = out.filter(Boolean);
  return rows.length ? rows : [''];
}

function ellipsised(text: string, room: number, measure: (s: string) => number): string {
  if (measure(text) <= room) return text;
  let cut = text.length;
  while (cut > 0 && measure(`${text.slice(0, cut).trimEnd()}…`) > room) cut--;
  return `${text.slice(0, cut).trimEnd()}…`;
}

/** The largest size on the ladder whose rows fit [room]; the bottom rung is a floor, and the panel grows past it. */
function fittestPlan(ctx: CanvasRenderingContext2D, lines: ShareLine[], room: number, width: number) {
  for (const size of LADDER) {
    const plan = planAt(ctx, lines, size, width);
    if (plan.content <= room) return plan;
  }
  return planAt(ctx, lines, LADDER.at(-1)!, width);
}

function planAt(ctx: CanvasRenderingContext2D, lines: ShareLine[], size: number, width: number) {
  const font = `700 ${size}px ${FONT}`;
  const markerSize = size * 0.65;
  const markerFont = `400 ${markerSize}px ${FONT}`;
  ctx.font = font;
  setTracking(ctx, size * TRACKING);
  const ascent = ctx.measureText('Hg').fontBoundingBoxAscent || size * 0.95;
  const rows: Omit<Row, 'baseline'>[] = [];
  lines.forEach((line, index) => {
    if (line.isGap) {
      // The skipped lines, as "(...)", the editorial mark for an omission:
      // compact, so it follows the lyric above closely.
      rows.push({ text: '(...)', font: markerFont, size: markerSize, height: markerSize * 1.2, gapBefore: 0, isGap: true, line: index, start: 0, rtl: false, ascent: ascent * 0.65 });
      return;
    }
    // One verse line is one paragraph: its first row carries the space above
    // it and its wraps sit tight underneath.
    const rtl = isRtl(line.text);
    let cursor = 0;
    wrap(line.text, width, (s) => ctx.measureText(s).width).forEach((piece, wrapIndex) => {
      const at = line.text.indexOf(piece, cursor);
      const start = at >= 0 ? at : cursor;
      cursor = start + piece.length;
      const gapBefore = rows.length && wrapIndex === 0 && index > 0 ? size * 0.55 : 0;
      rows.push({ text: piece, font, size, height: size * LEAD, gapBefore, isGap: false, line: index, start, rtl, ascent });
    });
  });
  const content = ascent + rows.reduce((total, row) => total + row.gapBefore + row.height, 0);
  return { rows, ascent, content };
}

/** Canvas letter-spacing where the browser has it (Chromium, and so WebView2); plain spacing elsewhere. */
function setTracking(ctx: CanvasRenderingContext2D, px: number) {
  if ('letterSpacing' in ctx) ctx.letterSpacing = `${px}px`;
}

function roundRect(ctx: CanvasRenderingContext2D, x: number, y: number, w: number, h: number, r = RADIUS) {
  ctx.beginPath();
  ctx.roundRect(x, y, w, h, r);
}

/**
 * The song's colours under the card: [image] (the sleeve, or a frame of its
 * Canvas) filling the frame, blurred at a quarter of the size and scaled back
 * up (the same picture as blurring the full frame, for a sixteenth of the
 * pixels), then dimmed under white type. LyricsShareVideo.kt's clip effects.
 */
export function drawBackdrop(
  ctx: CanvasRenderingContext2D,
  image: HTMLImageElement | HTMLVideoElement | null,
  scratch: HTMLCanvasElement,
  width: number,
  height: number,
) {
  ctx.fillStyle = '#000';
  ctx.fillRect(0, 0, width, height);
  const iw = image instanceof HTMLVideoElement ? image.videoWidth : image?.naturalWidth;
  const ih = image instanceof HTMLVideoElement ? image.videoHeight : image?.naturalHeight;
  if (!image || !iw || !ih) return;
  if (scratch.width !== Math.round(width / 4)) scratch.width = Math.round(width / 4);
  if (scratch.height !== Math.round(height / 4)) scratch.height = Math.round(height / 4);
  const small = scratch.getContext('2d')!;
  small.clearRect(0, 0, scratch.width, scratch.height);
  // A little overscan keeps the blur's soft edges out of the frame.
  const scale = Math.max(scratch.width / iw, scratch.height / ih) * 1.1;
  small.filter = `blur(${BLUR_PX}px)`;
  small.drawImage(image, (scratch.width - iw * scale) / 2, (scratch.height - ih * scale) / 2, iw * scale, ih * scale);
  small.filter = 'none';
  ctx.drawImage(scratch, 0, 0, width, height);
  ctx.fillStyle = `rgba(0, 0, 0, ${1 - DIM})`;
  ctx.fillRect(0, 0, width, height);
}

// LyricsShareCard.kt's story geometry, at 1080 wide.
const PANEL_W = 760;
const PANEL_MAX_H = 1280;
// A square frame has less height, so its card is a little wider and shorter.
const SQUARE_PANEL_W = 840;
const SQUARE_PANEL_MAX_H = 900;
const PAD = 52;
const RADIUS = 44;
const THUMB = 128;
const THUMB_RADIUS = 14;
const BAND_PAD = 40;
const LADDER = [60, 56, 52, 48, 44, 40, 36, 32];

// The panel's type and animation, as ratios of the size (its 34sp headline).
const TRACKING = -0.7 / 34;
const LEAD = 41 / 34;
const FEATHER_RATIO = 30 / 34;
const GAP_MARKER_OFFSET = 0.18;
const UNSUNG_ALPHA = 0.45;
const LINE_FALLOFF = [1, 0.8, 0.7, 0.58, 0.46];

/** At quarter size: about the blur the player's backdrop has at full size. */
const BLUR_PX = 12;
/** The backdrop at 60%: about as dark as the player's own, under white type. */
const DIM = 0.6;
