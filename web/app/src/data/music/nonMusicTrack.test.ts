import { describe, expect, it } from 'vitest';
import { YTNodes } from 'youtubei.js';
import { mapTrack } from './mappers';

// A playlist row as YouTube Music sent it (trimmed of tracking and menus): a
// music video added to a playlist, which YouTube links to an audio-track page
// and YouTube.js therefore types "non_music_track".
const row = {
  thumbnail: {
    musicThumbnailRenderer: { thumbnail: { thumbnails: [{ url: 'https://i.ytimg.com/vi/NUo8CKI34o4/hqdefault.jpg', width: 400, height: 225 }] } },
  },
  flexColumns: [
    {
      musicResponsiveListItemFlexColumnRenderer: {
        text: {
          runs: [
            {
              text: 'Tum Hi Ho',
              navigationEndpoint: {
                browseEndpoint: {
                  browseId: 'MPEDNUo8CKI34o4',
                  browseEndpointContextSupportedConfigs: { browseEndpointContextMusicConfig: { pageType: 'MUSIC_PAGE_TYPE_NON_MUSIC_AUDIO_TRACK_PAGE' } },
                },
              },
            },
          ],
        },
      },
    },
    {
      musicResponsiveListItemFlexColumnRenderer: {
        text: {
          runs: [
            {
              text: 'Arijit Singh',
              navigationEndpoint: {
                browseEndpoint: {
                  browseId: 'UCDxKh1gFWeYsqePvgVzmPoQ',
                  browseEndpointContextSupportedConfigs: { browseEndpointContextMusicConfig: { pageType: 'MUSIC_PAGE_TYPE_ARTIST' } },
                },
              },
            },
          ],
        },
      },
    },
    { musicResponsiveListItemFlexColumnRenderer: { text: { runs: [{ text: 'Mar 24, 2013' }] } } },
  ],
  fixedColumns: [{ musicResponsiveListItemFixedColumnRenderer: { text: { runs: [{ text: '2:21' }] } } }],
  playlistItemData: { videoId: 'NUo8CKI34o4' },
};

describe('a music video in a playlist', () => {
  it('is kept, with its artist and length', () => {
    const node = new YTNodes.MusicResponsiveListItem(row);
    expect(node.item_type).toBe('non_music_track');
    expect(mapTrack(node)).toMatchObject({
      videoId: 'NUo8CKI34o4',
      title: 'Tum Hi Ho',
      artists: [{ name: 'Arijit Singh', id: 'UCDxKh1gFWeYsqePvgVzmPoQ' }],
      album: null,
      durationSec: 141,
    });
  });
});
