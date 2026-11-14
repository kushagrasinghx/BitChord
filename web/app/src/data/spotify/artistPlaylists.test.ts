import { describe, expect, it } from 'vitest';
import { playlistsAbout } from './artistPlaylists';

const p = (name: string, owner: string | null = 'Spotify') => ({ id: name + (owner ?? ''), name, owner, imageUrl: null });

describe('playlistsAbout', () => {
  it("keeps Spotify's own playlists about the artist: This Is, then Radio, then the rest", () => {
    const found = [p('Radiohead Live'), p('Radiohead Radio'), p('This Is Radiohead'), p('This Is Radiohead', 'sean burke'), p('Alternative 90s')];
    expect(playlistsAbout('Radiohead', found).map((x) => x.name)).toEqual(['This Is Radiohead', 'Radiohead Radio', 'Radiohead Live']);
  });

  it('matches the whole name, ignoring case and accents', () => {
    expect(playlistsAbout('Beyonce', [p('This Is Beyoncé')]).map((x) => x.name)).toEqual(['This Is Beyoncé']);
    expect(playlistsAbout('Radio', [p('This Is Radiohead')])).toEqual([]);
  });

  it('lists a playlist both searches found once, and finds nothing for a blank artist', () => {
    expect(playlistsAbout('Radiohead', [p('This Is Radiohead'), p('This Is Radiohead')])).toHaveLength(1);
    expect(playlistsAbout('  ', [p('This Is Radiohead')])).toEqual([]);
  });
});
