import { describe, expect, it, vi } from 'vitest';
vi.mock('signalsmith-stretch', () => ({ default: vi.fn() }));
import type { TrackAnalysis } from '../analysis/types';
import type { LoopChange, LoopDeck } from './loopDeck';
import { LOOP_BARS, mixBeatPhaseAt, mixPositionAt, planLoopMix, scheduleLoopMix, sungShare, sungSpansFromLines } from './loopMix';

/** Synced lyrics with nothing sung in the searched stretches: every loop is clear. */
const CLEAR = { out: [] as [number, number][], in: [] as [number, number][] };

const SR = 1000;

/** An AudioBuffer stand-in: constant loudness except where [quiet] says. */
function buffer(durationSec: number, quiet: [number, number][] = []): AudioBuffer {
  const data = new Float32Array(Math.round(durationSec * SR)).map((_, i) => {
    const t = i / SR;
    return quiet.some(([a, b]) => t >= a && t < b) ? 0.001 : 0.3;
  });
  return { duration: durationSec, sampleRate: SR, length: data.length, numberOfChannels: 1, getChannelData: () => data } as unknown as AudioBuffer;
}

function analysis(key: string, bpm: number, durationSec: number, vocals: TrackAnalysis['vocals'] = []): TrackAnalysis {
  return {
    fileKey: key,
    path: '',
    tags: { title: key, artist: '' },
    durationSec,
    bpm,
    beatIntervalSec: 60 / bpm,
    firstDownbeatSec: 0.5,
    gridConfidence: 0.8,
    camelot: null,
    keyName: null,
    keyConfidence: 0,
    energy: 0.5,
    energyCurve: [],
    introEndSec: 0,
    outroStartSec: durationSec,
    analysisVersion: 5,
    vocals,
  };
}

/** A vocal window that is [level] everywhere except [clean] spans. */
function vocals(from: number, to: number, level: number, clean: [number, number][] = []) {
  const fps = 10;
  const curve = Array.from({ length: Math.round((to - from) * fps) }, (_, i) => {
    const t = from + i / fps;
    return clean.some(([a, b]) => t >= a && t < b) ? 0.05 : level;
  });
  return { startSec: from, fps, curve };
}

function fakeDeck() {
  const changes: LoopChange[] = [];
  const param = () => ({ cancelScheduledValues() {}, setValueAtTime() {}, linearRampToValueAtTime() {}, exponentialRampToValueAtTime() {} });
  const deck = {
    changes,
    schedule: (c: LoopChange[]) => changes.push(...c),
    gain: { gain: param() },
    sweep: { highpass: { frequency: param() }, lowpass: { frequency: param() } },
    eq: { setGainAt() {}, rampGain() {} },
  };
  return deck as unknown as LoopDeck & { changes: LoopChange[] };
}

describe('planLoopMix', () => {
  it('loops whole bars, the outgoing near its end and the incoming at its start', () => {
    const out = analysis('out', 124, 200);
    const inn = analysis('in', 124, 180);
    const plan = planLoopMix(out, buffer(200), inn, buffer(180), 0, CLEAR)!;
    const bar = (60 / 124) * 4;
    for (const pick of [plan.out, plan.in]) {
      const bars = (pick.loop[0] - 0.5) / bar;
      expect(Math.abs(bars - Math.round(bars))).toBeLessThan(1e-9);
      expect(pick.loop[1] - pick.loop[0]).toBeCloseTo(LOOP_BARS * bar, 9);
    }
    expect(plan.out.loop[1]).toBeLessThanOrEqual(199.5);
    expect(plan.out.loop[0]).toBeGreaterThan(200 - 60);
    expect(plan.in.loop[0]).toBeCloseTo(0.5, 9);
  });

  it('prefers bars the vocal mask calls instrumental', () => {
    // sung throughout except 150-170 s on the way out and 20-35 s on the way in
    const out = analysis('out', 120, 200, [vocals(130, 200, 0.8, [[150, 170]])]);
    const inn = analysis('in', 120, 180, [vocals(0, 45, 0.8, [[20, 35]])]);
    const plan = planLoopMix(out, buffer(200), inn, buffer(180), 0)!;
    expect(plan.out.loop[0]).toBeGreaterThanOrEqual(150);
    // up to 10% sung is still instrumental (a breath, an ad-lib): 0.8 s of an 8 s loop
    expect(plan.out.loop[1]).toBeLessThanOrEqual(170 + 0.8);
    expect(plan.out.vocal).toBeLessThanOrEqual(0.1);
    expect(plan.in.loop[0]).toBeGreaterThanOrEqual(20);
    expect(plan.in.loop[1]).toBeLessThanOrEqual(35 + 0.8);
  });

  it('skips a quiet fade-out for a loop that grooves', () => {
    const out = analysis('out', 120, 200);
    const plan = planLoopMix(out, buffer(200, [[185, 200]]), analysis('in', 120, 180), buffer(180), 0, CLEAR)!;
    expect(plan.out.loop[1]).toBeLessThanOrEqual(185);
  });

  it('ramps any tempo gap, folding to double or half time when closer', () => {
    const near = planLoopMix(analysis('o', 120, 200), buffer(200), analysis('i', 134, 180), buffer(180), 0, CLEAR)!;
    expect(near.fold).toBe(1);
    expect(near.rampBars).toBe(Math.ceil((134 - 120) / 2.5));
    // 111 -> 169 is 58 BPM up, but only 26.5 down to 169's half time: that's the ramp taken
    const wide = planLoopMix(analysis('o', 111, 200), buffer(200), analysis('i', 169, 180), buffer(180), 0, CLEAR)!;
    expect(wide.fold).toBe(2);
    expect(wide.rampBars).toBe(Math.ceil((111 - 169 / 2) / 2.5));
    const slow = planLoopMix(analysis('o', 170, 200), buffer(200), analysis('i', 80, 180), buffer(180), 0, CLEAR)!;
    expect(slow.fold).toBe(0.5);
    // the incoming loop can only let go at its own boundary
    for (const p of [near, wide, slow]) expect(p.totalBars % (LOOP_BARS / p.fold)).toBe(0);
  });
});

describe('planLoopMix on model beats', () => {
  it('cuts each loop from a detected beat 1 to the detected beat 1 four bars on, tempo drift and all', () => {
    // a live-played song: the beat stretches 0.5% over the minute, so a constant grid would miss
    const beats: number[] = [];
    for (let t = 140, beat = 60 / 120; t < 199.5; t += beat, beat *= 1.0002) beats.push(t);
    const downbeats = beats.filter((_, i) => i % 4 === 0);
    const out = { ...analysis('out', 120, 200), tailBeats: { beats, downbeats, bpm: 120, confidence: 0.9 } };
    const plan = planLoopMix(out, buffer(200), analysis('in', 120, 180), buffer(180), 0, CLEAR)!;
    expect(downbeats).toContain(plan.out.loop[0]);
    expect(downbeats).toContain(plan.out.loop[1]);
    expect(downbeats.indexOf(plan.out.loop[1]) - downbeats.indexOf(plan.out.loop[0])).toBe(LOOP_BARS);
    // the loop's tempo is its own length over 16 beats
    expect(plan.out.beatSec).toBeCloseTo((plan.out.loop[1] - plan.out.loop[0]) / 16, 12);
  });

  it('never cuts on a downbeat the model put on the wrong beat', () => {
    const beat = 0.5;
    // every bar line at 140 + 2k s, except the one at 190 s, which the model put two beats late
    const downbeats = Array.from({ length: 30 }, (_, i) => 140 + i * 4 * beat).map((t) => (Math.abs(t - 190) < 0.01 ? 191 : t));
    const out = { ...analysis('out', 120, 200), tailBeats: { beats: downbeats, downbeats, bpm: 120, confidence: 0.9 } };
    const plan = planLoopMix(out, buffer(200), analysis('in', 120, 180), buffer(180), 0, CLEAR)!;
    expect(plan.out.loop).not.toContain(191);
    expect(plan.out.loop[1] - plan.out.loop[0]).toBeCloseTo(LOOP_BARS * 4 * beat, 9);
  });
});

describe('scheduleLoopMix', () => {
  it('posts every beat exactly where the last left off, wrapping only at loop ends', () => {
    const plan = planLoopMix(analysis('o', 111, 200), buffer(200), analysis('i', 169, 180), buffer(180), 0, CLEAR)!;
    const outDeck = fakeDeck();
    const inDeck = fakeDeck();
    const times = scheduleLoopMix(plan, outDeck, inDeck, 100);
    for (const [deck, loop] of [
      [outDeck, plan.out.loop],
      [inDeck, plan.in.loop],
    ] as const) {
      const looped = deck.changes.filter((c) => c.loop);
      for (let i = 1; i < looped.length; i++) {
        const prev = looped[i - 1]!;
        const cur = looped[i]!;
        let expected = prev.input + prev.rate * (cur.output - prev.output);
        if (expected >= loop[1] - 1e-9) expected -= loop[1] - loop[0];
        expect(cur.input).toBeCloseTo(expected, 6);
      }
    }
    // the tempo arrives: the incoming song ends the ramp at its own speed and lets go from its loop's end
    const release = inDeck.changes.find((c) => c.output === times.releaseAt && !c.loop)!;
    expect(release.input).toBeCloseTo(plan.in.loop[1], 9);
    expect(release.rate).toBe(1);
    const lastLooped = inDeck.changes.filter((c) => c.loop).at(-1)!;
    expect(lastLooped.rate).toBeCloseTo(1, 9);
    expect(times.elementTrackTime).toBeCloseTo(plan.in.loop[1] + (times.elementAt - times.releaseAt), 9);
    expect(times.handoffAt).toBeGreaterThan(100);
    expect(times.handoffAt).toBeLessThan(times.releaseAt);
  });
});

describe('sungShare', () => {
  it('counts the frames over the phone threshold (0.6), and is null where the mask does not reach', () => {
    const w = [vocals(0, 10, 0.8, [[0, 5]])];
    expect(sungShare(w, 0, 10)).toBeCloseTo(0.5, 9);
    expect(sungShare(w, 0, 5)).toBe(0);
    expect(sungShare(w, 8, 14)).toBeNull();
  });
});

describe('mixPositionAt', () => {
  const plan = planLoopMix(analysis('o', 111, 200), buffer(200), analysis('i', 169, 180), buffer(180), 0, CLEAR)!;
  const times = scheduleLoopMix(plan, fakeDeck(), fakeDeck(), 100);

  it('is nothing before the mix, and the loop start at its first beat', () => {
    expect(mixPositionAt(plan, times, 'out', 99.9)).toBeNull();
    expect(mixPositionAt(plan, times, 'out', 100)).toBeCloseTo(plan.out.loop[0], 9);
    expect(mixPositionAt(plan, times, 'in', 100)).toBeCloseTo(plan.in.loop[0], 9);
  });

  it('stays inside each loop, wrapping back to its start', () => {
    let sawWrap = false;
    let last = -1;
    for (let t = 100; t < times.releaseAt; t += 0.05) {
      const out = mixPositionAt(plan, times, 'out', t)!;
      expect(out).toBeGreaterThanOrEqual(plan.out.loop[0] - 1e-9);
      expect(out).toBeLessThanOrEqual(plan.out.loop[1] + 1e-9);
      if (out < last - 1) sawWrap = true;
      last = out;
      const inn = mixPositionAt(plan, times, 'in', t)!;
      expect(inn).toBeGreaterThanOrEqual(plan.in.loop[0] - 1e-9);
      expect(inn).toBeLessThanOrEqual(plan.in.loop[1] + 1e-9);
    }
    expect(sawWrap).toBe(true);
  });

  it('lets the incoming song go on from its loop end in real time, and the outgoing one stop', () => {
    expect(mixPositionAt(plan, times, 'out', times.releaseAt + 0.1)).toBeNull();
    expect(mixPositionAt(plan, times, 'in', times.releaseAt)).toBeCloseTo(plan.in.loop[1], 9);
    expect(mixPositionAt(plan, times, 'in', times.elementAt)).toBeCloseTo(times.elementTrackTime, 9);
  });

  it('puts the beat phase at 0 on every scheduled beat', () => {
    expect(mixBeatPhaseAt(times, times.beatTimes[5]!)).toBeCloseTo(0, 9);
    const mid = (times.beatTimes[5]! + times.beatTimes[6]!) / 2;
    expect(mixBeatPhaseAt(times, mid)).toBeCloseTo(0.5, 9);
    expect(mixBeatPhaseAt(times, 99)).toBeNull();
  });
});

describe('loops never hold a sung word', () => {
  const beat = 0.5; // 120 BPM, bars of 2 s from 0.5 s
  it('skips a loop a synced line overlaps, and one a line is still ending into', () => {
    // out song: sung until 172.2 s, then a line from 189 s; the latest clean 4 bars end before 189
    const sung: [number, number][] = [[150, 172.2], [189, 199]];
    const plan = planLoopMix(analysis('out', 120, 200), buffer(200), analysis('in', 120, 180), buffer(180), 0, { out: sung, in: [] })!;
    const [a, b] = plan.out.loop;
    for (const [s0, s1] of sung) expect(a - 0.3 >= s1 || b <= s0).toBe(true);
    expect(plan.out.checkedBy).toBe('lyrics');
  });

  it('calls a soft verse sung (mask 0.3, as Afsos measured)', () => {
    const out = analysis('out', 120, 200, [vocals(130, 200, 0.3, [[176, 186]])]);
    const plan = planLoopMix(out, buffer(200), analysis('in', 120, 180), buffer(180), 0, { in: [] })!;
    expect(plan.out.loop[0]).toBeGreaterThanOrEqual(176.5);
    expect(plan.out.loop[1]).toBeLessThanOrEqual(186);
    expect(plan.out.checkedBy).toBe('mask');
  });

  it('falls back to a 1-bar loop when only one clean bar exists', () => {
    // sung everywhere in the search but 180.5-182.5 (one bar)
    const sung: [number, number][] = [[100, 180.2], [182.6, 199]];
    const plan = planLoopMix(analysis('out', 120, 200), buffer(200), analysis('in', 120, 180), buffer(180), 0, { out: sung, in: [] })!;
    expect(plan.out.bars).toBe(1);
    expect(plan.out.loop[1] - plan.out.loop[0]).toBeCloseTo(4 * beat, 9);
  });

  it('makes no loop mix at all when no bar is instrumental', () => {
    const plan = planLoopMix(analysis('out', 120, 200), buffer(200), analysis('in', 120, 180), buffer(180), 0, { out: [[0, 200]], in: [] });
    expect(plan).toBeNull();
  });

  it('turns synced lines into padded spans, a line without an end sung until the next', () => {
    const spans = sungSpansFromLines([
      { timeMs: 10_000, endMs: 12_000, blank: false },
      { timeMs: 13_000, endMs: null, blank: false },
      { timeMs: 16_000, endMs: null, blank: true },
      { timeMs: 20_000, endMs: 21_000, blank: false },
    ]);
    expect(spans[0]![0]).toBeCloseTo(9.85, 9);
    expect(spans[0]![1]).toBeCloseTo(12.35, 9);
    expect(spans[1]![1]).toBeCloseTo(16.35, 9);
    expect(spans).toHaveLength(3);
  });
});

describe('music videos', () => {
  it('get no loop mix on either side: their lyrics are the album cut', () => {
    const video = { ...analysis('out', 120, 200), musicVideo: true };
    expect(planLoopMix(video, buffer(200), analysis('in', 120, 180), buffer(180), 0, CLEAR)).toBeNull();
    const videoIn = { ...analysis('in', 120, 180), musicVideo: true };
    expect(planLoopMix(analysis('out', 120, 200), buffer(200), videoIn, buffer(180), 0, CLEAR)).toBeNull();
  });
});
