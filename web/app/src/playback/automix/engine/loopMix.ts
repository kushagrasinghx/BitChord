/*
 * BitChord: the loop mix, a DJ transition on AutoMix's grids and stretch
 * decks. AGPL-3.0-only with the rest of playback/automix (see ../NOTICE.md).
 *
 * The outgoing song hands over from its <audio> element to a stretch deck at
 * the start of an instrumental loop near its end and loops it. The incoming
 * song comes in on its own instrumental loop, bass cut, at the outgoing tempo.
 * One master tempo then ramps from the outgoing song's BPM to the incoming
 * one's, both decks following it beat by beat, so they stay locked however
 * far apart they started. The bass swaps, the outgoing loop drains away
 * through a rising high-pass, the incoming loop lets go into the rest of its
 * song at its own tempo, and its <audio> element takes over two bars later.
 *
 * Everything is placed on one beat clock: beat k of the mix (in outgoing
 * beats) is at beatTimes[k], and each deck's position at beat k is worked
 * out from the loop it is in, so nothing drifts and every change can be
 * posted with an exact input position.
 */
import type { BeatSide, TrackAnalysis, VocalWindow } from '../analysis/types';
import type { LoopChange, LoopDeck } from './loopDeck';

/** The longest loop, in each song's own bars; shorter ones are tried when no 4 clean bars exist. */
export const LOOP_BARS = 4;
/** Loop lengths tried, longest first: a 1-bar instrumental loop beats a 4-bar one with a word in it. */
const LOOP_LENGTHS = [4, 2, 1] as const;
/** Bars the incoming loop takes to come in at the outgoing tempo. */
const ENTER_BARS = 4;
/** Bars the outgoing loop takes to drain away once the tempo has arrived. */
const EXIT_BARS = 2;
/** Bars after the incoming loop lets go before its <audio> element takes over. */
const SETTLE_BARS = 2;
/** The fastest the master tempo moves: past this a ramp is heard as a lurch, not a mix. */
const MAX_BPM_PER_BAR = 2.5;
const MIN_RAMP_BARS = 2;
const MAX_RAMP_BARS = 24;
/**
 * A vocal-mask frame above this is sung. Measured on real songs (2026-10-23):
 * a clear chorus reads 0.57-0.71, a soft verse over guitar 0.2-0.4, an
 * instrumental break 0.02-0.15. The phone's 0.6 calls a soft verse
 * instrumental, which is how a sung line got looped.
 */
const VOCAL_ACTIVE = 0.25;
/** Share of sung mask frames a loop may still have (a breath), and the longest sung run it may hold. */
const MAX_SUNG_SHARE = 0.03;
const MAX_SUNG_RUN_SEC = 0.2;
/**
 * Clear time before a loop: a line still ending as the loop starts would be
 * cut and repeated. From lyrics (which time each line's end) a short guard;
 * from the mask, a whole beat.
 */
const LYRIC_GUARD_SEC = 0.3;
/** A synced line is padded this much at its start (lead-in breath) and its end (the voice's tail and reverb). */
const LYRIC_LEAD_SEC = 0.15;
const LYRIC_TAIL_SEC = 0.35;
/** A loop quieter than this share of the stretch around it is a breakdown or a fade, not a groove. */
const MIN_LOUDNESS = 0.6;
/** How far from the end the outgoing loop is looked for, and from the start the incoming one. */
export const OUT_SEARCH_SEC = 60;
export const IN_SEARCH_SEC = 45;
/** Seconds of each song decoded for the decks: the search span plus room around it. */
export const OUT_REGION_SEC = OUT_SEARCH_SEC + 5;
export const IN_REGION_SEC = IN_SEARCH_SEC + 40;
/** Element <-> deck handovers: identical audio crossed this quickly is heard as one. */
const HANDOVER_SEC = 0.025;
const BASS_CUT_DB = -18;

/** Where a song is sung, in its own seconds, from its synced lyrics. */
export type SungSpans = [number, number][];

export interface LoopPick {
  key: string;
  /** Track seconds. */
  loop: [number, number];
  /** The loop's length in this song's own bars: 4, or 2 or 1 when no 4 clean bars were found. */
  bars: number;
  /** Track seconds per beat on this song's grid. */
  beatSec: number;
  bpm: number;
  /** Share of the loop that is sung (0-1); 0 when the lyrics vouch for it; null when nothing measured it. */
  vocal: number | null;
  /** What said the loop is instrumental: the synced lyrics, or the vocal mask. */
  checkedBy: 'lyrics' | 'mask';
}

export interface LoopMixPlan {
  out: LoopPick;
  in: LoopPick;
  /** Incoming beats per outgoing beat: 1, or 2 / 0.5 when double or half time is closer. */
  fold: number;
  enterBars: number;
  rampBars: number;
  exitBars: number;
  /** Outgoing bars from the outgoing loop's start to the incoming loop's release. */
  totalBars: number;
}

/** What a plan becomes once it's on the clock. */
export interface LoopMixTimes {
  /** The outgoing element reaches its loop start; the decks take over. */
  t0: number;
  /** Beat k of the mix, in outgoing beats. */
  beatTimes: number[];
  /** Middle of the ramp: the app moves to the incoming song. */
  handoffAt: number;
  /** The incoming loop lets go. */
  releaseAt: number;
  /** The incoming <audio> element takes over, at this track position. */
  elementAt: number;
  elementTrackTime: number;
}

// ---- Choosing the loops ------------------------------------------------------------------

/**
 * The bars to loop on each side, or null when there is no loop mix to make:
 * either song has no usable grid there, or no instrumental bars at all. A
 * sung word is never looped; the pair then gets a blend or a filter mix.
 * [buffers] are the decoded songs, for loudness; [earliestOutSec] is the
 * earliest the outgoing loop may start (the playhead plus setup time);
 * [sung] is each song's sung lines from its synced lyrics, when it has them.
 */
export function planLoopMix(
  out: TrackAnalysis,
  outBuffer: AudioBuffer,
  inn: TrackAnalysis,
  inBuffer: AudioBuffer,
  earliestOutSec: number,
  sung: { out?: SungSpans | null; in?: SungSpans | null } = {},
): LoopMixPlan | null {
  if (!out.beatIntervalSec || out.firstDownbeatSec === null || !out.bpm) return null;
  if (!inn.beatIntervalSec || inn.firstDownbeatSec === null || !inn.bpm) return null;
  // A music video's audio rarely matches its lyrics' timing (they are the
  // album cut's), so nothing can vouch for its bars: no loop on either side.
  if (out.musicVideo || inn.musicVideo) return null;
  const outBar = out.beatIntervalSec * 4;
  const inBar = inn.beatIntervalSec * 4;

  // outgoing: the latest instrumental loop that ends before the song does
  const outEnd = Math.min(out.durationSec - 0.5, outBuffer.duration - 0.1);
  const outFirst = Math.max(earliestOutSec, out.durationSec - OUT_SEARCH_SEC);
  const outPick = firstClean(
    (bars) => loopsBetween(out.tailBeats, out.firstDownbeatSec!, outBar, outFirst, outEnd, bars).reverse(),
    outBuffer,
    out.vocals ?? [],
    sung.out ?? null,
    out.beatIntervalSec,
  );
  if (outPick === null) return null;

  // incoming: the earliest instrumental loop in its opening
  const inEnd = Math.min(IN_SEARCH_SEC + LOOP_BARS * inBar, inBuffer.duration - 20);
  const inPick = firstClean(
    (bars) => loopsBetween(inn.headBeats, inn.firstDownbeatSec!, inBar, 0, inEnd, bars),
    inBuffer,
    inn.vocals ?? [],
    sung.in ?? null,
    inn.beatIntervalSec,
  );
  if (inPick === null) return null;
  // a loop's own length is its tempo: its beats exactly, bar line to bar line
  const outBeat = (outPick.end - outPick.start) / (outPick.bars * 4);
  const inBeat = (inPick.end - inPick.start) / (inPick.bars * 4);
  const outBpm = 60 / outBeat;
  const inBpm = 60 / inBeat;

  const fold = [1, 2, 0.5].reduce((best, f) =>
    Math.abs(Math.log(inBpm / f / outBpm)) < Math.abs(Math.log(inBpm / best / outBpm)) ? f : best,
  );
  const target = inBpm / fold;
  const rampBars = Math.min(MAX_RAMP_BARS, Math.max(MIN_RAMP_BARS, Math.ceil(Math.abs(target - outBpm) / MAX_BPM_PER_BAR)));
  // the incoming loop lets go only at its own loop boundary: round the mix up to whole incoming loops
  const inLoopOutBars = inPick.bars / fold;
  const totalBars = Math.ceil((ENTER_BARS + rampBars + EXIT_BARS) / inLoopOutBars) * inLoopOutBars;

  const pick = (key: string, p: Clean, beatSec: number, bpm: number): LoopPick => ({
    key,
    loop: [p.start, p.end],
    bars: p.bars,
    beatSec,
    bpm,
    vocal: p.vocal,
    checkedBy: p.checkedBy,
  });
  return {
    out: pick(out.fileKey, outPick, outBeat, outBpm),
    in: pick(inn.fileKey, inPick, inBeat, inBpm),
    fold,
    enterBars: ENTER_BARS,
    rampBars,
    exitBars: totalBars - ENTER_BARS - rampBars,
    totalBars,
  };
}

type Span = { start: number; end: number };
type Clean = Span & { bars: number; vocal: number | null; checkedBy: 'lyrics' | 'mask' };

/**
 * [bars]-bar loops lying in [from, to]. With the model's downbeats, each loop
 * runs from a detected beat 1 to the detected beat 1 [bars] bars on, so its
 * seam is the music's own bar line however the tempo wanders. Without them
 * (or where the model found too few), AutoMix's constant grid.
 */
function loopsBetween(side: BeatSide | null | undefined, anchor: number, bar: number, from: number, to: number, bars: number): Span[] {
  const loops: Span[] = [];
  if (side && side.downbeats.length >= bars + 1) {
    const beat = 60 / side.bpm;
    const downs = side.downbeats;
    for (const start of downs) {
      if (start < from) continue;
      const target = start + bars * 4 * beat;
      const end = downs.reduce((best, d) => (Math.abs(d - target) < Math.abs(best - target) ? d : best), downs[0]!);
      // a missed or doubled downbeat in between shows as an end far from [bars] bars on
      if (Math.abs(end - target) > beat / 4 || end > to) continue;
      loops.push({ start, end });
    }
    if (loops.length) return loops;
  }
  if (to < from) return loops;
  const len = bars * bar;
  for (let t = anchor + Math.ceil((from - anchor) / bar) * bar; t + len <= to; t += bar) loops.push({ start: t, end: t + len });
  return loops;
}

/**
 * The first loop (in the order [candidates] gives) that is instrumental and
 * grooves, trying 4 bars, then 2, then 1. Null when there is none: a vocal is
 * never looped.
 *
 * Instrumental means: with synced lyrics, no sung line overlaps the loop or
 * the guard before it; without them, the vocal mask covers it and finds
 * almost nothing sung, and nothing sung in the beat before it either (a line
 * still ending as the loop starts would be cut and repeated).
 */
function firstClean(
  candidates: (bars: number) => Span[],
  buffer: AudioBuffer,
  vocals: VocalWindow[],
  sung: SungSpans | null,
  beatSec: number,
): Clean | null {
  for (const bars of LOOP_LENGTHS) {
    const loops = candidates(bars);
    if (!loops.length) continue;
    // loudness of the quietest bar against the stretch's own: no fade-out tail, no breakdown
    const barLoud = (l: Span) =>
      Math.min(...Array.from({ length: bars }, (_, b) => rms(buffer, l.start + (b * (l.end - l.start)) / bars, l.start + ((b + 1) * (l.end - l.start)) / bars)));
    const louds = loops.map(barLoud);
    const typical = [...louds].sort((a, b) => a - b)[Math.floor(louds.length * 0.6)] ?? 0;
    for (let i = 0; i < loops.length; i++) {
      const l = loops[i]!;
      if (louds[i]! < typical * MIN_LOUDNESS || louds[i]! <= 0.01) continue;
      if (sung) {
        const from = l.start - LYRIC_GUARD_SEC;
        if (sung.some(([a, b]) => a < l.end && b > from)) continue;
        return { ...l, bars, vocal: 0, checkedBy: 'lyrics' };
      }
      const m = maskStats(vocals, l.start - beatSec, l.end);
      if (!m || m.share > MAX_SUNG_SHARE || m.longestRunSec > MAX_SUNG_RUN_SEC) continue;
      return { ...l, bars, vocal: m.share, checkedBy: 'mask' };
    }
  }
  return null;
}

/** How much of [from, to) the vocal mask calls sung, and its longest sung run; null when the windows cover under 80% of it. */
export function maskStats(windows: VocalWindow[], from: number, to: number): { share: number; longestRunSec: number } | null {
  let sung = 0;
  let n = 0;
  let covered = 0;
  let run = 0;
  let longest = 0;
  let fps = 1;
  for (const w of windows) {
    const a = Math.max(0, Math.ceil((from - w.startSec) * w.fps));
    const b = Math.min(w.curve.length, Math.floor((to - w.startSec) * w.fps));
    for (let i = a; i < b; i++) {
      n++;
      if (w.curve[i]! > VOCAL_ACTIVE) {
        sung++;
        run++;
        longest = Math.max(longest, run);
      } else run = 0;
    }
    if (b > a) {
      covered += (b - a) / w.fps;
      fps = w.fps;
    }
  }
  return n && covered >= (to - from) * 0.8 ? { share: sung / n, longestRunSec: longest / fps } : null;
}

/** Share of [from, to) the vocal mask calls sung (maskStats), or null where it doesn't reach. */
export function sungShare(windows: VocalWindow[], from: number, to: number): number | null {
  return maskStats(windows, from, to)?.share ?? null;
}

/**
 * A song's sung stretches from its synced lyric lines: each from just
 * before its start to just after its end. A line with no stated end is taken
 * as sung until the next line (at most 10 s), which can only make a loop
 * pickier, never let a word through.
 */
export function sungSpansFromLines(lines: { timeMs: number; endMs: number | null; blank: boolean }[]): SungSpans {
  const spans: SungSpans = [];
  const sorted = [...lines].sort((a, b) => a.timeMs - b.timeMs);
  sorted.forEach((l, i) => {
    if (l.blank) return;
    const start = l.timeMs / 1000;
    const next = sorted.slice(i + 1).find((n) => n.timeMs > l.timeMs)?.timeMs;
    const end = l.endMs !== null && l.endMs > l.timeMs ? l.endMs / 1000 : Math.min(start + 10, (next ?? l.timeMs + 10_000) / 1000);
    spans.push([start - LYRIC_LEAD_SEC, end + LYRIC_TAIL_SEC]);
  });
  return spans;
}

function rms(buffer: AudioBuffer, from: number, to: number): number {
  const data = buffer.getChannelData(0);
  const a = Math.max(0, Math.floor(from * buffer.sampleRate));
  const b = Math.min(data.length, Math.floor(to * buffer.sampleRate));
  let ss = 0;
  let n = 0;
  for (let i = a; i < b; i += 16) {
    ss += data[i]! * data[i]!;
    n++;
  }
  return n ? Math.sqrt(ss / n) : 0;
}

// ---- Putting it on the clock -------------------------------------------------------------

/**
 * Schedules the whole mix on the two loop decks, from [t0] (the AudioContext
 * time the outgoing element reaches its loop start). Returns the times the
 * player needs: the handoff, and when and where the incoming element takes over.
 */
export function scheduleLoopMix(plan: LoopMixPlan, outDeck: LoopDeck, inDeck: LoopDeck, t0: number): LoopMixTimes {
  const { out, in: inn, fold } = plan;
  const beats = plan.totalBars * 4;
  const settleBeats = Math.round(SETTLE_BARS * 4 / fold);
  const rampFrom = plan.enterBars * 4;
  const rampTo = (plan.enterBars + plan.rampBars) * 4;
  const target = inn.bpm / fold;

  // master tempo per outgoing beat, and the time of each beat
  const tempo = (k: number) =>
    k < rampFrom ? out.bpm : k >= rampTo ? target : out.bpm + ((target - out.bpm) * (k - rampFrom + 0.5)) / (rampTo - rampFrom);
  const beatTimes = [t0];
  for (let k = 0; k < beats + settleBeats; k++) beatTimes.push(beatTimes[k]! + 60 / tempo(k));

  // positions by whole beats, so a wrap lands exactly on the loop start
  const outLoopBeats = out.bars * 4;
  const inLoopBeats = inn.bars * 4;
  const inPerBeat = fold * inn.beatSec; // incoming track seconds per outgoing beat
  const outChanges: LoopChange[] = [];
  const inChanges: LoopChange[] = [];
  for (let k = 0; k < beats; k++) {
    const t = beatTimes[k]!;
    const T = tempo(k);
    outChanges.push({
      output: t,
      input: out.loop[0] + (k % outLoopBeats) * out.beatSec,
      rate: T * out.beatSec / 60,
      active: true,
      loop: out.loop,
    });
    inChanges.push({
      output: t,
      input: inn.loop[0] + ((k * fold) % inLoopBeats) * inn.beatSec,
      rate: (T * inPerBeat) / 60,
      active: true,
      loop: inn.loop,
    });
  }
  const releaseAt = beatTimes[beats]!;
  outChanges.push({ output: releaseAt + 0.05, input: out.loop[0], rate: 1, active: false, loop: null });
  // the incoming loop lets go at its end: the song carries on from there at its own tempo
  inChanges.push({ output: releaseAt, input: inn.loop[1], rate: 1, active: true, loop: null });
  const elementAt = beatTimes[beats + settleBeats]!;
  const elementTrackTime = inn.loop[1] + (elementAt - releaseAt);
  inChanges.push({ output: elementAt + 0.1, input: elementTrackTime + 0.1, rate: 1, active: false, loop: null });
  outDeck.schedule(outChanges);
  inDeck.schedule(inChanges);

  // gains: outgoing takes over from its element, the incoming loop rises over the entry and the ramp
  const og = outDeck.gain.gain;
  const ig = inDeck.gain.gain;
  og.cancelScheduledValues(0);
  ig.cancelScheduledValues(0);
  og.setValueAtTime(0, t0 - HANDOVER_SEC);
  og.linearRampToValueAtTime(1, t0);
  const enterEnd = beatTimes[rampFrom]!;
  const rampEnd = beatTimes[rampTo]!;
  ig.setValueAtTime(0, t0);
  ig.linearRampToValueAtTime(0.55, enterEnd);
  og.setValueAtTime(1, enterEnd);
  og.linearRampToValueAtTime(0.6, rampEnd);
  ig.linearRampToValueAtTime(1, rampEnd);
  og.linearRampToValueAtTime(0, releaseAt);
  ig.setValueAtTime(1, elementAt);
  ig.linearRampToValueAtTime(0, elementAt + HANDOVER_SEC);

  // bass: the incoming loop comes in without it; they swap over one bar mid-ramp
  const mid = beatTimes[Math.round((rampFrom + rampTo) / 2)]!;
  const bar = 4 * (60 / tempo(Math.round((rampFrom + rampTo) / 2)));
  inDeck.eq.setGainAt('low', BASS_CUT_DB, t0);
  outDeck.eq.rampGain('low', 0, BASS_CUT_DB, mid - bar / 2, bar);
  inDeck.eq.rampGain('low', BASS_CUT_DB, 0, mid - bar / 2, bar);
  // the outgoing loop drains away through a rising high-pass once the tempo has arrived
  const hp = outDeck.sweep.highpass.frequency;
  hp.setValueAtTime(20, rampEnd);
  hp.exponentialRampToValueAtTime(1500, releaseAt);

  return { t0, beatTimes, handoffAt: mid, releaseAt, elementAt, elementTrackTime };
}

/** Where a deck is in its song right now, for settling a cancelled mix onto the <audio> element. */
export function deckTrackTime(deck: LoopDeck): number {
  return deck.inputTime;
}

/**
 * Where a song is in its own seconds at AudioContext time [t] during the
 * loop mix, worked out from the same beat clock the decks were scheduled on
 * (so it loops and follows the tempo ramp exactly). Null where that side
 * isn't sounding from a deck: the outgoing song before t0 and after its loop
 * stops; the incoming song before t0.
 */
export function mixPositionAt(plan: LoopMixPlan, times: LoopMixTimes, side: 'out' | 'in', t: number): number | null {
  const bt = times.beatTimes;
  if (t < times.t0) return null;
  const beats = plan.totalBars * 4;
  if (side === 'in' && t >= times.releaseAt) return plan.in.loop[1] + (t - times.releaseAt);
  if (side === 'out' && t >= times.releaseAt) return null;
  let k = 0;
  while (k + 1 < bt.length && bt[k + 1]! <= t) k++;
  const frac = Math.min(1, (t - bt[k]!) / (bt[k + 1]! - bt[k]!));
  if (k >= beats) return null;
  if (side === 'out') return plan.out.loop[0] + ((k % (plan.out.bars * 4)) + frac) * plan.out.beatSec;
  return plan.in.loop[0] + (((k * plan.fold) % (plan.in.bars * 4)) + frac * plan.fold) * plan.in.beatSec;
}

/** Where in its beat the mix is at [t], 0 on the beat and rising to 1; null outside the mix. */
export function mixBeatPhaseAt(times: LoopMixTimes, t: number): number | null {
  const bt = times.beatTimes;
  if (t < times.t0 || t >= bt[bt.length - 1]!) return null;
  let k = 0;
  while (k + 1 < bt.length && bt[k + 1]! <= t) k++;
  return (t - bt[k]!) / (bt[k + 1]! - bt[k]!);
}
