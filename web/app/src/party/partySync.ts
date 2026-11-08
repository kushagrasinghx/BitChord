import type { Track } from '@/data/music/types';
import type { QueueTier } from '@/playback/queue/timeline';
import { getAudio, partyPlayer, setPartyHooks, usePlayer } from '@/state/player';
import { controlsLocked, msUntilStart, party, partyPositionMs, useParty, type PartyState } from './listenTogether';
import type { PartyTrack } from './models';
import { autoplaySupplierId, detectSingleMove, partyQueueIndexOf, partyUpcomingAfter } from './queueReading';

/**
 * Makes the player obey the party, and the party obey this player: a port of
 * PartySync.kt.
 *
 * The two directions come through different doors (state/player.ts):
 * - outbound, [onIntent], is called by every user action in the player store,
 *   and by the queue moving on by itself;
 * - inbound, [reconcile], writes through `partyPlayer`, which reports nothing,
 *   so nothing done here can come back as an intent.
 *
 * Following in time: a resume waits for the server's scheduled start, a new
 * control is landed on exactly, and drift between controls is corrected only
 * when it's real (twice in a row past 1.2 s, then left alone for 6 s).
 *
 * Not ported: losing audio focus to another app, which a browser tab doesn't
 * have. And the audio element's output latency can't be read (there's no
 * AudioContext in the player), so it isn't subtracted; it's the same few tens
 * of milliseconds on every device of one kind.
 */

const TICK_MS = 700;
const DRIFT_LIMIT_MS = 1_200;
const DRIFT_STRIKES = 2;
const DRIFT_COOLDOWN_MS = 6_000;
const PAUSED_TOLERANCE_MS = 400;
const ALIGN_TOLERANCE_MS = 120;
const SEEK_REPORT_FLOOR_MS = 1_000;
const DEFERRED_PLAY_TIMEOUT_MS = 1_800;
const PUBLISH_DEBOUNCE_MS = 120;
const INTENT_QUIET_MS = 4_500;
const MAX_UPCOMING = 25;
/** Autoplay.MAX_QUEUED_AUTOPLAY. */
const MAX_QUEUED_AUTOPLAY = 10;
const QUEUE_ECHO_TIMEOUT_MS = 5_000;

// ---- Tracks across the boundary ----------------------------------------------------

export function toPartyTrack(track: Track, tier: QueueTier, playerDurationMs = 0): PartyTrack {
  return {
    videoId: track.videoId,
    title: track.title,
    artist: track.artists.map((a) => a.name).join(', '),
    thumbnailUrl: track.thumbnail,
    // The decoder's figure when there is one, else what the row claimed.
    durationMs: playerDurationMs > 0 ? Math.round(playerDurationMs) : track.durationSec ? track.durationSec * 1000 : null,
    fromAutoplay: tier === 'autoplay',
  };
}

export function fromPartyTrack(track: PartyTrack): Track {
  return {
    videoId: track.videoId,
    title: track.title,
    artists: track.artist ? [{ name: track.artist, id: null }] : [],
    album: null,
    durationSec: track.durationMs ? Math.round(track.durationMs / 1000) : null,
    explicit: false,
    thumbnail: track.thumbnailUrl,
    index: null,
    plays: null,
  };
}

const tierOf = (t: PartyTrack): 'user' | 'autoplay' => (t.fromAutoplay ? 'autoplay' : 'user');

// ---- Reading the player -------------------------------------------------------------

function current() {
  const { queue, index } = usePlayer.getState();
  return index >= 0 ? (queue[index] ?? null) : null;
}

const positionMs = () => Math.max(0, (getAudio()?.currentTime ?? 0) * 1000);

/** Whether the player means to be playing: going, stalled, or loading a track it will start. */
function playWhenReady(): boolean {
  const { status } = usePlayer.getState();
  return status === 'playing' || status === 'buffering' || (status === 'loading' && loadingVideoId === null);
}

// ---- State --------------------------------------------------------------------------

let reconcileQuietUntil = 0;
let awaitPlaybackSeq = Number.MAX_SAFE_INTEGER;
let awaitQueueSeq = Number.MAX_SAFE_INTEGER;
let alignedSeq = -1;
let lastAnchorMs = 0;
let lastPositionMs = 0;
let lastTrackId: string | null = null;
let lastIsPlaying = false;
let loadingVideoId: string | null = null;
let locallyPaused = false;
let driftStrikes = 0;
let driftCooldownUntil = 0;
let publishTimer: ReturnType<typeof setTimeout> | undefined;
let startTimer: ReturnType<typeof setTimeout> | undefined;
let lastPartyCode: string | null = null;
/** The personal queue, put back after the party (PartyPersonalQueueStash). */
let stash: { queue: ReturnType<typeof usePlayer.getState>['queue']; index: number; positionSec: number; wasPlaying: boolean } | null = null;

const setAwaiting = (value: boolean): void => {
  if (useParty.getState().awaitingStart !== value) useParty.setState({ awaitingStart: value });
};

// ---- Outbound ------------------------------------------------------------------------

function onIntent(): void {
  const state = useParty.getState();
  if (!state.code || controlsLocked(state)) return;
  reconcileQuietUntil = performance.now() + INTENT_QUIET_MS;
  // Nothing published yet: no seq to wait for, and someone else's control mustn't clear the window.
  awaitPlaybackSeq = Number.MAX_SAFE_INTEGER;
  awaitQueueSeq = Number.MAX_SAFE_INTEGER;
  clearTimeout(publishTimer);
  publishTimer = setTimeout(publish, PUBLISH_DEBOUNCE_MS);
}

function publish(): void {
  const state = useParty.getState();
  if (!state.code) return;
  const { queue, index } = usePlayer.getState();
  const entry = current();
  if (!entry) return;
  const audio = getAudio();
  const track = toPartyTrack(entry.track, entry.tier, audio && Number.isFinite(audio.duration) ? audio.duration * 1000 : 0);
  const position = positionMs();
  // During a deferred resume the player isn't going yet; what the user asked for is.
  const wantsPlaying = state.awaitingStart || playWhenReady();
  const basePlayback = state.playback.seq;
  const baseQueue = state.queue.seq;
  let playbackControls = 0;
  let queueControls = 0;

  // The party's queue is the server's: past and current, then what's queued by hand or by AutoPlay.
  const localItems = [...queue.slice(0, index + 1), ...queue.slice(index + 1).filter((e) => e.tier !== 'context')];
  const localIds = localItems.map((e) => e.track.videoId);
  const trackIndex = index;
  const clampedIds = localIds.slice(0, Math.min(localIds.length, trackIndex + 1 + MAX_UPCOMING));

  const partyIndex = partyQueueIndexOf(state.queue, state.playback, state.playback.track?.videoId);
  const upcomingParty = partyIndex >= 0 ? state.queue.items.slice(partyIndex + 1) : [];
  const partyIds = state.queue.items.map((t) => t.videoId);

  if (state.playback.track?.videoId !== track.videoId && clampedIds.length === 1 && upcomingParty.length) {
    // A new song on its own: the party's hand-queued songs carry on after it.
    const keep = upcomingParty.filter((t) => !t.fromAutoplay).slice(0, MAX_UPCOMING);
    if (queue.length === 1 && keep.length) partyPlayer.setUpcoming(keep.map(fromPartyTrack), keep.map(tierOf));
    party.setQueue([track, ...keep], 0);
    queueControls++;
  } else if (clampedIds.length !== partyIds.length || clampedIds.some((id, i) => id !== partyIds[i])) {
    const move =
      partyIds.length === clampedIds.length && trackIndex < partyIds.length && partyIds[trackIndex] === clampedIds[trackIndex]
        ? detectSingleMove(partyIds, clampedIds)
        : null;
    if (move && move.fromIndex > trackIndex && move.toIndex > trackIndex) {
      party.queueMove(move.fromIndex, move.toIndex, move.videoId);
    } else {
      party.setQueue(
        localItems.slice(0, clampedIds.length).map((e) => toPartyTrack(e.track, e.tier)),
        trackIndex,
      );
    }
    queueControls++;
  }

  if (state.playback.track?.videoId !== track.videoId) {
    // After the queue, never before: a track pointing into a running order nobody has yet plays the wrong song.
    party.setTrack(track, position, wantsPlaying);
    playbackControls++;
  } else if (state.playback.isPlaying !== wantsPlaying) {
    if (wantsPlaying) party.play(position);
    else party.pause(position);
    playbackControls++;
  } else {
    // Same track, same state: a seek, unless it barely moved (two devices finishing a track together).
    const partyPosition = partyPositionMs();
    if (partyPosition === null || Math.abs(position - partyPosition) > SEEK_REPORT_FLOOR_MS) {
      party.seek(position);
      playbackControls++;
    }
  }

  awaitPlaybackSeq = basePlayback + playbackControls;
  awaitQueueSeq = baseQueue + queueControls;
  if (!playbackControls && !queueControls) reconcileQuietUntil = 0;
}

/** A play to hold back for the party's scheduled instant; partySync starts it. */
function shouldDeferPlay(): boolean {
  const state = useParty.getState();
  if (!state.code || state.connection !== 'live' || !state.clockSynced) return false;
  if (playWhenReady()) return false;
  setAwaiting(true);
  clearTimeout(startTimer);
  // The press must do something even if the party never answers.
  startTimer = setTimeout(() => {
    if (!useParty.getState().awaitingStart) return;
    setAwaiting(false);
    if (getAudio()?.paused) partyPlayer.play();
  }, DEFERRED_PLAY_TIMEOUT_MS);
  return true;
}

/** A listener in a locked party: their own pause, and a resume that rejoins where the party is. */
function onLockedTransport(playing: boolean): boolean {
  const state = useParty.getState();
  if (!controlsLocked(state)) return false;
  if (playing) {
    locallyPaused = false;
    if (!state.playback.isPlaying) return true;
    const want = partyPositionMs();
    if (want !== null && state.clockSynced) partyPlayer.seekTo(want / 1000);
    partyPlayer.play();
  } else {
    locallyPaused = true;
    partyPlayer.pause();
  }
  return true;
}

// ---- Inbound -------------------------------------------------------------------------

function reconcile(): void {
  const state = useParty.getState();
  if (!state.code) {
    loadingVideoId = null;
    locallyPaused = false;
    lastAnchorMs = 0;
    lastPositionMs = 0;
    lastTrackId = null;
    lastIsPlaying = false;
    setAwaiting(false);
    return;
  }
  if (locallyPaused && !controlsLocked(state)) locallyPaused = false;
  if (performance.now() < reconcileQuietUntil) return;
  const target = state.playback;
  const track = target.track;
  if (!track) return seedEmptyParty(state);

  const entry = current();
  if (entry?.track.videoId !== track.videoId) return load(state);
  if (loadingVideoId === track.videoId && usePlayer.getState().status === 'loading') return;
  loadingVideoId = null;
  reconcileQueue(state);

  if (locallyPaused) {
    setAwaiting(false);
    return;
  }

  if (!target.isPlaying) {
    setAwaiting(false);
    if (playWhenReady()) partyPlayer.pause();
    if (Math.abs(positionMs() - target.positionMs) > PAUSED_TOLERANCE_MS) partyPlayer.seekTo(target.positionMs / 1000);
    return;
  }
  // A playing party can't be joined in time without a measured clock.
  if (!state.clockSynced) return;

  const want = partyPositionMs();
  if (!playWhenReady()) {
    const wait = msUntilStart();
    if (wait > 0) {
      // Woken for the scheduled instant, not the next tick: late is what this avoids.
      clearTimeout(startTimer);
      startTimer = setTimeout(reconcile, wait);
      return;
    }
    if (want !== null) partyPlayer.seekTo(want / 1000);
    setAwaiting(false);
    clearTimeout(startTimer);
    partyPlayer.play();
    alignedSeq = target.seq;
    return;
  }
  setAwaiting(false);
  if (want === null) return;
  // Only a settled player can be measured.
  if (usePlayer.getState().status !== 'playing') {
    driftStrikes = 0;
    return;
  }

  const drift = positionMs() - want;
  if (target.seq !== alignedSeq) {
    // A control is landed on exactly, even by the device that sent it; a queue-only change isn't one.
    const anchorChanged =
      lastAnchorMs !== target.anchorMs || lastPositionMs !== target.positionMs || lastTrackId !== track.videoId || lastIsPlaying !== target.isPlaying;
    alignedSeq = target.seq;
    lastAnchorMs = target.anchorMs;
    lastPositionMs = target.positionMs;
    lastTrackId = track.videoId;
    lastIsPlaying = target.isPlaying;
    if (anchorChanged && Math.abs(drift) > ALIGN_TOLERANCE_MS) {
      partyPlayer.seekTo(want / 1000);
      return;
    }
  }
  if (Math.abs(drift) <= DRIFT_LIMIT_MS) {
    driftStrikes = 0;
    return;
  }
  // A seek costs a re-buffer; asked for twice in a row, then left alone to settle.
  const now = performance.now();
  if (now < driftCooldownUntil) return;
  if (++driftStrikes < DRIFT_STRIKES) return;
  driftStrikes = 0;
  driftCooldownUntil = now + DRIFT_COOLDOWN_MS;
  partyPlayer.seekTo(want / 1000);
}

/** The party's running order onto the player, at its position; started only if the party is going now. */
function load(state: PartyState): void {
  const track = state.playback.track;
  if (!track || loadingVideoId === track.videoId) return;
  loadingVideoId = track.videoId;
  // A not-found index plays the track alone, never queue[0].
  const at = partyQueueIndexOf(state.queue, state.playback, track.videoId);
  const items = at >= 0 ? state.queue.items : [track];
  const startMs = partyPositionMs() ?? state.playback.positionMs;
  const play = state.playback.isPlaying && state.clockSynced && msUntilStart() <= 0;
  void partyPlayer
    .load(items.map(fromPartyTrack), items.map(tierOf), Math.max(0, at), startMs / 1000, play)
    .finally(() => {
      loadingVideoId = null;
      reconcile();
    });
}

/** Keeps the player's upcoming tracks equal to the party's, without touching the playing one. */
function reconcileQueue(state: PartyState): void {
  if (!state.queue.items.length) return;
  const entry = current();
  if (!entry) return;
  const at = partyQueueIndexOf(state.queue, state.playback, entry.track.videoId);
  if (at < 0) return;
  const desired = state.queue.items.slice(at + 1, at + 1 + MAX_UPCOMING);
  const { queue, index } = usePlayer.getState();
  const local = queue.slice(index + 1).map((e) => e.track.videoId);
  if (local.length === desired.length && local.every((id, i) => id === desired[i]!.videoId)) return;
  partyPlayer.setUpcoming(desired.map(fromPartyTrack), desired.map(tierOf));
}

/** A new party plays nothing until someone presses something; the host hands it what's already playing. */
function seedEmptyParty(state: PartyState): void {
  if (state.connection !== 'live' || state.you?.isHost !== true || !current()) return;
  const { queue, index } = usePlayer.getState();
  const keep = queue.slice(index + 1).filter((e) => e.tier !== 'context');
  if (keep.length !== queue.length - index - 1) partyPlayer.setUpcoming(keep.map((e) => e.track), keep.map((e) => (e.tier === 'autoplay' ? 'autoplay' : 'user')));
  publish();
}

function onEnteredParty(): void {
  const { queue, index, status } = usePlayer.getState();
  stash = queue.length ? { queue, index, positionSec: getAudio()?.currentTime ?? 0, wasPlaying: status === 'playing' } : null;
}

function onLeftParty(): void {
  loadingVideoId = null;
  const saved = stash;
  stash = null;
  if (!saved || saved.index < 0) return;
  const entries = saved.queue;
  void partyPlayer.load(
    entries.map((e) => e.track),
    entries.map((e) => (e.tier === 'autoplay' ? 'autoplay' : 'user')),
    saved.index,
    saved.positionSec,
    saved.wasPlaying,
  );
}

// ---- AutoPlay: one supplier for the whole party ------------------------------------------

let autoplaySeed: string | null = null;
let topping: Promise<void> | null = null;

/**
 * Tops the party up to 10 AutoPlay tracks after the playing one, from the
 * elected supplier only, counted against the server's queue (never the local
 * copy, which lags while reconcile is held off) and one batch at a time,
 * waiting for its echo: otherwise the same station went out twice.
 */
function maybeTopUpAutoplay(): void {
  const state = useParty.getState();
  const playing = state.playback.track;
  if (!state.code || !playing || !state.playback.autoplayEnabled) return;
  if (autoplaySupplierId(state.members, state.hostOnlyControl) !== state.you?.memberId) return;
  if (partyQueueIndexOf(state.queue, state.playback, playing.videoId) < 0) return;
  const waiting = partyUpcomingAfter(state.queue, state.playback, playing.videoId);
  const needed = MAX_QUEUED_AUTOPLAY - waiting.filter((t) => t.fromAutoplay).length;
  if (needed <= 0 || autoplaySeed === playing.videoId || topping) return;
  autoplaySeed = playing.videoId;
  topping = (async () => {
    try {
      const [{ fetchRadio }, { extendStation }] = await Promise.all([import('@/data/music/api'), import('@/playback/queue/builder')]);
      const { candidates, seedIsVideo } = await fetchRadio(playing.videoId);
      const latest = useParty.getState();
      if (latest.playback.track?.videoId !== playing.videoId) return;
      const existing = [...usePlayer.getState().queue.map((e) => e.track), ...latest.queue.items.map(fromPartyTrack)];
      const extra = extendStation(existing, candidates, needed, seedIsVideo);
      const waitingIds = new Set(partyUpcomingAfter(latest.queue, latest.playback, playing.videoId).map((t) => t.videoId));
      const fresh = extra.filter((t) => !waitingIds.has(t.videoId));
      if (!fresh.length) return;
      party.queueAdd(fresh.map((t) => ({ ...toPartyTrack(t, 'autoplay') })));
      const added = fresh[0]!.videoId;
      const landed = await new Promise<boolean>((resolve) => {
        const timer = setTimeout(() => (off(), resolve(false)), QUEUE_ECHO_TIMEOUT_MS);
        const off = useParty.subscribe((s) => {
          if (s.queue.items.some((t) => t.videoId === added)) {
            clearTimeout(timer);
            off();
            resolve(true);
          }
        });
      });
      // Refused (a full queue, a locked party): free the seed to try again.
      if (!landed) autoplaySeed = null;
    } catch {
      autoplaySeed = null;
    } finally {
      topping = null;
    }
  })();
}

// ---- Wiring ---------------------------------------------------------------------------

/** Starts following the party; returns the stop. Mounted once, in the shell. */
export function startPartySync(): () => void {
  setPartyHooks({
    onIntent,
    shouldDeferPlay,
    onLockedTransport,
    inParty: () => !!useParty.getState().code,
  });
  let key = '';
  const off = useParty.subscribe((state) => {
    const next = `${state.playback.seq}|${state.queue.seq}|${state.code}|${state.clockSynced}`;
    if (next === key) return;
    key = next;
    if (state.code !== lastPartyCode) {
      const was = lastPartyCode !== null;
      lastPartyCode = state.code;
      if (!was && state.code) onEnteredParty();
      else if (was && !state.code) onLeftParty();
    }
    // Every control this device sent has come back around (or someone moved the party past ours).
    if (state.playback.seq >= awaitPlaybackSeq && state.queue.seq >= awaitQueueSeq) reconcileQuietUntil = 0;
    reconcile();
    maybeTopUpAutoplay();
  });
  const tick = setInterval(() => {
    reconcile();
    maybeTopUpAutoplay();
  }, TICK_MS);
  return () => {
    off();
    clearInterval(tick);
    clearTimeout(publishTimer);
    clearTimeout(startTimer);
    setPartyHooks(null);
  };
}
