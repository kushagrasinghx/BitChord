/*
 * The tab bar's GlassSpring (app/.../ui/components/FloatingBottomBar.kt:
 * damping 0.72, stiffness 320, unit mass), which the selection pill and the
 * glyphs both ride. Under-damped: one soft pass of about 4% beyond the mark,
 * settled within ~360ms.
 */
const DAMPING = 0.72;
const STIFFNESS = 320;
const W0 = Math.sqrt(STIFFNESS);
const WD = W0 * Math.sqrt(1 - DAMPING * DAMPING);

export const SPRING_MS = 360;

/** Progress from 0 to 1 at `t` seconds. */
export function spring(t: number): number {
  return 1 - Math.exp(-DAMPING * W0 * t) * (Math.cos(WD * t) + (DAMPING / Math.sqrt(1 - DAMPING * DAMPING)) * Math.sin(WD * t));
}

/*
 * The pill draws out along its travel and gathers back as it arrives: 16%
 * longer (STRETCH) and half that narrower (SQUASH) while a whole tab's stride
 * or more is still ahead of it, easing to its own shape as the gap closes.
 */
const STRETCH = 0.16;
const SQUASH = 0.5;
const FRAMES = 24;

/** Keyframes for the pill moving down the sidebar from `from` to `to` (px), a tab every `step` px. */
export function pillFrames(from: number, to: number, step: number): Keyframe[] {
  return Array.from({ length: FRAMES + 1 }, (_, i) => {
    const y = i === FRAMES ? to : from + (to - from) * spring(((SPRING_MS / 1000) * i) / FRAMES);
    const lag = Math.min(1, Math.abs(to - y) / step);
    return { transform: `translateY(${y}px) scale(${1 - lag * STRETCH * SQUASH}, ${1 + lag * STRETCH})` };
  });
}
