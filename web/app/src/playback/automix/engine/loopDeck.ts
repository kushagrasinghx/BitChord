// Adapted from AutoMix's src/lib/engine/deck.ts (https://github.com/heydavidf/automix),
// Copyright (C) 2026 David Finnegan. AGPL-3.0-only: see ../LICENSE and ../NOTICE.md.
//
// BitChord: AutoMix's own deck (signalsmith-stretch over decoded audio), used
// for the loop mix only. It plays a decoded region of a track, so it can loop
// whole bars sample-accurately and change tempo with the pitch kept, over any
// ratio, which an <audio> element can't.
import SignalsmithStretch, { type SignalsmithStretchNode } from 'signalsmith-stretch';
import { collectGarbage } from '@/lib/gc';
import { ThreeBandEQ } from './eq';
import type { PcmRegion } from './loopMix';

/** One scheduled change, in the track's own seconds (not the region's). */
export interface LoopChange {
  output: number;
  input: number;
  rate: number;
  active: boolean;
  /** Track seconds; null for no loop. */
  loop: [number, number] | null;
}

/**
 * The worklet keeps only one pending future change (each schedule() prunes
 * everything after "now"), so changes wait here and go out just before
 * their time, as in AutoMix's Deck. Beats are at least 0.33 s apart at
 * 180 BPM; this is comfortably under that.
 */
const POST_AHEAD_SEC = 0.25;
const DISPATCH_MS = 20;

export class LoopDeck {
  readonly eq: ThreeBandEQ;
  readonly gain: GainNode;
  readonly sweep: { highpass: BiquadFilterNode; lowpass: BiquadFilterNode };
  /** Track seconds the loaded region starts at. */
  regionStart = 0;
  regionEnd = 0;
  loadedKey: string | null = null;
  latencySec = 0.12;
  private pending: LoopChange[] = [];
  private timer: ReturnType<typeof setInterval> | null = null;

  private constructor(
    readonly ctx: AudioContext,
    readonly stretch: SignalsmithStretchNode,
    destination: AudioNode,
  ) {
    this.eq = new ThreeBandEQ(ctx);
    this.sweep = {
      highpass: new BiquadFilterNode(ctx, { type: 'highpass', frequency: 20, Q: 0.7 }),
      lowpass: new BiquadFilterNode(ctx, { type: 'lowpass', frequency: 20000, Q: 0.7 }),
    };
    this.gain = new GainNode(ctx, { gain: 0 });
    stretch.connect(this.eq.input);
    // AutoMix's: keeps inputTime current (the default leaves it stale)
    stretch.setUpdateInterval(0.05);
    this.eq.output.connect(this.sweep.highpass).connect(this.sweep.lowpass).connect(this.gain).connect(destination);
  }

  static async create(ctx: AudioContext, destination: AudioNode): Promise<LoopDeck> {
    // NB (AutoMix): default node options only; numberOfInputs: 0 silently kills the processor.
    const stretch = await SignalsmithStretch(ctx);
    const deck = new LoopDeck(ctx, stretch, destination);
    deck.latencySec = await stretch.latency();
    return deck;
  }

  /** Loads a region of a decoded track (loopMix.regionOf). Its channels are handed to the worklet: don't read them after. */
  async load(key: string, region: PcmRegion): Promise<void> {
    this.stop();
    await this.stretch.dropBuffers();
    const channels = region.channels.length === 1 ? [region.channels[0]!, region.channels[0]!.slice()] : region.channels;
    // read before the hand-over, which empties them here
    const lengthSec = channels[0]!.length / region.sampleRate;
    await this.stretch.addBuffers(channels, channels.map((c) => c.buffer));
    this.regionStart = region.startSec;
    this.regionEnd = region.startSec + lengthSec;
    this.loadedKey = key;
  }

  /** Stops and lets go of the loaded region: a mix's two regions are ~60 MB until the next one. */
  unload(): void {
    this.stop();
    this.loadedKey = null;
    // the worklet hands the region back to this thread, where it's garbage
    void this.stretch.dropBuffers().then(collectGarbage);
  }

  /** Queues changes (sorted by output); each is posted just before it's due. */
  schedule(changes: LoopChange[]): void {
    this.pending.push(...changes);
    this.pending.sort((a, b) => a.output - b.output);
    this.timer ??= setInterval(() => this.dispatch(), DISPATCH_MS);
    this.dispatch();
  }

  private dispatch() {
    const now = this.ctx.currentTime;
    while (this.pending.length && this.pending[0]!.output - now < POST_AHEAD_SEC) {
      const c = this.pending.shift()!;
      const r = this.regionStart;
      this.stretch.schedule({
        output: c.output,
        input: c.input - r,
        rate: c.rate,
        active: c.active,
        loopStart: c.loop ? c.loop[0] - r : 0,
        loopEnd: c.loop ? c.loop[1] - r : 0,
      });
    }
    if (!this.pending.length && this.timer !== null) {
      clearInterval(this.timer);
      this.timer = null;
    }
  }

  /** Stops now and forgets everything queued; filters, EQ and gain back to rest. */
  stop(): void {
    this.pending = [];
    if (this.timer !== null) clearInterval(this.timer);
    this.timer = null;
    this.stretch.schedule({ active: false });
    const now = this.ctx.currentTime;
    this.gain.gain.cancelScheduledValues(now);
    this.gain.gain.setValueAtTime(0, now);
    this.eq.reset();
    for (const [param, open] of [
      [this.sweep.highpass.frequency, 20],
      [this.sweep.lowpass.frequency, 20000],
    ] as const) {
      param.cancelScheduledValues(now);
      param.setValueAtTime(open, now);
    }
  }

  /** Track seconds the worklet reports it's at (~50 ms granularity). */
  get inputTime(): number {
    return this.stretch.inputTime + this.regionStart;
  }
}
