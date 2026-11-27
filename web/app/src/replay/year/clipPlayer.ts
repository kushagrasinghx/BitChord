import type { YouTubeEngine } from '@/playback/youtube/engine';

/** A clip: which song, and where in it to start. [key] identifies it across cards. */
export interface Clip {
  key: string;
  videoId: string;
  startMs: number;
}

interface Deck {
  engine: YouTubeEngine;
  audio: HTMLAudioElement;
  key: string | null;
  /** The load in flight or done for [key]. */
  loading: Promise<boolean> | null;
  /** Bumped to cancel whatever fade is running. */
  fade: number;
  /** The fade out to silence in flight, which a new load waits for. */
  fading: Promise<void> | null;
}

const FADE_IN_MS = 500;
const FADE_OUT_MS = 420;
const MUTE_FADE_MS = 200;
const STEP_MS = 16;

/**
 * The year story's soundtrack (ReplayYearAudio.kt YearClipPlayer): one short
 * clip per card, each from its song's best part, faded across from one card
 * to the next.
 *
 * Its own engines and audio elements, never the player's: through the main
 * player a clip would land in the queue and the history, count towards next
 * year's Replay and set off Automix. Two of them, so the next card's clip is
 * already loaded at its start while this one plays, and the reveal it is timed
 * to doesn't land in silence. They take turns: one is heard, the other waits.
 */
export class YearClipPlayer {
  private decks: Promise<Deck[]>;
  private live = 0;
  private held = false;
  private muted = false;
  private released = false;

  constructor() {
    this.decks = import('@/playback/youtube/engine').then(({ YouTubeEngine }) =>
      [0, 1].map(() => {
        const audio = new Audio();
        audio.preload = 'auto';
        audio.volume = 0;
        return { engine: new YouTubeEngine(audio), audio, key: null, loading: null, fade: 0, fading: null };
      }),
    );
  }

  setMuted(muted: boolean) {
    this.muted = muted;
    void this.decks.then((decks) => {
      const deck = decks[this.live]!;
      if (deck.key) void this.ramp(deck, muted ? 0 : 1, MUTE_FADE_MS);
    });
  }

  /** Loads [clip] on the waiting deck, so [play] can start it without a gap. */
  prime(clip: Clip | null) {
    void this.decks.then(async (decks) => {
      if (!clip || decks[this.live]!.key === clip.key) return;
      const deck = decks[1 - this.live]!;
      if (deck.key === clip.key) return;
      // The waiting deck is usually still fading the last card out; loading over it would cut that dead.
      await deck.fading;
      if (decks[this.live] !== deck && deck.key !== clip.key) this.load(deck, clip);
    });
  }

  /** Fades the clip being heard out and [clip] in; null fades to silence. Already playing [clip] carries on. */
  play(clip: Clip | null) {
    void this.decks.then(async (decks) => {
      const current = decks[this.live]!;
      if (clip && current.key === clip.key) {
        if (!this.held) void current.audio.play().catch(() => undefined);
        return;
      }
      this.stop(current);
      if (!clip) return;
      this.live = 1 - this.live;
      const next = decks[this.live]!;
      if (next.key !== clip.key) {
        await next.fading;
        this.load(next, clip);
      }
      const loaded = await next.loading;
      // A card turned again while this one loaded: that card's play() has the deck now.
      if (!loaded || decks[this.live] !== next || next.key !== clip.key || this.released) return;
      next.audio.volume = 0;
      if (!this.held) void next.audio.play().catch(() => undefined);
      void this.ramp(next, this.muted ? 0 : 1, FADE_IN_MS);
    });
  }

  /** The card is held, or the share sheet is up: everything holds. */
  hold(holding: boolean) {
    if (this.held === holding) return;
    this.held = holding;
    void this.decks.then((decks) => {
      const deck = decks[this.live]!;
      if (!deck.key) return;
      if (holding) deck.audio.pause();
      else void deck.audio.play().catch(() => undefined);
    });
  }

  /** Fades out and lets go of both decks. */
  release() {
    this.released = true;
    void this.decks.then((decks) =>
      decks.forEach((deck) => {
        void this.ramp(deck, 0, FADE_OUT_MS).then(() => {
          deck.audio.pause();
          void deck.engine.stop();
          deck.audio.removeAttribute('src');
        });
      }),
    );
  }

  private load(deck: Deck, clip: Clip) {
    deck.fade++;
    deck.audio.volume = 0;
    deck.key = clip.key;
    deck.loading = deck.engine.load(clip.videoId, { startSec: clip.startMs / 1000, autoplay: false }).then(
      () => true,
      // Unplayable, superseded, offline: the card plays in silence rather than failing.
      () => false,
    );
  }

  private stop(deck: Deck) {
    if (!deck.key) return;
    deck.fading = this.ramp(deck, 0, FADE_OUT_MS).then((finished) => {
      if (!finished) return;
      deck.audio.pause();
      deck.key = null;
      deck.loading = null;
      deck.fading = null;
    });
  }

  /**
   * Volume from wherever it is to [to], a frame or so a step. Rising fast and
   * falling late: a straight line both ways sounds like a dip mid-crossfade.
   * Resolves false when another fade took over.
   */
  private ramp(deck: Deck, to: number, ms: number): Promise<boolean> {
    const token = ++deck.fade;
    const from = deck.audio.volume;
    const steps = Math.max(1, Math.round(ms / STEP_MS));
    return new Promise((resolve) => {
      let i = 0;
      const tick = () => {
        if (deck.fade !== token) return resolve(false);
        i++;
        const t = i / steps;
        const eased = to > from ? Math.sqrt(t) : t * t;
        deck.audio.volume = Math.min(1, Math.max(0, from + (to - from) * eased));
        if (i >= steps) {
          deck.audio.volume = to;
          resolve(true);
        } else setTimeout(tick, STEP_MS);
      };
      setTimeout(tick, STEP_MS);
    });
  }
}
