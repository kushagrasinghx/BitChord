import { useEffect, useRef, useState, useSyncExternalStore } from 'react';
import { GRID, meshPalette, type Rgb } from '@/lib/meshPalette';

/*
 * The 2D fallback's banding is broken up by a still film of grain (bg-grain).
 * The WebGL mesh dithers itself, finer and per frame, so it goes without.
 */
const backdrop =
  'absolute inset-0 -z-1 overflow-hidden bg-[#343438] data-fallback:after:absolute data-fallback:after:inset-0 data-fallback:after:bg-grain data-fallback:after:opacity-6';

const FRAME_MS = 1000 / 30;
const FADE_MS = 1200;
/** Before the artwork is read, and when it can't be: a neutral dark grey. */
const FALLBACK: Rgb[] = Array.from({ length: GRID * GRID }, () => [52, 52, 56]);

const reducedMotion = typeof matchMedia === 'function' ? matchMedia('(prefers-reduced-motion: reduce)') : null;
function useReducedMotion(): boolean {
  return useSyncExternalStore(
    (onChange) => {
      reducedMotion?.addEventListener('change', onChange);
      return () => reducedMotion?.removeEventListener('change', onChange);
    },
    () => reducedMotion?.matches ?? false,
  );
}

/**
 * Each cell's blob: where it sits and how it drifts. The grid is turned a
 * quarter: the colours keep their neighbours and proportions, but don't line
 * up column for column with the artwork shown over them, which otherwise reads
 * as a blurred reflection.
 */
const BLOBS = Array.from({ length: GRID * GRID }, (_, i) => {
  const row = Math.floor(i / GRID);
  const col = i % GRID;
  const spread = (n: number) => -0.1 + ((n + 0.5) / GRID) * 1.2;
  // A fixed pseudo-random phase per cell, so every track drifts the same way.
  const seed = (n: number) => ((Math.sin(i * 12.9898 + n * 78.233) * 43758.5453) % 1 + 1) % 1;
  return {
    x: spread(GRID - 1 - row),
    y: spread(col),
    phase: [seed(1), seed(2), seed(3)].map((v) => v * Math.PI * 2) as [number, number, number],
    speed: [0.07 + seed(4) * 0.06, 0.06 + seed(5) * 0.06, 0.05 + seed(6) * 0.04] as [number, number, number],
  };
});

/** Where every blob is at [seconds]: x and y as fractions of the screen, the reach as one of its longer side. */
function blobsAt(seconds: number): [number, number, number][] {
  return BLOBS.map(({ x, y, phase: [px, py, pr], speed: [sx, sy, sr] }) => [
    x + 0.16 * Math.sin(seconds * sx + px),
    y + 0.2 * Math.cos(seconds * sy + py),
    0.62 * (0.85 + 0.2 * Math.sin(seconds * sr + pr)),
  ]);
}

function mix(from: Rgb[], to: Rgb[], k: number): Rgb[] {
  return to.map((c, i) => c.map((v, j) => from[i]![j]! + (v - from[i]![j]!) * k) as Rgb);
}

function average(cells: Rgb[]): Rgb {
  const sum = cells.reduce<Rgb>((a, c) => [a[0] + c[0], a[1] + c[1], a[2] + c[2]], [0, 0, 0]);
  return sum.map((v) => v / cells.length) as Rgb;
}

interface Renderer {
  draw: (cells: Rgb[], seconds: number) => void;
  dispose: () => void;
}

/*
 * The mesh, worked out for every pixel of the screen: each blob a soft
 * Gaussian, the colours blended in linear light (so two colours meet through
 * a clean mid-tone, not a muddy one), then a triangular dither of under one
 * 8-bit step that changes every frame. That last part is what keeps smooth
 * dark gradients from banding without the visible grain a fixed texture gives.
 */
const VERTEX = `attribute vec2 p; void main() { gl_Position = vec4(p, 0.0, 1.0); }`;
const FRAGMENT = `
precision highp float;
uniform vec2 uRes;
uniform vec3 uBase;
uniform vec3 uCol[${GRID * GRID}];
uniform vec3 uBlob[${GRID * GRID}];
uniform float uFrame;

float hash(vec2 p) {
  vec3 q = fract(vec3(p.xyx) * 0.1031 + uFrame * 0.0137);
  q += dot(q, q.yzx + 33.33);
  return fract((q.x + q.y) * q.z);
}

void main() {
  vec2 px = vec2(gl_FragCoord.x, uRes.y - gl_FragCoord.y);
  float side = max(uRes.x, uRes.y);
  vec3 sum = pow(uBase, vec3(2.2)) * 0.35;
  float weight = 0.35;
  for (int i = 0; i < ${GRID * GRID}; i++) {
    float d = distance(px, uBlob[i].xy * uRes) / (uBlob[i].z * side);
    float w = exp(-4.0 * d * d);
    sum += pow(uCol[i], vec3(2.2)) * w;
    weight += w;
  }
  vec3 color = pow(sum / weight, vec3(1.0 / 2.2));
  color += (hash(gl_FragCoord.xy) + hash(gl_FragCoord.yx + 17.0) - 1.0) / 255.0;
  gl_FragColor = vec4(color, 1.0);
}`;

function webglRenderer(canvas: HTMLCanvasElement): Renderer | null {
  const gl = canvas.getContext('webgl', { alpha: false, antialias: false, depth: false, powerPreference: 'low-power' });
  if (!gl) return null;
  const shader = (type: number, source: string) => {
    const s = gl.createShader(type)!;
    gl.shaderSource(s, source);
    gl.compileShader(s);
    return gl.getShaderParameter(s, gl.COMPILE_STATUS) ? s : null;
  };
  const vertex = shader(gl.VERTEX_SHADER, VERTEX);
  const fragment = shader(gl.FRAGMENT_SHADER, FRAGMENT);
  if (!vertex || !fragment) return null;
  const program = gl.createProgram()!;
  gl.attachShader(program, vertex);
  gl.attachShader(program, fragment);
  gl.linkProgram(program);
  if (!gl.getProgramParameter(program, gl.LINK_STATUS)) return null;
  gl.useProgram(program);

  const buffer = gl.createBuffer();
  gl.bindBuffer(gl.ARRAY_BUFFER, buffer);
  gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1, -1, 3, -1, -1, 3]), gl.STATIC_DRAW);
  const position = gl.getAttribLocation(program, 'p');
  gl.enableVertexAttribArray(position);
  gl.vertexAttribPointer(position, 2, gl.FLOAT, false, 0, 0);

  const at = (name: string) => gl.getUniformLocation(program, name);
  const [uRes, uBase, uCol, uBlob, uFrame] = ['uRes', 'uBase', 'uCol', 'uBlob', 'uFrame'].map(at);
  let frame = 0;

  return {
    draw(cells, seconds) {
      // One device pixel per CSS pixel at most: the mesh has no detail a denser canvas would show.
      const scale = Math.min(window.devicePixelRatio || 1, 1);
      const width = Math.max(1, Math.round(canvas.clientWidth * scale));
      const height = Math.max(1, Math.round(canvas.clientHeight * scale));
      if (canvas.width !== width || canvas.height !== height) {
        canvas.width = width;
        canvas.height = height;
      }
      gl.viewport(0, 0, width, height);
      gl.uniform2f(uRes!, width, height);
      gl.uniform3fv(uBase!, average(cells).map((v) => v / 255));
      gl.uniform3fv(uCol!, cells.flatMap((c) => c.map((v) => v / 255)));
      gl.uniform3fv(uBlob!, blobsAt(seconds).flat());
      gl.uniform1f(uFrame!, (frame = (frame + 1) % 1024));
      gl.drawArrays(gl.TRIANGLES, 0, 3);
    },
    dispose() {
      gl.deleteBuffer(buffer);
      gl.deleteProgram(program);
      gl.deleteShader(vertex);
      gl.deleteShader(fragment);
    },
  };
}

/**
 * Without WebGL: the same blobs as radial gradients on a 64×40 canvas the
 * browser stretches. Softer and coarser, but it never fails.
 */
const SMALL_WIDTH = 64;
const SMALL_HEIGHT = 40;
const css = ([r, g, b]: Rgb, a: number) => `rgb(${r | 0} ${g | 0} ${b | 0} / ${a})`;
function canvasRenderer(canvas: HTMLCanvasElement): Renderer | null {
  const context = canvas.getContext('2d', { alpha: false });
  if (!context) return null;
  canvas.width = SMALL_WIDTH;
  canvas.height = SMALL_HEIGHT;
  return {
    draw(cells, seconds) {
      context.fillStyle = css(average(cells), 1);
      context.fillRect(0, 0, SMALL_WIDTH, SMALL_HEIGHT);
      const side = Math.max(SMALL_WIDTH, SMALL_HEIGHT);
      blobsAt(seconds).forEach(([bx, by, reach], i) => {
        const x = bx * SMALL_WIDTH;
        const y = by * SMALL_HEIGHT;
        const color = cells[i]!;
        const gradient = context.createRadialGradient(x, y, 0, x, y, reach * side);
        gradient.addColorStop(0, css(color, 0.9));
        gradient.addColorStop(0.55, css(color, 0.35));
        gradient.addColorStop(1, css(color, 0));
        context.fillStyle = gradient;
        context.fillRect(0, 0, SMALL_WIDTH, SMALL_HEIGHT);
      });
    },
    dispose() {},
  };
}

/**
 * The full-screen player's background: the artwork's colours as a slowly
 * drifting mesh gradient (report §7, "the artwork, blurred and animated").
 * It moves only while the music plays and motion is allowed, cross-fades to
 * each new track's colours, and draws nothing while the tab is hidden.
 */
export function MeshBackdrop({ artworkUrl, playing }: { artworkUrl: string | null; playing: boolean }) {
  const canvas = useRef<HTMLCanvasElement>(null);
  const still = useReducedMotion();
  const animate = playing && !still;
  // A lost WebGL context remounts the canvas, which starts over with a fresh one.
  const [generation, setGeneration] = useState(0);
  const [fallback, setFallback] = useState(false);
  // Kept outside React: the frame loop reads and writes these.
  const state = useRef({ from: FALLBACK, to: FALLBACK, fadeStart: -Infinity, seconds: 0, wake: () => {} });

  useEffect(() => {
    if (!artworkUrl) return;
    let live = true;
    meshPalette(artworkUrl)
      .then((cells) => {
        if (!live) return;
        const s = state.current;
        const now = performance.now();
        // The first colours show at once; later tracks cross-fade from whatever is on screen.
        s.from = s.fadeStart === -Infinity ? cells : mix(s.from, s.to, Math.min(1, (now - s.fadeStart) / FADE_MS));
        s.to = cells;
        s.fadeStart = now;
        s.wake();
      })
      .catch(() => {
        // An unreadable image keeps the colours already showing.
      });
    return () => {
      live = false;
    };
  }, [artworkUrl]);

  useEffect(() => {
    const el = canvas.current;
    if (!el) return;
    const renderer = fallback ? canvasRenderer(el) : webglRenderer(el);
    if (!renderer) {
      // No WebGL, or a shader it won't compile: a fresh canvas for the 2D version.
      if (!fallback) setFallback(true);
      return;
    }
    const s = state.current;
    let frame = 0;
    let last = performance.now();
    let drawn = -Infinity;
    const tick = (now: number) => {
      frame = 0;
      if (animate) s.seconds += Math.min(now - last, 100) / 1000;
      last = now;
      const fade = Math.min(1, (now - s.fadeStart) / FADE_MS);
      if (now - drawn >= FRAME_MS - 1) {
        drawn = now;
        // Ease out, so the old colours let go quickly and the new ones settle.
        renderer.draw(fade < 1 ? mix(s.from, s.to, 1 - (1 - fade) ** 3) : s.to, s.seconds);
      }
      if (animate || fade < 1) frame = requestAnimationFrame(tick);
    };
    s.wake = () => {
      if (!frame) {
        last = performance.now();
        frame = requestAnimationFrame(tick);
      }
    };
    // A paused mesh still has to follow the window's size.
    const resized = new ResizeObserver(() => s.wake());
    resized.observe(el);
    const lost = (e: Event) => {
      e.preventDefault();
      setGeneration((g) => g + 1);
    };
    el.addEventListener('webglcontextlost', lost);
    s.wake();
    return () => {
      cancelAnimationFrame(frame);
      resized.disconnect();
      el.removeEventListener('webglcontextlost', lost);
      renderer.dispose();
      s.wake = () => {};
    };
  }, [animate, generation, fallback]);

  return (
    <div className={backdrop} aria-hidden="true" data-fallback={fallback || undefined}>
      {/* Drawn at the screen's size by WebGL; the 2D fallback is a 64×40 canvas stretched. */}
      <canvas key={`${generation}-${fallback}`} ref={canvas} className="absolute inset-0 size-full" />
    </div>
  );
}
