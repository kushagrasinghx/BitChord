import { useLayoutEffect, useRef } from 'react';
import { claimVideoSurface, type VideoFit } from '@/state/video';

/**
 * Where the playing music video shows (state/video.ts). While mounted it
 * holds the one <video> element, unless a surface mounted after it took it;
 * it also tells the player the picture is wanted: with no surface mounted, a
 * music video's picture isn't streamed at all.
 */
export function VideoSurface({ fit = 'cover', className }: { fit?: VideoFit; className?: string }) {
  const host = useRef<HTMLDivElement>(null);
  useLayoutEffect(() => claimVideoSurface(host.current!, fit), [fit]);
  return <div ref={host} className={className} aria-hidden="true" />;
}
