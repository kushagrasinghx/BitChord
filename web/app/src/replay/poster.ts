import type { ReplayCharts, ReplayYearStory } from '@bitchord/shared-kotlin';
import { artworkAtPixels } from '@/lib/artwork';
import { GRID, meshPalette, type Rgb } from '@/lib/meshPalette';
import { accentOf, rotated } from './colors';
import { count, formatListening, formatMinutes } from './format';
import { cardContent, slideHue, type YearSlide } from './year/content';

/*
 * The Replay as pictures you can send (ReplayPoster.kt, ReplayYearShareSheet.kt),
 * drawn on a canvas rather than captured from the page: what is sent is the same
 * size and layout on every screen, without the page's chrome around it. 1080 ×
 * 1920, the 9:16 every story surface expects; the year card is the phone's
 * 405 × 720 card at that size.
 */

const W = 1080;
const H = 1920;
const MARGIN = 84;
const FALLBACK: Rgb[] = Array.from({ length: GRID * GRID }, () => [52, 52, 56]);

type Ctx = CanvasRenderingContext2D;

function family(): string {
  return getComputedStyle(document.body).fontFamily || 'system-ui, sans-serif';
}

/** YouTube's image hosts allow cross-origin reads (lib/meshPalette.ts), so the canvas stays exportable. */
async function loadImage(url: string | null | undefined, px: number): Promise<HTMLImageElement | null> {
  if (!url) return null;
  const image = new Image();
  image.crossOrigin = 'anonymous';
  image.referrerPolicy = 'no-referrer';
  image.src = artworkAtPixels(url, px);
  return image.decode().then(
    () => image,
    () => null,
  );
}

async function loadAll(urls: (string | null | undefined)[], px: number): Promise<Map<string, HTMLImageElement | null>> {
  const unique = [...new Set(urls.filter((u): u is string => !!u))];
  const images = await Promise.all(unique.map((u) => loadImage(u, px)));
  return new Map(unique.map((u, i) => [u, images[i] ?? null]));
}

function canvas(): [HTMLCanvasElement, Ctx] {
  const element = document.createElement('canvas');
  element.width = W;
  element.height = H;
  return [element, element.getContext('2d')!];
}

/** The artwork's mesh (as MeshBackdrop draws it), turned [hue] degrees, under a shade white type reads on. */
async function backdrop(ctx: Ctx, art: string | null | undefined, hue: number): Promise<Rgb[]> {
  const cells = art ? await meshPalette(art).catch(() => FALLBACK) : FALLBACK;
  ctx.fillStyle = '#18181a';
  ctx.fillRect(0, 0, W, H);
  cells.forEach((cell, i) => {
    const x = ((i % GRID) + 0.5) * (W / GRID);
    const y = (Math.floor(i / GRID) + 0.5) * (H / GRID);
    const g = ctx.createRadialGradient(x, y, 0, x, y, H * 0.42);
    g.addColorStop(0, rotated(cell, hue));
    g.addColorStop(1, 'transparent');
    ctx.fillStyle = g;
    ctx.fillRect(0, 0, W, H);
  });
  const shade = ctx.createLinearGradient(0, 0, 0, H);
  shade.addColorStop(0, 'rgb(0 0 0 / 0.3)');
  shade.addColorStop(1, 'rgb(0 0 0 / 0.62)');
  ctx.fillStyle = shade;
  ctx.fillRect(0, 0, W, H);
  return cells;
}

function text(ctx: Ctx, value: string, x: number, y: number, weight: number, px: number, color: string, maxWidth?: number) {
  ctx.font = `${weight} ${px}px ${family()}`;
  ctx.fillStyle = color;
  ctx.fillText(maxWidth ? ellipsised(ctx, value, maxWidth) : value, x, y);
}

function ellipsised(ctx: Ctx, value: string, maxWidth: number): string {
  if (ctx.measureText(value).width <= maxWidth) return value;
  let cut = value;
  while (cut.length > 1 && ctx.measureText(`${cut}…`).width > maxWidth) cut = cut.slice(0, -1);
  return `${cut.trimEnd()}…`;
}

/** [value] broken into at most [lines] lines of [maxWidth], the last one ellipsised. */
function wrapped(ctx: Ctx, value: string, maxWidth: number, lines: number): string[] {
  const out: string[] = [];
  let line = '';
  const words = value.split(/\s+/);
  for (let i = 0; i < words.length; i++) {
    const next = line ? `${line} ${words[i]}` : words[i]!;
    if (ctx.measureText(next).width <= maxWidth || !line) {
      line = next;
      continue;
    }
    if (out.length === lines - 1) {
      out.push(ellipsised(ctx, [line, ...words.slice(i)].join(' '), maxWidth));
      return out;
    }
    out.push(line);
    line = words[i]!;
  }
  if (line) out.push(out.length === lines - 1 ? ellipsised(ctx, line, maxWidth) : line);
  return out;
}

function cover(ctx: Ctx, image: HTMLImageElement | null | undefined, x: number, y: number, size: number, circular: boolean) {
  ctx.save();
  ctx.beginPath();
  if (circular) ctx.arc(x + size / 2, y + size / 2, size / 2, 0, Math.PI * 2);
  else ctx.roundRect(x, y, size, size, size * 0.06);
  ctx.clip();
  if (image) ctx.drawImage(image, x, y, size, size);
  else {
    ctx.fillStyle = 'rgb(255 255 255 / 0.12)';
    ctx.fillRect(x, y, size, size);
  }
  ctx.restore();
}

function brand(ctx: Ctx, y: number) {
  ctx.textAlign = 'right';
  text(ctx, 'BitChord', W - MARGIN, y, 800, 40, 'rgb(255 255 255 / 0.8)');
  ctx.textAlign = 'left';
}

const toPng = (element: HTMLCanvasElement) =>
  new Promise<Blob>((resolve, reject) => element.toBlob((b) => (b ? resolve(b) : reject(new Error('No picture'))), 'image/png'));

/** The whole Replay as one picture: the minutes, the top five songs and artists, and the albums. */
export async function renderReplayPoster(charts: ReplayCharts, label: string): Promise<Blob> {
  await document.fonts.ready;
  const songs = charts.songs.slice(0, 5);
  const artists = charts.artists.slice(0, 5);
  const albums = charts.albums.slice(0, 4);
  const images = await loadAll([...songs.map((s) => s.art), ...artists.map((a) => a.art), ...albums.map((a) => a.art)], 240);
  const [element, ctx] = canvas();
  await backdrop(ctx, songs[0]?.art ?? null, 0);

  brand(ctx, 132);
  text(ctx, 'Replay', MARGIN, 230, 900, 128, '#fff');
  text(ctx, label, MARGIN, 300, 600, 52, 'rgb(255 255 255 / 0.72)', W - MARGIN * 2);

  text(ctx, 'MINUTES LISTENED', MARGIN, 410, 700, 34, 'rgb(255 255 255 / 0.65)');
  text(ctx, formatMinutes(charts.totalMs), MARGIN, 560, 900, 160, '#fff');
  text(ctx, count(charts.totalPlays, 'play'), MARGIN, 630, 600, 44, 'rgb(255 255 255 / 0.72)');

  const column = (W - MARGIN * 2 - 48) / 2;
  const rows = (x: number, title: string, items: { title: string; sub: string; art: string | null | undefined }[], circular: boolean) => {
    text(ctx, title, x, 740, 800, 46, '#fff');
    items.forEach((item, i) => {
      const y = 780 + i * 148;
      cover(ctx, item.art ? images.get(item.art) : null, x, y, 112, circular);
      text(ctx, item.title, x + 136, y + 48, 700, 38, '#fff', column - 136);
      text(ctx, item.sub, x + 136, y + 96, 500, 32, 'rgb(255 255 255 / 0.65)', column - 136);
    });
  };
  rows(MARGIN, 'Top songs', songs.map((s) => ({ title: s.title, sub: s.artist, art: s.art })), false);
  rows(
    MARGIN + column + 48,
    'Top artists',
    artists.map((a) => ({ title: a.title, sub: formatListening(a.ms), art: a.art })),
    true,
  );

  if (albums.length) {
    text(ctx, 'Top albums', MARGIN, 1560, 800, 46, '#fff');
    const size = (W - MARGIN * 2 - 3 * 24) / 4;
    albums.forEach((album, i) => {
      const x = MARGIN + i * (size + 24);
      cover(ctx, album.art ? images.get(album.art) : null, x, 1596, size, false);
      text(ctx, album.title, x, 1596 + size + 46, 700, 32, '#fff', size);
    });
  }
  return toPng(element);
}

/** A card of the year story as it stands when it has finished, for its share button. */
export async function renderYearCard(story: ReplayYearStory, slide: YearSlide, year: number, art: string | null | undefined): Promise<Blob> {
  await document.fonts.ready;
  const content = cardContent(story, slide, year);
  const images = await loadAll([content.art, ...content.rows.map((r) => r.art)], 600);
  const [element, ctx] = canvas();
  const cells = await backdrop(ctx, art, slideHue(slide));
  const accent = accentOf(cells, slideHue(slide));
  const pad = 60;
  const width = W - pad * 2;

  text(ctx, `REPLAY’${String(year % 100).padStart(2, '0')}`, pad, 120, 900, 40, accent);
  brand(ctx, 120);

  ctx.font = `800 80px ${family()}`;
  let y = 250;
  for (const line of wrapped(ctx, content.setup, width, 2)) {
    text(ctx, line, pad, y, 800, 80, '#fff');
    y += 92;
  }
  y += 40;

  // The artist card's runners-up only flicker past on screen; the picture keeps the one.
  const rows = slide === 'ARTIST_REVEAL' ? [] : content.rows;
  if (content.art && rows.length === 0) {
    const size = slide === 'SUMMARY' ? 340 : 600;
    cover(ctx, images.get(content.art), (W - size) / 2, y, size, slide === 'ARTIST_REVEAL' || slide === 'SUMMARY');
    y += size + 30;
  }
  // y is the top of the next block; each line of text drops by its ascent before it is drawn.
  if (content.hero) {
    const numeric = /^[\d,]+$/.test(content.hero);
    const px = numeric ? (content.hero.length > 7 ? 220 : 280) : 120;
    ctx.font = `900 ${px}px ${family()}`;
    for (const line of wrapped(ctx, content.hero, width, numeric ? 1 : 2)) {
      y += px * 0.8;
      text(ctx, line, pad, y, 900, px, '#fff');
      y += px * 0.22;
    }
  }
  if (content.unit) {
    y += 80;
    text(ctx, content.unit, pad, y, 900, 96, accent, width);
    y += 60;
  }
  if (slide === 'MONTHS') {
    // Up to twelve months: a grid of covers, each under its month, rather than a list that runs off the card.
    const size = (width - 3 * 28) / 4;
    rows.forEach((row, i) => {
      const x = pad + (i % 4) * (size + 28);
      const top = y + Math.floor(i / 4) * (size + 120);
      text(ctx, row.subtitle ?? '', x, top + 40, 900, 40, accent, size);
      cover(ctx, row.art ? images.get(row.art) : null, x, top + 60, size, false);
      text(ctx, row.title, x, top + 60 + size + 40, 700, 30, '#fff', size);
    });
    y += Math.ceil(rows.length / 4) * (size + 120);
  } else if (rows.length) {
    const size = 150;
    rows.slice(0, 6).forEach((row, i) => {
      cover(ctx, row.art ? images.get(row.art) : null, pad, y, size, content.circular);
      text(ctx, `${i + (slide === 'TOP_SONGS' ? 2 : 1)}  ${row.title}`, pad + size + 36, y + 70, 800, 52, '#fff', width - size - 36);
      if (row.subtitle) text(ctx, row.subtitle, pad + size + 36, y + 124, 600, 40, 'rgb(255 255 255 / 0.7)', width - size - 36);
      y += size + 36;
    });
  }
  if (content.columns.length) {
    y += 40;
    content.columns.forEach((list, c) => {
      const x = pad + c * (width / 2 + 20);
      text(ctx, list.title, x, y + 40, 800, 44, accent, width / 2 - 20);
      list.names.forEach((name, i) => text(ctx, `${i + 1} ${name}`, x, y + 110 + i * 64, 700, 44, '#fff', width / 2 - 20));
    });
    y += 110 + 5 * 64;
  }
  y += 30;
  ctx.font = `600 50px ${family()}`;
  for (const line of content.detail) {
    for (const part of wrapped(ctx, line, width, 2)) {
      y += 50;
      text(ctx, part, pad, y, 600, 50, 'rgb(255 255 255 / 0.88)');
      y += 16;
    }
  }
  return toPng(element);
}

/** Hands [picture] to the system's share sheet where the browser has one for files, else saves it. */
export async function sharePicture(picture: Blob, fileName: string, title: string): Promise<void> {
  const file = new File([picture], fileName, { type: 'image/png' });
  if (navigator.canShare?.({ files: [file] })) {
    try {
      await navigator.share({ files: [file], title });
      return;
    } catch (error) {
      // Dismissing the sheet is an answer, not a reason to download instead.
      if (error instanceof DOMException && error.name === 'AbortError') return;
    }
  }
  const url = URL.createObjectURL(picture);
  const link = document.createElement('a');
  link.href = url;
  link.download = fileName;
  link.click();
  setTimeout(() => URL.revokeObjectURL(url), 10_000);
}
