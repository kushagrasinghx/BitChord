import type { SongCreditGroup } from '@bitchord/shared-kotlin';
import { getMusicClient } from '@/playback/youtube/innertube';

/**
 * A track's credits, as SongInfoRepository.fetchCredits finds them: YouTube
 * Music's credits page for the track (MPTC + its id) first, else the one its
 * `next` menu links to. The pages are read by the Android app's own parser
 * (SongCredits.kt). The watch page's music section, the app's last resort,
 * is left out: www.youtube.com's HTML isn't something a page can fetch.
 */
export async function fetchCredits(videoId: string): Promise<SongCreditGroup[]> {
  const yt = await getMusicClient();
  // On demand: the Kotlin runtime is large, and every track menu leads here.
  const { creditsBrowse, songCredits } = await import('@bitchord/shared-kotlin');
  const browse = async (browseId: string, params?: string | null) =>
    JSON.stringify((await yt.actions.execute('/browse', { browseId, ...(params && { params }), client: 'YTMUSIC' })).data);
  // A track without a credits page is answered with an error; the `next` route is still worth asking.
  const direct = await browse(`MPTC${videoId}`).then(songCredits, () => []);
  if (direct.length) return direct;
  const next = await yt.actions.execute('/next', { videoId, client: 'YTMUSIC' });
  const endpoint = creditsBrowse(JSON.stringify(next.data), videoId);
  return endpoint ? songCredits(await browse(endpoint.browseId, endpoint.params)) : [];
}
