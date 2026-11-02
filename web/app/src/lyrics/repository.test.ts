// @vitest-environment happy-dom
import { describe, expect, it, vi } from 'vitest';
import { line } from './model';
import { lookupLyrics } from './repository';
import { useLyricsSettings } from './settings';

vi.mock('./http', () => ({ lyricsGet: async () => null }));
vi.mock('./providers', () => ({
  lrclib: async () => ({ lines: [line(1000, 'line timed')], ttml: '<tt/>' }),
  lyricsPlus: async () => ({
    lines: [line(1000, 'word', { words: [{ startMs: 1000, endMs: 1500, text: 'word', syllables: [] }] })],
    ttml: '<tt/>',
  }),
}));

describe('lookupLyrics', () => {
  it('asks again when "prefer syllable timing" changes', async () => {
    useLyricsSettings.setState({ order: ['lrclib', 'lyrics-plus'], disabled: [], preferSyllables: false });
    const track = { videoId: 'abcdefghijk', title: 'Song', artist: 'Artist', durationMs: 180_000, album: null };

    expect((await lookupLyrics(track))?.source).toBe('lrclib');
    useLyricsSettings.setState({ preferSyllables: true });
    expect((await lookupLyrics(track))?.source).toBe('lyrics-plus');
  });
});
