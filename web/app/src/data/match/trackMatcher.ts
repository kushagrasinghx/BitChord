import { matchHasConflictingAlbums, matchMostCredited, matchQueries, matchRanked, MatchRow, MatchWanted } from '@bitchord/shared-kotlin';

/**
 * Whether one catalogue's track is the same recording as another's:
 * TrackMatcher.kt itself (shared/commonMain), so the web takes titles apart
 * and weighs credits and runtimes exactly as the phone does.
 */

/** One row from a catalogue: a YouTube Music search result, a Spotify track, an addon's answer. */
export interface MatchCandidate {
  title: string;
  /** Credited artists, as one string ("A, B & C"). */
  artist: string;
  durationSec: number | null;
  album: string | null;
  explicit?: boolean | null;
}

/** The recording being looked for, as much of it as is known. */
export interface MatchTarget {
  title: string;
  artist?: string;
  durationSec?: number | null;
  album?: string | null;
  isExplicit?: boolean | null;
  /** Music-video timing includes visual intros/outros that catalogue audio omits. */
  isVideo?: boolean;
}

const row = (c: MatchCandidate) => new MatchRow(c.title, c.artist, c.durationSec, c.album, c.explicit ?? null);
const wanted = (t: MatchTarget) =>
  new MatchWanted(t.title, t.artist ?? '', t.durationSec ?? null, t.album ?? null, t.isExplicit ?? null, t.isVideo ?? false);

export { foldLatin, romajiOf } from '@bitchord/shared-kotlin';

/** What to put to a search box, best query first. */
export const queries = (target: MatchTarget): string[] => matchQueries(wanted(target));

/** Every candidate that really is [target], most confident first. */
export const ranked = <T extends MatchCandidate>(candidates: T[], target: MatchTarget): T[] =>
  matchRanked(candidates.map(row), wanted(target)).map((i) => candidates[i]!);

export const best = <T extends MatchCandidate>(candidates: T[], target: MatchTarget): T | null => ranked(candidates, target)[0] ?? null;

export const matches = (candidate: MatchCandidate, title: string, artist: string, durationSec: number | null = null): boolean =>
  ranked([candidate], { title, artist, durationSec }).length > 0;

export const hasConflictingAlbums = (candidates: MatchCandidate[], target: MatchTarget): boolean =>
  matchHasConflictingAlbums(candidates.map(row), wanted(target));

export const uniquelyMostCreditedCloseMatch = <T extends MatchCandidate>(candidates: T[], target: MatchTarget): T | null =>
  candidates[matchMostCredited(candidates.map(row), wanted(target))] ?? null;
