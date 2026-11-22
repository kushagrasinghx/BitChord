import { useQuery } from '@tanstack/react-query';
import { getMusicClient } from '@/playback/youtube/innertube';
import { mapPlaylistTracks } from './mappers';
import { useYouTubeReady } from './queries';
import type { Track } from './types';

/**
 * The songs YouTube Music offers to round out one of the account's own
 * playlists: its "Suggestions" shelf, served through the page's section-list
 * continuation rather than with the playlist's rows (InnertubeParser.kt
 * parsePlaylistShelf, read here by YouTube.js's Playlist.getSuggestions). It
 * takes the playlist's first page again to reach that continuation, so it is
 * asked for only on a playlist the account owns, the only kind that has one.
 */
async function fetchPlaylistSuggestions(id: string): Promise<Track[]> {
  const yt = await getMusicClient();
  const playlist = await yt.music.getPlaylist(id);
  return mapPlaylistTracks(await playlist.getSuggestions());
}

/** Not under ['music', 'playlist', id]: that prefix is the playlist's own paged rows, and updates written to it assume their shape. */
export function usePlaylistSuggestions(id: string, enabled: boolean) {
  const { ready } = useYouTubeReady();
  return useQuery({
    queryKey: ['music', 'playlist-suggestions', id],
    queryFn: () => fetchPlaylistSuggestions(id),
    enabled: ready && enabled,
    staleTime: 10 * 60_000,
    retry: false,
  });
}
