import { autoMixArtworkWindow, autoMixMaskTuning, autoMixRevealProgress } from '@bitchord/shared-kotlin';
import { create } from 'zustand';
import { usePlayer } from '@/state/player';

/**
 * The AutoMix cover dissolve (the phone's AutoMixVisualFeed and AutoMixCover):
 * across a blend's handoff, the outgoing cover stays and the incoming one
 * comes through it in an organic noise mask, timed off the real overlap.
 *
 * The window and its easing are AutoMixVisualState.kt's own (shared Kotlin):
 * a short lag after the swap, never past the overlap's end, and no dissolve
 * at all when too little of the overlap is left. Times here are
 * performance.now() seconds, which tracks the AudioContext clock the blend is
 * scheduled on: the scheduler's snapshot says how far off its start is.
 */

export interface MixReveal {
  generation: number;
  /** The outgoing and incoming tracks' own artwork URLs. */
  from: string;
  to: string;
  startSec: number;
  endSec: number;
}

export const useMixReveal = create<{ reveal: MixReveal | null }>()(() => ({ reveal: null }));

const nowSec = () => performance.now() / 1000;

/** How far through its window [reveal] is at [atSec], eased as the phone eases it. */
export function revealAt(reveal: MixReveal, atSec: number): number {
  const span = reveal.endSec - reveal.startSec;
  const linear = span > 0 ? Math.min(1, Math.max(0, (atSec - reveal.startSec) / span)) : 1;
  return autoMixRevealProgress(linear);
}

let generation = 0;
/** When the coming blend was scheduled (the phone's arming), for its prepare lead. */
let armedAt: number | null = null;
/** Where the scheduled blend's overlap ends. */
let overlapEnd: number | null = null;

usePlayer.subscribe((state, previous) => {
  const snap = state.automixState;
  const plan = snap?.state === 'scheduled' && snap.startsInSec !== null ? snap.plan : null;
  if (plan) {
    armedAt ??= nowSec();
    if (snap !== previous.automixState) overlapEnd = nowSec() + snap!.startsInSec! + plan.durationSec;
  } else {
    armedAt = null;
    overlapEnd = null;
  }
  const { reveal } = useMixReveal.getState();
  // Cancelled mid-dissolve (a pause, a seek, another track): the live cover, at once.
  if (reveal && !plan && nowSec() < reveal.endSec) useMixReveal.setState({ reveal: null });

  const from = previous.nowPlaying;
  const to = state.nowPlaying;
  if (!from || !to || from.videoId === to.videoId) return;
  // The handoff: the track the blend brings in became the one playing.
  const swap = nowSec();
  const window =
    plan && overlapEnd !== null && snap!.nextKey === to.videoId && from.artworkUrl && to.artworkUrl && from.artworkUrl !== to.artworkUrl
      ? autoMixArtworkWindow(swap, overlapEnd, swap - (armedAt ?? swap))
      : null;
  useMixReveal.setState({
    reveal: window
      ? { generation: ++generation, from: from.artworkUrl!, to: to.artworkUrl!, startSec: window.startSeconds, endSec: window.endSeconds }
      : null,
  });
});

// ---- The mask: AndroidAutoMixReveal.kt's ORGANIC_MASK_FIELD shader, on a 2D canvas ----------
//
// A canvas rather than WebGL: covers come from other origins without CORS, which
// WebGL can't sample but a 2D canvas can draw (it is only ever drawn, never read).
// The noise doesn't move during a dissolve, only its threshold does, so the field
// is worked out once per dissolve at a small size and stretched: the shader's
// `xy / bounds` stretches it the same way over a tall or wide cover.

const tuning = autoMixMaskTuning();
const FIELD_PX = 96;

const fract = (x: number) => x - Math.floor(x);
const mod = (x: number, m: number) => x - m * Math.floor(x / m);
const smoothstep = (edge0: number, edge1: number, x: number) => {
  const t = Math.min(1, Math.max(0, (x - edge0) / (edge1 - edge0)));
  return t * t * (3 - 2 * t);
};

/** The shader's hashCell: bounded integer mixing, so adjacent cells agree on their corners. */
function hashCell(x: number, y: number): number {
  let n = mod(x * 127 + y * 197, 251);
  n = mod(n * (n + 73) + x * 97 + y * 53, 251);
  n = mod(n * (n + 37) + x * 31 + y * 109, 251);
  return n / 251;
}

function valueNoise(px: number, py: number): number {
  const cx = Math.floor(px);
  const cy = Math.floor(py);
  let fx = fract(px);
  let fy = fract(py);
  fx = fx * fx * (3 - 2 * fx);
  fy = fy * fy * (3 - 2 * fy);
  const top = hashCell(cx, cy) + (hashCell(cx + 1, cy) - hashCell(cx, cy)) * fx;
  const bottom = hashCell(cx, cy + 1) + (hashCell(cx + 1, cy + 1) - hashCell(cx, cy + 1)) * fx;
  return top + (bottom - top) * fy;
}

/** The shader's octaves of value noise, normalised between NOISE_LOW and NOISE_HIGH, at [size]×[size]. */
export function noiseField(size: number, seedX: number, seedY: number): Float32Array {
  const field = new Float32Array(size * size);
  for (let y = 0; y < size; y++) {
    for (let x = 0; x < size; x++) {
      let px = ((x + 0.5) / size) * tuning.noiseScale + seedX;
      let py = ((y + 0.5) / size) * tuning.noiseScale + seedY;
      let noise = 0;
      let weight = 0.5;
      let total = 0;
      for (let i = 0; i < tuning.noiseOctaves; i++) {
        noise += valueNoise(px, py) * weight;
        total += weight;
        px = px * 2 + 3.9;
        py = py * 2 + 5.1;
        weight *= 0.5;
      }
      field[y * size + x] = Math.min(1, Math.max(0, (noise / total - tuning.noiseLow) / (tuning.noiseHigh - tuning.noiseLow)));
    }
  }
  return field;
}

/** The shader's revealCoverage for a field value: the threshold sweeps from above 1 to below 0. */
export function coverage(field: number, progress: number): number {
  const softness = tuning.noiseSoftness;
  const threshold = 1 + softness + (-softness - (1 + softness)) * progress;
  return smoothstep(threshold - softness, threshold + softness, field);
}

/** One dissolve's mask and its painting of the incoming cover, frame by frame. */
export class RevealPainter {
  private readonly field: Float32Array;
  private readonly mask = document.createElement('canvas');
  private readonly pixels: ImageData;

  constructor() {
    // A fresh pattern per dissolve, as the phone seeds one per generation.
    this.field = noiseField(FIELD_PX, Math.random() * tuning.noiseSeedSpan, Math.random() * tuning.noiseSeedSpan);
    this.mask.width = this.mask.height = FIELD_PX;
    this.pixels = new ImageData(FIELD_PX, FIELD_PX);
  }

  paint(canvas: HTMLCanvasElement, image: HTMLImageElement, eased: number, blur: boolean): void {
    const ctx = canvas.getContext('2d');
    const maskCtx = this.mask.getContext('2d');
    if (!ctx || !maskCtx) return;
    const width = Math.round(canvas.clientWidth * devicePixelRatio);
    const height = Math.round(canvas.clientHeight * devicePixelRatio);
    if (!width || !height) return;
    if (canvas.width !== width || canvas.height !== height) {
      canvas.width = width;
      canvas.height = height;
    }
    const data = this.pixels.data;
    for (let i = 0; i < this.field.length; i++) data[i * 4 + 3] = coverage(this.field[i]!, eased) * 255;
    maskCtx.putImageData(this.pixels, 0, 0);

    ctx.globalCompositeOperation = 'copy';
    ctx.imageSmoothingEnabled = true;
    ctx.drawImage(this.mask, 0, 0, width, height);
    ctx.globalCompositeOperation = 'source-in';
    // object-fit: cover, as the <img> it stands in for.
    const scale = Math.max(width / image.naturalWidth, height / image.naturalHeight);
    const w = image.naturalWidth * scale;
    const h = image.naturalHeight * scale;
    ctx.drawImage(image, (width - w) / 2, (height - h) / 2, w, h);
    ctx.globalCompositeOperation = 'source-over';
    canvas.style.filter = blur ? blurAt(eased) : '';
  }
}

/** AndroidAutoMixReveal.kt's BlurSteps: soft as it starts to show, sharp from BLUR_END_PROGRESS on. */
export function blurAt(eased: number): string {
  if (eased <= 0 || eased >= tuning.blurEndProgress) return '';
  const remaining = 1 - eased / tuning.blurEndProgress;
  return `blur(${(tuning.blurStartDp * remaining * remaining).toFixed(2)}px)`;
}
