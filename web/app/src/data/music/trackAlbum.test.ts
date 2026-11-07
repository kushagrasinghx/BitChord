import { describe, expect, it } from 'vitest';
import { albumFromNext } from './trackAlbum';

const albumRun = (name: string, id: string) => ({
  text: name,
  navigationEndpoint: { browseEndpoint: { browseId: id, browseEndpointContextSupportedConfigs: { browseEndpointContextMusicConfig: { pageType: 'MUSIC_PAGE_TYPE_ALBUM' } } } },
});
const artistRun = {
  text: 'Arijit Singh',
  navigationEndpoint: { browseEndpoint: { browseId: 'UCDxKh1gFWeYsqePvgVzmPoQ', browseEndpointContextSupportedConfigs: { browseEndpointContextMusicConfig: { pageType: 'MUSIC_PAGE_TYPE_ARTIST' } } } },
};
const row = (videoId: string, runs: unknown[]) => ({ playlistPanelVideoRenderer: { videoId, longBylineText: { runs } } });

// The shape of /next: the watch queue, the song first, then what follows.
const next = (rows: unknown[]) => ({ contents: { singleColumnMusicWatchNextResultsRenderer: { tabbedRenderer: { watchNextTabbedResultsRenderer: { tabs: [{ tabRenderer: { content: { musicQueueRenderer: { content: { playlistPanelRenderer: { contents: rows } } } } } }] } } } } });

describe('the album from /next', () => {
  it("reads the song's own row, not the next song's", () => {
    const data = next([
      row('other', [artistRun, { text: ' • ' }, albumRun('Something Else', 'MPREb_other')]),
      row('fsiPzT50ZiM', [artistRun, { text: ' • ' }, albumRun('Aashiqui 2', 'MPREb_E4GfUXfDfhy'), { text: ' • ' }, { text: '2013' }]),
    ]);
    expect(albumFromNext(data, 'fsiPzT50ZiM')).toEqual({ name: 'Aashiqui 2', id: 'MPREb_E4GfUXfDfhy' });
  });

  it('is null for a song with no album, or one missing from the answer', () => {
    expect(albumFromNext(next([row('v', [artistRun, { text: ' • ' }, { text: '1.2B views' }])]), 'v')).toBeNull();
    expect(albumFromNext(next([]), 'v')).toBeNull();
  });
});
