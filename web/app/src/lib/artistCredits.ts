import type { ArtistRef } from '@/data/music/types';

/**
 * The phone's credit line (shared/…/data/model/ArtistCredits.kt and
 * sharedUi/…/ui/player/ArtistCredits.kt), ported as is: which artists a byline
 * credits, and how the line is cut into stretches so each name can be opened
 * on its own while the line still reads exactly as it was written.
 */

/** One stretch of a credit line: a credited name, or the plain text between names. */
export interface CreditSegment {
  text: string;
  credit: ArtistRef | null;
}

/**
 * The marks a byline joins credits with, in every script: the comma and its
 * full-width form, both ampersands, the bullet, the middle dot, and `・`, `、`.
 * Marks rather than words, because none of them can be a name (`and` can).
 */
const CREDIT_JOINER = /\s*,\s*|\s*，\s*|\s+&\s+|\s+＆\s+|\s*•\s*|\s*·\s*|\s*・\s*|\s*、\s*/g;

/**
 * Folds a name to the form two spellings of one artist share: case, accents
 * and spacing only, so names in every script survive it (normalizedArtistName).
 */
export function normalizedArtistName(raw: string): string {
  return raw.normalize('NFD').replace(/[̀-ͯ]+/g, '').toLowerCase().replace(/\s+/g, ' ').trim();
}

/** Whether a search answer is exactly the artist asked for; a blank name matches nothing. */
export function isExactArtistMatch(answer: string, wanted: string): boolean {
  const name = normalizedArtistName(wanted);
  return name !== '' && normalizedArtistName(answer) === name;
}

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

/**
 * Cuts [text] into the stretches it's drawn from, marking where each credited
 * name begins. The names are found in the line rather than the line cut into
 * names, so everything that isn't a name (", ", " & ", " and ") is kept as
 * written. The longest name wins at any position, so `2115, Bedoes 2115` marks
 * both. Matching ignores case; what's drawn is the line's own spelling.
 */
export function creditSegments(text: string, credits: readonly ArtistRef[]): CreditSegment[] {
  if (!text) return [];
  const seen = new Set<string>();
  const names = credits
    .filter((c) => c.name.trim())
    .filter((c) => {
      const key = normalizedArtistName(c.name);
      if (seen.has(key)) return false;
      seen.add(key);
      return true;
    })
    .sort((a, b) => b.name.length - a.name.length);
  if (!names.length) return [{ text, credit: null }];

  const out: CreditSegment[] = [];
  let plain = '';
  let index = 0;
  while (index < text.length) {
    const hit = names.find((c) => text.slice(index, index + c.name.length).toLowerCase() === c.name.toLowerCase());
    if (!hit) {
      plain += text[index];
      index++;
      continue;
    }
    if (plain) {
      out.push({ text: plain, credit: null });
      plain = '';
    }
    out.push({ text: text.slice(index, index + hit.name.length), credit: hit });
    index += hit.name.length;
  }
  if (plain) out.push({ text: plain, credit: null });
  return out;
}
