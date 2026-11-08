// From AutoMix (https://github.com/heydavidf/automix), Copyright (C) 2026 David Finnegan.
// AGPL-3.0-only: see ./LICENSE and ./NOTICE.md in playback/automix.

export type Band = 'low' | 'mid' | 'high';

/** DJ-mixer-style 3-band EQ: lowshelf 250 Hz, peaking 1 kHz, highshelf 4 kHz. */
export class ThreeBandEQ {
  readonly input: BiquadFilterNode;
  private readonly mid: BiquadFilterNode;
  readonly output: BiquadFilterNode;

  constructor(private ctx: BaseAudioContext) {
    this.input = new BiquadFilterNode(ctx, { type: 'lowshelf', frequency: 250 });
    this.mid = new BiquadFilterNode(ctx, { type: 'peaking', frequency: 1000, Q: 0.6 });
    this.output = new BiquadFilterNode(ctx, { type: 'highshelf', frequency: 4000 });
    this.input.connect(this.mid).connect(this.output);
  }

  private param(band: Band): AudioParam {
    return band === 'low' ? this.input.gain : band === 'mid' ? this.mid.gain : this.output.gain;
  }

  /** Immediate set (UI sliders). */
  setGain(band: Band, dB: number): void {
    try {
      this.param(band).setTargetAtTime(dB, this.ctx.currentTime, 0.02);
    } catch {
      // param is locked by an active automation curve — ignore
    }
  }

  getGain(band: Band): number {
    return this.param(band).value;
  }

  /** Scheduled hard set at a future time (transition automation). */
  setGainAt(band: Band, dB: number, ctxTime: number): void {
    this.param(band).setValueAtTime(dB, ctxTime);
  }

  /**
   * Scheduled linear ramp with an explicit anchor, so ramps compose with
   * other future-scheduled values (param.value is only "now", not "then").
   */
  rampGain(band: Band, fromDb: number, toDb: number, startCtxTime: number, durationSec: number): void {
    const p = this.param(band);
    p.setValueAtTime(fromDb, startCtxTime);
    p.linearRampToValueAtTime(toDb, startCtxTime + durationSec);
  }

  reset(): void {
    for (const band of ['low', 'mid', 'high'] as Band[]) {
      this.param(band).cancelScheduledValues(this.ctx.currentTime);
      this.param(band).setValueAtTime(0, this.ctx.currentTime);
    }
  }
}
