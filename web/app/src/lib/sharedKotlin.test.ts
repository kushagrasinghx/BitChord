import { artworkAt, playlistMoves, searchFilters } from '@bitchord/shared-kotlin';
import { describe, expect, it } from 'vitest';

// The Android app's own rules (shared/commonMain), run here as compiled JavaScript.
describe('shared Kotlin, from the web', () => {
  it('reorders a playlist with the fewest moves (PlaylistOrder.kt)', () => {
    const moves = playlistMoves(['a', 'b', 'c', 'd'], ['b', 'c', 'd', 'a']);
    expect(moves.map((m) => [m.setVideoId, m.before ?? null])).toEqual([['a', null]]);
  });

  it('upgrades a YouTube thumbnail for a large cover (Models.kt artworkAt)', () => {
    expect(artworkAt('https://i.ytimg.com/vi/abc/hqdefault.jpg', 544)).toBe('https://i.ytimg.com/vi/abc/maxresdefault.jpg');
  });

  it('lists the search filters with their YouTube Music params', () => {
    const library = searchFilters().find((f) => f.id === 'LIBRARY');
    expect(library?.params).toBe('agIYBA==');
    expect(library?.requiresAuth).toBe(true);
  });
});
