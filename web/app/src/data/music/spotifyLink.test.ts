import { describe, expect, it } from 'vitest';
import { spotifyPlaylistId } from './spotifyLink';

describe('spotifyPlaylistId', () => {
  it('reads a share link, a URI or a bare id', () => {
    expect(spotifyPlaylistId('https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M?si=abc')).toBe('37i9dQZF1DXcBWIGoYBM5M');
    expect(spotifyPlaylistId('spotify:playlist:37i9dQZF1DXcBWIGoYBM5M')).toBe('37i9dQZF1DXcBWIGoYBM5M');
    expect(spotifyPlaylistId(' 37i9dQZF1DXcBWIGoYBM5M ')).toBe('37i9dQZF1DXcBWIGoYBM5M');
  });

  it('turns down anything that is not a playlist', () => {
    expect(spotifyPlaylistId('https://open.spotify.com/album/37i9dQZF1DXcBWIGoYBM5M')).toBeNull();
    expect(spotifyPlaylistId('daft punk')).toBeNull();
  });
});
