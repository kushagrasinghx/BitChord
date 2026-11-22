import { Music2 } from 'lucide-react';
import { useLayoutEffect, useRef, type Ref } from 'react';
import { MotionArtwork } from '@/components/music/MotionArtwork';
import { VideoSurface } from '@/components/music/VideoSurface';
import { useTrackMotion } from '@/data/apple/motionArt';
import { useReducedMotion } from '@/data/spotify/canvas';
import type { NowPlaying } from '@/state/player';
import { BestPartPill } from '../BestPartPill';
import { NowPlayingCover } from '../NowPlayingCover';
import { FAST_OUT_SLOW_IN } from './motion';

/** NowPlayingScreen.kt THUMB_SIZE: the sleeve as the header's thumbnail while a pane is up on a narrow window. */
export const THUMB = 54;
/** QUEUE_TRAVEL_MS: how long the sleeve takes between the full player and that header. */
const COLLAPSE_MS = 420;

const layer = 'absolute inset-0 size-full object-cover opacity-0 transition-opacity duration-300 ease-in';

/**
 * The sleeve (LandscapePlayer.kt LandscapeArtwork): the cover with 10px
 * corners and a 14dp shadow, cast only once there is a picture, over a
 * placeholder note; Apple's motion cover over it while it plays; a music
 * video inside it at 16:9 rather than across the window. Best Part and the
 * AutoMix dissolve stay on it, as on the phone.
 *
 * Square, as large as its box allows either way (the box is a size
 * container). It shrinks to 88% while paused and 94% while the seek bar is
 * held, paused winning outright, over 500ms of an ease-out cubic
 * (ARTWORK_PAUSE_SHRINK_SCALE, ARTWORK_DRAG_SHRINK_SCALE, ArtworkScaleEasing).
 *
 * `dock` is the outer layer the sheet moves to fly the sleeve in from the
 * mini player's cover; `thumb` collapses it to the header's 54px, which it
 * travels to and from over 420ms, as it does to and from its docked size.
 */
export function Sleeve({
  nowPlaying,
  album,
  playing,
  video,
  thumb,
  docked = false,
  dock,
  onClick,
}: {
  nowPlaying: NowPlaying;
  /** The album's name, which Apple's motion covers are looked up by. */
  album: string | null;
  playing: boolean;
  video: boolean;
  thumb: boolean;
  /** Shrunk in its column to make room for the controls under it; changing it plays the same move as `thumb`. */
  docked?: boolean;
  dock: Ref<HTMLDivElement>;
  onClick?: () => void;
}) {
  const motion = useTrackMotion(nowPlaying.title, nowPlaying.artist, album);
  const box = useRef<HTMLDivElement>(null);
  const last = useRef<DOMRect | null>(null);
  const still = useReducedMotion();

  // The collapse into the header and back, played from where it was to where it is (FLIP).
  useLayoutEffect(() => {
    const el = box.current;
    if (!el) return;
    const from = last.current;
    const to = el.getBoundingClientRect();
    last.current = to;
    if (!from || still || !to.width) return;
    el.animate(
      [
        {
          transformOrigin: '0 0',
          transform: `translate(${from.left - to.left}px, ${from.top - to.top}px) scale(${from.width / to.width}, ${from.height / to.height})`,
        },
        { transformOrigin: '0 0', transform: 'none' },
      ],
      { duration: COLLAPSE_MS, easing: FAST_OUT_SLOW_IN },
    );
  }, [thumb, docked, still]);

  const size = thumb
    ? { width: video ? (THUMB * 16) / 9 : THUMB, height: THUMB }
    : video
      ? { width: 'min(100cqw, 100cqh * 16 / 9)', aspectRatio: '16 / 9' }
      : { width: 'min(100cqw, 100cqh)', aspectRatio: '1' };

  return (
    <div ref={dock} className="relative flex-none origin-top-left will-change-transform" style={size}>
      <div ref={box} className="size-full">
        <div
          className={[
            'group/sleeve relative size-full transition-[scale] duration-500 ease-[cubic-bezier(0.215,0.61,0.355,1)] motion-reduce:transition-none',
            '[[data-player][data-paused]_&]:scale-88 [[data-player]:not([data-paused]):has([data-scrubbing])_&]:scale-94',
          ].join(' ')}
        >
          <div
            className="relative block size-full overflow-hidden bg-black/18 shadow-none transition-shadow duration-300 [border-radius:var(--dock-radius,10px)] has-data-loaded:shadow-[0_6px_12px_rgb(0_0_0/0.22),0_14px_28px_rgb(0_0_0/0.28)]"
            onClick={onClick}
          >
            <Music2 className="absolute inset-0 m-auto size-[36%] text-white/35" strokeWidth={1.5} />
            <NowPlayingCover url={nowPlaying.artworkUrl} px={600} reveal blur={!thumb} className={`${layer} data-loaded:opacity-100`} />
            {motion && (
              <MotionArtwork
                key={motion.url}
                motion={motion}
                className="absolute inset-0 size-full object-cover opacity-0 transition-opacity duration-600 ease-in data-ready:opacity-100"
                playing={playing}
              />
            )}
            {video && <VideoSurface className="absolute inset-0 bg-black" />}
          </div>
          {!thumb && <BestPartPill />}
        </div>
      </div>
    </div>
  );
}
