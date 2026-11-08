import type { DetectedExtension } from '@bitchord/shared/bridge';
import { create } from 'zustand';
import type { Track } from '@/data/music/types';
import { extensionQueryKey } from '@/extension/useExtension';
import { artworkAtPixels } from '@/lib/artwork';
import { queryClient } from '@/lib/queryClient';
import { extendStation } from '@/playback/queue/builder';
import {
  applyJump,
  asEntry,
  BACK_RESTARTS_AFTER_SEC,
  buildContextQueue,
  buildOneOffQueue,
  playedUserQueueIndices,
  sectionedOrder,
  shuffledStartingOrder,
  shuffledUpcomingOrder,
  upcomingUserQueueIndices,
  userQueueInsertionIndex,
  type QueueEntry,
  type QueueSource,
} from '@/playback/queue/timeline';
import { trackLog } from '@/playback/trackLog';
import type { LoadedTrack, StreamStats, YouTubeEngine } from '@/playback/youtube/engine';
import type { AutomixGraph, AutoDJSnapshot } from '@/playback/automix';

export interface NowPlaying {
  videoId: string;
  title: string;
  artist: string;
  /** Largest available; size it for display with sizedArtwork(). */
  artworkUrl: string | null;
  durationSec: number;
  /** "251 opus 140kbps via VISIONOS", once known; for the debug panel. */
  streamInfo: string | null;
  /** The audio stream field by field, once known; for stats for nerds. */
  stream: (StreamStats & { client: string }) | null;
}

export type PlayerStatus = 'idle' | 'loading' | 'playing' | 'paused' | 'buffering' | 'error';
export type RepeatMode = 'off' | 'all' | 'one';

/** Crossfade lengths offered, in seconds; 0 is off. */
export const CROSSFADE_CHOICES = [0, 3, 6, 9, 12] as const;

interface Settings {
  shuffle: boolean;
  repeat: RepeatMode;
  /** Keep playing similar music (YouTube Music radio) when the queue runs out. */
  autoplay: boolean;
  volume: number;
  muted: boolean;
  crossfadeSec: number;
  /** AutoMix: beat-matched, phrase-aligned DJ blends between tracks (playback/automix). Overrides crossfadeSec. */
  automix: boolean;
  /** AutoMix's blend length, in bars: a maximum, shortened for weak beat grids. */
  automixBars: number;
  /** Stats for nerds: the stream's codec, bitrate and sample rate, and what Automix is doing, under the artist. */
  showNerdStats: boolean;
}

interface PlayerState extends Settings {
  /** The whole timeline: what played, what plays, what's next (playback/queue/timeline.ts). */
  queue: QueueEntry[];
  /** The playing entry in [queue]; -1 when nothing is loaded. */
  index: number;
  nowPlaying: NowPlaying | null;
  status: PlayerStatus;
  error: string | null;
  /** Bumped whenever the audio element in front changes (crossfade swaps them). */
  deck: number;
  /** AutoMix's loop, while it runs: what it plans and when. */
  automixState: AutoDJSnapshot | null;
  /** A loop mix is playing from its decks: read the position from getPlayhead(), not the element. */
  mixing: boolean;

  /** Plays [tracks] as the context (an album, a playlist), from [startIndex]. */
  playTracks: (tracks: Track[], startIndex: number, source: QueueSource) => void;
  /** Plays one song on its own (search, home); AutoPlay carries on after it. */
  playTrack: (track: Track, source: QueueSource) => void;
  /** Plays a bare video id (the Debug page). */
  playVideo: (videoId: string, hint?: Partial<Omit<NowPlaying, 'videoId'>>) => Promise<void>;
  playNext: (tracks: Track[], source?: QueueSource | null) => void;
  addToQueue: (tracks: Track[], source?: QueueSource | null) => void;
  /** Tapping a row in Up Next (QueueTimeline.computeJumpOrder). */
  jumpTo: (index: number) => void;
  removeAt: (index: number) => void;
  /** Moves an upcoming row; both indices are into [queue] and after the playing one. */
  move: (from: number, to: number) => void;
  /** "Clear": the upcoming hand-queued tracks. The album or playlist and AutoPlay stay. */
  clearUpNext: () => void;
  next: () => void;
  previous: () => void;
  togglePlay: () => void;
  seek: (seconds: number) => void;
  toggleShuffle: () => void;
  cycleRepeat: () => void;
  setAutoplay: (on: boolean) => void;
  setVolume: (volume: number) => void;
  toggleMute: () => void;
  setCrossfade: (seconds: number) => void;
  setAutomix: (on: boolean) => void;
  setAutomixBars: (bars: number) => void;
  setShowNerdStats: (on: boolean) => void;
}

// ---- Settings, kept across visits ---------------------------------------------------

const SETTINGS_KEY = 'bitchord:player';
const DEFAULT_SETTINGS: Settings = {
  shuffle: false,
  repeat: 'off',
  autoplay: true,
  volume: 1,
  muted: false,
  crossfadeSec: 0,
  automix: false,
  automixBars: 16,
  showNerdStats: false,
};

function loadSettings(): Settings {
  try {
    return { ...DEFAULT_SETTINGS, ...(JSON.parse(localStorage.getItem(SETTINGS_KEY) ?? '{}') as Partial<Settings>) };
  } catch {
    return DEFAULT_SETTINGS;
  }
}

function saveSettings(state: Settings) {
  const { shuffle, repeat, autoplay, volume, muted, crossfadeSec, automix, automixBars, showNerdStats } = state;
  localStorage.setItem(
    SETTINGS_KEY,
    JSON.stringify({ shuffle, repeat, autoplay, volume, muted, crossfadeSec, automix, automixBars, showNerdStats }),
  );
}

// ---- Decks: two engines, so a crossfade can play the next track under this one ------------

interface Deck {
  engine: YouTubeEngine;
  audio: HTMLAudioElement;
  /** The crossfade's share of the volume, 0–1. */
  gain: number;
}

let decks: Deck[] | null = null;
let decksPromise: Promise<Deck[]> | null = null;
let active = 0;

/** The audio element in front, if playback has started at least once. */
export function getAudio(): HTMLAudioElement | null {
  return decks?.[active]?.audio ?? null;
}

/**
 * Where the song in front is, in seconds, and whether it's moving. Its
 * element, except while a loop mix plays it from a deck: then the mix's own
 * beat clock, which loops and follows the tempo ramp (the element is paused
 * underneath). The lyrics and the seek bars read this.
 */
export function getPlayhead(): { sec: number; paused: boolean } | null {
  const audio = getAudio();
  const { mixing, nowPlaying } = usePlayer.getState();
  if (mixing && graph && nowPlaying) {
    const sec = graph.dj.mixPlayhead(nowPlaying.videoId);
    if (sec !== null) return { sec, paused: false };
  }
  return audio ? { sec: audio.currentTime, paused: audio.paused } : null;
}

/** The running Automix blend's beat (phase 0 on the beat), for the seek bar's pulse; null between blends. */
export function getMixBeat(): { phase: number | null } | null {
  return graph?.dj.mixBeat() ?? null;
}

function getDecks(): Promise<Deck[]> {
  decksPromise ??= (async () => {
    // Shaka, YouTube.js and googlevideo are the heavy part of the app; they
    // load on the first play, not with the page (prompt §3).
    const { YouTubeEngine } = await import('@/playback/youtube/engine');
    decks = [0, 1].map((n) => {
      const audio = new Audio();
      audio.preload = 'auto';
      // Media from another origin is silent inside an AudioContext unless it
      // is fetched with CORS (prompt §6, the Web Audio caveat).
      audio.crossOrigin = 'anonymous';
      const engine = new YouTubeEngine(audio);
      const deck: Deck = { engine, audio, gain: 1 };
      engine.onFatal = (videoId, message) => {
        if (decks?.[active] !== deck) return;
        trackLog.e(`stopped: ${message}`, videoId);
        usePlayer.setState({ status: 'error', error: message });
      };
      engine.onRecovered = (track) => {
        if (decks?.[active] !== deck) return;
        // Keep YouTube Music's title and artist: without the queue's track the
        // video's own ("Doja Cat - Woman" by "dojacatVEVO") would replace them.
        const { queue, index } = usePlayer.getState();
        const entry = queue[index];
        applyTrack(track, entry?.track.videoId === track.videoId ? entry.track : undefined);
      };
      bindElement(deck, n);
      return deck;
    });
    applyVolumes();
    // For browser-driven tests: the elements aren't in the DOM.
    if (import.meta.env.DEV) Object.defineProperty(window, '__bcAudio', { get: getAudio, configurable: true });
    return decks;
  })();
  return decksPromise;
}

function applyVolumes() {
  const { volume, muted } = usePlayer.getState();
  const level = muted ? 0 : Math.min(1, Math.max(0, volume));
  if (graph) {
    // Routed through Web Audio: the elements stay at 1 and the graph carries volume and the fade.
    const now = graph.ctx.currentTime;
    graph.master.gain.setTargetAtTime(level, now, 0.015);
    (decks ?? []).forEach((deck, n) => {
      deck.audio.volume = 1;
      graph!.decks[n]!.fader.gain.setTargetAtTime(deck.gain, now, 0.015);
    });
    return;
  }
  for (const deck of decks ?? []) deck.audio.volume = level * deck.gain;
}

// ---- AutoMix (playback/automix): the two decks routed through Web Audio, mixed like a DJ ----

let graph: AutomixGraph | null = null;
let graphPromise: Promise<AutomixGraph | null> | null = null;
/** The track AutoMix loaded on the back deck, for the handoff. */
let mixLoaded: { videoId: string; track: LoadedTrack } | null = null;
/**
 * A loop mix is running: the decks carry the sound and both elements are
 * paused or started by the mix, so their pause, waiting and ended events
 * aren't the user's and mustn't move the player.
 */
let holding = false;

/** Builds the Web Audio graph and the mix loop over the decks, once. */
function ensureAutomix(): Promise<AutomixGraph | null> {
  const all = decks;
  if (!all) return Promise.resolve(null);
  graphPromise ??= import('@/playback/automix').then(({ createAutomixGraph, analyzer }) => {
    const built = createAutomixGraph(
      all.map((d) => d.audio),
      (routed) => ({
        front: () => routed[active]!,
        back: () => routed[1 - active]!,
        current: () => {
          // A party doesn't mix: every member would blend on their own clock (the phone stops too).
          if (partyHooks?.inParty()) return null;
          const { queue, index, status } = usePlayer.getState();
          const entry = queue[index];
          return entry && status === 'playing' ? queued(entry) : null;
        },
        next: () => {
          const plan = planNext(true);
          return plan?.kind === 'index' ? queued(plan.queue[plan.index]!) : null;
        },
        loadBack: async (videoId) => {
          const back = all[1 - active]!;
          back.gain = 1;
          applyVolumes();
          try {
            const track = await back.engine.load(videoId, { autoplay: false });
            mixLoaded = { videoId, track };
            return true;
          } catch (error) {
            if (!(error instanceof Error && error.name === 'SupersededError')) {
              trackLog.w(`automix couldn't load the next track: ${String(error)}`, videoId);
            }
            return false;
          }
        },
        handoff: automixHandoff,
        sungSpans,
        hold: (on) => {
          holding = on;
          usePlayer.setState(on ? { status: 'playing', mixing: true } : { mixing: false });
        },
        finished: () => {
          void all[1 - active]!.engine.stop().catch(() => {});
        },
      }),
      (snap) => usePlayer.setState({ automixState: snap }),
    );
    graph = built;
    applyVolumes();
    if (import.meta.env.DEV) Object.assign(window, { __bcAutomix: built, __bcAnalyzer: analyzer });
    return built;
  });
  return graphPromise;
}

function queued(entry: QueueEntry) {
  return { videoId: entry.track.videoId, title: entry.track.title, artist: artistNames(entry.track) };
}

/** How long the loop mix waits for a song's lyrics before planning from the vocal mask alone. */
const LYRICS_WAIT_MS = 8000;

/**
 * Where a queued song is sung, from its synced lyrics (the same lookup, and
 * cache, as the lyrics panel). Null when it has no synced lyrics or they
 * don't arrive in time; the loop mix then falls back on the vocal mask.
 */
async function sungSpans(videoId: string) {
  const { queue } = usePlayer.getState();
  const track = queue.find((e) => e.track.videoId === videoId)?.track;
  if (!track) return null;
  const [{ lookupLyrics }, { endMs, hasKnownEnd, isGap }, { sungSpansFromLines }] = await Promise.all([
    import('@/lyrics/repository'),
    import('@/lyrics/model'),
    import('@/playback/automix'),
  ]);
  const found = await Promise.race([
    lookupLyrics({
      videoId,
      title: track.title,
      artist: artistNames(track),
      durationMs: Math.round((track.durationSec ?? 0) * 1000),
      album: track.album?.name ?? null,
    }).catch(() => null),
    new Promise<null>((r) => setTimeout(() => r(null), LYRICS_WAIT_MS)),
  ]);
  if (!found?.synced) return null;
  return sungSpansFromLines(
    found.lines.map((l) => ({ timeMs: l.timeMs, endMs: hasKnownEnd(l) ? endMs(l) : null, blank: isGap(l) })),
  );
}

/** The blend's midpoint: the incoming track is the one playing now, as far as everything else knows. */
function automixHandoff() {
  if (!decks) return;
  const loaded = mixLoaded;
  mixLoaded = null;
  const plan = planNext(true);
  const entry = plan?.kind === 'index' ? plan.queue[plan.index] : undefined;
  if (!plan || plan.kind !== 'index' || !entry || !loaded || entry.track.videoId !== loaded.videoId) {
    trackLog.w('automix handoff found the queue moved; playing on', loaded?.videoId ?? null);
    graph?.dj.cancel();
    return;
  }
  active = 1 - active;
  usePlayer.setState((s) => ({ queue: plan.queue, index: plan.index, deck: s.deck + 1, status: 'playing' }));
  applyTrack(loaded.track, entry.track);
  afterTrackStarted();
  intent();
}

/** Starts or stops the mix loop to match the setting; the first start builds the graph. */
function syncAutomix() {
  if (!usePlayer.getState().automix) {
    graph?.dj.stop();
    return;
  }
  void ensureAutomix().then((g) => {
    const { automix, automixBars } = usePlayer.getState();
    if (!g || !automix) return;
    void g.ctx.resume();
    g.dj.transitionBars = automixBars;
    g.dj.start();
  });
}

function extensionReady(): boolean {
  const detected = queryClient.getQueryData<DetectedExtension | null>(extensionQueryKey);
  return !!detected?.info.capabilities.includes('yt');
}

const artistNames = (track: Track) => track.artists.map((a) => a.name).join(', ');

function hintOf(track: Track): NowPlaying {
  return {
    videoId: track.videoId,
    title: track.title,
    artist: artistNames(track),
    artworkUrl: track.thumbnail,
    durationSec: track.durationSec ?? 0,
    streamInfo: null,
    stream: null,
  };
}

// ---- Listen Together's two doors (playback/PartySync.kt) ----------------------------
//
// Every user action reports an intent through [partyHooks]; that is how the
// party hears about it. What the party does to this player goes through
// [partyPlayer] instead, which reports nothing, so a correction can never
// come back around as a control.

export interface PartyHooks {
  /** The user did something to playback, or the queue moved on by itself. */
  onIntent(): void;
  /** True when a resume must wait for the party's scheduled start (partySync starts it). */
  shouldDeferPlay(): boolean;
  /** A listener in a host-locked party: play/pause is theirs alone. True when handled. */
  onLockedTransport(playing: boolean): boolean;
  /** In a party, AutoPlay is the party's: one elected member tops up the shared queue. */
  inParty(): boolean;
}

let partyHooks: PartyHooks | null = null;
export function setPartyHooks(hooks: PartyHooks | null): void {
  partyHooks = hooks;
}
const intent = () => partyHooks?.onIntent();

// ---- Store ------------------------------------------------------------------------

/** Each load gets a number; an answer for an older one is dropped. */
let loadToken = 0;
/** Canonical positions of the queue before shuffle, for turning it off again. */
let canonical: Map<string, number> | null = null;

export const usePlayer = create<PlayerState>()((set, get) => ({
  ...loadSettings(),
  queue: [],
  index: -1,
  nowPlaying: null,
  status: 'idle',
  error: null,
  deck: 0,
  automixState: null,
  mixing: false,

  playTracks(tracks, startIndex, source) {
    if (!tracks.length) return;
    const { queue, index, shuffle } = get();
    const built = buildContextQueue(queue, index, tracks, startIndex, source);
    if (shuffle) {
      canonical = positions(built.timeline);
      set({ queue: shuffledStartingOrder(built.timeline, built.startIndex) });
      void startAt(0);
    } else {
      canonical = null;
      set({ queue: built.timeline });
      void startAt(built.startIndex);
    }
    intent();
  },

  playTrack(track, source) {
    const { queue, index } = get();
    canonical = null;
    set({ queue: buildOneOffQueue(queue, index, track, source) });
    void startAt(0);
    intent();
  },

  async playVideo(videoId, hint = {}) {
    get().playTrack(
      {
        videoId,
        title: hint.title ?? '',
        artists: hint.artist ? [{ name: hint.artist, id: null }] : [],
        album: null,
        durationSec: hint.durationSec ?? null,
        explicit: false,
        thumbnail: hint.artworkUrl ?? null,
        index: null,
        plays: null,
      },
      { title: 'Debug', type: 'other' },
    );
  },

  playNext(tracks, source = null) {
    insertUser(tracks, source, true);
    intent();
  },

  addToQueue(tracks, source = null) {
    insertUser(tracks, source, false);
    intent();
  },

  jumpTo(target) {
    const { queue, index } = get();
    if (target === index) return;
    const jumped = applyJump(queue, index, target);
    if (!jumped) return;
    set({ queue: jumped.timeline });
    void startAt(jumped.index);
    intent();
  },

  removeAt(at) {
    const { queue, index } = get();
    if (at === index || at < 0 || at >= queue.length) return;
    set({ queue: queue.filter((_, i) => i !== at), index: at < index ? index - 1 : index });
    void refillAutoplay();
    intent();
  },

  move(from, to) {
    const { queue, index } = get();
    if (from <= index || to <= index || from >= queue.length || to >= queue.length || from === to) return;
    const next = [...queue];
    const [entry] = next.splice(from, 1);
    next.splice(to, 0, entry!);
    set({ queue: next });
    intent();
  },

  clearUpNext() {
    const { queue, index } = get();
    const drop = new Set(upcomingUserQueueIndices(queue.map((e) => e.tier), index));
    set({ queue: queue.filter((_, i) => !drop.has(i)) });
    intent();
  },

  next() {
    void advance(false).then(intent);
  },

  previous() {
    const { index, queue, repeat } = get();
    const audio = getAudio();
    if (audio && audio.currentTime > BACK_RESTARTS_AFTER_SEC) return get().seek(0);
    if (index > 0) void startAt(index - 1);
    else if (repeat === 'all' && queue.length > 1) void startAt(queue.length - 1);
    else return get().seek(0);
    intent();
  },

  togglePlay() {
    // A loop mix holds both elements paused: settle it onto a playing song first.
    if (graph?.dj.loopMixing) cancelCrossfade();
    const audio = getAudio();
    const { nowPlaying, status, index } = get();
    if (!nowPlaying) return;
    const resuming = !audio || audio.paused || status === 'error';
    // A routed graph left suspended (no gesture when it was built) would play silence.
    if (resuming) void graph?.ctx.resume();
    // A listener in a party the host has locked: the press moves this device alone.
    if (partyHooks?.onLockedTransport(resuming)) return;
    if (!audio || status === 'error') {
      // A stopped or failed track starts again from where it was.
      void startAt(index, { startSec: audio?.currentTime ?? 0 });
    } else if (audio.paused) {
      // In a party, a resume waits for the instant the server schedules for everyone.
      if (!partyHooks?.shouldDeferPlay()) void audio.play().catch((e: unknown) => trackLog.w(`play() refused: ${String(e)}`));
    } else {
      cancelCrossfade();
      audio.pause();
    }
    intent();
  },

  seek(seconds) {
    const audio = getAudio();
    if (!audio) return;
    cancelCrossfade();
    audio.currentTime = Math.max(0, Math.min(seconds, audio.duration || seconds));
    updatePositionState();
    intent();
  },

  toggleShuffle() {
    const { queue, index, shuffle } = get();
    const head = queue.slice(0, index + 1);
    const upcoming = queue.slice(index + 1);
    if (!shuffle) {
      canonical = positions(queue);
      const order = shuffledUpcomingOrder(upcoming.map((e) => e.tier));
      set({ shuffle: true, queue: [...head, ...order.map((i) => upcoming[i]!)] });
    } else {
      // Back to the order the queue had, by section; tracks added since keep their place at the end.
      const known = canonical ?? new Map<string, number>();
      const restored = upcoming
        .map((entry, i) => ({ i, at: known.get(entry.entryId) ?? Number.MAX_SAFE_INTEGER - upcoming.length + i }))
        .sort((a, b) => a.at - b.at)
        .map((x) => x.i);
      const order = sectionedOrder(restored, upcoming.map((e) => e.tier));
      canonical = null;
      set({ shuffle: false, queue: [...head, ...order.map((i) => upcoming[i]!)] });
    }
    saveSettings(get());
  },

  cycleRepeat() {
    const repeat: RepeatMode = { off: 'all', all: 'one', one: 'off' }[get().repeat] as RepeatMode;
    set({ repeat });
    saveSettings(get());
    if (repeat === 'off') void refillAutoplay(true);
  },

  setAutoplay(autoplay) {
    set({ autoplay });
    saveSettings(get());
    if (autoplay) void refillAutoplay(true);
    else {
      // Turning it off takes the radio out of what's to come.
      const { queue, index } = get();
      set({ queue: queue.filter((e, i) => i <= index || e.tier !== 'autoplay') });
    }
  },

  setVolume(volume) {
    set({ volume: Math.min(1, Math.max(0, volume)), muted: false });
    applyVolumes();
    saveSettings(get());
  },

  toggleMute() {
    set({ muted: !get().muted });
    applyVolumes();
    saveSettings(get());
  },

  setCrossfade(crossfadeSec) {
    set({ crossfadeSec, automix: false });
    saveSettings(get());
    syncAutomix();
  },

  setAutomix(automix) {
    set({ automix, automixState: automix ? get().automixState : null });
    saveSettings(get());
    syncAutomix();
  },

  setAutomixBars(automixBars) {
    set({ automixBars });
    saveSettings(get());
    if (graph) graph.dj.transitionBars = automixBars;
  },


  setShowNerdStats(showNerdStats) {
    set({ showNerdStats });
    saveSettings(get());
  },
}));

// For browser-driven tests, next to __bcAudio.
if (import.meta.env.DEV) {
  Object.assign(window, {
    __bcPlayer: usePlayer,
    // AutoMix's analysis self-test: expect BPM ≈ 123.93 and Camelot 8A.
    __automixSelfTest: () => import('@/playback/automix/analysis/selftest').then((m) => m.analysisSelfTest()),
  });
}

function positions(queue: readonly QueueEntry[]): Map<string, number> {
  return new Map(queue.map((e, i) => [e.entryId, i]));
}

function insertUser(tracks: Track[], source: QueueSource | null, isNext: boolean) {
  if (!tracks.length) return;
  const { queue, index } = usePlayer.getState();
  const entries = tracks.map((t) => asEntry(t, 'user', source));
  if (index < 0) {
    usePlayer.setState({ queue: entries });
    void startAt(0);
    return;
  }
  const at = userQueueInsertionIndex(queue, index, isNext);
  usePlayer.setState({ queue: [...queue.slice(0, at), ...entries, ...queue.slice(at)] });
}

// ---- Loading and moving through the queue -------------------------------------------

/** Loads queue[at] into the deck in front and plays it. `auto` marks a move the player made on its own. */
async function startAt(at: number, { startSec = 0, auto = false, autoplay = true } = {}): Promise<void> {
  const state = usePlayer.getState();
  const entry = state.queue[at];
  if (!entry) return;
  cancelCrossfade();
  const token = ++loadToken;

  if (!extensionReady()) {
    usePlayer.setState({
      index: at,
      nowPlaying: { ...hintOf(entry.track), title: entry.track.title || 'Can’t play this here yet' },
      status: 'error',
      error: 'YouTube playback needs the BitChord extension.',
    });
    return;
  }

  usePlayer.setState({ index: at, nowPlaying: hintOf(entry.track), status: 'loading', error: null });
  updateMediaSession(usePlayer.getState().nowPlaying);
  try {
    const all = await getDecks();
    const deck = all[active]!;
    all[1 - active]!.engine.stop().catch(() => {});
    deck.gain = 1;
    applyVolumes();
    const track = await deck.engine.load(entry.track.videoId, { startSec, autoplay });
    if (token !== loadToken) return;
    applyTrack(track, entry.track);
    if (!autoplay) usePlayer.setState({ status: 'paused' });
    afterTrackStarted();
    syncAutomix();
  } catch (error) {
    if (token !== loadToken || (error instanceof Error && error.name === 'SupersededError')) return;
    const message = error instanceof Error ? error.message : String(error);
    usePlayer.setState({ status: 'error', error: message });
    // A track the player reached on its own that can't play is skipped, as on the phone.
    if (auto && planNext(true)) {
      trackLog.w(`skipping: ${message}`, entry.track.videoId);
      setTimeout(() => {
        if (token === loadToken) void advance(true);
      }, 1500);
    }
  }
}

type NextPlan = { kind: 'repeat-one' } | { kind: 'index'; queue: QueueEntry[]; index: number };

/**
 * What comes after the playing track, without doing it. `auto` is a track
 * ending (repeat-one replays it); a Next tap moves on regardless.
 */
function planNext(auto: boolean): NextPlan | null {
  const { queue, index, repeat } = usePlayer.getState();
  if (auto && repeat === 'one') return { kind: 'repeat-one' };
  if (index + 1 < queue.length) return { kind: 'index', queue, index: index + 1 };
  if (repeat !== 'off' && queue.length) {
    // Round again: the hand-queued tracks have had their turn; the context repeats.
    const tiers = queue.map((e) => e.tier);
    const played = new Set(playedUserQueueIndices(tiers, index));
    const kept = queue.filter((e, i) => !played.has(i) && (e.tier !== 'user' || i > index));
    return kept.length ? { kind: 'index', queue: kept, index: 0 } : null;
  }
  return null;
}

async function advance(auto: boolean): Promise<void> {
  let plan = planNext(auto);
  if (!plan && usePlayer.getState().autoplay) {
    await refillAutoplay(true);
    plan = planNext(auto);
  }
  if (!plan) {
    // The end: leave the last track loaded and paused, as a music app does.
    if (auto) usePlayer.setState({ status: 'paused' });
    return;
  }
  if (plan.kind === 'repeat-one') {
    const audio = getAudio();
    if (audio) {
      audio.currentTime = 0;
      void audio.play();
    }
    return;
  }
  usePlayer.setState({ queue: plan.queue });
  await startAt(plan.index, { auto });
}

/** Once a track is under way: prune hand-queued tracks already played, and top up AutoPlay. */
function afterTrackStarted() {
  const { queue, index } = usePlayer.getState();
  const played = new Set(playedUserQueueIndices(queue.map((e) => e.tier), index));
  if (played.size) {
    usePlayer.setState({
      queue: queue.filter((_, i) => !played.has(i)),
      index: index - [...played].filter((i) => i < index).length,
    });
  }
  void refillAutoplay();
}

// ---- AutoPlay ---------------------------------------------------------------------

/**
 * How many AutoPlay tracks wait after the playing one (Autoplay.MAX_QUEUED_AUTOPLAY).
 * Topped back up on every track change, as on the phone: a suggestion that
 * starts playing is replaced by a new one at the end, rather than the whole
 * station arriving in one burst once the queue has run dry.
 */
const MAX_QUEUED_AUTOPLAY = 10;
let refilling: Promise<void> | null = null;
/** The track the last top-up was seeded from, so one track isn't asked about twice. */
let autoplaySeed: string | null = null;
/** Seeds that gave nothing new, so the same empty radio isn't asked for again. */
const exhaustedSeeds = new Set<string>();

/**
 * [anySeed]: when the playing track's radio has nothing new left, seed from
 * the latest earlier track in the queue whose radio still might (the empty
 * queue watchdog, ensureAutoplay).
 */
function refillAutoplay(force = false, anySeed = false): Promise<void> {
  const { autoplay, repeat, queue, index } = usePlayer.getState();
  if (!autoplay || repeat !== 'off' || index < 0) return Promise.resolve();
  // A party's AutoPlay goes through the server (party/partySync.ts), never straight onto this queue.
  if (partyHooks?.inParty()) return Promise.resolve();
  const needed = MAX_QUEUED_AUTOPLAY - queue.slice(index + 1).filter((e) => e.tier === 'autoplay').length;
  if (needed <= 0) return Promise.resolve();
  // Seeded from the playing track (loadAutoplayForCurrentTrack), not the queue's tail.
  const playing = queue[index]?.track;
  let seed = playing;
  if (seed && exhaustedSeeds.has(seed.videoId) && anySeed) {
    seed = queue
      .slice(0, index)
      .reverse()
      .find((e) => !exhaustedSeeds.has(e.track.videoId))?.track;
  }
  if (!playing || !seed || exhaustedSeeds.has(seed.videoId)) return Promise.resolve();
  if (!force && autoplaySeed === seed.videoId) return Promise.resolve();
  autoplaySeed = seed.videoId;
  refilling ??= (async () => {
    try {
      const { fetchRadio } = await import('@/data/music/api');
      const { candidates, seedIsVideo } = await fetchRadio(seed.videoId);
      const current = usePlayer.getState();
      const extra = extendStation(
        current.queue.map((e) => e.track),
        candidates,
        needed,
        seedIsVideo,
      );
      if (!extra.length) {
        exhaustedSeeds.add(seed.videoId);
        return;
      }
      const source: QueueSource = { title: `${seed.title} Radio`, type: 'radio', id: `RDAMVM${seed.videoId}` };
      // Still on the same track? One that moved on gets its own top-up.
      if (current.queue[current.index]?.track.videoId !== playing.videoId || !current.autoplay) {
        autoplaySeed = null;
        return;
      }
      usePlayer.setState({ queue: [...current.queue, ...extra.map((t) => asEntry(t, 'autoplay', source))] });
      trackLog.i(`AutoPlay added ${extra.length} tracks from ${seed.title}'s radio`, seed.videoId);
    } catch (error) {
      trackLog.w(`AutoPlay couldn't load the radio: ${String(error)}`, seed.videoId);
      autoplaySeed = null;
    } finally {
      refilling = null;
    }
  })();
  return refilling;
}

/** How often an empty AutoPlay is asked for again while a track plays. */
const EMPTY_RETRY_MS = 15_000;
let lastEmptyRetry = 0;

/**
 * The watchdog for an AutoPlay that is on but has nothing after the playing
 * track. A top-up runs only when a track starts, so a radio that failed to
 * load (a network blip, the extension not ready yet), one dropped because the
 * track changed while it loaded, or a seed whose radio had nothing new left
 * the queue empty until the music stopped. Called as the track plays.
 */
function ensureAutoplay() {
  const { autoplay, repeat, queue, index, status } = usePlayer.getState();
  if (!autoplay || repeat !== 'off' || index < 0 || status !== 'playing' || partyHooks?.inParty()) return;
  if (index + 1 < queue.length || refilling) return;
  const now = Date.now();
  if (now - lastEmptyRetry < EMPTY_RETRY_MS) return;
  lastEmptyRetry = now;
  trackLog.i('AutoPlay is on but nothing is queued; asking for the radio again', queue[index]?.track.videoId ?? null);
  void refillAutoplay(true, true);
}

// ---- Crossfade ----------------------------------------------------------------------

/**
 * The fade is stepped by a timer, not requestAnimationFrame: Chrome stops
 * frames altogether in a background tab, or a window covered by another,
 * which is where music usually plays from. The new track then sat at volume
 * 0 under the old one's end, silent until the tab was looked at again.
 * Timers keep running there (at most a second apart), and each step works
 * the volumes out from the clock, so a late step only lands further along.
 */
const FADE_STEP_MS = 40;

let fade: { token: number; frame: number; pending: boolean } | null = null;

function cancelCrossfade() {
  // AutoMix's blend or preloaded next track goes the same way as a fade: the deck in front plays on.
  if (graph && (graph.dj.mixing || mixLoaded)) {
    mixLoaded = null;
    graph.dj.cancel();
    if (decks) void decks[1 - active]!.engine.stop().catch(() => {});
  } else graph?.dj.cancel();
  if (!fade) return;
  clearTimeout(fade.frame);
  const wasPending = fade.pending;
  fade = null;
  if (!decks) return;
  // Whichever deck isn't in front stops; the one in front plays on at full volume.
  void decks[1 - active]!.engine.stop().catch(() => {});
  decks[active]!.gain = 1;
  applyVolumes();
  if (wasPending) loadToken++;
}

/** Called as the track in front plays: within the crossfade length of its end, start the next one underneath. */
function maybeCrossfade() {
  const state = usePlayer.getState();
  if (!decks || fade || state.automix || !state.crossfadeSec || state.status !== 'playing') return;
  const front = decks[active]!;
  const remaining = front.audio.duration - front.audio.currentTime;
  if (!Number.isFinite(remaining) || remaining > state.crossfadeSec || remaining < 1) return;
  const plan = planNext(true);
  if (!plan || plan.kind !== 'index') return;

  const back = decks[1 - active]!;
  const entry = plan.queue[plan.index]!;
  const token = ++loadToken;
  fade = { token, frame: 0, pending: true };
  back.gain = 0;
  applyVolumes();
  trackLog.i(`crossfading into the next track over ${Math.round(remaining)}s`, entry.track.videoId);

  back.engine
    .load(entry.track.videoId, { autoplay: true })
    .then((track) => {
      if (!fade || fade.token !== token || !decks) return;
      fade.pending = false;
      const from = front;
      const to = back;
      // The new track is the one playing now, as far as everything else knows.
      active = 1 - active;
      usePlayer.setState((s) => ({ queue: plan.queue, index: plan.index, deck: s.deck + 1, status: 'playing' }));
      applyTrack(track, entry.track);
      afterTrackStarted();

      const length = Math.max(0.5, Math.min(from.audio.duration - from.audio.currentTime, usePlayer.getState().crossfadeSec)) * 1000;
      const started = performance.now();
      const step = () => {
        if (!fade || fade.token !== token) return;
        const t = Math.min(1, (performance.now() - started) / length);
        // Equal-power: the sum stays level through the middle.
        from.gain = Math.cos((t * Math.PI) / 2);
        to.gain = Math.sin((t * Math.PI) / 2);
        applyVolumes();
        if (t < 1 && !from.audio.ended) fade.frame = window.setTimeout(step, FADE_STEP_MS);
        else {
          fade = null;
          to.gain = 1;
          from.gain = 1;
          void from.engine.stop().catch(() => {});
          applyVolumes();
        }
      };
      step();
    })
    .catch((error: unknown) => {
      if (!fade || fade.token !== token) return;
      trackLog.w(`crossfade load failed: ${String(error)}; the next track starts when this one ends`, entry.track.videoId);
      fade = null;
      back.gain = 1;
      void back.engine.stop().catch(() => {});
      applyVolumes();
      // If the track in front already ended while this loaded, move on now.
      if (front.audio.ended) void advance(true);
    });
}

function applyTrack(track: LoadedTrack, from?: Track) {
  const nowPlaying: NowPlaying = {
    videoId: track.videoId,
    // YouTube Music metadata (from the queue) beats the video's own title.
    title: from?.title || track.title,
    // Auto-generated YouTube channels are named "Artist - Topic".
    artist: (from && artistNames(from)) || track.author.replace(/ - Topic$/, ''),
    artworkUrl: from?.thumbnail ?? track.thumbnails[0]?.url ?? null,
    durationSec: track.durationSec || from?.durationSec || 0,
    streamInfo: `${track.format} via ${track.client}`,
    stream: { ...track.stream, client: track.client },
  };
  usePlayer.setState({ nowPlaying, error: null });
  updateMediaSession(nowPlaying);
  followVideo();
}

/** A music video's picture follows the deck in front (playback/youtube/videoDeck.ts, loaded with the engine). */
function followVideo() {
  const deck = decks?.[active];
  if (!deck) return;
  void import('@/playback/youtube/videoDeck').then((m) => m.followVideo(deck.engine, deck.audio));
}

const endedListeners = new Set<() => void>();
/** Called when the track in front plays to its end, before the queue moves on; a repeat-one loop included. */
export function onTrackEnded(listener: () => void): () => void {
  endedListeners.add(listener);
  return () => endedListeners.delete(listener);
}

/** The elements' own events are the source of truth for play state; only the deck in front speaks. */
function bindElement(deck: Deck, n: number) {
  const element = deck.audio;
  const inFront = () => active === n;
  const set = (status: PlayerStatus) => {
    if (!inFront() || holding) return;
    if (usePlayer.getState().status === 'error' && status !== 'playing') return;
    usePlayer.setState({ status });
  };
  element.addEventListener('playing', () => set('playing'));
  element.addEventListener('pause', () => {
    if (!element.ended) set('paused');
  });
  element.addEventListener('waiting', () => set('buffering'));
  element.addEventListener('ended', () => {
    if (!inFront() || holding) return;
    endedListeners.forEach((listener) => listener());
    // The track ran out before the crossfade's next one could start: give up
    // on the fade and play the next track here. Waiting on it was silence for
    // as long as that load took, which on a slow or failing load (each client
    // gets 15 s for first audio) came to half a minute.
    if (fade?.pending) cancelCrossfade();
    // AutoMix's outgoing track ran out before the blend's midpoint (a stall): the incoming one is already playing.
    if (graph?.dj.forceHandoff()) return;
    // The queue moving on by itself is the user's intent, as far as a party is concerned.
    void advance(true).then(intent);
  });
  element.addEventListener('timeupdate', () => {
    if (!inFront()) return;
    maybeCrossfade();
    ensureAutoplay();
  });
  for (const event of ['durationchange', 'ratechange', 'seeked'] as const) {
    element.addEventListener(event, () => {
      if (inFront()) updatePositionState();
    });
  }
  element.addEventListener('play', () => {
    if (inFront() && 'mediaSession' in navigator) navigator.mediaSession.playbackState = 'playing';
  });
  element.addEventListener('pause', () => {
    if (inFront() && 'mediaSession' in navigator) navigator.mediaSession.playbackState = 'paused';
  });
}

// ---- Media Session (prompt §5.2 step 6) ---------------------------------------------

let handlersBound = false;

function updateMediaSession(track: NowPlaying | null) {
  if (!('mediaSession' in navigator)) return;
  const session = navigator.mediaSession;
  if (!track) {
    session.metadata = null;
    return;
  }
  session.metadata = new MediaMetadata({
    title: track.title,
    artist: track.artist,
    artwork: track.artworkUrl
      ? [96, 256, 512].map((px) => ({ src: artworkAtPixels(track.artworkUrl!, px), sizes: `${px}x${px}` }))
      : [],
  });

  if (handlersBound) return;
  handlersBound = true;
  const handle = (action: MediaSessionAction, handler: MediaSessionActionHandler | null) => {
    try {
      session.setActionHandler(action, handler);
    } catch {
      // Not every browser knows every action.
    }
  };
  const player = () => usePlayer.getState();
  // Through the store, so a party hears about them like any other press.
  // A loop mix holds the elements paused while it plays: that's playing, for the keys.
  const sounding = () => graph?.dj.loopMixing || getAudio()?.paused === false;
  handle('play', () => !sounding() && player().togglePlay());
  handle('pause', () => sounding() && player().togglePlay());
  handle('seekto', (details) => {
    if (details.seekTime !== undefined) player().seek(details.seekTime);
  });
  handle('seekbackward', (details) => player().seek((getAudio()?.currentTime ?? 0) - (details.seekOffset ?? 10)));
  handle('seekforward', (details) => player().seek((getAudio()?.currentTime ?? 0) + (details.seekOffset ?? 10)));
  handle('previoustrack', () => player().previous());
  handle('nexttrack', () => player().next());
}

function updatePositionState() {
  const audio = getAudio();
  if (!audio || !('mediaSession' in navigator) || !Number.isFinite(audio.duration) || audio.duration <= 0) return;
  try {
    navigator.mediaSession.setPositionState({
      duration: audio.duration,
      playbackRate: audio.playbackRate,
      position: Math.min(audio.currentTime, audio.duration),
    });
  } catch {
    // Position past duration during a seek; the next event corrects it.
  }
}

// ---- The party's door (party/partySync.ts): moves the player, reports nothing ----------

const PARTY_SOURCE: QueueSource = { title: 'Listen Together', type: 'queue' };

export const partyPlayer = {
  /** Puts the party's running order on the player at [index], at [startSec]; starts it only when [play]. */
  load(tracks: Track[], tiers: ('user' | 'autoplay')[], index: number, startSec: number, play: boolean): Promise<void> {
    canonical = null;
    usePlayer.setState({ queue: tracks.map((t, i) => asEntry(t, tiers[i] ?? 'user', PARTY_SOURCE)) });
    return startAt(index, { startSec, autoplay: play });
  },
  play(): void {
    const audio = getAudio();
    if (audio?.paused) void audio.play().catch((e: unknown) => trackLog.w(`party play() refused: ${String(e)}`));
  },
  pause(): void {
    const audio = getAudio();
    if (audio && !audio.paused) {
      cancelCrossfade();
      audio.pause();
    }
  },
  seekTo(seconds: number): void {
    const audio = getAudio();
    if (!audio) return;
    cancelCrossfade();
    audio.currentTime = Math.max(0, seconds);
    updatePositionState();
  },
  /** Replaces everything after the playing track; the playing track is left alone. */
  setUpcoming(tracks: Track[], tiers: ('user' | 'autoplay')[]): void {
    const { queue, index } = usePlayer.getState();
    if (index < 0) return;
    usePlayer.setState({ queue: [...queue.slice(0, index + 1), ...tracks.map((t, i) => asEntry(t, tiers[i] ?? 'user', PARTY_SOURCE))] });
  },
};
