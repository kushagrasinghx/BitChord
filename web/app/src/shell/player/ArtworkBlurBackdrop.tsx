import { useEffect, useRef, useState } from 'react';
import { artworkAtPixels } from '@/lib/artwork';
import { boxBlur } from './boxBlur';

/** ArtworkMeshBackdrop.kt FULL_BLUR_SOURCE_PX: small is the performance feature; a screen can't recover detail lost here. */
const SOURCE_PX = 128;
/** The crossfade to a new cover (FullBlurTrackFade: tween(320, FastOutSlowInEasing)). */
const FADE_MS = 320;

/** Blurred covers already made, by URL: a skip back costs nothing (fullBlurCache keeps eight). */
const cache = new Map<string, ImageData>();

async function blurred(url: string): Promise<ImageData> {
  const known = cache.get(url);
  if (known) return known;
  const image = new Image();
  // YouTube's image hosts allow any origin, so the canvas stays readable.
  image.crossOrigin = 'anonymous';
  image.referrerPolicy = 'no-referrer';
  image.src = artworkAtPixels(url, SOURCE_PX);
  await image.decode();
  const canvas = document.createElement('canvas');
  canvas.width = canvas.height = SOURCE_PX;
  const context = canvas.getContext('2d', { willReadFrequently: true })!;
  // Cropped to a square from the middle, as ContentScale.Crop takes it.
  const side = Math.min(image.naturalWidth, image.naturalHeight);
  context.drawImage(image, (image.naturalWidth - side) / 2, (image.naturalHeight - side) / 2, side, side, 0, 0, SOURCE_PX, SOURCE_PX);
  const data = context.getImageData(0, 0, SOURCE_PX, SOURCE_PX);
  boxBlur(data.data, SOURCE_PX, SOURCE_PX);
  cache.set(url, data);
  if (cache.size > 8) cache.delete(cache.keys().next().value!);
  return data;
}

/** One cover's blur: a 128px canvas the browser stretches over the window, fading in once drawn. */
function Layer({ url, onShown }: { url: string; onShown: () => void }) {
  const canvas = useRef<HTMLCanvasElement>(null);
  const [ready, setReady] = useState(false);
  useEffect(() => {
    let live = true;
    blurred(url)
      .then((data) => {
        if (!live || !canvas.current) return;
        canvas.current.getContext('2d')!.putImageData(data, 0, 0);
        setReady(true);
      })
      .catch(() => undefined);
    return () => {
      live = false;
    };
  }, [url]);
  return (
    <canvas
      ref={canvas}
      width={SOURCE_PX}
      height={SOURCE_PX}
      className="absolute inset-0 size-full object-cover opacity-0 transition-opacity ease-[cubic-bezier(0.4,0,0.2,1)] data-ready:opacity-100 motion-reduce:transition-none"
      style={{ transitionDuration: `${FADE_MS}ms` }}
      data-ready={ready || undefined}
      onTransitionEnd={onShown}
    />
  );
}

/**
 * The player's backdrop on a wide window (ArtworkMeshBackdrop.kt
 * FullArtworkBlurBackdrop): one copy of the cover, cropped across the whole
 * window and blurred, under a shade that deepens toward the controls at the
 * foot (black at 34%, 48% at 55% down, 64% at the bottom). Still: nothing
 * moves, so moving between the player, the lyrics and the queue never
 * redraws it. The blur is baked once into a 128px copy (three box passes,
 * as the phone's) rather than left to a live CSS filter over the screen.
 * #121212 until the first cover is read; a new cover crossfades in over
 * 320ms.
 */
export function ArtworkBlurBackdrop({ artworkUrl }: { artworkUrl: string | null }) {
  const [layers, setLayers] = useState<string[]>(artworkUrl ? [artworkUrl] : []);
  useEffect(() => {
    if (artworkUrl) setLayers((now) => (now.at(-1) === artworkUrl ? now : [...now.slice(-1), artworkUrl]));
  }, [artworkUrl]);
  return (
    <div className="absolute inset-0 -z-1 overflow-hidden bg-[#121212]" aria-hidden="true">
      {layers.map((url) => (
        <Layer key={url} url={url} onShown={() => setLayers((now) => (now.at(-1) === url ? [url] : now))} />
      ))}
      <div className="absolute inset-0 bg-[linear-gradient(rgb(0_0_0/0.34),rgb(0_0_0/0.48)_55%,rgb(0_0_0/0.64))]" />
    </div>
  );
}
