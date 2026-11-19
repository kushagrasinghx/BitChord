import { line, type LyricLine } from './model';

type Obj = Record<string, unknown>;

/**
 * The Lyrics tab's browse id on a `next` response.
 *
 * Matched by the page type rather than the tab's label, which is translated.
 */
export function findLyricsBrowseId(node: unknown): string | null {
  if (Array.isArray(node)) {
    for (const child of node) {
      const id = findLyricsBrowseId(child);
      if (id) return id;
    }
    return null;
  }
  if (!node || typeof node !== 'object') return null;
  const object = node as Obj;
  const supported = object.browseEndpointContextSupportedConfigs as Obj | undefined;
  const music = supported?.browseEndpointContextMusicConfig as Obj | undefined;
  if (typeof object.browseId === 'string' && music?.pageType === 'MUSIC_PAGE_TYPE_TRACK_LYRICS') {
    return object.browseId;
  }
  for (const value of Object.values(object)) {
    const id = findLyricsBrowseId(value);
    if (id) return id;
  }
  return null;
}

/**
 * YouTube Music's line-synced lyrics, where the track has them.
 *
 * The mobile client answers with `timedLyricsData`, one entry per line and a
 * `cueRange` for each. There are no word timings in it: the app lights the
 * whole line, as it does for any other line-synced provider.
 */
export function timedYoutubeLyrics(data: unknown): LyricLine[] | null {
  const entries = findArray(data, 'timedLyricsData');
  if (!entries) return null;
  const lines = entries.flatMap((entry): LyricLine[] => {
    if (!entry || typeof entry !== 'object') return [];
    const object = entry as Obj;
    const text = typeof object.lyricLine === 'string' ? object.lyricLine.trim() : '';
    if (!text) return [];
    const cue = object.cueRange && typeof object.cueRange === 'object' ? (object.cueRange as Obj) : {};
    const start = milliseconds(cue.startTimeMilliseconds);
    if (start === null) return [];
    return [line(start, text, { sungUntilMs: milliseconds(cue.endTimeMilliseconds) })];
  });
  return lines.length ? lines : null;
}

/** First array named [name] anywhere in [node], depth-first. */
function findArray(node: unknown, name: string): unknown[] | null {
  if (Array.isArray(node)) {
    for (const child of node) {
      const found = findArray(child, name);
      if (found) return found;
    }
    return null;
  }
  if (!node || typeof node !== 'object') return null;
  const object = node as Obj;
  if (Array.isArray(object[name])) return object[name] as unknown[];
  for (const value of Object.values(object)) {
    const found = findArray(value, name);
    if (found) return found;
  }
  return null;
}

function milliseconds(value: unknown): number | null {
  const parsed = typeof value === 'number' ? value : typeof value === 'string' ? Number(value) : NaN;
  return Number.isFinite(parsed) ? parsed : null;
}
