import shaka from 'shaka-player/dist/shaka-player.compiled';
import { getVideoElement, qualityLabel, setVideoQuality, useVideo, type VideoQuality } from '@/state/video';
import { trackLog } from '../trackLog';
import type { YouTubeEngine } from './engine';

/**
 * A music video's picture, as Apple Music plays one: a second Shaka player,
 * video only and muted, on the shared <video> element (state/video.ts),
 * held to the audio element of the deck in front. The audio stays the clock;
 * the picture follows it, nudging its rate for small drift and seeking for
 * large. It streams only while a surface shows it.
 */

/** Past this the picture jumps to the audio; under it, its rate is nudged. */
const SEEK_DRIFT_SEC = 0.35;
const IGNORE_DRIFT_SEC = 0.02;
const SYNC_EVERY_MS = 250;

/**
 * What Auto opens at. Shaka's own default (500kbps) starts a music video at
 * 360p and climbs from there, which is most of a short song; the picture is
 * worth more than the first few seconds of caution, and a connection that
 * can't hold this is measured within seconds and backed off.
 */
const AUTO_START_BPS = 6_000_000;

/**
 * What Auto climbs to: H.264 tops out here on YouTube and every browser
 * decodes it in hardware, so Auto never asks the machine for a VP9 2K or 4K
 * frame it may have to decode in software. Above this is the listener's call,
 * in the quality switcher.
 */
const AUTO_MAX_HEIGHT = 1080;

let player: shaka.Player | null = null;
let source: { engine: YouTubeEngine; audio: HTMLAudioElement } | null = null;
/** The video whose manifest is loaded (or loading) in the picture player, and at which quality. */
let loaded: string | null = null;
let loadedQuality: VideoQuality | null = null;
let generation = 0;
let timer: ReturnType<typeof setInterval> | null = null;
let listening: HTMLAudioElement | null = null;

async function ensurePlayer(): Promise<shaka.Player> {
  if (player) return player;
  const created = new shaka.Player();
  created.configure({
    manifest: { disableAudio: true, disableText: true, disableThumbnails: true },
    // H.264 first: every browser decodes it in hardware. A picked height above
    // the H.264 ladder arrives as a manifest with only that height in it
    // (engine.videoStream), so this falls through to VP9 or AV1 there.
    preferredVideoCodecs: ['avc1', 'vp9', 'av01'],
    preferredDecodingAttributes: ['smooth', 'powerEfficient'],
    streaming: {
      bufferingGoal: 30,
      rebufferingGoal: 0.5,
      bufferBehind: 30,
      retryParameters: { maxAttempts: 2, baseDelay: 1000, backoffFactor: 2, fuzzFactor: 0.5, timeout: 30_000 },
    },
  });
  await created.attach(getVideoElement());
  created.addEventListener('error', (event) => {
    const error = (event as unknown as { detail: shaka.util.Error }).detail;
    trackLog.d(`video: Shaka error ${error.code} (severity ${error.severity})`, loaded);
  });
  // Auto moves between renditions as it measures the connection; the switcher shows where it landed.
  for (const event of ['adaptation', 'variantchanged'] as const) {
    created.addEventListener(event, () => reportActive(created));
  }
  player = created;
  return created;
}

/** Holds the picture at [quality]: Auto adapts up to the H.264 ceiling, a picked height doesn't move. */
function configureQuality(target: shaka.Player, quality: VideoQuality) {
  target.configure({
    abr: {
      enabled: quality === 'auto',
      defaultBandwidthEstimate: AUTO_START_BPS,
      restrictions: { maxHeight: quality === 'auto' ? AUTO_MAX_HEIGHT : Infinity },
    },
  });
}

function reportActive(target: shaka.Player) {
  const active = target.getVariantTracks().find((t) => t.active);
  useVideo.setState({ activeHeight: active?.height ?? null });
}

/** The deck in front changed track, or recovered it: show its picture, if it has one. */
export function followVideo(engine: YouTubeEngine, audio: HTMLAudioElement): void {
  source = { engine, audio };
  listen(audio);
  void refresh();
}

/** Nothing is playing: drop the picture. */
export function stopVideo(): void {
  source = null;
  void refresh();
}

useVideo.subscribe((state, previous) => {
  if (state.surfaces > 0 !== previous.surfaces > 0 || state.quality !== previous.quality) void refresh();
});

async function refresh(): Promise<void> {
  const run = ++generation;
  const quality = useVideo.getState().quality;
  const wanted = source && useVideo.getState().surfaces > 0 ? source : null;
  const stream = wanted ? await wanted.engine.videoStream(quality === 'auto' ? null : quality).catch(() => null) : null;
  if (run !== generation) return;
  if (!stream) {
    await unload();
    return;
  }
  if (stream.videoId === loaded && quality === loadedQuality) return;
  // A quality change reloads the same picture, so it picks up where it was.
  // Its surface stays on screen through the swap: dropping it would send the
  // full-screen player back to the artwork and in again.
  const resumeAt = wanted!.audio.currentTime;
  await unload(stream.videoId === loaded);
  loaded = stream.videoId;
  loadedQuality = quality;
  const video = getVideoElement();
  try {
    const shakaPlayer = await ensurePlayer();
    configureQuality(shakaPlayer, quality);
    await shakaPlayer.load(`data:application/dash+xml;base64,${btoa(stream.manifest)}`, resumeAt, 'application/dash+xml');
    if (run !== generation) return;
    if (video.readyState < HTMLMediaElement.HAVE_CURRENT_DATA) {
      await new Promise<void>((resolve) => video.addEventListener('loadeddata', () => resolve(), { once: true }));
    }
    if (run !== generation) return;
    const active = shakaPlayer.getVariantTracks().find((t) => t.active);
    trackLog.i(`video: ${active?.height ?? '?'}p ${active?.videoCodec ?? ''} (${quality === 'auto' ? 'auto' : qualityLabel(quality)})`, stream.videoId);
    useVideo.setState({ videoId: stream.videoId, aspect: stream.aspect, heights: stream.heights, activeHeight: active?.height ?? null });
    sync();
    timer ??= setInterval(sync, SYNC_EVERY_MS);
  } catch (error) {
    if (run !== generation) return;
    trackLog.w(`video: couldn't load the picture: ${error instanceof Error ? error.message : String(error)}`, stream.videoId);
    loaded = null;
    loadedQuality = null;
    useVideo.setState({ videoId: null });
    // A height this machine can't decode shouldn't cost the picture: back to Auto, which is H.264.
    if (quality !== 'auto') {
      trackLog.w(`video: ${qualityLabel(quality)} wouldn't play; back to Auto`, stream.videoId);
      setVideoQuality('auto');
    }
  }
}

/** Stops the picture player; [keepPicture] leaves the surface showing, for a reload of the same video. */
async function unload(keepPicture = false): Promise<void> {
  if (timer) clearInterval(timer);
  timer = null;
  loaded = null;
  loadedQuality = null;
  if (!keepPicture) useVideo.setState({ videoId: null, heights: [], activeHeight: null });
  await player?.unload().catch(() => {});
}

function listen(audio: HTMLAudioElement) {
  if (listening === audio) return;
  for (const type of ['play', 'pause', 'seeked', 'ratechange'] as const) {
    listening?.removeEventListener(type, sync);
    audio.addEventListener(type, sync);
  }
  listening = audio;
}

/** Holds the picture to the audio: same play state, same rate, the same moment. */
function sync() {
  const audio = source?.audio;
  const video = getVideoElement();
  if (!audio || !loaded || useVideo.getState().videoId !== loaded || video.readyState < HTMLMediaElement.HAVE_METADATA) return;
  const drift = video.currentTime - audio.currentTime;
  if (Math.abs(drift) > SEEK_DRIFT_SEC || audio.paused) {
    if (Math.abs(drift) > IGNORE_DRIFT_SEC) video.currentTime = audio.currentTime;
    video.playbackRate = audio.playbackRate;
  } else {
    // Catch up (or wait) over about a second, rather than jumping.
    const nudge = Math.abs(drift) > IGNORE_DRIFT_SEC ? Math.max(-0.1, Math.min(0.1, -drift)) : 0;
    video.playbackRate = audio.playbackRate * (1 + nudge);
  }
  if (audio.paused && !video.paused) video.pause();
  else if (!audio.paused && video.paused) void video.play().catch(() => {});
}
