import { useEffect, useState } from 'react';
import { artworkAtPixels } from './artwork';

export interface ArtworkColor {
  /** The background colour behind the page ("joe colour", report §3). */
  background: string;
  /** Whether text on it should be light (a dark colour) or dark. */
  dark: boolean;
}

const cache = new Map<string, ArtworkColor>();

/**
 * The artwork's average colour, drawn from a tiny copy of it on a canvas.
 * YouTube's image hosts send `Access-Control-Allow-Origin: *`, so the canvas
 * stays readable without the extension.
 */
async function measure(url: string): Promise<ArtworkColor> {
  const image = new Image();
  image.crossOrigin = 'anonymous';
  image.referrerPolicy = 'no-referrer';
  image.decoding = 'async';
  // 60px: a size YouTube's pages request, so the CDN has it cached (lib/artwork.ts).
  image.src = artworkAtPixels(url, 60);
  await image.decode();
  const canvas = document.createElement('canvas');
  canvas.width = canvas.height = 8;
  const context = canvas.getContext('2d', { willReadFrequently: true })!;
  context.drawImage(image, 0, 0, 8, 8);
  const { data } = context.getImageData(0, 0, 8, 8);
  let r = 0;
  let g = 0;
  let b = 0;
  for (let i = 0; i < data.length; i += 4) {
    r += data[i]!;
    g += data[i + 1]!;
    b += data[i + 2]!;
  }
  const n = data.length / 4;
  [r, g, b] = [r / n, g / n, b / n];
  // Apple darkens the colour behind its dark layout; keep text legible.
  const luminance = (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255;
  const dark = luminance < 0.6;
  const k = dark ? Math.min(1, 0.55 / Math.max(luminance, 0.05)) * 0.6 : 1;
  const scale = (v: number) => Math.round(dark ? Math.min(v * k, 90) : v);
  return { background: `rgb(${scale(r)}, ${scale(g)}, ${scale(b)})`, dark };
}

export function useArtworkColor(url: string | null | undefined): ArtworkColor | null {
  const [color, setColor] = useState<ArtworkColor | null>(() => (url ? cache.get(url) ?? null : null));
  useEffect(() => {
    if (!url) return setColor(null);
    const known = cache.get(url);
    if (known) return setColor(known);
    let live = true;
    measure(url)
      .then((measured) => {
        cache.set(url, measured);
        if (live) setColor(measured);
      })
      .catch(() => {
        // An unreadable image leaves the page in its normal colours.
      });
    return () => {
      live = false;
    };
  }, [url]);
  return color;
}
