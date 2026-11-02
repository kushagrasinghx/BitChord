import { botguard } from '@/playback/youtube/botguard';
import { ytAuthSnapshot } from '@/extension/useYtAuth';
import { getInnertube, getSignedInnertube } from '@/playback/youtube/innertube';
import { resolvePlayer, UnplayableError, usesWebPo } from '@/playback/youtube/resolver';

/**
 * A picture wider than this is a music video; a square one is YouTube's art
 * track for a song (the player's own rule, YouTubeEngine.MIN_VIDEO_ASPECT).
 */
const MIN_VIDEO_ASPECT = 1.2;

/** The track's audio, and whether YouTube serves it as a music video rather than a song. */
export interface TrackAudio {
  bytes: ArrayBuffer;
  musicVideo: boolean;
}

/** googlevideo serves a direct URL in ranges; Android (vivi/InnerTubeX) asks for 512 KiB–1 MiB at a time. */
const CHUNK_BYTES = 1024 * 1024;
const MAX_BYTES = 40 * 1024 * 1024;

/**
 * The whole audio file of [videoId], for analysis: the same direct URL Shaka
 * plays (VISIONOS), fetched in ranges. Opus in WebM when there is one (every
 * browser that runs the app decodes it in decodeAudioData), else AAC.
 * Throws when the track only streams over SABR, which has no file to fetch.
 */
export async function fetchTrackAudio(videoId: string, signal?: AbortSignal): Promise<TrackAudio> {
  let source = await resolveAudioUrl(videoId);
  const parts: Uint8Array[] = [];
  let at = 0;
  let refusals = 0;
  for (;;) {
    const { url, total } = source;
    const end = total ? Math.min(total, at + CHUNK_BYTES) - 1 : at + CHUNK_BYTES - 1;
    url.searchParams.set('range', `${at}-${end}`);
    const response = await fetch(url, { signal, credentials: 'omit' });
    if (response.status === 403 && refusals < MAX_REFUSALS) {
      // googlevideo sometimes refuses a URL part-way through; a fresh one
      // (another player response) carries on from the same byte.
      refusals++;
      source = await resolveAudioUrl(videoId, source.itag);
      continue;
    }
    if (!response.ok) throw new Error(`audio fetch HTTP ${response.status} at byte ${at}`);
    const part = new Uint8Array(await response.arrayBuffer());
    parts.push(part);
    at += part.byteLength;
    if (part.byteLength === 0 || (total ? at >= total : part.byteLength < CHUNK_BYTES) || at > MAX_BYTES) break;
  }

  const out = new Uint8Array(at);
  let offset = 0;
  for (const part of parts) {
    out.set(part, offset);
    offset += part.byteLength;
  }
  return { bytes: out.buffer, musicVideo: source.musicVideo };
}

const MAX_REFUSALS = 2;

/** A deciphered direct URL for the track's best audio (or the same itag, when resuming). */
async function resolveAudioUrl(videoId: string, itag?: number): Promise<{ url: URL; total: number; itag: number; musicVideo: boolean }> {
  const { info, client, yt } = await resolveAsPlayerWould(videoId);
  const audio = (info.streaming_data?.adaptive_formats ?? []).filter(
    (f) => f.has_audio && !f.has_video && (f.url || f.signature_cipher || f.cipher) && !f.is_drc,
  );
  const pick = (mime: string) =>
    audio.filter((f) => f.mime_type.startsWith(mime)).sort((a, b) => b.bitrate - a.bitrate)[0];
  const format = (itag !== undefined ? audio.find((f) => f.itag === itag) : undefined) ?? pick('audio/webm') ?? pick('audio/mp4');
  if (!format) throw new Error('no direct audio URL (SABR only)');
  if (itag !== undefined && format.itag !== itag) throw new Error('the format changed between requests');

  const url = new URL(await format.decipher(yt.session.player));
  if (usesWebPo(client)) {
    const pot = await botguard.mint(videoId);
    if (pot) url.searchParams.set('pot', pot);
  }
  const pictures = (info.streaming_data?.adaptive_formats ?? []).filter((f) => f.has_video && f.width && f.height);
  const widest = pictures.sort((a, b) => b.width! * b.height! - a.width! * a.height!)[0];
  const musicVideo = !!widest && widest.width! / widest.height! >= MIN_VIDEO_ASPECT;
  return { url, total: format.content_length ?? 0, itag: format.itag, musicVideo };
}

/**
 * The player's own walk (YouTubeEngine.load): anonymously first, and when
 * YouTube calls that an age gate (which is also how its "sign in to confirm
 * you're not a bot" reads), again as the signed-in user. Without the second
 * step a song that plays fine could never be analysed.
 */
async function resolveAsPlayerWould(videoId: string) {
  try {
    return await resolvePlayer(await getInnertube(), videoId);
  } catch (error) {
    if (!(error instanceof UnplayableError && error.ageGated) || !ytAuthSnapshot().signedIn) throw error;
    return resolvePlayer(await getSignedInnertube(), videoId, new Set(), true);
  }
}
