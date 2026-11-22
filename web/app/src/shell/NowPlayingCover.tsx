import { useEffect, useRef, useState } from 'react';
import { useDecodedArtwork } from '@/components/music/Artwork';
import { useReducedMotion } from '@/data/spotify/canvas';
import { decodedArtwork, sizedArtwork } from '@/lib/artwork';
import { RevealPainter, revealAt, useMixReveal, type MixReveal } from '@/playback/mixVisual';

interface CoverProps {
  /** The track's own artwork URL. */
  url: string | null;
  /** The CSS pixel width it shows at, for the CDN size. */
  px: number;
  /** The layer's classes: each picture is one absolutely placed layer. Fades in once `data-loaded` is set. */
  className: string;
  /** The AutoMix dissolve across a blend (off for copies like the glow). */
  reveal?: boolean;
  /** The dissolve's blur, which a cover too small to see it does without (AutoMixCover's `blur`). */
  blur?: boolean;
}

/**
 * The now-playing cover. A new track's picture replaces the old one only once
 * it has decoded, so a skip never flashes an empty frame; across an AutoMix
 * blend the incoming cover comes through the outgoing one in the phone's
 * noise mask, timed off the blend (playback/mixVisual.ts).
 */
export function NowPlayingCover({ url, px, className, reveal = false, blur = false }: CoverProps) {
  const sized = url ? sizedArtwork(url, px) : null;
  const shown = useDecodedArtwork(sized);
  const mix = useMixReveal((s) => s.reveal);
  const [done, setDone] = useState<number | null>(null);
  // A cover shown after a dissolve ended (the full-screen player opened later) skips straight to the result.
  const dissolving = reveal && mix && mix.to === url && done !== mix.generation && performance.now() / 1000 < mix.endSec ? mix : null;
  if (dissolving) {
    return <Dissolve key={dissolving.generation} mix={dissolving} px={px} className={className} blur={blur} onDone={setDone} />;
  }
  // Not keyed by the URL: a new src on the same element swaps in place, already decoded.
  // The first picture fades in, as the cover always has; after a dissolve it is already there.
  return shown ? (
    <img
      className={className}
      data-loaded={done !== null ? '' : undefined}
      src={shown}
      alt=""
      referrerPolicy="no-referrer"
      draggable={false}
      onLoad={(e) => e.currentTarget.setAttribute('data-loaded', '')}
    />
  ) : null;
}

function Dissolve({
  mix,
  px,
  className,
  blur,
  onDone,
}: {
  mix: MixReveal;
  px: number;
  className: string;
  blur: boolean;
  onDone: (generation: number) => void;
}) {
  const canvas = useRef<HTMLCanvasElement>(null);
  const plain = useRef<HTMLImageElement>(null);
  // Reduced motion: a crossfade over the same window instead of the mask (the phone's plain alpha).
  const still = useReducedMotion();
  const from = sizedArtwork(mix.from, px);
  const to = sizedArtwork(mix.to, px);

  useEffect(() => {
    let frame = 0;
    let live = true;
    const painter = still ? null : new RevealPainter();
    void decodedArtwork(to).then((ready) => {
      if (!live) return;
      const image = new Image();
      image.referrerPolicy = 'no-referrer';
      if (ready) image.src = ready;
      const draw = () => {
        if (!live) return;
        const eased = revealAt(mix, performance.now() / 1000);
        if (painter && canvas.current && image.complete && image.naturalWidth) painter.paint(canvas.current, image, eased, blur);
        if (plain.current) plain.current.style.opacity = String(eased);
        if (eased >= 1 || !ready) onDone(mix.generation);
        else frame = requestAnimationFrame(draw);
      };
      draw();
    });
    return () => {
      live = false;
      cancelAnimationFrame(frame);
    };
  }, [mix, to, still, blur, onDone]);

  return (
    <>
      <img className={className} data-loaded="" src={from} alt="" referrerPolicy="no-referrer" draggable={false} />
      {still ? (
        <img ref={plain} className={className} data-loaded="" src={to} alt="" referrerPolicy="no-referrer" draggable={false} style={{ opacity: 0, transition: 'none' }} />
      ) : (
        <canvas ref={canvas} className={className} data-loaded="" style={{ transition: 'none', background: 'none' }} aria-hidden="true" />
      )}
    </>
  );
}
