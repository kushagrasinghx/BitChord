import { MixWindowHighlight } from '../NerdStats';
import { useScrubber } from '../useScrubber';
import { ThinSlider } from './ThinSlider';

/**
 * The seek bar (PlayerControls.kt PlayerScrubber): the thin slider, and under
 * it the elapsed and remaining times at 12px, white at 55%, pulled up 9px into
 * the slider's touch target (SCRUBBER_LABEL_LIFT). Between them, pinned to the
 * middle so a minute rolling over never moves it, "Mixing" while an Automix
 * blend holds the bar. A music video's quality sits on the video itself
 * (Sleeve), as the phone has it.
 *
 * `data-scrubbing` while held, for the sleeve's scrub pose.
 */
export function Scrubber() {
  const { bar, elapsed, remaining, mixLabel, dragging, handlers } = useScrubber();
  return (
    <div data-scrubbing={dragging || undefined}>
      <ThinSlider fill={bar} active={dragging} under={<MixWindowHighlight />} {...handlers} />
      <div className="relative -mt-[9px] flex h-[18px] items-center justify-between type-label-medium tabular-nums text-white/55">
        <span ref={elapsed} />
        <span className="absolute inset-0 flex items-center justify-center">
          <span ref={mixLabel} className="pointer-events-none absolute text-white/85 opacity-0" aria-hidden="true">
            Mixing
          </span>
        </span>
        <span ref={remaining} />
      </div>
    </div>
  );
}
