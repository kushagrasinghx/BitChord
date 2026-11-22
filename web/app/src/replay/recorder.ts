import type { ReplayMonth, ReplayTally } from '@bitchord/shared-kotlin';
import { trackLog } from '@/playback/trackLog';
import { getPlayhead, usePlayer } from '@/state/player';
import { monthKey } from './periods';
import { readMonth, writeMonth } from './store';

/**
 * Turns the player's ticking into the Replay's numbers, as the phone's
 * ListeningRecorder does, with its rule (the shared ListeningTally): every
 * tick while audio is coming out adds the wall-clock time since the last one,
 * capped at 8 s, so a seek or a pause can't inflate it, and a play counts once
 * min(half the track, 4 min) has gone by, never under 30 s.
 *
 * The page has no sampler of its own to borrow, so this ticks every
 * [SAMPLE_MS], the phone's interval, and reads whether audio is moving from
 * the player rather than trusting the timer.
 */
const SAMPLE_MS = 5_000;

type SharedKotlin = typeof import('@bitchord/shared-kotlin');

/** The shared Kotlin, loaded at the first sound rather than with the page (it is a few hundred kB). */
let kotlin: SharedKotlin | null = null;
let loading: Promise<void> | null = null;
let tally: ReplayTally | null = null;
/** The month being written to, as the shared OpenBucket; null until the first tick. */
let open: ReplayMonth | null = null;
let dirty = false;
/** Reads and writes, one after another, so a flush can't overtake the tick before it. */
let work: Promise<void> = Promise.resolve();

function enqueue(job: () => Promise<void>): Promise<void> {
  work = work.then(job).catch((error: unknown) => trackLog.w(`replay: ${String(error)}`));
  return work;
}

async function flush() {
  if (!open || !dirty) return;
  dirty = false;
  await writeMonth(open.month, open.json());
}

/** The month [key], loading it and putting the last one away when the month rolls over. */
async function monthFor(key: string): Promise<ReplayMonth> {
  if (open?.month === key) return open;
  await flush();
  open = new kotlin!.ReplayMonth(key, (await readMonth(key)) ?? null);
  return open;
}

/** Writes out what hasn't been yet: a reader calls this first, so the Replay agrees with what just played. */
export function flushListening(): Promise<void> {
  return enqueue(flush);
}

/** Playback paused, stopped or failed: the next tick anchors rather than counts (ListeningRecorder.onStopped). */
function stopped() {
  tally?.stop();
  void flushListening();
}

function sample() {
  const { status, nowPlaying, queue, index } = usePlayer.getState();
  const head = getPlayhead();
  if (!nowPlaying || status !== 'playing' || !head || head.paused) return;
  if (!kotlin || !tally) {
    // The tick that would have anchored the track waits for the rules; the next one anchors instead.
    loading ??= import('@bitchord/shared-kotlin').then((module) => {
      kotlin = module;
      tally = new module.ReplayTally();
    });
    return;
  }
  const entry = queue[index]?.track.videoId === nowPlaying.videoId ? queue[index]!.track : null;
  // The decoder's length when it has one, the row's until then.
  const lengthMs = (nowPlaying.durationSec || entry?.durationSec || 0) * 1000;
  const now = Date.now();
  const step = tally.sample(nowPlaying.videoId, now, lengthMs);
  if (!step) return;
  const track = new kotlin.ReplayTrack(
    nowPlaying.videoId,
    entry?.title ?? nowPlaying.title,
    // The credit as one string, as the phone's Song.artist is: the shared rule files it under the lead.
    entry ? entry.artists.map((a) => a.name).join(', ') : nowPlaying.artist,
    entry?.album?.name ?? null,
    entry?.album?.id ?? null,
    entry?.artists[0]?.id ?? null,
    entry?.thumbnail ?? nowPlaying.artworkUrl,
  );
  void enqueue(async () => {
    const at = new Date(now);
    const month = await monthFor(monthKey(at));
    if (month.record(track, step.playedMs, step.countsAsPlay, now, at.getHours(), at.getDate())) dirty = true;
    if (step.flush) await flush();
  });
}

/** Starts recording listening for the Replay; returns the stop. Mounted once, by the app shell. */
export function startListeningRecorder(): () => void {
  const timer = setInterval(sample, SAMPLE_MS);
  const offPlayer = usePlayer.subscribe((state, before) => {
    if (before.status === 'playing' && state.status !== 'playing') stopped();
  });
  // A closing or hidden tab may never come back: what it has counted goes to disk now.
  const onHide = () => {
    if (document.visibilityState === 'hidden') void flushListening();
  };
  document.addEventListener('visibilitychange', onHide);
  window.addEventListener('pagehide', onHide);
  return () => {
    clearInterval(timer);
    offPlayer();
    document.removeEventListener('visibilitychange', onHide);
    window.removeEventListener('pagehide', onHide);
    stopped();
  };
}
