import { useHasVideo } from '@/state/video';
import { usePlayer } from '@/state/player';
import { MixWindowHighlight } from '../NerdStats';
import { useScrubber } from '../useScrubber';
import { VideoQuality } from '../VideoQuality';
import { ThinSlider } from './ThinSlider';

/**
 * The seek bar (PlayerControls.kt PlayerScrubber): the thin slider, and under
 * it the elapsed and remaining times at 12px, white at 55%, pulled up 9px into
 * the slider's touch target (SCRUBBER_LABEL_LIFT). Between them, pinned to the
 * middle so a minute rolling over never moves it, the quality label: a music
 * video's rendition here, as the phone puts Lossless there. "Mixing" takes its
 * place while an Automix blend holds the bar.
 *
 * `data-scrubbing` while held, for the sleeve's scrub pose.
 */
export function Scrubber() {
  const { bar, elapsed, remaining, mixLabel, dragging, handlers } = useScrubber();
  const videoId = usePlayer((s) => s.nowPlaying?.videoId);
  const video = useHasVideo(videoId);
  return (
    <div data-scrubbing={dragging || undefined}>
      <ThinSlider fill={bar} active={dragging} under={<MixWindowHighlight />} {...handlers} />
      <div className="relative -mt-[9px] flex h-[18px] items-center justify-between type-label-medium tabular-nums text-white/55">
        <span ref={elapsed} />
        <span className="absolute inset-0 flex items-center justify-center">
          {video && <VideoQuality />}
          <span ref={mixLabel} className="pointer-events-none absolute text-white/85 opacity-0" aria-hidden="true">
            Mixing
          </span>
        </span>
        <span ref={remaining} />
      </div>
    </div>
  );
}
