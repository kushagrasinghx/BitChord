/**
 * The square sizes YouTube Music's own pages request. googleusercontent
 * resizes on demand, and answers bursts of uncached sizes with 429 (measured:
 * 68 of 73 artwork requests on Home refused when each asked for its exact
 * displayed size). Snapping to these keeps requests on cached variants.
 */
const SQUARE_SIZES = [60, 120, 226, 544, 1200];

const RESIZABLE = /googleusercontent\.com|ggpht\.com/;

/**
 * Asks YouTube's image CDN for a size close to what's displayed (prompt §3):
 * the smallest standard size at least as large as the rendered pixels.
 * Non-square artwork (artist heroes) and i.ytimg.com thumbnails are fixed
 * sizes and pass through unchanged.
 */
export function sizedArtwork(url: string, cssPx: number, aspect = 1): string {
  if (!RESIZABLE.test(url) || Math.abs(aspect - 1) > 0.01) return url;
  const dpr = Math.min(typeof window === 'undefined' ? 2 : window.devicePixelRatio || 1, 3);
  const needed = cssPx * dpr;
  const size = SQUARE_SIZES.find((s) => s >= needed) ?? SQUARE_SIZES[SQUARE_SIZES.length - 1]!;
  return artworkAtPixels(url, size);
}

/** A square artwork at an exact pixel size (Media Session, canvases). */
export function artworkAtPixels(url: string, px: number): string {
  if (!RESIZABLE.test(url)) return url;
  return `${url.replace(/=[^/=]*$/, '')}=w${px}-h${px}-l90-rj`;
}
