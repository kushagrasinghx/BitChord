import { albumHref, artistHref } from '@/components/music/links';
import { fetchTrackLinks, findArtistPageId } from '@/data/music/api';
import type { AlbumRef, ArtistRef } from '@/data/music/types';
import { isExactArtistMatch, normalizedArtistName } from '@/lib/artistCredits';
import { queryClient } from '@/lib/queryClient';

/** The track's own watch-queue entry, asked for once per track. */
function trackLinks(videoId: string) {
  return queryClient
    .fetchQuery({ queryKey: ['music', 'track-links', videoId], queryFn: () => fetchTrackLinks(videoId), staleTime: Infinity })
    .catch(() => null);
}

function searchHref(query: string): string {
  return `/search?q=${encodeURIComponent(query)}`;
}

/**
 * Where the title goes: the track's album. Queue rows from home cards often
 * don't carry it, so the track's watch-queue entry is asked; a track with no
 * album at all (a video) opens a search for it instead.
 */
export async function albumPageFor(videoId: string, album: AlbumRef | null, fallbackQuery: string): Promise<string> {
  if (album?.id) return albumHref(album.id)!;
  const linked = await trackLinks(videoId);
  return albumHref(linked?.album?.id ?? null) ?? searchHref(fallbackQuery);
}

/**
 * Where a credited name goes, as the phone resolves it: its own channel when
 * the track linked one; else the channel the track's watch-queue entry links
 * under that exact name; else the artist search answers to exactly that name.
 * Nothing near enough is ever opened. Where the phone says it couldn't find
 * them, the search page opens on the name.
 */
export async function artistPageFor(videoId: string, credit: ArtistRef): Promise<string> {
  if (credit.id) return artistHref(credit.id)!;
  const linked = await trackLinks(videoId);
  const stated = linked?.artists.find((a) => a.id && isExactArtistMatch(a.name, credit.name));
  if (stated) return artistHref(stated.id)!;
  const found = await queryClient
    .fetchQuery({
      queryKey: ['music', 'artist-by-name', normalizedArtistName(credit.name)],
      queryFn: () => findArtistPageId(credit.name),
      staleTime: Infinity,
    })
    .catch(() => null);
  return artistHref(found) ?? searchHref(credit.name);
}
