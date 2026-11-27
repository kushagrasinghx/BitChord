import { GRID, type Rgb } from '@/lib/meshPalette';

function toHsl([r, g, b]: Rgb): [number, number, number] {
  const [rn, gn, bn] = [r / 255, g / 255, b / 255];
  const max = Math.max(rn, gn, bn);
  const min = Math.min(rn, gn, bn);
  const l = (max + min) / 2;
  if (max === min) return [0, 0, l];
  const d = max - min;
  const s = l > 0.5 ? d / (2 - max - min) : d / (max + min);
  const h = max === rn ? (gn - bn) / d + (gn < bn ? 6 : 0) : max === gn ? (bn - rn) / d + 2 : (rn - gn) / d + 4;
  return [h * 60, s, l];
}

export function hsl(h: number, s: number, l: number): string {
  return `hsl(${((h % 360) + 360) % 360} ${Math.round(s * 100)}% ${Math.round(l * 100)}%)`;
}

/** `0xFA2D48` as a colour, for palettes copied from the Kotlin. */
export const rgbOf = (hex: number): Rgb => [(hex >> 16) & 255, (hex >> 8) & 255, hex & 255];

/** A blob's colour (MeshGradient.kt tuned): saturation ×1.35, lightness held to .28–.58. */
export function meshTuned(rgb: Rgb): string {
  const [h, s, l] = toHsl(rgb);
  return hsl(h, Math.min(1, s * 1.35), Math.min(0.58, Math.max(0.28, l)));
}

/** The mesh's ground under the blobs (MeshGradient.kt dimmed): the colour at 12% lightness. */
export function meshDimmed(rgb: Rgb): string {
  const [h, s] = toHsl(rgb);
  return hsl(h, s, 0.12);
}

/**
 * The artwork's GRID² cells as the mesh's four colours, in the blobs' order
 * (top left, top right, bottom right, bottom left): from each quarter of the
 * sleeve its most colourful cell, so each colour sits roughly where it is on
 * the cover and a letterboxed thumbnail's black bars don't wash it out. The
 * phone picks palette swatches (rememberArtworkColors); this is the nearest
 * a 4×4 reading gets.
 */
export function meshSlots(cells: Rgb[]): Rgb[] {
  const half = GRID / 2;
  const colourful = (rgb: Rgb) => {
    const [, s, l] = toHsl(rgb);
    return s * (1 - Math.abs(l - 0.5));
  };
  return [
    [0, 0],
    [0, half],
    [half, half],
    [half, 0],
  ].map(([row, col]) => {
    let best: Rgb = [0, 0, 0];
    let score = -1;
    for (let r = row!; r < row! + half; r++) {
      for (let c = col!; c < col! + half; c++) {
        const cell = cells[r * GRID + c] ?? [0, 0, 0];
        if (colourful(cell) > score) {
          score = colourful(cell);
          best = cell;
        }
      }
    }
    return best;
  });
}

/** [rgb] turned [degrees] round the colour wheel, saturation and lightness kept (the story's per-card hue). */
export function rotated(rgb: Rgb, degrees: number): string {
  const [h, s, l] = toHsl(rgb);
  return hsl(h + degrees, s, l);
}

/**
 * The Replay button's fill (ReplayYearButton.kt pillColor): the most colourful
 * of the artwork's mesh colours, held dark and rich enough for white type, so
 * the button already looks like the story it opens.
 */
export function pillColor(cells: Rgb[]): string {
  let best: [number, number, number] | null = null;
  let score = -1;
  for (const cell of cells) {
    const [h, s, l] = toHsl(cell);
    const value = s * (1 - Math.abs(l - 0.5));
    if (value > score) {
      score = value;
      best = [h, s, l];
    }
  }
  return best ? hsl(best[0], Math.min(0.85, Math.max(0.55, best[1])), 0.4) : '#e5243b';
}

/**
 * A card's highlight (ReplayYearSlides.kt accentOf): the most colourful of its
 * mesh colours, turned with the card and lifted until it reads against the
 * darkened backdrop.
 */
export function accentOf(cells: Rgb[], degrees: number): string {
  let best: [number, number, number] = [0, 0, 0.5];
  let score = -1;
  for (const cell of cells) {
    const [h, s, l] = toHsl(cell);
    const value = s * (1 - Math.abs(l - 0.5));
    if (value > score) {
      score = value;
      best = [h, s, l];
    }
  }
  return hsl(best[0] + degrees, Math.min(1, Math.max(0.55, best[1])), 0.7);
}
