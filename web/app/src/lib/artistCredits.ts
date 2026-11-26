import { creditJoinerPattern, creditStretches } from '@bitchord/shared-kotlin';
import type { ArtistRef } from '@/data/music/types';

/**
 * The phone's credit line: which artists a byline credits, and how the line is
 * cut into stretches so each name can be opened on its own while the line
 * still reads exactly as it was written. The folding and the cutting are
 * shared/…/data/model/ArtistCredits.kt itself; the choice of credits to draw
 * ports sharedUi/…/ui/player/ArtistCredits.kt, which isn't shared.
 */

export { isExactArtistMatch, normalizedArtistName } from '@bitchord/shared-kotlin';

/** One stretch of a credit line: a credited name, or the plain text between names. */
export interface CreditSegment {
  text: string;
  credit: ArtistRef | null;
}

/** The marks a byline joins credits with, in every script (ArtistCredits.kt `CreditJoiner`). */
const CREDIT_JOINER = new RegExp(creditJoinerPattern(), 'g');

/**
 * A byline split on its joiners, for a track that states no per-artist
 * credits. A name is not an identity, so these carry no channel; opening one
 * asks for that exact name.
 */
function splitCreditLine(artist: string): ArtistRef[] {
  return artist
    .trim()
    .split(CREDIT_JOINER)
    .map((name) => name.trim())
    .filter(Boolean)
    .map((name) => ({ name, id: null }));
}

/**
 * The credits to draw for a track: its own artists, each with the channel
 * YouTube linked, and the byline split on its joiners only when there are none.
 *
 * One web-only case: a track started from a bare video id (playVideo) holds
 * the whole byline as a single unlinked artist, which is a byline, not a
 * credit, so it is split like one.
 */
export function artistCredits(artist: string, artists: readonly ArtistRef[]): ArtistRef[] {
  const stated = artists.filter((a) => a.name.trim());
  const wholeLine = stated.length === 1 && !stated[0]!.id && new RegExp(CREDIT_JOINER.source).test(stated[0]!.name);
  return stated.length && !wholeLine ? stated : splitCreditLine(stated[0]?.name ?? artist);
}

/** ArtistCredits.kt `creditSegments`: [text] cut where each credited name begins, the longest name winning. */
export function creditSegments(text: string, credits: readonly ArtistRef[]): CreditSegment[] {
  return creditStretches(
    text,
    credits.map((c) => c.name),
  ).map((s) => ({ text: s.text, credit: credits[s.credit] ?? null }));
}
