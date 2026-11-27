// @vitest-environment happy-dom
import { describe, expect, it, vi } from 'vitest';
import { line } from './model';
import { lookupLyrics } from './repository';
import { useLyricsSettings } from './settings';

vi.mock('./http', () => ({ lyricsGet: async () => null }));
vi.mock('./providers', () => ({
  lrclib: async () => ({ lines: [line(1000, 'line timed')], ttml: '<tt/>' }),
  // QQ's stand-in for an instrumental: "this song has no lyrics, please enjoy".
  betterLyricsPortato: async () => ({
    lines: [line(0, '此歌曲为没有填词的纯音乐，请您欣赏', { words: [{ startMs: 0, endMs: 5000, text: '此歌曲为没有填词的纯音乐，请您欣赏', syllables: [] }] })],
    ttml: '<tt/>',
  }),
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

  it("passes over a source that answers an instrumental with QQ's no-lyrics notice", async () => {
    useLyricsSettings.setState({ order: ['better-lyrics-portato', 'lrclib'], disabled: [], preferSyllables: false });
    const track = { videoId: 'instrumenta', title: 'Instrumental', artist: 'Artist', durationMs: 180_000, album: null };

    expect((await lookupLyrics(track))?.source).toBe('lrclib');
  });
});
