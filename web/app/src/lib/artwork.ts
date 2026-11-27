import { artworkAt, youTubeThumbnailFallback } from '@bitchord/shared-kotlin';

/**
 * The square sizes YouTube Music's own pages request. googleusercontent
 * resizes on demand, and answers bursts of uncached sizes with 429 (measured:
 * 68 of 73 artwork requests on Home refused when each asked for its exact
 * displayed size). Snapping to these keeps requests on cached variants.
 */
const SQUARE_SIZES = [60, 120, 226, 544, 1200];

const RESIZABLE = /googleusercontent\.com|ggpht\.com/;
const YOUTUBE_THUMBNAIL = /ytimg\.com|img\.youtube\.com/;

/** Upgraded thumbnails YouTube said this video doesn't have, for the rest of the visit. */
const missing = new Set<string>();

/**
 * Asks YouTube's image CDN for a size close to what's displayed (prompt §3):
 * the smallest standard size at least as large as the rendered pixels.
 * A video's i.ytimg.com thumbnail goes up to the rendition that suits that
 * size, as the phone's artworkAt does (Models.kt, shared Kotlin), unless this
 * video has turned out not to have it. Other non-square artwork (artist
 * heroes) is a fixed size and passes through unchanged.
 */
export function sizedArtwork(url: string, cssPx: number, aspect = 1): string {
  const dpr = Math.min(typeof window === 'undefined' ? 2 : window.devicePixelRatio || 1, 3);
  const needed = cssPx * dpr;
  if (YOUTUBE_THUMBNAIL.test(url)) {
    const upgraded = artworkAt(url, Math.round(needed)) ?? url;
    return missing.has(upgraded) ? (youTubeThumbnailFallback(upgraded) ?? url) : upgraded;
  }
  if (!RESIZABLE.test(url) || Math.abs(aspect - 1) > 0.01) return url;
  const size = SQUARE_SIZES.find((s) => s >= needed) ?? SQUARE_SIZES[SQUARE_SIZES.length - 1]!;
  return artworkAtPixels(url, size);
}

/** A square artwork at an exact pixel size (Media Session, canvases). */
export function artworkAtPixels(url: string, px: number): string {
  if (!RESIZABLE.test(url)) return url;
  return `${url.replace(/=[^/=]*$/, '')}=w${px}-h${px}-l90-rj`;
}

/**
 * The `hqdefault` every video has, for an upgraded thumbnail that [img] failed
 * to load (YouTubeThumbnailFallback.kt); null when there's nothing to fall
 * back to. YouTube answers a missing rendition with a 120×90 grey stand-in,
 * which is remembered as missing. A failed load falls back this once only:
 * a dropped connection says nothing about the video.
 */
export function thumbnailFallback(url: string, img: HTMLImageElement | null, failed: boolean): string | null {
  const fallback = youTubeThumbnailFallback(url) ?? null;
  if (!fallback) return null;
  if (failed) return fallback;
  if (!img || img.naturalWidth > 120) return null;
  missing.add(url);
  return fallback;
}

/**
 * Loads and decodes [url] off screen, resolving the URL that is ready to draw
 * without a blank frame (its fallback, if it had to fall back), or null.
 */
export async function decodedArtwork(url: string): Promise<string | null> {
  const img = new Image();
  img.referrerPolicy = 'no-referrer';
  img.src = url;
  let failed = false;
  try {
    await img.decode();
  } catch {
    failed = true;
  }
  const fallback = thumbnailFallback(url, img, failed);
  if (fallback) return decodedArtwork(fallback);
  return failed ? null : url;
}
