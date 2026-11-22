import { useEffect, useRef, useState, useSyncExternalStore } from 'react';
import { meshPalette, type Rgb } from '@/lib/meshPalette';
import { meshDimmed, meshSlots, meshTuned, rgbOf } from './colors';

/** MeshGradient.kt FallbackColors: what shows before the artwork is read, and when it can't be. */
export const FALLBACK_MESH: Rgb[] = [0x3a1c71, 0xd76d77, 0x2b5876, 0xffaf7b].map(rgbOf);

/** Where each of the four blobs sits, and how fast and which way it orbits (MeshGradient.kt anchors, speeds). */
const ANCHORS = [
  [0.2, 0.25],
  [0.8, 0.2],
  [0.75, 0.8],
  [0.25, 0.75],
] as const;
const SPEEDS = [1, -0.7, 0.85, -1.15];
const FRAME_MS = 1000 / 30;

/*
 * Each blob's falloff: 85% at the centre and along smootherstep to nothing at
 * the rim (BLOB_ALPHA, FADE_CURVE_STOPS), so no ring shows where it ends.
 */
const FALLOFF =
  'radial-gradient(closest-side, rgb(0 0 0 / 0.85), rgb(0 0 0 / 0.8) 20%, rgb(0 0 0 / 0.58) 40%, rgb(0 0 0 / 0.27) 60%, rgb(0 0 0 / 0.05) 80%, transparent)';

const reducedMotion = typeof matchMedia === 'function' ? matchMedia('(prefers-reduced-motion: reduce)') : null;
export function useReducedMotion(): boolean {
  return useSyncExternalStore(
    (onChange) => {
      reducedMotion?.addEventListener('change', onChange);
      return () => reducedMotion?.removeEventListener('change', onChange);
    },
    () => reducedMotion?.matches ?? false,
  );
}

/** Blob [i]'s centre at [phase], as fractions of the box (MeshGradient.kt's orbit). */
function centre(i: number, phase: number): [number, number] {
  const [x, y] = ANCHORS[i]!;
  const speed = SPEEDS[i]!;
  return [x + 0.16 * Math.cos(phase * speed + i * 1.7), y + 0.16 * Math.sin(phase * speed * 0.9 + i * 2.3)];
}

/**
 * The phone's palette mesh (sharedUi/.../ui/player/MeshGradient.kt
 * MeshGradientBackground): four soft colour blobs over the first colour
 * dimmed, the whole layer blurred and scaled 1.3× so the blur's edges never
 * show, under its own light scrim (.10 to .38) for white type. It fills its
 * positioned parent.
 *
 * Still by default, as Replay and its cards draw it. With [driftMs] the blobs
 * orbit continuously, a full turn every four drifts (the onboarding pages),
 * and each colour eases over [fadeMs] when the palette changes. Reduced motion
 * keeps it still.
 */
export function PaletteMesh({
  palette,
  blur = 64,
  driftMs,
  fadeMs = 0,
  className = '',
}: {
  /** Four colours, in the blobs' order. */
  palette: Rgb[];
  blur?: number;
  driftMs?: number;
  fadeMs?: number;
  className?: string;
}) {
  const blobs = useRef<(HTMLSpanElement | null)[]>([]);
  const still = useReducedMotion();
  const drifting = driftMs !== undefined && !still;
  const transition = fadeMs && !still ? `background-color ${fadeMs}ms cubic-bezier(0.4, 0, 0.2, 1)` : undefined;

  useEffect(() => {
    const place = (phase: number) =>
      blobs.current.forEach((blob, i) => {
        if (!blob) return;
        const [x, y] = centre(i, phase);
        blob.style.left = `${x * 100}%`;
        blob.style.top = `${y * 100}%`;
      });
    place(0);
    if (!drifting) return;
    let frame = 0;
    let drawn = -Infinity;
    const start = performance.now();
    const tick = (now: number) => {
      if (now - drawn >= FRAME_MS - 1) {
        drawn = now;
        place(((now - start) / (driftMs * 4)) * Math.PI * 2);
      }
      frame = requestAnimationFrame(tick);
    };
    frame = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(frame);
  }, [drifting, driftMs]);

  return (
    <div
      aria-hidden="true"
      className={`pointer-events-none absolute inset-0 overflow-hidden [container-type:size] ${className}`}
      style={{ backgroundColor: meshDimmed(palette[0] ?? FALLBACK_MESH[0]!), transition }}
    >
      <div className="absolute inset-0 scale-[1.3]" style={{ filter: `blur(${blur}px)` }}>
        {[0, 1, 2, 3].map((i) => (
          <span
            key={i}
            ref={(el) => {
              blobs.current[i] = el;
            }}
            className="absolute size-[124cqmax] -translate-1/2"
            style={{ backgroundColor: meshTuned(palette[i] ?? FALLBACK_MESH[i]!), transition, maskImage: FALLOFF }}
          />
        ))}
      </div>
      <div className="absolute inset-0 bg-linear-to-b from-black/10 to-black/38" />
    </div>
  );
}

/** The artwork's four mesh colours (rememberArtworkColors), the fallback until they're read. */
export function useArtworkMesh(url: string | null | undefined): Rgb[] {
  const [palette, setPalette] = useState(FALLBACK_MESH);
  useEffect(() => {
    if (!url) return setPalette(FALLBACK_MESH);
    let live = true;
    meshPalette(url).then(
      (cells) => live && setPalette(meshSlots(cells)),
      () => undefined,
    );
    return () => {
      live = false;
    };
  }, [url]);
  return palette;
}
