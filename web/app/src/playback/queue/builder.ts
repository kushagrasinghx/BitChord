import type { Track } from '@/data/music/types';

/**
 * Builds the station that plays on after the queue runs out: a port of
 * shared/…/playback/QueueBuilder.kt.
 *
 * - YouTube's watch queues carry the same recording twice (the official audio
 *   and the music video), so tracks are matched on a normalised title plus a
 *   shared artist, not only on id.
 * - A radio mix leans on the seed's artist; per-artist caps keep a station
 *   from becoming one artist's discography.
 */

/** No more than this many tracks by one artist in a single batch. */
export const PER_ARTIST_LIMIT = 2;
/** The artist the station was seeded on gets more room, but not the run of it. */
export const SEED_ARTIST_LIMIT = 4;

/** A radio candidate: a track, and whether it's a music video rather than the catalogue cut. */
export interface Candidate {
  track: Track;
  isVideo: boolean;
}

const NOISE = new RegExp(
  String.raw`\((?:official|lyric|lyrics|lyrical|audio|video|visuali[sz]er|full song|hd|4k)[^)]*\)` +
    String.raw`|\(from[^)]*\)` +
    String.raw`|\[[^\]]*\]` +
    String.raw`|\b(?:official (?:video|audio|music video)|lyrical video|full video|4k video)\b`,
  'giu',
);
const TOPIC = /\s*-\s*topic\b/giu;
const PUNCTUATION = /[^\p{L}\p{N}]+/gu;
const SPACES = /\s+/g;
const CREDIT_SEPARATORS = /,|&|·|•|;| feat| ft\.| ft | x | with /;

export const artistLine = (track: Track) => track.artists.map((a) => a.name).join(', ');

/** `Kesariya (From "Brahmastra") | Official Video` and `Kesariya` are one recording. Remix and cover markers stay. */
export function normalisedTitle(raw: string): string {
  return raw.toLowerCase().split(' | ')[0]!.replace(NOISE, ' ').replace(PUNCTUATION, ' ').replace(SPACES, ' ').trim();
}

/** The cast behind a credit, split out so billing order stops mattering. */
export function artistSet(raw: string): Set<string> {
  return new Set(
    raw
      .toLowerCase()
      .replace(TOPIC, ' ')
      .split(CREDIT_SEPARATORS)
      .map((part) => part.replace(PUNCTUATION, ' ').replace(SPACES, ' ').trim())
      .filter(Boolean),
  );
}

/** The audio and video cuts of a track have different ids, which is the case id equality misses. */
export function isSameRecording(a: Track, b: Track): boolean {
  if (a.videoId === b.videoId) return true;
  const title = normalisedTitle(a.title);
  if (!title || title !== normalisedTitle(b.title)) return false;
  // One shared name is enough: the two cuts bill their artists in any order.
  const left = artistSet(artistLine(a));
  const right = artistSet(artistLine(b));
  return !left.size || !right.size || [...left].some((name) => right.has(name));
}

/**
 * The candidates worth appending after [existing]: nothing already queued,
 * nothing repeated, at most [limit]. [preferVideo] when the station was seeded
 * on a music video, which keeps it on music videos.
 */
export function extendStation(
  existing: readonly Track[],
  candidates: readonly Candidate[],
  limit: number,
  preferVideo = false,
): Track[] {
  const taken = [...existing];
  const perArtist = new Map<string, number>();
  const last = existing.at(-1);
  const seedArtists = last ? artistSet(artistLine(last)) : new Set<string>();
  const out: Track[] = [];

  // A mix pairs most tracks with their music-video upload, titled too
  // differently for any title match. The catalogue cut is what a music app
  // wants — unless a music video is playing, when the station carries on in
  // pictures. Either way the other kind only stands in when there's nothing else.
  const wanted = candidates.filter((c) => c.isVideo === preferVideo);
  const ordered = (wanted.length ? wanted : candidates).map((c) => c.track);

  for (const candidate of ordered) {
    if (out.length >= limit) break;
    if (taken.some((t) => isSameRecording(t, candidate))) continue;
    const artists = [...artistSet(artistLine(candidate))];
    const key = artists.sort()[0];
    if (key !== undefined) {
      const cap = artists.some((a) => seedArtists.has(a)) ? SEED_ARTIST_LIMIT : PER_ARTIST_LIMIT;
      const played = perArtist.get(key) ?? 0;
      if (played >= cap) continue;
      perArtist.set(key, played + 1);
    }
    taken.push(candidate);
    out.push(candidate);
  }
  return out;
}
