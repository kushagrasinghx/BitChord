import { useEffect, useRef, useState } from 'react';
import type { AppleMotion } from '@/data/apple/motionArt';

const nativeHls = () => document.createElement('video').canPlayType('application/vnd.apple.mpegurl') !== '';

/**
 * Apple Music's motion artwork over a static cover (CanvasArtworkPlayer on
 * the phone): a muted, looping HLS clip straight from Apple's CDN, which
 * allows any origin, played by hls.js (loaded the first time a clip is
 * shown), or by the browser itself where there's no MediaSource (iOS). It fades in once it's actually playing, so
 * the cover under it is all that shows until then, or if it never plays; the
 * second rendition is tried once when the first won't. [playing] false holds
 * it on its frame, as the music is paused.
 */
export function MotionArtwork({ motion, className, playing = true }: { motion: AppleMotion; className?: string; playing?: boolean }) {
  const video = useRef<HTMLVideoElement>(null);
  const [url, setUrl] = useState(motion.url);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    setUrl(motion.url);
    setReady(false);
  }, [motion.url]);

  useEffect(() => {
    const el = video.current;
    if (!el) return;
    const retry = () => {
      if (motion.fallbackUrl && url !== motion.fallbackUrl) setUrl(motion.fallbackUrl);
    };
    let destroyed = false;
    let hls: import('hls.js').default | null = null;
    // hls.js wherever MediaSource works; the browser's own HLS only where it doesn't (iOS Safari).
    // Desktop Chromium now claims native HLS too, and its player failed on these clips.
    void import('hls.js').then(({ default: Hls }) => {
      if (destroyed) return;
      if (!Hls.isSupported()) {
        if (!nativeHls()) return;
        el.src = url;
        el.addEventListener('error', retry, { once: true });
        return;
      }
      // A sleeve is small: no need for the 1080p rendition, nor a long buffer for a few-second loop.
      hls = new Hls({ capLevelToPlayerSize: true, maxBufferLength: 10, enableWorker: true });
      hls.on(Hls.Events.ERROR, (_, data) => {
        if (data.fatal) retry();
      });
      hls.loadSource(url);
      hls.attachMedia(el);
    });
    return () => {
      destroyed = true;
      hls?.destroy();
      el.removeEventListener('error', retry);
      if (el.getAttribute('src')) {
        el.removeAttribute('src');
        el.load();
      }
    };
  }, [url, motion.fallbackUrl]);

  useEffect(() => {
    const el = video.current;
    if (!el) return;
    if (playing) void el.play().catch(() => undefined);
    else el.pause();
  }, [playing, url]);

  return (
    <video
      ref={video}
      className={className}
      data-ready={ready || undefined}
      muted
      loop
      playsInline
      autoPlay={playing}
      disablePictureInPicture
      aria-hidden="true"
      onPlaying={() => setReady(true)}
    />
  );
}
