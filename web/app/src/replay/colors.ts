import type { Rgb } from '@/lib/meshPalette';

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
