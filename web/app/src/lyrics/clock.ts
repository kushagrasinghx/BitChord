/**
 * The lyric playhead: a clock that runs on its own frames and is *steered* by
 * the player's readings rather than set by them. A verbatim port of the
 * native app's LyricClock.kt, which fixed
 * every "stuck" and "back-and-forth" lyric bug the apps had:
 *
 *  1. A reading is dated by when it was taken, not when it was seen.
 *  2. Nothing but an announced jump moves the words backwards.
 *  3. Readings steer, they don't set: the words close the gap to the newest
 *     reading carried forward at the learned rate, at most 25 % faster or
 *     50 % slower.
 *
 * All times are milliseconds on the `performance.now()` clock.
 */
export class LyricClock {
  /** Where the lyrics are drawn, in song milliseconds. */
  displayedMs: number;

  private lastNowMs = Number.NaN;
  private lastReportMs = Number.MIN_SAFE_INTEGER;
  private lastSampledAtMs = Number.NaN;
  /** When playback was last seen stopped; a reading taken before then was taken standing still. */
  private heldAtMs = Number.NEGATIVE_INFINITY;
  private lastDiscontinuity: number | null = null;
  private jumpPending = false;
  private anchorAtMs = Number.NaN;
  private anchorMs = 0;
  /** The playback rate, learned from how far the song moves between readings. */
  private rate = 1;
  private historyAt: number[] = [];
  private historyMs: number[] = [];
  private rateSettled = false;
  private suspectAtMs = Number.NaN;
  private suspectMs = 0;
  private rejections = 0;
  private glideStartMs = Number.NaN;
  private glideOffsetMs = 0;

  constructor(startMs: number) {
    this.displayedMs = startMs;
  }

  /** Forget the run so far: frames stopped arriving, and the next frame starts a new one. */
  restart(): void {
    this.lastNowMs = Number.NaN;
    this.jumpPending = false;
    this.glideStartMs = Number.NaN;
    this.clearSuspect();
  }

  /** Playback is not moving: settle on [positionMs] outright, as of [nowMs]. */
  hold(positionMs: number, nowMs: number): void {
    this.restart();
    this.displayedMs = positionMs;
    this.heldAtMs = nowMs;
  }

  /**
   * Advance to the frame at [nowMs] and return where the lyrics should be drawn.
   * [sampledAtMs] is when [reportedMs] was taken (NaN when unknown);
   * [discontinuity] changes whenever the playhead jumps on purpose.
   */
  frame(nowMs: number, reportedMs: number, sampledAtMs: number, discontinuity: number): number {
    const firstOfRun = Number.isNaN(this.lastNowMs);
    if (discontinuity !== this.lastDiscontinuity) {
      if (this.lastDiscontinuity !== null && !firstOfRun) this.jumpPending = true;
      this.lastDiscontinuity = discontinuity;
    }
    const isNew =
      reportedMs !== this.lastReportMs || (!Number.isNaN(sampledAtMs) && sampledAtMs !== this.lastSampledAtMs);
    this.lastReportMs = reportedMs;
    this.lastSampledAtMs = sampledAtMs;

    if (firstOfRun) {
      this.lastNowMs = nowMs;
      const takenAt = Number.isNaN(sampledAtMs) || sampledAtMs <= this.heldAtMs ? nowMs : Math.min(sampledAtMs, nowMs);
      this.anchor(takenAt, reportedMs);
      this.displayedMs = this.targetAt(nowMs);
      return Math.trunc(this.displayedMs);
    }

    const dt = Math.max(0, nowMs - this.lastNowMs);
    this.lastNowMs = nowMs;
    this.advance(nowMs, dt);

    if (isNew) {
      const takenAt = Number.isNaN(sampledAtMs) ? nowMs : Math.min(sampledAtMs, nowMs);
      if (takenAt >= this.anchorAtMs || this.jumpPending) this.take(nowMs, takenAt, reportedMs);
    }
    return Math.trunc(this.displayedMs);
  }

  private take(nowMs: number, takenAt: number, reportedMs: number): void {
    if (this.jumpPending) {
      this.jumpPending = false;
      this.clearSuspect();
      this.anchor(takenAt, reportedMs);
      this.jumpTo(nowMs);
      return;
    }
    const elapsed = takenAt - this.anchorAtMs;
    if (Math.abs(reportedMs - (this.anchorMs + elapsed * this.rate)) <= this.tolerance(elapsed)) {
      this.clearSuspect();
      this.anchor(takenAt, reportedMs, true);
      return;
    }
    const sinceSuspect = takenAt - this.suspectAtMs;
    const agrees =
      !Number.isNaN(this.suspectAtMs) &&
      sinceSuspect >= 0 &&
      Math.abs(reportedMs - (this.suspectMs + sinceSuspect * this.rate)) <= this.tolerance(sinceSuspect);
    this.rejections++;
    if (agrees) {
      const firstAt = this.suspectAtMs;
      const first = this.suspectMs;
      this.clearSuspect();
      this.anchor(firstAt, first);
      this.anchor(takenAt, reportedMs, true);
    } else if (this.rejections > MAX_REJECTIONS) {
      this.clearSuspect();
      this.anchor(takenAt, reportedMs);
    } else {
      this.suspectAtMs = takenAt;
      this.suspectMs = reportedMs;
    }
  }

  private tolerance(elapsedMs: number): number {
    return OUTLIER_MS + Math.abs(elapsedMs) * (this.rateSettled ? RATE_SLACK : UNSETTLED_RATE_SLACK);
  }

  private anchor(atMs: number, positionMs: number, continues = false): void {
    this.anchorAtMs = atMs;
    this.anchorMs = positionMs;
    if (!continues) {
      this.historyAt = [];
      this.historyMs = [];
    }
    if (this.historyAt.length === HISTORY) {
      this.historyAt.shift();
      this.historyMs.shift();
    }
    this.historyAt.push(atMs);
    this.historyMs.push(this.anchorMs);
    this.learnRate();
  }

  private learnRate(): void {
    const newest = this.historyAt.length - 1;
    if (newest < 1) return;
    let oldest = 0;
    while (oldest < newest && this.historyAt[newest]! - this.historyAt[oldest]! > RATE_WINDOW_MS) oldest++;
    const span = this.historyAt[newest]! - this.historyAt[oldest]!;
    if (span >= MIN_RATE_SPAN_MS) {
      this.rate = clamp((this.historyMs[newest]! - this.historyMs[oldest]!) / span, MIN_RATE, MAX_RATE);
      this.rateSettled = true;
      return;
    }
    const pairSpan = this.historyAt[newest]! - this.historyAt[newest - 1]!;
    if (!this.rateSettled && pairSpan >= MIN_PAIR_SPAN_MS) {
      const measured = clamp((this.historyMs[newest]! - this.historyMs[newest - 1]!) / pairSpan, MIN_RATE, MAX_RATE);
      this.rate += (measured - this.rate) * PAIR_LEARNING;
    }
  }

  private clearSuspect(): void {
    this.suspectAtMs = Number.NaN;
    this.rejections = 0;
  }

  private advance(nowMs: number, dt: number): void {
    const target = this.targetAt(nowMs);
    const step = dt * this.rate;
    const error = target - (this.displayedMs + step);
    if (!Number.isNaN(this.glideStartMs)) {
      const progress = (nowMs - this.glideStartMs) / GLIDE_MS;
      if (progress >= 1) {
        this.displayedMs = target;
        this.glideStartMs = Number.NaN;
      } else {
        this.displayedMs = target + this.glideOffsetMs * (1 - smoothstep(progress));
      }
    } else if (error <= -LOST_MS || error >= LOST_MS) {
      this.displayedMs = target;
    } else if (error >= GLIDE_FROM_MS) {
      this.startGlide(nowMs - dt, this.displayedMs - (target - step));
      this.displayedMs = target + this.glideOffsetMs * (1 - smoothstep(dt / GLIDE_MS));
    } else {
      const closing = error * (dt / (dt + CORRECTION_MS));
      this.displayedMs += step + clamp(closing, -MAX_SLOW * step, MAX_SLEW * step);
    }
  }

  private jumpTo(nowMs: number): void {
    const offset = this.displayedMs - this.targetAt(nowMs);
    if (Math.abs(offset) > GLIDE_MAX_MS) {
      this.displayedMs = this.targetAt(nowMs);
      this.glideStartMs = Number.NaN;
    } else {
      this.startGlide(nowMs, offset);
    }
  }

  private startGlide(startMs: number, offsetMs: number): void {
    this.glideStartMs = startMs;
    this.glideOffsetMs = offsetMs;
  }

  /** Where the song is at [atMs]: the newest reading believed, carried forward. */
  private targetAt(atMs: number): number {
    return this.anchorMs + (atMs - this.anchorAtMs) * this.rate;
  }
}

const clamp = (value: number, min: number, max: number) => Math.min(max, Math.max(min, value));

function smoothstep(fraction: number): number {
  const t = clamp(fraction, 0, 1);
  return t * t * (3 - 2 * t);
}

const OUTLIER_MS = 250;
const RATE_SLACK = 0.2;
const UNSETTLED_RATE_SLACK = 0.75;
const MAX_REJECTIONS = 2;
const MIN_RATE_SPAN_MS = 900;
const RATE_WINDOW_MS = 4000;
const HISTORY = 16;
const MIN_PAIR_SPAN_MS = 300;
const PAIR_LEARNING = 0.5;
const MIN_RATE = 0.25;
const MAX_RATE = 4;
const CORRECTION_MS = 600;
const MAX_SLEW = 0.25;
const MAX_SLOW = 0.5;
const GLIDE_FROM_MS = 300;
const GLIDE_MS = 260;
const GLIDE_MAX_MS = 2000;
const LOST_MS = 3000;
