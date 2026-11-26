import { isLocalGroupId, localGroupHref } from '@/data/local/ids';
import type { BrowseLink, MusicItem } from '@/data/music/types';

/** Where tapping an item goes; null for songs and videos, which play instead. */
export function hrefFor(item: Pick<MusicItem, 'kind' | 'id'>): string | null {
  if (isLocalGroupId(item.id)) return localGroupHref(item.id);
  switch (item.kind) {
    case 'album':
      return `/album/${encodeURIComponent(item.id)}`;
    case 'playlist':
      return `/playlist/${encodeURIComponent(item.id)}`;
    case 'spotify-playlist':
      return `/spotify/playlist/${encodeURIComponent(item.id)}`;
    case 'artist':
      return `/artist/${encodeURIComponent(item.id)}`;
    case 'podcast':
      return `/browse/${encodeURIComponent(item.id)}`;
    default:
      return null;
  }
}

// A local file credits its LocalGroups pages (LocalGroups.kt), which open in Library › Local music.
export function artistHref(id: string | null): string | null {
  if (isLocalGroupId(id)) return localGroupHref(id!);
  return id ? `/artist/${encodeURIComponent(id)}` : null;
}

export function albumHref(id: string | null): string | null {
  if (isLocalGroupId(id)) return localGroupHref(id!);
  return id ? `/album/${encodeURIComponent(id)}` : null;
}

/** See All and mood/genre pages. Playlists open as playlists. */
export function browseHref(link: BrowseLink): string {
  if (link.browseId.startsWith('VL')) return `/playlist/${encodeURIComponent(link.browseId)}`;
  if (link.browseId.startsWith('MPREb_')) return `/album/${encodeURIComponent(link.browseId)}`;
  if (link.browseId.startsWith('UC')) return `/artist/${encodeURIComponent(link.browseId)}`;
  const query = link.params ? `?params=${encodeURIComponent(link.params)}` : '';
  return `/browse/${encodeURIComponent(link.browseId)}${query}`;
}

export function formatDuration(seconds: number | null): string {
  if (seconds == null || !Number.isFinite(seconds)) return '';
  const s = Math.round(seconds);
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  const sec = String(s % 60).padStart(2, '0');
  return h ? `${h}:${String(m).padStart(2, '0')}:${sec}` : `${m}:${sec}`;
}
