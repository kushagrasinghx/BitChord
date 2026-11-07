import type { AlbumRef } from './types';

type Raw = Record<string, unknown>;

/** The first value under [key] anywhere inside [root] that [match] accepts. */
function find(root: unknown, key: string, match: (value: Raw) => boolean): Raw | null {
  if (Array.isArray(root)) {
    for (const item of root) {
      const found = find(item, key, match);
      if (found) return found;
    }
  } else if (root && typeof root === 'object') {
    const value = (root as Raw)[key];
    if (value && typeof value === 'object' && match(value as Raw)) return value as Raw;
    for (const child of Object.values(root)) {
      const found = find(child, key, match);
      if (found) return found;
    }
  }
  return null;
}

interface Run {
  text?: string;
  navigationEndpoint?: { browseEndpoint?: { browseId?: string; browseEndpointContextSupportedConfigs?: { browseEndpointContextMusicConfig?: { pageType?: string } } } };
}

/**
 * The album of [videoId] from YouTube Music's /next answer: the song's own
 * row in the watch queue links it ("Arijit Singh • Aashiqui 2"). Search
 * results no longer say, so Go to Album asks here. Null for songs with none.
 */
export function albumFromNext(data: unknown, videoId: string): AlbumRef | null {
  const row = find(data, 'playlistPanelVideoRenderer', (value) => value.videoId === videoId);
  const runs = ((row?.longBylineText as { runs?: Run[] } | undefined)?.runs ?? []) as Run[];
  const album = runs.find(
    (run) => run.navigationEndpoint?.browseEndpoint?.browseEndpointContextSupportedConfigs?.browseEndpointContextMusicConfig?.pageType === 'MUSIC_PAGE_TYPE_ALBUM',
  );
  const id = album?.navigationEndpoint?.browseEndpoint?.browseId;
  return album?.text && id ? { name: album.text, id } : null;
}
