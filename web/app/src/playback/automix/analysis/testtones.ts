// From AutoMix (https://github.com/heydavidf/automix), Copyright (C) 2026 David Finnegan.
// AGPL-3.0-only: see ./LICENSE and ./NOTICE.md in playback/automix.

/**
 * Synthesized test loops for beatmatch verification without real music:
 * kick on quarters, hats on offbeat 8ths, a downbeat blip on beat 1 of each
 * bar, and a sustained bass tone. Deterministic and exactly on-grid.
 */
export interface TestLoopSpec {
  bpm: number;
  bars: number;
  toneHz: number;
}

export function makeTestLoop(ctx: BaseAudioContext, { bpm, bars, toneHz }: TestLoopSpec): AudioBuffer {
  const sr = ctx.sampleRate;
  const beatSec = 60 / bpm;
  const totalSec = bars * 4 * beatSec;
  const length = Math.round(totalSec * sr);
  const buffer = ctx.createBuffer(2, length, sr);
  const data = buffer.getChannelData(0);

  for (let beat = 0; beat < bars * 4; beat++) {
    const t0 = beat * beatSec;
    addKick(data, sr, t0);
    addHat(data, sr, t0 + beatSec / 2);
    if (beat % 4 === 0) addBlip(data, sr, t0, 880);
  }

  // Bass tone, gated per bar so phrase position is audible.
  const barSec = 4 * beatSec;
  for (let i = 0; i < length; i++) {
    const t = i / sr;
    const barPos = (t % barSec) / barSec;
    const gate = barPos < 0.75 ? 1 : 0;
    data[i]! += 0.12 * gate * Math.sin(2 * Math.PI * toneHz * t);
    data[i] = Math.max(-1, Math.min(1, data[i]!));
  }

  buffer.copyToChannel(data, 1);
  return buffer;
}

function addKick(data: Float32Array, sr: number, atSec: number): void {
  const dur = 0.09;
  const start = Math.round(atSec * sr);
  let phase = 0;
  for (let i = 0; i < dur * sr && start + i < data.length; i++) {
    const t = i / sr;
    const freq = 120 * Math.exp(-t * 35) + 45;
    phase += (2 * Math.PI * freq) / sr;
    data[start + i]! += 0.8 * Math.exp(-t * 30) * Math.sin(phase);
  }
}

function addHat(data: Float32Array, sr: number, atSec: number): void {
  const dur = 0.025;
  const start = Math.round(atSec * sr);
  let seed = 12345 + start;
  for (let i = 0; i < dur * sr && start + i < data.length; i++) {
    seed = (seed * 1103515245 + 12345) & 0x7fffffff;
    const noise = (seed / 0x40000000 - 1) * 0.5;
    data[start + i]! += noise * 0.25 * Math.exp(-(i / sr) * 120);
  }
}

function addBlip(data: Float32Array, sr: number, atSec: number, hz: number): void {
  const dur = 0.04;
  const start = Math.round(atSec * sr);
  for (let i = 0; i < dur * sr && start + i < data.length; i++) {
    const t = i / sr;
    data[start + i]! += 0.3 * Math.exp(-t * 80) * Math.sin(2 * Math.PI * hz * t);
  }
}
