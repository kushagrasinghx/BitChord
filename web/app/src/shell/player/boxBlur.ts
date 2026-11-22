/**
 * ArtworkMeshBackdrop.kt boxBlurred: three box passes each way over a small
 * copy of the cover, which together approximate a broad Gaussian. Edges are
 * clamped (the pixel at the border repeats), so the blur stays opaque out to
 * the corners rather than fading in from black as a CSS blur would.
 *
 * Works in place on RGBA bytes; alpha is left as it is.
 */
export function boxBlur(pixels: Uint8ClampedArray, width: number, height: number, passes = 3): void {
  if (width < 2 || height < 2) return;
  // FULL_BLUR_SOURCE_PX / 12: a radius of 10 on the phone's 128px copy.
  const radius = Math.max(2, Math.floor(Math.min(width, height) / 12));
  const scratch = new Uint8ClampedArray(pixels.length);
  for (let pass = 0; pass < passes; pass++) {
    blurPass(pixels, scratch, width, height, radius, true);
    blurPass(scratch, pixels, width, height, radius, false);
  }
}

function blurPass(source: Uint8ClampedArray, target: Uint8ClampedArray, width: number, height: number, radius: number, horizontal: boolean) {
  const major = horizontal ? width : height;
  const minor = horizontal ? height : width;
  const window = radius * 2 + 1;
  const at = (fixed: number, moving: number) => {
    const p = Math.min(major - 1, Math.max(0, moving));
    return (horizontal ? fixed * width + p : p * width + fixed) * 4;
  };
  for (let fixed = 0; fixed < minor; fixed++) {
    let r = 0;
    let g = 0;
    let b = 0;
    for (let offset = -radius; offset <= radius; offset++) {
      const i = at(fixed, offset);
      r += source[i]!;
      g += source[i + 1]!;
      b += source[i + 2]!;
    }
    for (let moving = 0; moving < major; moving++) {
      const i = at(fixed, moving);
      target[i] = (r + (window >> 1)) / window;
      target[i + 1] = (g + (window >> 1)) / window;
      target[i + 2] = (b + (window >> 1)) / window;
      target[i + 3] = source[i + 3]!;
      const leaving = at(fixed, moving - radius);
      const entering = at(fixed, moving + radius + 1);
      r += source[entering]! - source[leaving]!;
      g += source[entering + 1]! - source[leaving + 1]!;
      b += source[entering + 2]! - source[leaving + 2]!;
    }
  }
}
