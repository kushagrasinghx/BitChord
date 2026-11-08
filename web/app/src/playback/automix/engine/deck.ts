// Adapted from AutoMix's src/lib/engine/deck.ts (https://github.com/heydavidf/automix),
// Copyright (C) 2026 David Finnegan. AGPL-3.0-only: see ../LICENSE and ../NOTICE.md.
//
// BitChord: AutoMix's deck is a signalsmith-stretch worklet over a decoded
// buffer. Here it is one of the player's two <audio> elements (Shaka-fed),
// routed through Web Audio. The public surface is AutoMix's, so the
// transition scheduler runs unchanged: a Timeline mirror of every scheduled
// change, a 3-band EQ, a gain, and changes posted only when imminent. What
// the worklet did sample-accurately, a phase-lock servo does here: while a
// transition is in flight the element is held on its timeline by nudging its
// pitch-preserving playbackRate, or by a seek when it is far off.
import { ThreeBandEQ } from './eq';
import { Timeline } from './timeline';

export interface LoadedTrackMeta {
  fileKey: string;
  title: string;
  artist: string;
}

export interface PlayOptions {
  /** AudioContext time to start/take effect. Defaults to "now + latency". */
  ctxTime?: number;
  /** Position in the track to play from (seconds). */
  trackTime: number;
  rate?: number;
}

type Change = { output: number; input: number; rate: number; active: boolean };

/** Off by more than this, a seek; below it, a rate nudge. */
const SEEK_ERROR_SEC = 0.25;
/** Inside this, the element is on the beat (a 16th at 128 BPM is 117 ms). */
const DEADBAND_SEC = 0.006;
/** Largest rate nudge: 3% is below what a listener hears with pitch preserved. */
const MAX_NUDGE = 0.03;
/** Largest rate nudge while the deck's gain is under 0.3. */
const MAX_QUIET_NUDGE = 0.1;
/** Seconds of error corrected per second of playback. */
const SERVO_GAIN = 0.8;
const SERVO_MS = 40;

/**
 * One DJ deck: <audio> -> 3-band EQ -> gain (AutoMix's automation) -> fader
 * (the player's own crossfade share) -> master. All transport goes through
 * scheduleChange() on the AudioContext clock and is mirrored into a Timeline
 * for exact trackTime <-> ctxTime conversion.
 */
export class Deck {
  readonly eq: ThreeBandEQ;
  readonly gain: GainNode;
  /** The player's plain-crossfade share and nothing else; AutoMix never touches it. */
  readonly fader: GainNode;
  /** BitChord: the filter mix's high- and low-pass, open (inaudible) except during one. */
  readonly sweep: { highpass: BiquadFilterNode; lowpass: BiquadFilterNode };
  readonly timeline = new Timeline();
  loaded: LoadedTrackMeta | null = null;
  /** How long play() takes to put audio out, learned from each start. */
  latencySec = 0.12;

  private pending: Change[] = [];
  private dispatchTimer: ReturnType<typeof setInterval> | null = null;
  /** True while a transition holds this deck to its timeline. */
  private locked = false;
  /**
   * The deck this one follows while locked: the outgoing track, which is
   * never nudged (the phone never tempo-shifts the deck you hear leaving).
   * This deck's timeline is read at the moment the leader's element is
   * actually at, so the two stay together even when the leader's element
   * drifts from the AudioContext clock.
   */
  private leader: Deck | null = null;
  private errors: number[] = [];
  private seekingUntil = 0;

  constructor(
    readonly id: string,
    readonly ctx: AudioContext,
    readonly audio: HTMLAudioElement,
    destination: AudioNode,
  ) {
    this.eq = new ThreeBandEQ(ctx);
    this.gain = new GainNode(ctx, { gain: 1 });
    this.fader = new GainNode(ctx, { gain: 1 });
    this.sweep = {
      highpass: new BiquadFilterNode(ctx, { type: 'highpass', frequency: 20, Q: 0.7 }),
      lowpass: new BiquadFilterNode(ctx, { type: 'lowpass', frequency: 20000, Q: 0.7 }),
    };
    ctx.createMediaElementSource(audio).connect(this.eq.input);
    this.eq.output.connect(this.sweep.highpass).connect(this.sweep.lowpass).connect(this.gain).connect(this.fader).connect(destination);
    audio.preservesPitch = true;
  }

  get durationSec(): number {
    return Number.isFinite(this.audio.duration) ? this.audio.duration : 0;
  }

  /** Earliest ctx time a new schedule can take full effect. */
  get scheduleAhead(): number {
    return this.latencySec + 0.05;
  }

  /**
   * Model = what the element is doing now. Called while no transition is in
   * flight, so the timeline follows the user's seeks, pauses and the
   * element's own clock.
   */
  anchor(): void {
    if (this.locked || this.pending.length) return;
    const now = this.ctx.currentTime;
    this.timeline.clear();
    this.timeline.add({
      output: now,
      input: this.audio.currentTime,
      rate: this.audio.playbackRate || 1,
      active: !this.audio.paused && !this.audio.ended,
    });
  }

  /**
   * BitChord: anchor() from the median of several readings (the element's
   * clock moves in audio-callback steps), for a handover that has to land on
   * the element's real position: the loop mix.
   */
  async anchorPrecise(samples = 9, spacingMs = 35): Promise<void> {
    if (this.audio.paused) return this.anchor();
    const offsets: number[] = [];
    const rate = this.audio.playbackRate || 1;
    for (let i = 0; i < samples; i++) {
      offsets.push(this.audio.currentTime - this.ctx.currentTime * rate);
      await new Promise((r) => setTimeout(r, spacingMs));
    }
    offsets.sort((a, b) => a - b);
    const offset = offsets[offsets.length >> 1]!;
    const now = this.ctx.currentTime;
    this.timeline.clear();
    this.timeline.add({ output: now, input: offset + now * rate, rate, active: !this.audio.paused });
  }

  /** Sample-accurately schedule playback of trackTime at ctxTime. */
  playAt({ ctxTime, trackTime, rate = 1 }: PlayOptions): void {
    const output = ctxTime ?? this.ctx.currentTime + this.scheduleAhead;
    this.scheduleChange({ output, input: trackTime, rate, active: true });
  }

  /** Change rate while playing, keeping position continuous. */
  setRate(rate: number, atCtxTime?: number): void {
    const output = atCtxTime ?? this.ctx.currentTime + this.scheduleAhead;
    const input = this.timeline.trackTimeAt(output);
    if (input === null) return;
    const active = this.timeline.activeAt(output);
    this.scheduleChange({ output, input, rate, active });
  }

  pause(): void {
    this.pauseAt(this.ctx.currentTime);
  }

  /** Schedule a stop at a future AudioContext time. */
  pauseAt(ctxTime: number): void {
    const input = this.timeline.trackTimeAt(ctxTime) ?? this.audio.currentTime;
    const rate = this.timeline.rateAt(ctxTime) || 1;
    this.scheduleChange({ output: ctxTime, input, rate, active: false });
  }

  /** Hold the element to its timeline from now until release(); [leader] is the deck to follow. */
  lock(leader: Deck | null = null): void {
    this.leader = leader;
    this.locked = true;
    this.errors = [];
    this.ensureTimer();
  }

  /** Let the element run free again at the rate the timeline ends on. */
  release(): void {
    this.locked = false;
    this.leader = null;
    const rate = this.timeline.rateAt(this.ctx.currentTime) || 1;
    if (this.pending.length === 0) this.audio.playbackRate = rate;
  }

  /** Drop everything scheduled: automation, pending changes, the servo. */
  cancel(): void {
    this.pending = [];
    this.locked = false;
    this.leader = null;
    this.timeline.clear();
    const now = this.ctx.currentTime;
    this.gain.gain.cancelScheduledValues(now);
    this.gain.gain.setValueAtTime(1, now);
    this.eq.reset();
    this.resetSweep();
    this.audio.playbackRate = 1;
  }

  /** Opens the filter mix's filters again. */
  resetSweep(): void {
    const now = this.ctx.currentTime;
    for (const [param, open] of [
      [this.sweep.highpass.frequency, 20],
      [this.sweep.lowpass.frequency, 20000],
    ] as const) {
      param.cancelScheduledValues(now);
      param.setValueAtTime(open, now);
    }
  }

  /**
   * AutoMix posted changes to the worklet only when imminent (< 2 s). The
   * element has no scheduler at all, so a change is applied the moment its
   * time comes, less the start latency for a start.
   */
  private scheduleChange(p: Change): void {
    this.timeline.add(p);
    this.pending = this.pending.filter((q) => q.output < p.output);
    this.pending.push(p);
    // The start (the first change, from paused) more than a moment away: put
    // the element there now, so the seek's fetch is done by the time it plays.
    // Later changes (the rate glide) must not move it.
    const first = this.pending[0]!;
    if (first === p && p.active && this.audio.paused && p.output - this.ctx.currentTime > 0.5) {
      this.audio.currentTime = p.input;
    }
    this.ensureTimer();
    this.dispatch();
  }

  private ensureTimer() {
    this.dispatchTimer ??= setInterval(() => this.dispatch(), SERVO_MS);
  }

  private dispatch(): void {
    const now = this.ctx.currentTime;
    while (this.pending.length > 0) {
      const next = this.pending[0]!;
      const lead = next.active && this.audio.paused ? this.latencySec : 0;
      if (next.output - lead > now) break;
      this.pending.shift();
      this.apply(next, now);
    }
    if (this.locked) this.servo(now);
    if (this.pending.length === 0 && !this.locked && this.dispatchTimer !== null) {
      clearInterval(this.dispatchTimer);
      this.dispatchTimer = null;
    }
  }

  private apply(change: Change, now: number) {
    const audio = this.audio;
    if (!change.active) {
      if (!audio.paused) audio.pause();
      return;
    }
    audio.playbackRate = change.rate;
    if (audio.paused) {
      const target = Math.max(0, change.input + Math.max(0, now + this.latencySec - change.output) * change.rate);
      if (Math.abs(audio.currentTime - target) > 0.02) audio.currentTime = target;
      const asked = this.ctx.currentTime;
      const started = () => {
        // How long the element took to move: next start leads by that much.
        const took = this.ctx.currentTime - asked;
        if (took > 0 && took < 1) this.latencySec = this.latencySec * 0.5 + took * 0.5;
      };
      audio.addEventListener('playing', started, { once: true });
      void audio.play().catch(() => {});
    }
  }

  /** Stop following the outgoing deck (it has gone); the clock alone leads from here. */
  unfollow(): void {
    this.leader = null;
  }

  /** The AudioContext time this deck's element is actually at, per its timeline; null when it isn't playing to one. */
  private actualCtxTime(now: number): number | null {
    if (this.audio.paused || this.audio.seeking) return null;
    return this.timeline.ctxTimeAt(this.audio.currentTime, now - 10);
  }

  private servo(now: number) {
    const audio = this.audio;
    // Following the outgoing deck: read this timeline where that element really is.
    const at = this.leader?.actualCtxTime(now) ?? now;
    const expected = this.timeline.trackTimeAt(at);
    const rate = this.timeline.rateAt(at);
    if (expected === null || !this.timeline.activeAt(at) || audio.paused || audio.seeking || now < this.seekingUntil) return;
    const error = audio.currentTime - expected;
    if (Math.abs(error) > SEEK_ERROR_SEC) {
      audio.currentTime = expected + this.latencySec * rate;
      this.seekingUntil = now + 0.3;
      this.errors = [];
      return;
    }
    // currentTime moves in audio-callback steps; a short median keeps the
    // nudge from chasing that jitter.
    this.errors.push(error);
    if (this.errors.length > 5) this.errors.shift();
    const smoothed = [...this.errors].sort((a, b) => a - b)[this.errors.length >> 1]!;
    // While the deck is still quiet (an incoming track's first bars) it may
    // be pulled harder: nobody hears a 10% nudge at -20 dB.
    const limit = this.gain.gain.value < 0.3 ? MAX_QUIET_NUDGE : MAX_NUDGE;
    const nudge = Math.abs(smoothed) < DEADBAND_SEC ? 0 : Math.max(-limit, Math.min(limit, -smoothed * SERVO_GAIN * (limit / MAX_NUDGE)));
    const want = rate * (1 + nudge);
    if (Math.abs(audio.playbackRate - want) > 0.0015) audio.playbackRate = want;
  }

  get isPlaying(): boolean {
    return !this.audio.paused;
  }

  /** Expected track position right now per the timeline (model truth). */
  get expectedTrackTime(): number | null {
    return this.timeline.trackTimeAt(this.ctx.currentTime);
  }

  /** Reported track position from the element (measured truth). */
  get reportedTrackTime(): number {
    return this.audio.currentTime;
  }

  setVolume(v: number): void {
    try {
      this.gain.gain.setTargetAtTime(v, this.ctx.currentTime, 0.02);
    } catch {
      // param is locked by an active setValueCurveAtTime (mid-transition) — ignore
    }
  }
}
