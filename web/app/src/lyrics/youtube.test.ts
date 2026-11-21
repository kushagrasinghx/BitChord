// @vitest-environment happy-dom
import { describe, expect, it } from 'vitest';
import { findLyricsBrowseId, timedYoutubeLyrics } from './youtube';

const timed = {
  contents: {
    elementRenderer: {
      newElement: {
        type: {
          componentType: {
            model: {
              timedLyricsModel: {
                lyricsData: {
                  timedLyricsData: [
                    { lyricLine: 'first line', cueRange: { startTimeMilliseconds: '2210', endTimeMilliseconds: '7440' } },
                    { lyricLine: 'second line', cueRange: { startTimeMilliseconds: 7440, endTimeMilliseconds: 9000 } },
                  ],
                },
              },
            },
          },
        },
      },
    },
  },
};

describe('YouTube Music timed lyrics', () => {
  it('reads the timed lines the phone client is served', () => {
    const lines = timedYoutubeLyrics(timed)!;
    expect(lines.map((l) => l.text)).toEqual(['first line', 'second line']);
    // Line-level only: YouTube Music does not publish word timings here.
    expect(lines[0]).toMatchObject({ timeMs: 2210, sungUntilMs: 7440, words: [] });
    expect(lines[1]).toMatchObject({ timeMs: 7440, sungUntilMs: 9000 });
  });

  it('drops an empty line rather than leaving a gap in the timing', () => {
    const lines = timedYoutubeLyrics({
      timedLyricsData: [
        { lyricLine: '  ', cueRange: { startTimeMilliseconds: '0', endTimeMilliseconds: '100' } },
        { lyricLine: 'kept', cueRange: { startTimeMilliseconds: '100', endTimeMilliseconds: '900' } },
      ],
    })!;
    expect(lines.map((l) => l.text)).toEqual(['kept']);
    expect(lines[0]!.timeMs).toBe(100);
  });

  it('is not lyrics when the page has no timed data', () => {
    expect(timedYoutubeLyrics({ contents: {} })).toBeNull();
  });
});

describe('YouTube Music lyrics tab', () => {
  it('finds the tab by page type, not by its label', () => {
    const next = {
      contents: {
        singleColumnMusicWatchNextResultsRenderer: {
          tabbedRenderer: {
            watchNextTabbedResultsRenderer: {
              tabs: [
                { tabRenderer: { title: 'Up next' } },
                {
                  tabRenderer: {
                    title: 'Lyrics',
                    endpoint: {
                      browseEndpoint: {
                        browseId: 'MPLYt_abc',
                        browseEndpointContextSupportedConfigs: {
                          browseEndpointContextMusicConfig: { pageType: 'MUSIC_PAGE_TYPE_TRACK_LYRICS' },
                        },
                      },
                    },
                  },
                },
              ],
            },
          },
        },
      },
    };
    expect(findLyricsBrowseId(next)).toBe('MPLYt_abc');
  });

  it('returns null when there is no lyrics tab', () => {
    expect(findLyricsBrowseId({ contents: { tabs: [] } })).toBeNull();
  });
});
