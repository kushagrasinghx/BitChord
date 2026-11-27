/**
 * "Available offline": whole audio files kept in this browser (Cache Storage),
 * one per video id, with what the player needs to show the track without
 * asking YouTube. The player plays a copy from here whenever there is one,
 * online or not (YouTubeEngine.load).
 */

const CACHE = 'bitchord-offline';
const META = 'X-BitChord-Track';

/** What is shown for a downloaded track, and how its bytes are typed. */
export interface OfflineTrack {
  videoId: string;
  title: string;
  author: string;
  durationSec: number;
  thumbnail: string | null;
  /** "audio/webm" (Opus) or "audio/mp4" (AAC). */
  mimeType: string;
  sizeBytes: number;
  savedAt: number;
}

/** Cache Storage needs a secure context (https, or localhost / tauri.localhost). */
export const offlineSupported = (): boolean => typeof caches !== 'undefined';

const keyOf = (videoId: string) => new URL(`/offline/${encodeURIComponent(videoId)}`, location.origin).href;

function metaOf(response: Response): OfflineTrack | null {
  try {
    return JSON.parse(decodeURIComponent(response.headers.get(META) ?? '')) as OfflineTrack;
  } catch {
    return null;
  }
}

/** Opus in WebM or AAC in MP4, from the file's own first bytes (EBML's magic, or MP4's `ftyp` box). */
export function sniffMimeType(bytes: Uint8Array): string | null {
  if (bytes[0] === 0x1a && bytes[1] === 0x45 && bytes[2] === 0xdf && bytes[3] === 0xa3) return 'audio/webm';
  if (String.fromCharCode(...bytes.subarray(4, 8)) === 'ftyp') return 'audio/mp4';
  return null;
}

export async function saveOffline(track: Omit<OfflineTrack, 'mimeType' | 'sizeBytes' | 'savedAt'>, bytes: Uint8Array): Promise<void> {
  const mimeType = sniffMimeType(bytes);
  if (!mimeType) throw new Error('not an audio file this player can read');
  const meta: OfflineTrack = { ...track, mimeType, sizeBytes: bytes.byteLength, savedAt: Date.now() };
  const cache = await caches.open(CACHE);
  await cache.put(
    keyOf(track.videoId),
    new Response(bytes as BodyInit, { headers: { 'Content-Type': mimeType, [META]: encodeURIComponent(JSON.stringify(meta)) } }),
  );
}

/** The downloaded copy of [videoId], or null. Cheap enough to ask before every load. */
export async function offlineCopy(videoId: string): Promise<{ track: OfflineTrack; blob: Blob } | null> {
  if (!offlineSupported()) return null;
  const response = await (await caches.open(CACHE)).match(keyOf(videoId));
  const track = response && metaOf(response);
  return response && track ? { track, blob: await response.blob() } : null;
}

/** Everything downloaded, without reading the audio. */
export async function offlineTracks(): Promise<OfflineTrack[]> {
  if (!offlineSupported()) return [];
  const responses = await (await caches.open(CACHE)).matchAll();
  return responses.map(metaOf).filter((t): t is OfflineTrack => t !== null);
}

/** The song menu's Remove download (SongActionsMenu.kt DownloadRow, Downloads.delete). */
export async function removeOffline(videoId: string): Promise<void> {
  if (offlineSupported()) await (await caches.open(CACHE)).delete(keyOf(videoId));
}

/** Settings › Remove Downloads. */
export async function removeAllOffline(): Promise<void> {
  if (offlineSupported()) await caches.delete(CACHE);
}
