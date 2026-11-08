// Adapted from AutoMix's src/lib/engine/autodj.ts (https://github.com/heydavidf/automix),
// Copyright (C) 2026 David Finnegan. AGPL-3.0-only: see ../LICENSE and ../NOTICE.md.
//
// BitChord: AutoMix picks every next track from a local library. Here the
// queue decides what comes next, as on the phone, and only the playing and
// the next song are analysed, so AutoMix's selector isn't used. Tracks
// are analyzed from YouTube on the way (analysis/analyzer.ts); a track whose
// analysis isn't ready in time mixes through AutoMix's own fallback, a plain
// 12-second equal-power crossfade. The handoff to the incoming track happens
// at the blend's midpoint, where the phone's CrossfadeController hands over.
import type { TrackAnalysis } from '../analysis/types';
import { analyzer } from '../analysis/analyzer';
import type { Deck } from './deck';
import type { Candidate } from './selector';
import { scheduleTransition, plannedBars, MIN_GRID_CONFIDENCE, type TransitionPlan } from './transition-scheduler';
import { asOutgoing, gridOf, phraseAtOrBefore } from './grid';
import type { LoopDeck } from './loopDeck';
import {
  IN_REGION_SEC,
  mixBeatPhaseAt,
  mixPositionAt,
  OUT_REGION_SEC,
  OUT_SEARCH_SEC,
  planLoopMix,
  scheduleLoopMix,
  type LoopMixPlan,
  type LoopMixTimes,
  type SungSpans,
} from './loopMix';

export type AutoDJState = 'idle' | 'playing' | 'scheduled';

export interface QueuedTrack {
  videoId: string;
  title: string;
  artist: string;
}

/** What the player gives the mix loop: its decks and its queue. */
export interface AutomixHost {
  ctx: AudioContext;
  /** The deck in front (the one the app shows and controls). */
  front(): Deck;
  back(): Deck;
  /** The track in front, if one is playing. */
  current(): QueuedTrack | null;
  /** What the queue plays next on its own (repeat and AutoPlay applied), or null at the end. */
  next(): QueuedTrack | null;
  /** Loads [videoId] onto the back deck, paused. Resolves false if it can't play. */
  loadBack(videoId: string): Promise<boolean>;
  /** The blend's midpoint: the incoming track becomes the one playing, as far as the app knows. */
  handoff(): void;
  /** The blend is over: the old deck can let go of its track. */
  finished(): void;
  /** BitChord: the two stretch decks the loop mix plays on (built on first use). */
  loopDecks(): Promise<[LoopDeck, LoopDeck]>;
  /**
   * BitChord: where [videoId] is sung, from its synced lyrics; null when it
   * has none. The loop mix never loops a sung word, and these are its truest
   * answer (the vocal mask is the fallback).
   */
  sungSpans(videoId: string): Promise<SungSpans | null>;
  /**
   * BitChord: while a loop mix runs, the elements are paused and started by
   * the mix, not the user; the player must not read that as a pause or an end.
   */
  hold(on: boolean): void;
}

export interface AutoDJSnapshot {
  state: AutoDJState;
  currentKey: string | null;
  nextKey: string | null;
  nextLoaded: boolean;
  candidates: Candidate[];
  /** AudioContext time of the next blend start, when scheduled. */
  transitionAt: number | null;
  transitionDuration: number | null;
  plan: TransitionPlan | null;
  /**
   * The stretch of the playing (outgoing) track the next blend covers, in its
   * own seconds: planned while it is ahead, the real one once scheduled. For
   * the seek bar's highlight, as on the phone.
   */
  window: MixWindow | null;
  /** Seconds until the blend starts (negative once it has), when scheduled. */
  startsInSec: number | null;
  /** The outgoing track's beat at the rate it plays, for a pulse in time with the blend; null without a grid. */
  beatSec: number | null;
  /** BitChord: the loop mix planned or running, for stats for nerds. */
  loop: LoopMixPlan | null;
}

export interface MixWindow {
  videoId: string;
  startSec: number;
  endSec: number;
}

const PRELOAD_WINDOW_SEC = 60;
/** BitChord: the loop mix is prepared (bars chosen, decks loaded) from this far before the end. */
const LOOP_PREPARE_SEC = OUT_SEARCH_SEC + 40;
/** BitChord: the outgoing loop starts at least this far ahead of the playhead when it's planned. */
const LOOP_SETUP_SEC = 15;
const SCHEDULE_WINDOW_SEC = 12;
const EMERGENCY_REMAINING_SEC = 10;

/**
 * The auto-mix loop: preload the queue's next track into the idle deck as
 * the mix-out point approaches, schedule the blend, flip decks, repeat. All
 * timing decisions read the active deck's timeline; the tick only decides,
 * the Web Audio clock executes.
 */
export class AutoDJ {
  transitionBars = 16;

  private state: AutoDJState = 'idle';
  private history: string[] = [];
  private currentKey: string | null = null;
  private nextKey: string | null = null;
  private nextLoadedKey: string | null = null;
  private candidates: Candidate[] = [];
  private plan: TransitionPlan | null = null;
  private blend: { from: Deck; to: Deck; handedOff: boolean } | null = null;
  /** The deck a finished blend left gliding back to rate 1. */
  private settling: Deck | null = null;
  private timer: ReturnType<typeof setInterval> | null = null;
  private ticking = false;
  private loadToken = 0;
  private window: MixWindow | null = null;
  /** Both songs of the coming mix are analysed (the seek bar shows nothing until they are). */
  private bothAnalyzed = false;
  /** BitChord: the loop mix for the coming pair, once its bars are chosen and loaded on the decks. */
  private loopPlan: LoopMixPlan | null = null;
  private loopPreparing: string | null = null;
  private loopTimes: LoopMixTimes | null = null;
  private loopDecks: [LoopDeck, LoopDeck] | null = null;
  private beatSec: number | null = null;

  constructor(
    private host: AutomixHost,
    private onUpdate: (snap: AutoDJSnapshot) => void = () => {},
    private onTransition?: (from: TrackAnalysis, to: TrackAnalysis, plan: TransitionPlan, bars: number) => void,
  ) {}

  get snapshot(): AutoDJSnapshot {
    return {
      state: this.state,
      currentKey: this.currentKey,
      nextKey: this.nextKey,
      nextLoaded: this.nextKey !== null && this.nextLoadedKey === this.nextKey,
      candidates: this.candidates.slice(0, 8),
      transitionAt: this.plan?.t0 ?? null,
      transitionDuration: this.plan?.durationSec ?? null,
      plan: this.plan,
      window: this.window,
      startsInSec: this.plan ? this.plan.t0 - this.host.ctx.currentTime : null,
      beatSec: this.beatSec,
      loop: this.loopPlan,
    };
  }

  /**
   * Where [videoId] is right now in its own seconds while a loop mix plays it
   * from a deck (looping, at the ramped tempo); null when its element is
   * what's playing it. The lyrics, the seek bar and a cancelled mix read this.
   */
  mixPlayhead(videoId: string): number | null {
    const plan = this.loopPlan;
    const times = this.loopTimes;
    if (!plan || !times) return null;
    const side = videoId === plan.out.key ? 'out' : videoId === plan.in.key ? 'in' : null;
    return side ? mixPositionAt(plan, times, side, this.host.ctx.currentTime) : null;
  }

  /**
   * The running mix's beat, for the seek bar's pulse: [phase] 0 on the beat,
   * rising to 1; null phase when the tempo isn't known. Null between mixes.
   */
  mixBeat(): { phase: number | null } | null {
    if (this.state !== 'scheduled' || !this.plan) return null;
    const now = this.host.ctx.currentTime;
    if (this.loopTimes) {
      const phase = mixBeatPhaseAt(this.loopTimes, now);
      return phase === null ? null : { phase };
    }
    const { t0, durationSec } = this.plan;
    if (now < t0 || now > t0 + durationSec) return null;
    const beat = this.beatSec;
    if (!beat) return { phase: null };
    const x = (now - t0) / beat;
    return { phase: x - Math.floor(x) };
  }

  /** True while a loop mix runs: both elements are held paused and the decks carry the sound. */
  get loopMixing(): boolean {
    return this.state === 'scheduled' && this.loopTimes !== null;
  }

  /** True between scheduling a blend and its handoff: the back deck carries the next track. */
  get mixing(): boolean {
    return this.state === 'scheduled';
  }

  start(): void {
    if (this.state === 'idle') this.state = 'playing';
    this.timer ??= setInterval(() => void this.tick(), 1000);
    void this.tick();
  }

  stop(): void {
    if (this.timer) {
      clearInterval(this.timer);
      this.timer = null;
    }
    this.cancel();
    this.state = 'idle';
    this.push();
  }

  /**
   * The user took over (pause, seek, another track): drop the blend and the
   * preloaded deck. The deck in front plays on, at full volume and rate 1
   * once the glide is gone.
   */
  cancel(): void {
    this.loadToken++;
    if (this.loopTimes && this.blend && this.loopDecks) {
      // Settle onto the song that's ahead in the mix, where its deck is now.
      const [outDeck, inDeck] = this.loopDecks;
      const onIncoming = this.blend.handedOff;
      const keep = onIncoming ? this.blend.to : this.blend.from;
      const drop = onIncoming ? this.blend.from : this.blend.to;
      // where the song really is in the mix (the deck's own report lags and starts stale)
      const at =
        this.mixPlayhead((onIncoming ? this.loopPlan?.in.key : this.loopPlan?.out.key) ?? '') ??
        (onIncoming ? this.loopTimes.elementTrackTime : this.loopPlan?.out.loop[0]) ??
        keep.audio.currentTime;
      outDeck.stop();
      inDeck.stop();
      drop.cancel();
      keep.cancel();
      keep.audio.currentTime = at;
      void keep.audio.play().catch(() => {});
      this.loopTimes = null;
      this.blend = null;
      this.host.hold(false);
    }
    this.loopPlan = null;
    this.loopPreparing = null;
    if (this.blend) {
      this.blend.from.cancel();
      this.blend.to.cancel();
    }
    this.settling?.cancel();
    this.settling = null;
    this.window = null;
    this.blend = null;
    this.plan = null;
    this.nextKey = null;
    this.nextLoadedKey = null;
    this.candidates = [];

    if (this.state === 'scheduled') this.state = 'playing';
    this.push();
  }

  /** The outgoing track ran out before the midpoint (a stalled stream): hand over now. */
  forceHandoff(): boolean {
    if (!this.blend || this.blend.handedOff) return false;
    this.blend.handedOff = true;
    this.host.handoff();
    return true;
  }

  private currentAnalysis(): TrackAnalysis | null {
    return this.currentKey ? analyzer.get(this.currentKey) : null;
  }

  private async tick(): Promise<void> {
    if (this.ticking || this.state === 'idle') return;
    this.ticking = true;
    try {
      const now = this.host.ctx.currentTime;

      if (this.state === 'scheduled' && this.plan && this.blend) {
        const handoffAt = this.loopTimes?.handoffAt ?? this.plan.t0 + this.plan.durationSec / 2;
        if (!this.blend.handedOff && now >= handoffAt) {
          this.blend.handedOff = true;
          this.host.handoff();
        }
        if (now > this.plan.t0 + this.plan.durationSec + 0.3) this.completeTransition();
        this.push();
        return;
      }

      const playing = this.host.current();
      const front = this.host.front();
      if (!playing || !front.isPlaying) return;
      if (playing.videoId !== this.currentKey) {
        // A new track in front that this loop didn't mix in (a tap, a skip).
        this.currentKey = playing.videoId;
        this.nextKey = null;
        this.nextLoadedKey = null;

        this.candidates = [];
        this.window = null;
        this.loopPlan = null;
      }
      front.anchor();
      analyzer.request(playing.videoId, tags(playing), true);

      const queued = this.host.next();
      if (!queued) {
        this.nextKey = null;
        this.window = null;
        this.push();
        return;
      }
      if (queued.videoId !== this.nextKey) {
        this.nextKey = queued.videoId;
        this.nextLoadedKey = null;
        this.loopPlan = null;
        this.loadToken++;
      }
      analyzer.request(queued.videoId, tags(queued), true);
      // and the playing track ahead of it, if it's still waiting: its mix-out comes first
      analyzer.request(playing.videoId, tags(playing), true);

      // BitChord, as the phone: only with both songs analysed is there a mix
      // to plan. Until then nothing is drawn on the seek bar, and if they
      // aren't ready in time the song plays to its end and crossfades there
      // (a stub has no grid and no outro, so AutoMix's fallback lands at the
      // last 12 s) rather than cutting away mid-song.
      this.bothAnalyzed = this.currentAnalysis() !== null && analyzer.get(queued.videoId) !== null;
      const cur = asOutgoing(
        this.bothAnalyzed ? this.currentAnalysis()! : stubAnalysis(playing, front.durationSec),
      );
      if (this.bothAnalyzed && !this.loopPlan && this.trackRemaining() < LOOP_PREPARE_SEC) {
        void this.prepareLoopMix(cur, analyzer.get(queued.videoId)!);
      }
      const remaining = this.timeToMixOut(cur);
      if (!this.bothAnalyzed) this.window = null;

      if (this.nextKey && this.nextLoadedKey !== this.nextKey && remaining < PRELOAD_WINDOW_SEC) {
        await this.loadNext(this.nextKey);
      }

      const ready = this.nextKey !== null && this.nextLoadedKey === this.nextKey;
      if (ready && remaining < SCHEDULE_WINDOW_SEC && this.loopPlan) {
        await this.scheduleLoop(cur);
      } else if (ready && remaining < SCHEDULE_WINDOW_SEC) {
        this.scheduleBlend(cur, false);
      } else if (ready && this.trackRemaining() < EMERGENCY_REMAINING_SEC) {
        this.scheduleBlend(cur, true);
      }
      this.push();
    } finally {
      this.ticking = false;
    }
  }

  /** Output seconds until the natural blend start on the active deck. */
  private timeToMixOut(cur: TrackAnalysis): number {
    const front = this.host.front();
    const now = this.host.ctx.currentTime;
    const pos = front.expectedTrackTime ?? 0;
    if (this.loopPlan && this.loopPlan.out.loop[0] > pos + 1) {
      // the loop mix starts where the outgoing loop does; that's the stretch the seek bar shows
      const [start, end] = this.loopPlan.out.loop;
      this.window = { videoId: cur.fileKey, startSec: start, endSec: end };
      this.beatSec = this.loopPlan.out.beatSec;
      const t0 = front.timeline.ctxTimeAt(start, now);
      return t0 === null ? 0 : Math.max(0, t0 - now);
    }
    if (this.loopPlan) this.loopPlan = null; // the playhead went past it
    const grid = gridOf(cur);
    if (!grid || cur.gridConfidence < MIN_GRID_CONFIDENCE) {
      const target = (cur.outroStartSec ?? cur.durationSec) - 12;
      this.window = { videoId: cur.fileKey, startSec: Math.max(0, target), endSec: Math.max(0, target) + 12 };
      this.beatSec = null;
      return Math.max(0, target - pos);
    }
    const outRate = front.timeline.rateAt(now) || 1;
    const effBpm = grid.bpm * outRate;
    const next = this.nextKey ? analyzer.get(this.nextKey) : null;
    // the same length the scheduler will choose for this pair (a filter mix runs longer than a weak-grid blend)
    const bars = plannedBars(this.transitionBars, cur, next ?? null, outRate);
    const blendOutputSec = bars * 4 * (60 / effBpm);
    const blendInputSec = blendOutputSec * outRate;
    let mixOut = phraseAtOrBefore(grid, (cur.outroStartSec ?? cur.durationSec) - blendInputSec);
    mixOut = Math.min(mixOut, phraseAtOrBefore(grid, cur.durationSec - blendInputSec - grid.barSec));
    this.window = { videoId: cur.fileKey, startSec: mixOut, endSec: mixOut + blendInputSec };
    this.beatSec = grid.beatSec / outRate;
    const t0 = front.timeline.ctxTimeAt(mixOut, now);
    return t0 === null ? 0 : Math.max(0, t0 - now);
  }

  private trackRemaining(): number {
    const front = this.host.front();
    const pos = front.expectedTrackTime ?? 0;
    return front.durationSec - pos;
  }

  private async loadNext(videoId: string): Promise<void> {
    const token = ++this.loadToken;
    const ok = await this.host.loadBack(videoId);
    if (token !== this.loadToken) return; // superseded by cancel() or a newer load
    if (ok) this.nextLoadedKey = videoId;
    else this.nextKey = null;
  }

  private scheduleBlend(cur: TrackAnalysis, forceNow: boolean): void {
    const queued = this.host.next();
    if (!this.nextKey || queued?.videoId !== this.nextKey) return;
    const from = this.host.front();
    const to = this.host.back();
    const next = (this.bothAnalyzed ? analyzer.get(this.nextKey) : null) ?? stubAnalysis(queued, to.durationSec);
    // The outgoing deck runs free (only its scheduled stop applies); the incoming one follows it.
    to.lock(from);
    to.setVolume(1);
    to.eq.reset();
    this.plan = scheduleTransition({
      from,
      to,
      fromAnalysis: cur,
      toAnalysis: next,
      bars: this.transitionBars,
      forceNow,
    });
    this.blend = { from, to, handedOff: false };
    const outRate = from.timeline.rateAt(this.host.ctx.currentTime) || 1;
    this.window = {
      videoId: cur.fileKey,
      startSec: this.plan.mixOutTrackTime,
      endSec: this.plan.mixOutTrackTime + this.plan.durationSec * outRate,
    };
    if (!this.bothAnalyzed) this.window = null;
    const grid = this.plan.style === 'crossfade' ? null : gridOf(cur);
    this.beatSec = grid ? grid.beatSec / outRate : null;
    this.state = 'scheduled';
    this.onTransition?.(cur, next, this.plan, this.transitionBars);
  }

  /**
   * BitChord: picks the bars to loop and loads both decks, in the background
   * while the song plays. Decodes each song once (from the bytes the analysis
   * kept) and holds only the stretch each deck plays.
   */
  private async prepareLoopMix(cur: TrackAnalysis, next: TrackAnalysis): Promise<void> {
    const key = `${cur.fileKey}>${next.fileKey}`;
    if (this.loopPreparing === key) return;
    this.loopPreparing = key;
    const stillWanted = () => this.loopPreparing === key && this.currentKey === cur.fileKey && this.nextKey === next.fileKey;
    try {
      const outGrid = asOutgoing(cur);
      if (outGrid.gridConfidence < MIN_GRID_CONFIDENCE || next.gridConfidence < MIN_GRID_CONFIDENCE) return;
      this.loopDecks ??= await this.host.loopDecks();
      const ctx = this.host.ctx;
      const [outBytes, inBytes, outSung, inSung] = await Promise.all([
        analyzer.audioBytes(cur.fileKey),
        analyzer.audioBytes(next.fileKey),
        this.host.sungSpans(cur.fileKey).catch(() => null),
        this.host.sungSpans(next.fileKey).catch(() => null),
      ]);
      const [outBuffer, inBuffer] = await Promise.all([ctx.decodeAudioData(outBytes.slice(0)), ctx.decodeAudioData(inBytes.slice(0))]);
      if (!stillWanted()) return;
      const pos = this.host.front().expectedTrackTime ?? 0;
      const plan = planLoopMix(outGrid, outBuffer, next, inBuffer, pos + LOOP_SETUP_SEC, { out: outSung, in: inSung });
      if (!plan) return;
      const [outDeck, inDeck] = this.loopDecks;
      await outDeck.load(cur.fileKey, outBuffer, Math.max(0, cur.durationSec - OUT_REGION_SEC), cur.durationSec);
      await inDeck.load(next.fileKey, inBuffer, 0, Math.min(next.durationSec, IN_REGION_SEC));
      if (!stillWanted()) return;
      this.loopPlan = plan;
    } catch (error) {
      console.warn('automix: loop mix not prepared', error);
    } finally {
      if (this.loopPreparing === key && !this.loopPlan) this.loopPreparing = null;
    }
  }

  /** BitChord: puts the loop mix on the clock: the outgoing element hands over at its loop start. */
  private async scheduleLoop(cur: TrackAnalysis): Promise<void> {
    const plan = this.loopPlan!;
    const queued = this.host.next();
    if (!this.loopDecks || queued?.videoId !== plan.in.key || cur.fileKey !== plan.out.key) {
      this.loopPlan = null;
      return;
    }
    const from = this.host.front();
    const to = this.host.back();
    await from.anchorPrecise();
    const now = this.host.ctx.currentTime;
    const t0 = from.timeline.ctxTimeAt(plan.out.loop[0], now);
    if (t0 === null || t0 - now < 1) {
      this.loopPlan = null;
      return;
    }
    const [outDeck, inDeck] = this.loopDecks;
    const times = scheduleLoopMix(plan, outDeck, inDeck, t0);
    this.host.hold(true);

    // the outgoing element hands its sound to the deck at the loop start, then pauses
    const og = from.gain.gain;
    og.cancelScheduledValues(now);
    og.setValueAtTime(1, t0 - 0.025);
    og.linearRampToValueAtTime(0, t0);
    from.pauseAt(t0 + 0.06);
    // the incoming element starts unheard 1.5 s early, held to the deck's clock, and takes over
    const ig = to.gain.gain;
    ig.cancelScheduledValues(now);
    ig.setValueAtTime(0, now);
    ig.setValueAtTime(0, times.elementAt);
    ig.linearRampToValueAtTime(1, times.elementAt + 0.025);
    to.eq.reset();
    to.playAt({ ctxTime: times.elementAt - 1.5, trackTime: times.elementTrackTime - 1.5, rate: 1 });
    to.lock(null);

    this.loopTimes = times;
    this.plan = {
      t0,
      durationSec: times.elementAt - t0,
      mixInTrackTime: plan.in.loop[0],
      mixOutTrackTime: plan.out.loop[0],
      rate: plan.fold,
      fallback: false,
      style: 'loop',
      reason: null,
    };
    this.blend = { from, to, handedOff: false };
    this.window = { videoId: cur.fileKey, startSec: plan.out.loop[0], endSec: plan.out.loop[1] };
    this.beatSec = plan.out.beatSec;
    this.state = 'scheduled';
  }

  private completeTransition(): void {
    const blend = this.blend;
    if (blend && this.loopTimes && this.loopDecks) {
      for (const d of this.loopDecks) d.stop();
      this.loopTimes = null;
      this.host.hold(false);
    }
    this.loopPlan = null;
    this.loopPreparing = null;
    if (blend) {
      if (!blend.handedOff) this.host.handoff();
      blend.from.cancel();
      blend.to.unfollow();
      // the incoming deck keeps its rate glide (posted up to ~24 s ahead); it
      // releases itself once that is played out
      this.settling = blend.to;
      releaseWhenSettled(blend.to, () => this.settling === blend.to);
      this.host.finished();
    }
    if (this.currentKey) this.history.unshift(this.currentKey);
    if (this.nextKey) this.history.unshift(this.nextKey);
    this.history.length = Math.min(this.history.length, 50);
    this.currentKey = this.nextKey;
    this.nextKey = null;
    this.nextLoadedKey = null;
    this.plan = null;
    this.blend = null;
    this.window = null;
    this.candidates = [];

    this.state = 'playing';
  }

  private push(): void {
    this.onUpdate(this.snapshot);
  }
}

function tags(t: QueuedTrack) {
  return { title: t.title, artist: t.artist };
}

/** A track with no analysis yet: no grid, so the scheduler takes AutoMix's plain-crossfade fallback. */
function stubAnalysis(t: QueuedTrack, durationSec: number): TrackAnalysis {
  return {
    fileKey: t.videoId,
    path: `youtube:${t.videoId}`,
    tags: tags(t),
    durationSec,
    bpm: null,
    beatIntervalSec: null,
    firstDownbeatSec: null,
    gridConfidence: 0,
    camelot: null,
    keyName: null,
    keyConfidence: 0,
    energy: 0.5,
    energyCurve: [],
    introEndSec: null,
    outroStartSec: null,
    analysisVersion: 0,
  };
}

/** The glide's last step lands at blend end + 24 s; hold the servo until no rate change is left ahead. */
function releaseWhenSettled(deck: Deck, isCurrent: () => boolean) {
  const check = () => {
    if (!isCurrent()) return;
    const now = deck.ctx.currentTime;
    if (Math.abs(deck.timeline.rateAt(now + 60) - deck.timeline.rateAt(now)) < 1e-6) deck.release();
    else setTimeout(check, 1000);
  };
  setTimeout(check, 1000);
}
