import type { DeezerPage, DeezerPlaylist, DeezerTrack } from '@bitchord/shared/bridge';

/** Favorite Tracks ("Loved Tracks") entry in the library; DeezerLibrary.LOVED_ID. */
export const LOVED_ID = 'loved';

const IMAGE_CDN = 'https://cdn-images.dzcdn.net/images';

export type Json = Record<string, unknown>;

export const obj = (v: unknown): Json | null => (v && typeof v === 'object' && !Array.isArray(v) ? (v as Json) : null);
export const arr = (v: unknown): unknown[] => (Array.isArray(v) ? v : []);
export const str = (v: unknown): string | null => {
  if (typeof v === 'string') return v;
  if (typeof v === 'number' && Number.isFinite(v)) return String(v);
  return null;
};
export const num = (v: unknown): number | null => {
  if (typeof v === 'number' && Number.isFinite(v)) return v;
  if (typeof v === 'string' && v.trim() !== '') {
    const parsed = Number(v);
    return Number.isFinite(parsed) ? parsed : null;
  }
  return null;
};
export const path = (v: unknown, ...keys: string[]): unknown => keys.reduce<unknown>((at, key) => obj(at)?.[key], v);

export function hasErrors(errors: unknown): boolean {
  if (Array.isArray(errors)) return errors.length > 0;
  const o = obj(errors);
  return !!o && Object.keys(o).length > 0;
}

export function coverUrl(type: string, md5: string | null | undefined, size = 500): string | null {
  const hash = md5?.trim();
  if (!hash) return null;
  if (hash.startsWith('https://') || hash.startsWith('http://')) return hash;
  return `${IMAGE_CDN}/${type}/${hash}/${size}x${size}-000000-80-0-0.jpg`;
}

export function parseUserData(root: Json): {
  userId: string;
  apiToken: string;
  lovedTracksId: string | null;
  name: string;
  avatarUrl: string | null;
} | null {
  const results = obj(root.results);
  const user = obj(results?.USER);
  const userId = str(user?.USER_ID)?.trim();
  if (!userId || userId === '0') return null;
  const apiToken = str(results?.checkForm)?.trim();
  if (!apiToken) return null;
  const lovedTracksId = str(user?.LOVEDTRACKS_ID)?.trim() || null;
  const name = str(user?.BLOG_NAME)?.trim() || 'Deezer';
  const avatarUrl = coverUrl('user', str(user?.USER_PICTURE), 64);
  return { userId, apiToken, lovedTracksId, name, avatarUrl };
}

function artistsOfGwTrack(item: Json): string {
  const names = arr(item.ARTISTS)
    .map((a) => str(path(a, 'ART_NAME'))?.trim())
    .filter((n): n is string => !!n);
  if (names.length > 0) return names.join(', ');
  return str(item.ART_NAME)?.trim() ?? '';
}

function trackOfGw(item: unknown): DeezerTrack | null {
  const row = obj(item);
  if (!row) return null;
  const id = str(row.SNG_ID)?.trim();
  const title = str(row.SNG_TITLE)?.trim();
  if (!id || !title) return null;
  const version = str(row.VERSION)?.trim();
  const fullTitle =
    version && !title.toLowerCase().includes(version.toLowerCase()) ? `${title} ${version}` : title;
  const durationSec = num(row.DURATION) ?? 0;
  return {
    id,
    title: fullTitle,
    artist: artistsOfGwTrack(row),
    album: str(row.ALB_TITLE)?.trim() || null,
    durationMs: Math.max(0, durationSec) * 1000,
    imageUrl: coverUrl('cover', str(row.ALB_PICTURE)),
  };
}

function trackOfPublic(item: unknown): DeezerTrack | null {
  const row = obj(item);
  if (!row) return null;
  const id = str(row.id)?.trim();
  const title = str(row.title)?.trim();
  if (!id || !title) return null;
  const contributors = arr(row.contributors)
    .map((c) => str(path(c, 'name'))?.trim())
    .filter((n): n is string => !!n);
  const artist = contributors.length > 0 ? contributors.join(', ') : (str(path(row, 'artist', 'name'))?.trim() ?? '');
  const albumObj = obj(row.album);
  const durationSec = num(row.duration) ?? 0;
  const cover =
    str(albumObj?.cover_big) ??
    str(albumObj?.cover_xl) ??
    str(albumObj?.cover_medium) ??
    coverUrl('cover', str(albumObj?.md5_image));
  return {
    id,
    title,
    artist,
    album: str(albumObj?.title)?.trim() || null,
    durationMs: Math.max(0, durationSec) * 1000,
    imageUrl: cover,
  };
}

export function parsePlaylistPage(root: Json): DeezerPage<DeezerPlaylist> {
  const tab = obj(path(root, 'results', 'TAB', 'playlists')) ?? obj(path(root, 'results', 'playlists'));
  if (!tab) throw new Error('Deezer library response was empty');
  const raw = arr(tab.data);
  const items = raw.flatMap((entry): DeezerPlaylist[] => {
    const item = obj(entry);
    if (!item) return [];
    const id = str(item.PLAYLIST_ID)?.trim();
    const name = str(item.TITLE)?.trim();
    if (!id || !name) return [];
    const picType = str(item.PICTURE_TYPE)?.trim() || 'playlist';
    const imageUrl =
      coverUrl(picType, str(item.PLAYLIST_PICTURE)) ??
      str(item.PICTURE_BIG) ??
      str(item.PICTURE_XL) ??
      str(item.PICTURE_MEDIUM);
    return [
      {
        id,
        name,
        owner: str(item.PARENT_USERNAME)?.trim() || null,
        imageUrl,
      },
    ];
  });
  const total = num(tab.total) ?? raw.length;
  return { items, total, raw: raw.length };
}

export function parsePublicPlaylistsPage(root: Json): DeezerPage<DeezerPlaylist> & { lovedTracksId: string | null } {
  if (hasErrors(root.error)) throw new Error('Deezer public playlists returned an error');
  const raw = arr(root.data);
  let lovedTracksId: string | null = null;
  const items = raw.flatMap((entry): DeezerPlaylist[] => {
    const item = obj(entry);
    if (!item) return [];
    const id = str(item.id)?.trim();
    const name = str(item.title)?.trim();
    if (!id || !name) return [];
    const isLoved = item.is_loved_track === true;
    if (isLoved) lovedTracksId = id;
    const owner = str(path(item, 'creator', 'name'))?.trim() ?? str(path(item, 'user', 'name'))?.trim() ?? null;
    const imageUrl =
      str(item.picture_big) ??
      str(item.picture_xl) ??
      str(item.picture_medium) ??
      coverUrl(str(item.picture_type)?.trim() || 'playlist', str(item.md5_image));
    return [
      {
        id: isLoved ? LOVED_ID : id,
        name: isLoved ? 'Favorite Tracks' : name,
        owner,
        imageUrl,
      },
    ];
  });
  const total = num(root.total) ?? raw.length;
  return { items, total, raw: raw.length, lovedTracksId };
}

export function parseTrackPage(root: Json): DeezerPage<DeezerTrack> {
  const results = obj(root.results);
  if (!results) throw new Error('Deezer playlist response was empty');
  const raw = arr(results.data);
  const items = raw.flatMap((entry) => {
    const track = trackOfGw(entry);
    return track ? [track] : [];
  });
  const total = num(results.total) ?? num(results.count) ?? raw.length;
  return { items, total, raw: raw.length };
}

export function parsePublicTracksPage(root: Json): DeezerPage<DeezerTrack> {
  if (hasErrors(root.error)) throw new Error('Deezer public playlist tracks returned an error');
  const raw = arr(root.data);
  const items = raw.flatMap((entry) => {
    const track = trackOfPublic(entry);
    return track ? [track] : [];
  });
  const total = num(root.total) ?? raw.length;
  return { items, total, raw: raw.length };
}
