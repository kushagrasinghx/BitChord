import { describe, expect, it } from 'vitest';
import { songLink } from './links';

describe('songLink', () => {
  it('links to the song on YouTube Music', () => {
    expect(songLink('dQw4w9WgXcQ')).toBe('https://music.youtube.com/watch?v=dQw4w9WgXcQ');
  });
});
