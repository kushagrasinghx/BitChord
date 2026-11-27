import { useEffect, useState, type CSSProperties } from 'react';
import { decodedArtwork, sizedArtwork, thumbnailFallback } from '@/lib/artwork';

interface ArtworkProps {
  src: string | null;
  /** The CSS pixel width it renders at, so the CDN is asked for that size (prompt §3). */
  displayWidth: number;
  /** width / height. */
  aspect?: number;
  radius?: CSSProperties['borderRadius'];
  className?: string;
  /** Eager for above-the-fold heroes; lazy everywhere else. */
  eager?: boolean;
}

const frame = 'relative isolate w-full overflow-hidden bg-joe artwork-stroke';
const layer = 'absolute inset-0 size-full object-cover';

/** How a src is being loaded: the 429 retry's attempt, and the thumbnail it fell back to. */
interface Loading {
  src: string | null;
  attempt: number;
  fallback: string | null;
}

/**
 * Fixed-aspect artwork: a neutral placeholder until the image decodes, then a
 * short fade (prompt §3). When the src changes, the picture already there
 * stays until the new one has decoded and fades in over it, rather than the
 * frame flashing empty. The 0.5px inner stroke is Apple's "has-borders"
 * treatment (report §1).
 */
export function Artwork({ src, displayWidth, aspect = 1, radius, className, eager }: ArtworkProps) {
  // googleusercontent answers bursts with 429 (lib/artwork.ts). A failed load
  // is retried once after a pause, then falls back to YouTube's own URL.
  const [loading, setLoading] = useState<Loading>({ src, attempt: 0, fallback: null });
  const state = loading.src === src ? loading : { src, attempt: 0, fallback: null };
  const { attempt, fallback } = state;
  /** The last URL that finished decoding: what stays on screen while another loads. */
  const [shown, setShown] = useState<string | null>(null);
  const sized = src ? sizedArtwork(src, displayWidth, aspect) : null;
  const url = fallback ?? (attempt >= 2 || sized === src ? src : sized);
  return (
    <div
      className={className ? `${frame} ${className}` : frame}
      style={{ aspectRatio: String(aspect), borderRadius: radius }}
    >
      {url && shown && shown !== url && <img className={layer} src={shown} alt="" referrerPolicy="no-referrer" draggable={false} />}
      {url && (
        <img
          key={`${url}#${attempt}`}
          className={`${layer} opacity-0 transition-opacity duration-250 ease-in data-loaded:opacity-100`}
          data-loaded={shown === url || undefined}
          src={url}
          alt=""
          // googleusercontent throttles hotlinked images (429) by Referer;
          // without one every artwork on a page loads (measured on Artist).
          referrerPolicy="no-referrer"
          loading={eager ? 'eager' : 'lazy'}
          decoding="async"
          draggable={false}
          onLoad={(e) => {
            const img = e.currentTarget;
            const next = thumbnailFallback(url, img, false);
            if (next) return setLoading({ ...state, fallback: next });
            void img.decode().catch(() => undefined).then(() => setShown(url));
          }}
          onError={(e) => {
            const next = thumbnailFallback(url, e.currentTarget, true);
            if (next) setLoading({ ...state, fallback: next });
            else if (attempt === 0) setTimeout(() => setLoading({ ...state, attempt: 1 }), 1500);
            else if (attempt === 1 && url !== src) setLoading({ ...state, attempt: 2 });
          }}
        />
      )}
    </div>
  );
}

/**
 * The artwork to draw for [url], held at the last one that decoded until the
 * next has (falling back from a thumbnail rendition the video lacks). For
 * surfaces that swap pictures in place, like the now-playing cover.
 */
export function useDecodedArtwork(url: string | null): string | null {
  const [shown, setShown] = useState<string | null>(null);
  useEffect(() => {
    if (!url) return;
    let live = true;
    void decodedArtwork(url).then((ready) => {
      if (live) setShown(ready);
    });
    return () => {
      live = false;
    };
  }, [url]);
  return url ? shown : null;
}
