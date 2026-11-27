import { useCallback, useState } from 'react';
import { Menu, MenuHeader, MenuItem, MenuSeparator } from '@/components/Menu';
import { qualityLabel, setVideoQuality, useVideo } from '@/state/video';

/**
 * The playing music video's quality, on the video's corner beside its
 * full-screen button (VideoCornerControls): a small glass box, black at 38%
 * as the phone's, with the rendition on screen written
 * in it — 360p, 480p, HD, FullHD, 2K, 4K — which opens the list of the
 * heights this video offers.
 *
 * The label is see-through: it blends additively with the box's own blurred
 * sample of the picture behind it (mix-blend-mode: plus-lighter), so the video
 * shows through the letters and they take its colour, and it morphs from one
 * reading to the next rather than cutting.
 */
export function VideoQuality({ className = '' }: { className?: string }) {
  const heights = useVideo((s) => s.heights);
  const quality = useVideo((s) => s.quality);
  const activeHeight = useVideo((s) => s.activeHeight);
  const [anchor, setAnchor] = useState<HTMLElement | null>(null);
  const close = useCallback(() => setAnchor(null), []);

  // Nothing to switch between until the renditions are known.
  if (heights.length < 2) return null;
  // What the pick comes to on this video: one that doesn't offer the picked
  // height (4K held, then a song shot in 1080p) plays the best it has, and the
  // box says so. The pick itself is kept for the next video that can meet it.
  const selected = quality === 'auto' || !heights.includes(quality) ? activeHeight : quality;
  const label = selected ? qualityLabel(selected) : 'Auto';

  return (
    <>
      <button
        className={`grid h-[30px] min-w-11 place-items-center overflow-hidden rounded-[8px] bg-black/38 px-2 [backdrop-filter:saturate(1.8)_blur(18px)] [interpolate-size:allow-keywords] [transition:width_0.3s_ease,background-color_0.1s_ease-in,opacity_0.3s_ease] hover:bg-black/50 ${className}`}
        aria-haspopup="menu"
        aria-label={`Video quality: ${quality === 'auto' ? `Auto (${label})` : label}`}
        onClick={(event) => {
          // Not a click on the picture under it, which plays and pauses in the video's full screen.
          event.stopPropagation();
          setAnchor(event.currentTarget);
        }}
      >
        {/* Keyed on the reading, so each new one melts in over the last. */}
        <span
          key={label}
          className="animate-morph type-[700_11px/14px] tracking-[0.04em] text-white/70 tabular-nums mix-blend-plus-lighter motion-reduce:animate-none"
        >
          {label}
        </span>
      </button>
      {anchor && (
        <Menu anchor={anchor} placement="below-end" onClose={close} label="Video quality">
          <MenuItem
            checked={quality === 'auto'}
            trailing={quality === 'auto' && selected ? qualityLabel(selected) : undefined}
            onSelect={() => (close(), setVideoQuality('auto'))}
          >
            Auto
          </MenuItem>
          <MenuSeparator />
          <MenuHeader>Quality</MenuHeader>
          {heights.map((height) => (
            <MenuItem
              key={height}
              checked={quality !== 'auto' && selected === height}
              trailing={`${height}p`}
              onSelect={() => (close(), setVideoQuality(height))}
            >
              {qualityLabel(height)}
            </MenuItem>
          ))}
        </Menu>
      )}
    </>
  );
}
