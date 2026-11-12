/**
 * Apple Music's motion artwork, read and matched (no network here, so it can
 * be tested on its own). A port of the phone's AppleMusicCanvas.kt: the
 * animated sleeves Apple shows on album pages, `editorialVideo` on the
 * catalogue API. A free-text search returns a plausible wrong album for almost
 * any query, so hits are scored rather than trusted: the artist is a gate,
 * compilations and playlists are dropped, and anything under the bar is
 * skipped even when it's the only hit. A wrong clip is worse than none.
 */

import { obj, str, type Json } from './artistPage';

/** An HLS playlist: the square rendition first, the next one as the retry. */
export interface AppleMotion {
  url: string;
  fallbackUrl: string | null;
}

/**
 * The floor a hit has to clear. An exact artist and an exact title alone reach
 * 25, so this only admits a result that matched on both, or one that matched
 * the artist plus a fuzzy title and the right album.
 */
export const MIN_SCORE = 12;

const EDITION_WORDS = ['deluxe', 'expanded', 'remastered', 'remix', 'version', 'edit', 'mix', 'bonus'];

/**
 * Editorial playlists and radio mixes have motion artwork of their own, and
 * Apple returns them alongside albums. Theirs belongs to the playlist, not the
 * track.
 */
const COMPILATION_MARKERS = ['playlist', 'set list', 'essentials', 'dj mix', 'mixed', 'apple music', "today's hits", 'session'];

export function isCompilation(name: string): boolean {
  const lower = name.toLowerCase();
  return COMPILATION_MARKERS.some((m) => lower.includes(m));
}

/**
 * Case, accents and punctuation all differ between YouTube Music and Apple for
 * the same release; fold all three away before comparing (normalizeForMatch).
 */
export function normalizeForMatch(text: string): string {
  return text
    .normalize('NFD')
    .replace(/\p{M}+/gu, '')
    .toLowerCase()
    .replace(/[^a-z0-9\s]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

const ARTIST_SEPARATORS = /(?:\s*,\s*|\s*&\s*|\s+×\s+|\s+x\s+|\bfeat\.?\b|\bft\.?\b|\bfeaturing\b|\bwith\b)/i;

/** One credit line into its artists: every service picks its own separator (splitArtists). */
export function splitArtists(raw: string): string[] {
  return raw.split(ARTIST_SEPARATORS).map(normalizeForMatch).filter(Boolean);
}

/**
 * Whether a found clip belongs to what was asked (CanvasArtwork.matches): title
 * and artists exact once folded; the album only when both sides know it.
 */
export function matches(
  found: { title: string | null; artist: string | null; album: string | null },
  wantTitle: string,
  wantArtist: string,
  wantAlbum: string | null,
): boolean {
  const titleOk = found.title === null || !wantTitle.trim() || normalizeForMatch(found.title) === normalizeForMatch(wantTitle);
  const wanted = splitArtists(wantArtist);
  const ours = splitArtists(found.artist ?? '');
  const artistOk =
    found.artist === null || !wantArtist.trim() || (wanted.length > 0 && ours.length > 0 && wanted.every((w) => ours.includes(w)));
  const albumOk = !found.album?.trim() || !wantAlbum?.trim() || normalizeForMatch(found.album) === normalizeForMatch(wantAlbum);
  return titleOk && artistOk && albumOk;
}

/**
 * How well a search hit lines up with what's wanted, or null to reject it.
 * Artist is a gate rather than a score: a clip credited to someone else is
 * never the right one, however well the title reads.
 */
export function score(hit: Json, title: string, artist: string, album: string | null, albumIsSelf = false): number | null {
  const attributes = obj(hit.attributes);
  if (!attributes) return null;
  const hitName = str(attributes.name);
  const hitArtist = str(attributes.artistName);
  // An album result has no `albumName` of its own: it is the album.
  const hitAlbum = albumIsSelf ? hitName : str(attributes.albumName);

  if (isCompilation(hitName) || isCompilation(hitAlbum)) return null;

  const wanted = splitArtists(artist);
  const credited = splitArtists(hitArtist);
  if (!wanted.length || !credited.length) return null;
  if (!wanted.every((want) => credited.includes(want))) return null;

  let total = 10;
  const wantTitle = normalizeForMatch(title);
  const hitTitle = normalizeForMatch(hitName);
  if (hitTitle === wantTitle) total += 15;
  else if (hitTitle.includes(wantTitle) || wantTitle.includes(hitTitle)) total += 7;
  // Same artist, different song: exactly the mismatch that has to be kept out.
  else total -= 10;

  if (album && hitAlbum) {
    const wantAlbum = normalizeForMatch(album);
    const gotAlbum = normalizeForMatch(hitAlbum);
    if (gotAlbum === wantAlbum) total += 20;
    else if (gotAlbum.includes(wantAlbum) || wantAlbum.includes(gotAlbum)) total += 10;
  }

  // A "(Deluxe)" or "(Remastered)" on one side only is a different master, and often a different clip.
  for (const word of EDITION_WORDS) {
    const inWanted = title.toLowerCase().includes(word);
    const inHit = hitName.toLowerCase().includes(word);
    if (inWanted && inHit) total += 5;
    else if (inHit) total -= 3;
  }
  return total;
}

/** [hits] that clear the bar, best first. */
export function ranked(hits: Json[], title: string, artist: string, album: string | null, albumIsSelf = false): Json[] {
  return hits
    .map((hit) => ({ hit, score: score(hit, title, artist, album, albumIsSelf) }))
    .filter((r): r is { hit: Json; score: number } => r.score !== null && r.score >= MIN_SCORE)
    .sort((a, b) => b.score - a.score)
    .map((r) => r.hit);
}

/**
 * The square rendition first: it fills a square sleeve without cropping. The
 * tall one is the same clip framed for a phone and only worth having as the
 * retry.
 */
export function motionUrls(video: unknown): AppleMotion | null {
  const v = obj(video);
  if (!v) return null;
  const link = (key: string): string | null => {
    const asset = obj(v[key]);
    return (asset && (str(asset.video) || str(asset.videoUrl) || str(asset.hlsUrl) || str(asset.url))) || null;
  };
  const square = link('motionDetailSquare') ?? link('motionSquareVideo1x1');
  const raw = link('motionDetailRaw');
  const tall = link('motionDetailTall') ?? link('motionTallVideo3x4');
  const url = square ?? raw ?? tall;
  if (!url) return null;
  return { url, fallbackUrl: [square, raw, tall].find((u) => u && u !== url) ?? null };
}

/** The album a song hit sits on: its relationship, else the id at the end of its web URL. */
export function albumIdOf(song: Json): string | null {
  const related = obj((obj(obj(song.relationships)?.albums)?.data as Json[] | undefined)?.[0]);
  const id = str(related?.id);
  if (id) return id.startsWith('pl.') ? null : id;
  const url = str(obj(song.attributes)?.url);
  const tail = url.split('/album/')[1]?.split('?')[0]?.split('/').pop() ?? '';
  return /^\d+$/.test(tail) ? tail : null;
}

/** The search term: everything known, so an artist alone doesn't pull in their whole discography. */
export function songTerm(title: string, artist: string, album: string | null): string {
  let term = title.toLowerCase().includes(artist.toLowerCase()) ? '' : `${artist} `;
  term += title;
  if (album && !title.toLowerCase().includes(album.toLowerCase())) term += ` ${album}`;
  return term;
}
