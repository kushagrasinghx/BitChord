import type { HTMLAttributes, ReactNode, Ref } from 'react';
import { SLIDER_SPRING } from './motion';

/**
 * The phone's seek and volume bar (sharedUi/.../ui/player/ThinSlider.kt): a
 * hairline capsule with no knob that swells while it is held and settles back
 * with a little bounce when let go. Played white at 92%, unplayed at 26%.
 *
 * Only the drawing and the hit area: the caller owns the value and the
 * pointer (useScrubber writes `--progress` straight onto `fill` each frame).
 * The hit area is the active height plus 22px, as the phone's touch target.
 * On the player's dark glass it is white; elsewhere (the mini player's capsule,
 * which follows the theme) the caller sets `--slider-track` and `--slider-fill`.
 */
export function ThinSlider({
  fill,
  progress,
  active,
  idle = 7,
  pressed = 12,
  under,
  className = '',
  ...hit
}: {
  /** The played part; its `--progress` (0–1) sets how much shows. */
  fill?: Ref<HTMLDivElement>;
  /** The value, where the caller renders it rather than writing it. */
  progress?: number;
  /** Held: drawn at `pressed` height. */
  active: boolean;
  idle?: number;
  pressed?: number;
  /** Drawn on the track under the fill (the Automix window). */
  under?: ReactNode;
} & HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      className={`flex cursor-pointer touch-none items-center ${className}`}
      style={{ height: pressed + 22 }}
      {...hit}
    >
      <div
        className={`relative w-full overflow-hidden rounded-pill bg-[var(--slider-track,rgb(255_255_255/0.26))] transition-[height] motion-reduce:transition-none ${SLIDER_SPRING}`}
        style={{ height: active ? pressed : idle }}
        aria-hidden="true"
      >
        {under}
        <div
          ref={fill}
          className="relative size-full origin-left scale-x-(--progress) bg-[var(--slider-fill,rgb(255_255_255/0.92))] [--progress:0] rtl:origin-right"
          style={progress === undefined ? undefined : ({ '--progress': progress } as React.CSSProperties)}
        />
      </div>
    </div>
  );
}
