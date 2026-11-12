/**
 * The lyric providers, in default priority: LyricsSource.kt's declaration
 * order, minus its hidden entries (SimpMusic and Megalobiz). The user can
 * reorder and switch them off, as on the desktop (useLyricsSettings).
 */
export const LYRICS_SOURCES = [
  { id: 'lrc-red', label: 'lrc.red', detail: 'Apple timings by recording, syllable by syllable', wordSynced: true },
  { id: 'bini', label: 'BiniLyrics', detail: 'The same Apple timings, matched on the recording itself', wordSynced: true },
  { id: 'better-lyrics', label: 'BetterLyrics', detail: 'Apple Music timings, word by word', wordSynced: true },
  { id: 'better-lyrics-portato', label: 'BetterLyrics Portato', detail: 'QQ Music karaoke timings through BetterLyrics', wordSynced: true },
  { id: 'paxsenix', label: 'PaxSenix', detail: 'Apple Music timings through the original keyless provider', wordSynced: true },
  { id: 'paxsenix-spotify', label: 'PaxSenix: Spotify', detail: 'Spotify lyrics with PaxSenix fallback; API key required', wordSynced: false },
  { id: 'paxsenix-musixmatch', label: 'PaxSenix: Musixmatch', detail: 'Musixmatch timings with PaxSenix fallback; API key required', wordSynced: true },
  { id: 'lyrics-plus', label: 'LyricsPlus', detail: 'Syllable by syllable, on community mirrors', wordSynced: true },
  { id: 'unison', label: 'Unison', detail: 'Contributed by listeners, so it has what nobody licensed', wordSynced: true },
  { id: 'youtube-captions', label: 'YouTube captions', detail: 'Timed captions matched to the exact playing video', wordSynced: false },
  { id: 'youtube-music', label: 'YouTube Music', detail: 'Plain lyrics from the playing video’s Lyrics tab', wordSynced: false },
  { id: 'kugou', label: 'KuGou', detail: 'Whole lines, strong outside the English catalogue', wordSynced: false },
  { id: 'lrclib', label: 'LRCLIB', detail: 'Whole lines only, and always up', wordSynced: false },
  { id: 'musixmatch', label: 'Musixmatch', detail: 'Whole lines, from the biggest lyrics database there is', wordSynced: false },
  { id: 'genius', label: 'Genius', detail: 'Plain text fallback, massive web catalogue', wordSynced: false },
] as const;

export type LyricsSourceId = (typeof LYRICS_SOURCES)[number]['id'];

export const sourceById = (id: string) => LYRICS_SOURCES.find((s) => s.id === id);

/**
 * A saved order brought up to date with this build (LyricsSource.ordered):
 * unknown ids dropped, and a source added since slotted in right after the
 * one it's declared behind, so it lands where a fresh install puts it.
 */
export function orderedSources(saved: readonly string[]): LyricsSourceId[] {
  const offered = LYRICS_SOURCES.map((s) => s.id);
  const result = [...new Set(saved)].filter((id): id is LyricsSourceId => (offered as readonly string[]).includes(id));
  offered.forEach((id, index) => {
    if (result.includes(id)) return;
    const before = offered.slice(0, index).reverse().find((b) => result.includes(b));
    result.splice(before ? result.indexOf(before) + 1 : 0, 0, id);
  });
  return result;
}

// ---- LyricsQuery.kt -------------------------------------------------------------------

const CREDITS = [
  /\s*[([]\s*(feat|ft|featuring|with)\b[^)\]]*[)\]]/gi,
  /\s+(feat|ft|featuring)\.?\s+.*$/gi,
  /\s*[([]\s*(official\s*)?(music\s*)?(video|audio|visuali[sz]er|lyrics?\s*video|lyrics?|m\/?v|hd|hq|4k|full\s*song)\s*[)\]]/gi,
  /\s*[([]\s*official\s*[)\]]/gi,
];

/** A YouTube title as a lyrics database indexed it: credits and packaging off, version words kept. */
export function forLyricsSearch(title: string): string {
  let name = title;
  for (const pattern of CREDITS) name = name.replace(pattern, ' ');
  const cleaned = name.replace(/\s+/g, ' ').trim().replace(/[,\-–—]+$/, '').trim();
  return cleaned || title.trim();
}

/** " - Topic" off an auto-generated channel name. */
export function artistForLyricsSearch(artist: string): string {
  return artist.replace(/ - Topic$/, '').trim() || artist.trim();
}
