import { describe, expect, it } from 'vitest';
import { LyricClock } from '@bitchord/shared-kotlin';

// LyricClockTest.kt's cases, run against LyricClock.kt itself (shared/commonMain, compiled to JavaScript).

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
    shown.push(clock.frame(now, playback.report, playback.sampledAt, 0, true));
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
      shown = clock.frame(now, 10_000 + taken, taken, 0, true);
      now += FRAME_MS;
    }
    // Seek to 60 s, announced.
    const seekAt = now;
    while (now <= seekAt + 1000) {
      const taken = Math.floor(now / 500) * 500;
      shown = clock.frame(now, 60_000 + Math.max(0, taken - seekAt), Math.max(taken, seekAt), 1, true);
      now += FRAME_MS;
    }
    expect(Math.abs(shown - (60_000 + (now - FRAME_MS - seekAt)))).toBeLessThan(100);
  });

  // Pause and resume: the readings LyricClockTest.kt builds from a PlaybackPosition, written out.

  it('resumes where it stopped', () => {
    const clock = new LyricClock(10_000);
    clock.frame(0, 10_000, 0, 0, true);
    clock.observe(5, 10_000, 5, 0, false);
    clock.restart();
    expect(clock.frame(60_000, 10_000, 0, 0, true)).toBe(10_000);
    expect(clock.frame(60_016, 10_000, 0, 0, true)).toBe(10_016);
  });

  it('freezes the sweep through a pause, then carries on from it', () => {
    const clock = new LyricClock(30_000);
    clock.frame(1000, 30_000, 1000, 0, true);
    const beforePause = clock.frame(1200.5, 30_000, 1000, 0, true);
    const frozen = clock.displayedMs;

    expect(clock.observe(1210, 30_170, 1210, 0, false)).toBe(beforePause);
    expect(clock.observe(1500, 30_150, 1500, 0, false)).toBe(beforePause);
    expect(clock.displayedMs).toBe(frozen);

    expect(clock.observe(5000, 30_100, 5000, 0, true)).toBeNull();
    clock.restart();
    expect(clock.frame(5000, 30_100, 5000, 0, true)).toBe(beforePause);
    let previous = beforePause;
    let position = 30_100;
    for (let step = 1; step <= 600; step++) {
      const elapsed = step * FRAME_MS;
      const now = 5000 + elapsed;
      position = 30_100 + Math.trunc(elapsed);
      const next = clock.frame(now, position, now, 0, true);
      expect(next).toBeGreaterThanOrEqual(previous);
      previous = next;
    }
    expect(Math.abs(previous - position)).toBeLessThan(40);
  });

  it('still lands a seek made while paused', () => {
    const clock = new LyricClock(30_867);
    clock.observe(1000, 30_867, 1000, 0, false);
    expect(clock.observe(2000, 20_000, 2000, 1, false)).toBe(20_000);
    expect(clock.observe(3000, 35_000, 3000, 2, false)).toBe(35_000);
    expect(clock.observe(5000, 34_990, 5000, 2, true)).toBeNull();
    clock.restart();
    expect(clock.frame(5000, 34_990, 5000, 2, true)).toBe(35_000);
  });

  it('does not hold the paused place over a seek made as playback resumes', () => {
    const clock = new LyricClock(30_867);
    clock.observe(1000, 30_867, 1000, 0, false);
    expect(clock.observe(5000, 20_000, 5000, 1, true)).toBeNull();
    clock.restart();
    expect(clock.frame(5000, 20_000, 5000, 1, true)).toBe(20_000);
  });
});
