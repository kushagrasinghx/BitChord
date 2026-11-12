import { describe, expect, it } from 'vitest';
import { LyricClock } from './clock';

// Ported from the native app's LyricClockTest.kt.

const FRAME_MS = 1000 / 60;

/** A small seeded generator, so a failure reproduces. */
function seeded(seed: number): () => number {
  let state = seed >>> 0 || 1;
  return () => {
    state = (state * 1664525 + 1013904223) >>> 0;
    return state / 2 ** 32;
  };
}

/** A song read every 500 ms, each reading stamped when taken and delivered up to [maxDelayMs] later. */
class Playback {
  report: number;
  sampledAt: number;
  private nextPollMs = 0;
  private inFlight: { arrives: number; reading: number; at: number }[] = [];
  private random: () => number;

  constructor(
    readonly startMs = 44_500,
    readonly rate = 1,
    readonly maxDelayMs = 60,
    readonly stamped = true,
    seed = 7,
  ) {
    this.report = startMs;
    this.sampledAt = stamped ? 0 : Number.NaN;
    this.random = seeded(seed);
  }

  trueAt(nowMs: number) {
    return this.startMs + nowMs * this.rate;
  }

  deliver(nowMs: number) {
    while (this.nextPollMs <= nowMs) {
      this.inFlight.push({
        arrives: this.nextPollMs + this.random() * this.maxDelayMs,
        reading: Math.trunc(this.trueAt(this.nextPollMs)),
        at: this.nextPollMs,
      });
      this.nextPollMs += 500;
    }
    this.inFlight.sort((a, b) => a.arrives - b.arrives);
    while (this.inFlight.length && this.inFlight[0]!.arrives <= nowMs) {
      const { reading, at } = this.inFlight.shift()!;
      this.report = reading;
      if (this.stamped) this.sampledAt = at;
    }
  }
}

function run(playback: Playback, seconds: number, maxStallMs = 0, seed = 11) {
  const random = seeded(seed);
  const clock = new LyricClock(playback.startMs);
  const frames: number[] = [];
  const shown: number[] = [];
  const truth: number[] = [];
  let now = 0;
  while (now <= seconds * 1000) {
    playback.deliver(now);
    frames.push(now);
    shown.push(clock.frame(now, playback.report, playback.sampledAt, 0));
    truth.push(playback.trueAt(now));
    now += FRAME_MS;
    if (maxStallMs > 0 && random() < 0.02) now += random() * maxStallMs;
  }
  return { frames, shown, truth };
}

function neverBack({ frames, shown }: ReturnType<typeof run>) {
  for (let i = 1; i < shown.length; i++) {
    if (shown[i]! < shown[i - 1]!) throw new Error(`went back at ${frames[i]}`);
  }
}

describe('LyricClock', () => {
  for (const seed of [7, 19, 42, 101, 2024]) {
    it(`never moves the words back on late readings (seed ${seed})`, () => {
      neverBack(run(new Playback(44_500, 1, 800, true, seed), 90, 600, seed + 4));
    });
  }

  it('keeps late readings from pulling the words off the song', () => {
    const r = run(new Playback(44_500, 1, 800), 60, 600);
    r.frames.forEach((at, i) => {
      if (at < 2000) return;
      expect(Math.abs(r.shown[i]! - r.truth[i]!)).toBeLessThan(40);
    });
  });

  it('never stalls the words', () => {
    const r = run(new Playback(44_500, 1, 800), 60);
    const window = 15;
    for (let i = window; i < r.shown.length; i++) {
      const moved = r.shown[i]! - r.shown[i - window]!;
      const elapsed = r.frames[i]! - r.frames[i - window]!;
      expect(moved).toBeGreaterThanOrEqual(elapsed * 0.5);
    }
  });

  it('never goes back on unstamped readings', () => {
    neverBack(run(new Playback(44_500, 1, 800, false), 60, 600));
  });

  it('learns a faster playback rate', () => {
    const r = run(new Playback(44_500, 1.25, 300), 30);
    r.frames.forEach((at, i) => {
      if (at < 4000) return;
      expect(Math.abs(r.shown[i]! - r.truth[i]!)).toBeLessThan(40);
    });
    neverBack(r);
  });

  it('jumps on an announced seek', () => {
    const clock = new LyricClock(10_000);
    let now = 0;
    let shown = 0;
    while (now <= 3000) {
      const taken = Math.floor(now / 500) * 500;
      shown = clock.frame(now, 10_000 + taken, taken, 0);
      now += FRAME_MS;
    }
    // Seek to 60 s, announced.
    const seekAt = now;
    while (now <= seekAt + 1000) {
      const taken = Math.floor(now / 500) * 500;
      shown = clock.frame(now, 60_000 + Math.max(0, taken - seekAt), Math.max(taken, seekAt), 1);
      now += FRAME_MS;
    }
    expect(Math.abs(shown - (60_000 + (now - FRAME_MS - seekAt)))).toBeLessThan(100);
  });
});
