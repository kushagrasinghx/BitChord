import { stationArtists, stationExtend, StationTrack, stationSameRecording, stationTitle } from '@bitchord/shared-kotlin';
import type { Track } from '@/data/music/types';

/**
 * The station that plays on after the queue runs out: QueueBuilder.kt itself
 * (shared/commonMain), so the web de-duplicates audio and video cuts and caps
 * each artist exactly as the phone does.
 */

/** A radio candidate: a track, and whether it's a music video rather than the catalogue cut. */
export interface Candidate {
  track: Track;
  isVideo: boolean;
}

export const artistLine = (track: Track) => track.artists.map((a) => a.name).join(', ');

const station = (track: Track, isVideo = false) => new StationTrack(track.videoId, track.title, artistLine(track), isVideo);

/** `Kesariya (From "Brahmastra") | Official Video` and `Kesariya` are one recording. Remix and cover markers stay. */
export const normalisedTitle = (raw: string): string => stationTitle(raw);

/** The cast behind a credit, split out so billing order stops mattering. */
export const artistSet = (raw: string): Set<string> => new Set(stationArtists(raw));

/** The audio and video cuts of a track have different ids, which is the case id equality misses. */
export const isSameRecording = (a: Track, b: Track): boolean => stationSameRecording(station(a), station(b));

/**
 * The candidates worth appending after [existing]: nothing already queued,
 * nothing repeated, at most [limit]. [preferVideo] when the station was seeded
 * on a music video, which keeps it on music videos.
 */
export function extendStation(existing: readonly Track[], candidates: readonly Candidate[], limit: number, preferVideo = false): Track[] {
  const picked = stationExtend(
    existing.map((t) => station(t)),
    candidates.map((c) => station(c.track, c.isVideo)),
    limit,
    preferVideo,
  );
  return picked.map((i) => candidates[i]!.track);
}
