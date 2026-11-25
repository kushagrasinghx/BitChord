/**
 * What Auto climbs to: H.264 tops out here on YouTube and every browser
 * decodes it in hardware, so Auto never asks the machine for a VP9 2K or 4K
 * frame it may have to decode in software. Above this is the listener's call,
 * in the quality switcher.
 */
export const AUTO_MAX_HEIGHT = 1080;

/**
 * Auto's ceiling: the smallest rendition that fills the front surface, never above the H.264
 * ceiling. The mini player's preview is ~80 device pixels tall, so it takes 144p instead of 1080p
 * (a fraction of the decode, GPU memory and data); the full-screen player still gets HD.
 */
export function autoHeight(displayHeight: number, heights: readonly number[]): number {
  const fits = heights.filter((h) => h >= displayHeight && h <= AUTO_MAX_HEIGHT);
  if (fits.length) return Math.min(...fits);
  const below = heights.filter((h) => h <= AUTO_MAX_HEIGHT);
  return below.length ? Math.max(...below) : AUTO_MAX_HEIGHT;
}
