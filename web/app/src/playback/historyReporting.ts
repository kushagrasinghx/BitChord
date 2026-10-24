import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type { DetectedExtension } from '@bitchord/shared/bridge';
import { getBridge } from '@/extension/bridge';
import { extensionQueryKey } from '@/extension/useExtension';
import { ensureYtAuth } from '@/extension/useYtAuth';
import { queryClient } from '@/lib/queryClient';
import { getAudio, onTrackEnded, usePlayer } from '@/state/player';
import { getSignedInnertube } from './youtube/innertube';
import { trackLog } from './trackLog';

/** Settings › Share Listening Activity: whether plays go to the account's YouTube Music history. On by default, as on the phone. */
export const useHistorySettings = create<{ share: boolean; setShare: (on: boolean) => void }>()(
  persist((set) => ({ share: true, setShare: (share) => set({ share }) }), { name: 'bitchord:history', version: 1 }),
);

/**
 * Registers plays in the signed-in account's YouTube Music history, as the
 * phone does (PlaybackTracker.kt), so Recently Played and the
 * recommendations follow what's played here too. One play is three pings,
 * all with one client-playback-nonce:
 *
 *  - `videostatsPlaybackUrl` once the track is audible: this makes the entry;
 *  - `atrUrl` a few seconds in: the play happened, it didn't only start;
 *  - `videostatsWatchtimeUrl` every 30 s of new audio and once at the end:
 *    an entry with no watchtime reads as a skip.
 *
 * The URLs come from a signed-in WEB_REMIX /player call, which only includes
 * them when it quotes the current player's signatureTimestamp. The calls and
 * the pings go through the extension, which signs them as the account; the
 * page never sees the cookies. Best effort: nothing here touches playback.
 */

const REPORT_INTERVAL_SEC = 30;
const DEFAULT_ATR_SEC = 5;
const OPEN_ATTEMPTS = 3;
const OPEN_RETRY_MS = 2_000;
const SAMPLE_MS = 5_000;
const VIDEO_ID = /^[A-Za-z0-9_-]{11}$/;
const CPN_ALPHABET = 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_';

interface Tracking {
  playback: string;
  watchtime: string | null;
  atr: string | null;
  atrAfterSec: number;
}

interface Session {
  videoId: string;
  cpn: string;
  tracking: Tracking;
  /** Seconds already reported, and the most a report in flight will cover. */
  reported: number;
  flushingTo: number;
  atrSent: boolean;
  /** The furthest point heard, for the closing report. */
  heard: number;
}

const newCpn = () => Array.from(crypto.getRandomValues(new Uint8Array(16)), (b) => CPN_ALPHABET[b & 63]).join('');

function extensionSupportsStats(): boolean {
  const detected = queryClient.getQueryData<DetectedExtension | null>(extensionQueryKey);
  return !!detected?.info.capabilities.includes('yt.stats');
}

type Raw = Record<string, unknown>;
const baseUrl = (tracking: Raw, key: string) => ((tracking[key] as Raw | undefined)?.baseUrl as string | undefined) ?? null;

/** The tracking block for one play; null when YouTube declines to issue one (a verdict, not a failure). */
async function fetchTracking(videoId: string): Promise<Tracking | null> {
  const yt = await getSignedInnertube();
  const signatureTimestamp = yt.session.player?.signature_timestamp;
  if (!signatureTimestamp) throw new Error('no signature timestamp yet');
  const response = await yt.actions.execute('/player', {
    videoId,
    contentCheckOk: true,
    racyCheckOk: true,
    playbackContext: {
      contentPlaybackContext: {
        html5Preference: 'HTML5_PREF_WANTS',
        referer: `https://music.youtube.com/watch?v=${videoId}`,
        signatureTimestamp,
      },
    },
    client: 'YTMUSIC',
  });
  const data = response.data as Raw;
  const tracking = data.playbackTracking as Raw | undefined;
  const playback = tracking && baseUrl(tracking, 'videostatsPlaybackUrl');
  if (!tracking || !playback) {
    const status = data.playabilityStatus as Raw | undefined;
    trackLog.w(`history: no playbackTracking for ${videoId} (${String(status?.status)}, ${String(status?.reason ?? '')})`);
    return null;
  }
  const atrAfter = Number((tracking.atrUrl as Raw | undefined)?.elapsedMediaTimeSeconds);
  return {
    playback,
    watchtime: baseUrl(tracking, 'videostatsWatchtimeUrl'),
    atr: baseUrl(tracking, 'atrUrl'),
    atrAfterSec: Number.isFinite(atrAfter) && atrAfter > 0 ? atrAfter : DEFAULT_ATR_SEC,
  };
}

/** What the web client says about itself on every stats ping (Innertube.pingStats). */
async function statsUrl(base: string, cpn: string, extra: Record<string, string> = {}, bare = false): Promise<string> {
  const url = new URL(base);
  if (!bare) {
    const yt = await getSignedInnertube();
    const { Constants } = await import('youtubei.js/web');
    const client = yt.session.context.client;
    const params: Record<string, string> = {
      ver: '2',
      c: 'WEB_REMIX',
      cver: Constants.CLIENTS.YTMUSIC.VERSION,
      cplayer: 'UNIPLAYER',
      hl: (client.hl ?? 'en').replace('-', '_'),
      cr: client.gl ?? 'US',
    };
    for (const [key, value] of Object.entries(params)) url.searchParams.set(key, value);
  }
  url.searchParams.set('cpn', cpn);
  for (const [key, value] of Object.entries(extra)) url.searchParams.set(key, value);
  return url.toString();
}

async function ping(url: string): Promise<number> {
  const bridge = getBridge();
  if (!bridge) throw new Error('no extension');
  const yt = await getSignedInnertube();
  const visitor = yt.session.context.client.visitorData;
  const response = await bridge.call('yt.fetch', {
    url,
    method: 'GET',
    headers: visitor ? { 'X-Goog-Visitor-Id': visitor } : {},
    auth: true,
  });
  if (response.status >= 400) throw new Error(`HTTP ${response.status}`);
  return response.status;
}

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

let session: Session | null = null;
let opening: string | null = null;

async function open(videoId: string): Promise<void> {
  for (let attempt = 1; attempt <= OPEN_ATTEMPTS; attempt++) {
    if (opening !== videoId) return;
    try {
      const tracking = await fetchTracking(videoId);
      if (!tracking || opening !== videoId) return;
      const cpn = newCpn();
      const status = await ping(await statsUrl(tracking.playback, cpn));
      if (opening !== videoId) return;
      session = { videoId, cpn, tracking, reported: 0, flushingTo: 0, atrSent: false, heard: 0 };
      trackLog.i(`history: play registered for ${videoId} (HTTP ${status})`);
      // Recently Played on Home reads that history.
      setTimeout(() => void queryClient.invalidateQueries({ queryKey: ['music', 'library', 'recent'] }), 3_000);
      return;
    } catch (error) {
      trackLog.w(`history: registering ${videoId} failed (attempt ${attempt}/${OPEN_ATTEMPTS}): ${String(error)}`);
      if (attempt < OPEN_ATTEMPTS) await sleep(OPEN_RETRY_MS);
    }
  }
}

async function flush(target: Session, seconds: number, final = false): Promise<void> {
  if (!target.tracking.watchtime) return;
  // A closing report is worth sending even at a position already covered: it's the final=1 that counts.
  if (!final && seconds <= target.reported) return;
  target.flushingTo = Math.max(target.flushingTo, seconds);
  const s = String(Math.floor(seconds));
  try {
    await ping(
      await statsUrl(target.tracking.watchtime, target.cpn, {
        st: '0',
        et: s,
        cmt: s,
        state: final ? 'paused' : 'playing',
        ...(final && { final: '1' }),
      }),
    );
    target.reported = Math.max(target.reported, seconds);
  } catch (error) {
    trackLog.w(`history: watchtime ${s}s for ${target.videoId} failed: ${String(error)}`);
  }
}

/** Ends the current play, reporting how far it got. */
function close(): void {
  const closing = session;
  session = null;
  if (closing) void flush(closing, closing.heard, true);
}

function sample(): void {
  const { nowPlaying, status } = usePlayer.getState();
  const videoId = nowPlaying?.videoId ?? null;
  if (session && session.videoId !== videoId) close();
  if (!videoId || status !== 'playing') return;

  if (!session) {
    if (opening === videoId || !VIDEO_ID.test(videoId)) return;
    if (!useHistorySettings.getState().share || !extensionSupportsStats()) return;
    opening = videoId;
    void ensureYtAuth()
      .then((auth) => (auth.signedIn ? open(videoId) : undefined))
      .catch(() => undefined)
      .finally(() => {
        if (opening === videoId) opening = null;
      });
    return;
  }

  const audio = getAudio();
  if (!audio) return;
  const position = audio.currentTime;
  session.heard = Math.max(session.heard, position);
  const current = session;
  if (!current.atrSent && position >= current.tracking.atrAfterSec && current.tracking.atr) {
    current.atrSent = true;
    // Its base URL already carries ver, c and cver; it goes as it is, with the nonce.
    void statsUrl(current.tracking.atr, current.cpn, {}, true)
      .then(ping)
      .catch((error: unknown) => trackLog.w(`history: atr for ${current.videoId} failed: ${String(error)}`));
  }
  if (position - Math.max(current.reported, current.flushingTo) >= REPORT_INTERVAL_SEC) void flush(current, position);
}

/** Runs for the app's lifetime (AppShell). */
export function startHistoryReporting(): () => void {
  const offPlayer = usePlayer.subscribe((state, before) => {
    if (state.nowPlaying?.videoId !== before.nowPlaying?.videoId || state.status !== before.status) sample();
  });
  // Switched off mid-play: this play stops being reported, as if it had never been shared.
  const offSettings = useHistorySettings.subscribe((state) => {
    if (!state.share) {
      session = null;
      opening = null;
    }
  });
  // Played to the end: closed here, as the phone's track transition closes it. A repeat-one
  // loop keeps the same video, so it is only a new play this way, and the queue's last song
  // gets its closing report instead of reading as abandoned at the last 30 s sample.
  const offEnded = onTrackEnded(() => {
    const audio = getAudio();
    if (session && audio) session.heard = Math.max(session.heard, audio.currentTime);
    close();
  });
  const timer = setInterval(sample, SAMPLE_MS);
  const onHide = () => {
    if (document.visibilityState === 'hidden' && session) void flush(session, session.heard);
  };
  document.addEventListener('visibilitychange', onHide);
  return () => {
    offPlayer();
    offSettings();
    offEnded();
    clearInterval(timer);
    document.removeEventListener('visibilitychange', onHide);
    close();
  };
}
