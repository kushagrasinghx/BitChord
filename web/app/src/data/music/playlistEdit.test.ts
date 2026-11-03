import { describe, expect, it } from 'vitest';
import { privacyOf } from '@/components/music/playlistPrivacy';
import { continuationToken, playlistItemIds } from './api';

describe('playlistItemIds', () => {
  it('finds every row and its id within the playlist, however deep', () => {
    const answer = {
      contents: [
        { musicResponsiveListItemRenderer: { playlistItemData: { videoId: 'a', playlistSetVideoId: 'SA' } } },
        { wrapper: [{ musicResponsiveListItemRenderer: { playlistItemData: { videoId: 'b', playlistSetVideoId: 'SB' } } }] },
        { musicResponsiveListItemRenderer: { playlistItemData: { videoId: 'c' } } },
      ],
    };
    expect(playlistItemIds(answer)).toEqual([
      { videoId: 'a', setVideoId: 'SA' },
      { videoId: 'b', setVideoId: 'SB' },
    ]);
  });
});

describe('continuationToken', () => {
  it('reads either way YouTube writes the next page', () => {
    expect(continuationToken({ a: [{ continuationItemRenderer: { continuationEndpoint: { continuationCommand: { token: 'T1' } } } }] })).toBe('T1');
    expect(continuationToken({ continuations: [{ nextContinuationData: { continuation: 'T2' } }] })).toBe('T2');
    expect(continuationToken({ contents: [] })).toBeNull();
  });
});

describe('privacyOf', () => {
  it('reads the privacy from the header line, private by default', () => {
    expect(privacyOf('Playlist • Private • 2026')).toBe('PRIVATE');
    expect(privacyOf('Playlist • Public • 2024')).toBe('PUBLIC');
    expect(privacyOf('Playlist • Unlisted')).toBe('UNLISTED');
    expect(privacyOf('Playlist • 2026')).toBe('PRIVATE');
  });
});
