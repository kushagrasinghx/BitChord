import { describe, expect, it } from 'vitest';
import {
  LOVED_ID,
  parsePlaylistPage,
  parsePublicPlaylistsPage,
  parsePublicTracksPage,
  parseTrackPage,
  parseUserData,
} from '../../../../extension/lib/deezerParse';
import { pendingTrack } from './api';

describe('Deezer parsing and pendingTrack', () => {
  it('parses deezer.getUserData and rejects anonymous sessions', () => {
    const valid = parseUserData({
      error: [],
      results: {
        checkForm: 'token123',
        USER: {
          USER_ID: 424242,
          BLOG_NAME: 'Ada',
          USER_PICTURE: 'abcdef0123456789abcdef0123456789',
          LOVEDTRACKS_ID: '999999',
        },
      },
    });
    expect(valid).toEqual({
      userId: '424242',
      apiToken: 'token123',
      lovedTracksId: '999999',
      name: 'Ada',
      avatarUrl: 'https://cdn-images.dzcdn.net/images/user/abcdef0123456789abcdef0123456789/64x64-000000-80-0-0.jpg',
    });

    const anonymous = parseUserData({
      error: [],
      results: {
        checkForm: 'token123',
        USER: { USER_ID: 0 },
      },
    });
    expect(anonymous).toBeNull();
  });

  it('parses gw-light.php playlists and public playlists', () => {
    const gwPage = parsePlaylistPage({
      error: [],
      results: {
        TAB: {
          playlists: {
            data: [
              {
                PLAYLIST_ID: '12345',
                TITLE: 'Synthwave Mix',
                PARENT_USERNAME: 'Ada',
                PLAYLIST_PICTURE: '0123456789abcdef0123456789abcdef',
                PICTURE_TYPE: 'playlist',
              },
            ],
            total: 1,
          },
        },
      },
    });
    expect(gwPage).toEqual({
      items: [
        {
          id: '12345',
          name: 'Synthwave Mix',
          owner: 'Ada',
          imageUrl: 'https://cdn-images.dzcdn.net/images/playlist/0123456789abcdef0123456789abcdef/500x500-000000-80-0-0.jpg',
        },
      ],
      total: 1,
      raw: 1,
    });

    const publicPage = parsePublicPlaylistsPage({
      data: [
        {
          id: 999999,
          title: 'Loved Tracks',
          is_loved_track: true,
          creator: { name: 'Ada' },
          picture_big: 'https://cdn-images.dzcdn.net/images/playlist/loved/500x500.jpg',
        },
      ],
      total: 1,
    });
    expect(publicPage.items[0]?.id).toBe(LOVED_ID);
    expect(publicPage.items[0]?.name).toBe('Favorite Tracks');
    expect(publicPage.lovedTracksId).toBe('999999');

    expect(() => parsePlaylistPage({ error: [], results: {} })).toThrow('Deezer library response was empty');
  });

  it('parses gw-light.php tracks and public tracks and builds pendingTrack', () => {
    const gwTracks = parseTrackPage({
      error: [],
      results: {
        data: [
          {
            SNG_ID: '3135556',
            SNG_TITLE: 'Harder, Better, Faster, Stronger',
            VERSION: '(Radio Edit)',
            ART_NAME: 'Daft Punk',
            ARTISTS: [{ ART_NAME: 'Daft Punk' }],
            ALB_TITLE: 'Discovery',
            ALB_PICTURE: '2e018122cb56986277102d2041a592c8',
            DURATION: '224',
          },
        ],
        total: 1,
      },
    });
    expect(gwTracks.items[0]).toEqual({
      id: '3135556',
      title: 'Harder, Better, Faster, Stronger (Radio Edit)',
      artist: 'Daft Punk',
      album: 'Discovery',
      durationMs: 224_000,
      imageUrl: 'https://cdn-images.dzcdn.net/images/cover/2e018122cb56986277102d2041a592c8/500x500-000000-80-0-0.jpg',
    });

    const publicTracks = parsePublicTracksPage({
      data: [
        {
          id: 3135556,
          title: 'One More Time',
          duration: 320,
          artist: { name: 'Daft Punk' },
          contributors: [{ name: 'Daft Punk' }, { name: 'Romanthony' }],
          album: {
            title: 'Discovery',
            cover_big: 'https://cdn-images.dzcdn.net/images/cover/2e018122cb56986277102d2041a592c8/500x500.jpg',
          },
        },
      ],
      total: 1,
    });
    expect(publicTracks.items[0]?.artist).toBe('Daft Punk, Romanthony');

    const pending = pendingTrack(gwTracks.items[0]!);
    expect(pending.videoId).toBe('deezer:3135556');
    expect(pending.durationSec).toBe(224);
    expect(pending.unavailable).toBe('matching');
  });
});
