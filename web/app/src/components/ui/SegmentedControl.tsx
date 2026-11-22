import { useLayoutEffect, useRef, useState } from 'react';

export interface Segment<T> {
  value: T;
  label: string;
  /** Shown but not offered: dimmed, and clicks do nothing. */
  disabled?: boolean;
}

// PillSegmentedControl.kt's spring (damping .8, StiffnessMediumLow) as a CSS curve.
const SLIDE =
  'duration-[388ms] ease-[linear(0,0.114,0.337,0.561,0.74,0.866,0.944,0.988,1.008,1.015,1.015,1.012,1.008,1.005,1)]';

/**
 * Pick one of a few: the phone's segmented control (sharedUi/.../ui/
 * components/PillSegmentedControl.kt). A pill-shaped track in Apple's
 * tertiary fill, and the selection a pill that slides between the labels.
 *
 * Segments share the width evenly while their labels fit; a longer label
 * takes what it needs. Past the track's width they keep their own widths
 * and scroll, faded out at whichever edge has more beyond it, with the
 * selection kept in view. Values are compared with ===.
 *
 *   <SegmentedControl label="Theme" options={THEME_MODES} value={mode} onChange={setMode} />
 */
export function SegmentedControl<T>({
  label,
  options,
  value,
  onChange,
  className = '',
}: {
  /** Names the group for assistive tech. */
  label: string;
  options: readonly Segment<T>[];
  value: T;
  onChange: (value: T) => void;
  className?: string;
}) {
  const scroller = useRef<HTMLDivElement>(null);
  const placed = useRef(false);
  const [pill, setPill] = useState<{ x: number; width: number; animate: boolean } | null>(null);
  const [fade, setFade] = useState({ start: 0, end: 0 });
  const selected = options.findIndex((o) => o.value === value);
  // What the layout depends on, as a value: callers pass a fresh array each render.
  const labels = options.map((o) => o.label).join('\n');

  // The pill follows the selected segment, there at once the first time and
  // slid after; the scroller brings the selection to the middle.
  useLayoutEffect(() => {
    const track = scroller.current;
    if (!track) return;
    const measure = () => {
      const button = track.querySelectorAll<HTMLElement>('[role=radio]')[selected];
      if (!button) return setPill(null);
      setPill({ x: button.offsetLeft, width: button.offsetWidth, animate: placed.current });
      const centred = button.offsetLeft + button.offsetWidth / 2 - track.clientWidth / 2;
      track.scrollTo({ left: centred, behavior: placed.current ? 'smooth' : 'instant' });
      placed.current = true;
      onScroll();
    };
    measure();
    const observer = new ResizeObserver(measure);
    observer.observe(track);
    return () => observer.disconnect();
  }, [selected, labels]);

  // 28px fades, deepening as each edge's overflow grows to that.
  const onScroll = () => {
    const track = scroller.current;
    if (!track) return;
    const end = track.scrollWidth - track.clientWidth - track.scrollLeft;
    setFade({ start: Math.min(track.scrollLeft, 28), end: Math.min(Math.max(end, 0), 28) });
  };

  return (
    <div className={`overflow-hidden rounded-pill bg-segmented ${className}`}>
      <div
        ref={scroller}
        role="radiogroup"
        aria-label={label}
        onScroll={onScroll}
        className="relative flex overflow-x-auto p-1 [scrollbar-width:none] [&::-webkit-scrollbar]:hidden"
        style={
          fade.start || fade.end
            ? { maskImage: `linear-gradient(to right, transparent, #000 ${fade.start}px, #000 calc(100% - ${fade.end}px), transparent)` }
            : undefined
        }
      >
        {pill && (
          <span
            aria-hidden
            className={`absolute top-1 bottom-1 left-0 rounded-pill bg-segmented-selected ${pill.animate ? `transition-[translate,width] ${SLIDE}` : ''}`}
            style={{ translate: `${pill.x}px 0`, width: pill.width }}
          />
        )}
        {options.map((option, index) => (
          <button
            key={index}
            type="button"
            role="radio"
            aria-checked={index === selected}
            disabled={option.disabled}
            onClick={() => index !== selected && onChange(option.value)}
            className="relative min-w-max flex-1 basis-0 px-[18px] py-[9px] type-label-medium whitespace-nowrap text-primary disabled:opacity-30"
          >
            {option.label}
          </button>
        ))}
      </div>
    </div>
  );
}
