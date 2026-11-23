/**
 * The square sizes YouTube Music's own pages request. googleusercontent
 * resizes on demand, and answers bursts of uncached sizes with 429 (measured:
 * 68 of 73 artwork requests on Home refused when each asked for its exact
 * displayed size). Snapping to these keeps requests on cached variants.
 */
const SQUARE_SIZES = [60, 120, 226, 544, 1200];

const RESIZABLE = /googleusercontent\.com|ggpht\.com/;
const YTIMG = /i\.ytimg\.com\/vi\/([^/]+)\//;
const YTIMG_FILE = /\/(?:hqdefault|mqdefault|sddefault|default|hq720|maxresdefault)\.jpg(?:\?.*)?$/;

/**
 * Asks YouTube's image CDN for a size close to what's displayed (prompt §3):
 * the smallest standard size at least as large as the rendered pixels.
 * Upgrades i.ytimg.com video thumbnails to HD (maxresdefault / hq720) when
 * displayed at high resolution (fixes #1459).
 */
export function sizedArtwork(url: string, cssPx: number, aspect = 1): string {
  const dpr = Math.min(typeof window === 'undefined' ? 2 : window.devicePixelRatio || 1, 3);
  const needed = cssPx * dpr;

  if (RESIZABLE.test(url) && Math.abs(aspect - 1) <= 0.01) {
    const size = SQUARE_SIZES.find((s) => s >= needed) ?? SQUARE_SIZES[SQUARE_SIZES.length - 1]!;
    return artworkAtPixels(url, size);
  }

  if (YTIMG.test(url) && needed >= 480) {
    return artworkAtPixels(url, needed >= 720 ? 1200 : 720);
  }

  return url;
}

/** A square artwork or high-res video thumbnail at an exact pixel size (Media Session, canvases). */
export function artworkAtPixels(url: string, px: number): string {
  if (RESIZABLE.test(url)) {
    return `${url.replace(/=[^/=]*$/, '')}=w${px}-h${px}-l90-rj`;
  }
  if (YTIMG.test(url) && px >= 480) {
    const target = px >= 720 ? 'maxresdefault.jpg' : 'hq720.jpg';
    return url.replace(YTIMG_FILE, `/${target}`);
  }
  return url;
}
