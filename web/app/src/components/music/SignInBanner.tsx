import { useRef, type ReactNode } from 'react';
import { useRefreshYtAuth, useSetConnected, useYtAuth } from '@/extension/useYtAuth';
import { SIGN_IN_URL } from '@/shell/AccountButton';
import { useCanvasLoop } from './canvasLoop';

type Rgb = [number, number, number];
const hex = (h: number): Rgb => [(h >> 16) & 0xff, (h >> 8) & 0xff, h & 0xff];

/**
 * BitChord's own colours for a surface with no artwork to take them from
 * (sharedUi/.../player/MeshGradient.kt WelcomeMeshPalettes): red, violet and
 * ember first, then sunset, ocean, aurora, grape and rose, gold.
 */
const PALETTES: Rgb[][] = [
  [0xfa2d48, 0x7b2ff7, 0xff7a45, 0x2e1a8a],
  [0xff4e8a, 0xff9a3c, 0xe0245e, 0x6a1b9a],
  [0x1e88e5, 0x00bfa5, 0x5e35b1, 0x0d47a1],
  [0x00c853, 0x00b8d4, 0x7c4dff, 0x1a237e],
  [0xd500f9, 0xff4081, 0x651fff, 0x4a148c],
  [0xffb300, 0xff5252, 0xf06292, 0x6d2e46],
].map((p) => p.map((c) => tuned(hex(c))));

function toHsl([r, g, b]: Rgb): Rgb {
  const [rn, gn, bn] = [r / 255, g / 255, b / 255];
  const max = Math.max(rn, gn, bn);
  const min = Math.min(rn, gn, bn);
  const l = (max + min) / 2;
  const d = max - min;
  if (!d) return [0, 0, l];
  const s = d / (1 - Math.abs(2 * l - 1));
  const h = max === rn ? ((gn - bn) / d + 6) % 6 : max === gn ? (bn - rn) / d + 2 : (rn - gn) / d + 4;
  return [h * 60, s, l];
}
function fromHsl([h, s, l]: Rgb): Rgb {
  const c = (1 - Math.abs(2 * l - 1)) * s;
  const x = c * (1 - Math.abs(((h / 60) % 2) - 1));
  const m = l - c / 2;
  const [r, g, b] = h < 60 ? [c, x, 0] : h < 120 ? [x, c, 0] : h < 180 ? [0, c, x] : h < 240 ? [0, x, c] : h < 300 ? [x, 0, c] : [c, 0, x];
  return [(r + m) * 255, (g + m) * 255, (b + m) * 255];
}
/** MeshGradient.kt tuned(): saturation up by a third, lightness held between 28% and 58%. */
function tuned(color: Rgb): Rgb {
  const [h, s, l] = toHsl(color);
  return fromHsl([h, Math.min(1, s * 1.35), Math.min(0.58, Math.max(0.28, l))]);
}
/** MeshGradient.kt dimmed(): the same hue at 12% lightness, the mesh's base. */
const dimmed = (color: Rgb): Rgb => {
  const [h, s] = toHsl(color);
  return fromHsl([h, s, 0.12]);
};

/** Each palette is held this long, and the fade into the next takes as long (MESH_CYCLE_MILLIS). */
const CYCLE_S = 3.6;
/** SignInBanner's driftMillis; a continuous mesh turns once every four of them. */
const DRIFT_S = 6;
const ANCHORS = [
  [0.2, 0.25],
  [0.8, 0.2],
  [0.75, 0.8],
  [0.25, 0.75],
] as const;
const SPEEDS = [1, -0.7, 0.85, -1.15];

const mixRgb = (a: Rgb, b: Rgb, t: number): Rgb => [a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t];
const css = ([r, g, b]: Rgb, alpha = 1) => `rgb(${r | 0} ${g | 0} ${b | 0} / ${alpha})`;

/**
 * MeshGradientBackground as the sign-in card runs it: four soft blobs in the
 * palette's colours orbiting their corners over a dimmed base, the palette
 * crossfading into the next every 3.6s, the layer blurred 36px and drawn 1.3×
 * so the blur's rim is clipped, under a scrim of black from 10% to 38%.
 */
function drawMesh(ctx: CanvasRenderingContext2D, w: number, h: number, seconds: number) {
  const step = seconds / CYCLE_S;
  const from = PALETTES[Math.floor(step) % PALETTES.length]!;
  const to = PALETTES[(Math.floor(step) + 1) % PALETTES.length]!;
  const colors = from.map((c, i) => mixRgb(c, to[i]!, step % 1));
  const drift = (seconds / (DRIFT_S * 4)) * Math.PI * 2;
  ctx.fillStyle = css(dimmed(colors[0]!));
  ctx.fillRect(0, 0, w, h);
  const radius = Math.max(w, h) * 0.62;
  colors.forEach((color, i) => {
    const [ax, ay] = ANCHORS[i]!;
    const x = (ax + 0.16 * Math.cos(drift * SPEEDS[i]! + i * 1.7)) * w;
    const y = (ay + 0.16 * Math.sin(drift * SPEEDS[i]! * 0.9 + i * 2.3)) * h;
    const blob = ctx.createRadialGradient(x, y, 0, x, y, radius);
    // Along smootherstep, so a blob has no rim to show through the blur.
    for (let k = 0; k <= 8; k++) {
      const t = k / 8;
      blob.addColorStop(t, css(color, 0.85 * (1 - t * t * t * (t * (t * 6 - 15) + 10))));
    }
    ctx.fillStyle = blob;
    ctx.fillRect(0, 0, w, h);
  });
  const scrim = ctx.createLinearGradient(0, 0, 0, h);
  scrim.addColorStop(0, 'rgb(0 0 0 / 0.1)');
  scrim.addColorStop(1, 'rgb(0 0 0 / 0.38)');
  ctx.fillStyle = scrim;
  ctx.fillRect(0, 0, w, h);
}

/** Google's G, as the phone draws it (composeResources/drawable/ic_google_g.xml). */
function GoogleG() {
  return (
    <svg viewBox="0 0 48 48" className="size-[18px] flex-none" aria-hidden="true">
      <path fill="#EA4335" d="M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z" />
      <path fill="#4285F4" d="M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z" />
      <path fill="#FBBC05" d="M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z" />
      <path fill="#34A853" d="M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z" />
    </svg>
  );
}

/** The mesh, a quarter of the card's resolution: it is all blur. */
function Mesh() {
  const canvas = useRef<HTMLCanvasElement>(null);
  useCanvasLoop(canvas, drawMesh, { scale: 0.25 });
  return <canvas ref={canvas} className="absolute inset-0 -z-1 size-full scale-130 blur-[36px]" aria-hidden="true" />;
}

// The whole card is the control, and sinks to 98% while pressed (stiffness 900 spring, about 150ms).
const card =
  'relative isolate mx-(--body-gutter) my-2 block overflow-hidden rounded-signin-card text-start text-white transition-[scale] duration-150 ease-out active:scale-[.98] disabled:opacity-60';

/**
 * The prompt atop Home and Radio while signed out (Common.kt SignInBanner): a
 * card in the welcome pages' living mesh, so the one thing these pages ask of
 * a signed-out listener reads as an invitation from the app. White on colour
 * in either theme.
 *
 * It goes the sidebar's way in (shell/AccountButton.tsx): Google's own sign-in
 * in a new window, which the extension notices; or, signed in to the browser
 * already, connecting that account.
 */
export function SignInBanner() {
  const status = useYtAuth();
  const connect = useSetConnected();
  const refresh = useRefreshYtAuth();

  if (status.state !== 'ready' || status.auth.signedIn) return null;
  const { auth } = status;
  const body: ReactNode = (
    <>
      <Mesh />
      <span className="flex flex-col items-start px-5 pt-5 pb-[18px]">
        <span className="type-[800_22px/26px] tracking-[-0.25px]">Sign in to YouTube Music</span>
        <span className="mt-4 flex items-center gap-2 rounded-pill bg-white py-2.5 ps-4 pe-5 type-[700_15px/19px] text-black">
          <GoogleG />
          Sign in with Google
        </span>
      </span>
    </>
  );

  if (!auth.browserSignedIn) {
    return (
      <a className={card} href={SIGN_IN_URL} target="_blank" rel="noreferrer">
        {body}
      </a>
    );
  }
  // Signed in to the browser: the account is there to connect, or to read again.
  const pending = connect.isPending || refresh.isPending;
  return (
    <button
      type="button"
      className={`${card} w-[calc(100%-2*var(--body-gutter))]`}
      disabled={pending}
      onClick={() => (auth.connected ? refresh.mutate() : connect.mutate(true))}
    >
      {body}
    </button>
  );
}
