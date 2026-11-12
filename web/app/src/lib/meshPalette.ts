import { artworkAtPixels } from './artwork';

export type Rgb = [number, number, number];

/** The mesh is the artwork averaged into a GRID × GRID square of means. */
export const GRID = 4;

const cache = new Map<string, Rgb[]>();

/**
 * Pushes a cell's colour toward something white text stands on: a little more
 * saturated, and no brighter than a mid-dark tone. Dark cells stay as they are,
 * so a mostly black sleeve still makes a mostly black backdrop.
 */
function shape([r, g, b]: Rgb): Rgb {
  const gray = 0.2126 * r + 0.7152 * g + 0.0722 * b;
  const saturated = [r, g, b].map((v) => gray + (v - gray) * 1.3) as Rgb;
  const luminance = gray / 255;
  const k = luminance > 0.38 ? 0.38 / luminance : 1;
  return saturated.map((v) => Math.round(Math.min(255, Math.max(0, v * k)))) as Rgb;
}

/**
 * The artwork's colours, roughly where and in the proportions they are on the
 * sleeve, as GRID² cells read row by row (the approach of the desktop's
 * ArtworkMeshBackdrop.kt; no quantiser, so nine-tenths black stays black).
 * YouTube's image hosts send `Access-Control-Allow-Origin: *`, so the canvas
 * stays readable without the extension.
 */
async function measure(url: string): Promise<Rgb[]> {
  const image = new Image();
  image.crossOrigin = 'anonymous';
  image.referrerPolicy = 'no-referrer';
  image.decoding = 'async';
  // 60px: a size YouTube's pages request, so the CDN has it cached (lib/artwork.ts).
  image.src = artworkAtPixels(url, 60);
  await image.decode();
  const side = GRID * 3;
  const canvas = document.createElement('canvas');
  canvas.width = canvas.height = side;
  const context = canvas.getContext('2d', { willReadFrequently: true })!;
  context.drawImage(image, 0, 0, side, side);
  const { data } = context.getImageData(0, 0, side, side);
  const cells: Rgb[] = [];
  for (let row = 0; row < GRID; row++) {
    for (let col = 0; col < GRID; col++) {
      const sum: Rgb = [0, 0, 0];
      for (let y = row * 3; y < row * 3 + 3; y++) {
        for (let x = col * 3; x < col * 3 + 3; x++) {
          const i = (y * side + x) * 4;
          sum[0] += data[i]!;
          sum[1] += data[i + 1]!;
          sum[2] += data[i + 2]!;
        }
      }
      cells.push(shape(sum.map((v) => v / 9) as Rgb));
    }
  }
  return cells;
}

export function meshPalette(url: string): Promise<Rgb[]> {
  const known = cache.get(url);
  if (known) return Promise.resolve(known);
  return measure(url).then((cells) => {
    cache.set(url, cells);
    return cells;
  });
}
