import { fitsOnShareCard, pickShareLines, shareTimeline, ShareSourceLine } from '@bitchord/shared-kotlin';
import { describe, expect, it } from 'vitest';
import { line } from '../model';
import { revealedChars, wrap } from './card';
import { videoType } from './record';

const verse = [
  [10_000, 'First line'],
  [14_000, 'Second line'],
  [18_000, ''],
  [30_000, 'Third line'],
  [34_000, 'Fourth line'],
].map(([t, s]) => new ShareSourceLine(t as number, s as string, null));

// LyricsShareLines.kt, run from the web: what a pick puts on the card, and what the video plays.
describe('lyrics share (LyricsShareLines.kt), from the web', () => {
  it('marks a skip between picked lines and times each line from its stamp', () => {
    const lines = pickShareLines(verse, [3, 0, 1]);
    expect(lines.map((l) => (l.isGap ? '(...)' : l.text))).toEqual(['First line', 'Second line', '(...)', 'Third line']);
    // A run plays through to the next stamp; its last line stops at the next stamp, at most 9 s on.
    expect(lines.map((l) => [l.startMs ?? null, l.endMs ?? null])).toEqual([
      [10_000, 14_000],
      [14_000, 18_000],
      [null, null],
      [30_000, 34_000],
    ]);
    expect(lines.map((l) => l.source)).toEqual([0, 1, -1, 3]);
  });

  it('holds a last line until it was sung, plus a beat', () => {
    const [only] = pickShareLines([new ShareSourceLine(5_000, 'Alone', 7_000), new ShareSourceLine(20_000, 'Later', null)], [0]);
    expect([only!.startMs, only!.endMs]).toEqual([5_000, 7_400]);
  });

  it('strings the sung stretches together, one take per run', () => {
    const timeline = shareTimeline(pickShareLines(verse, [0, 1, 3]))!;
    expect(timeline.segments.map((s) => [s.startMs, s.endMs])).toEqual([
      [10_000, 18_000],
      [30_000, 34_000],
    ]);
    expect(timeline.durationMs).toBe(12_000);
    expect(timeline.trackMsAt(0)).toBe(10_000);
    expect(timeline.trackMsAt(8_500)).toBe(30_500);
    expect(timeline.trackMsAt(99_000)).toBe(34_000);
  });

  it('stops at a story clip’s 60 seconds', () => {
    const long = Array.from({ length: 20 }, (_, i) => new ShareSourceLine(i * 8_000, `Line ${i}`, null));
    const timeline = shareTimeline(pickShareLines(long, long.map((_, i) => i)))!;
    expect(timeline.durationMs).toBe(60_000);
  });

  it('has no timeline for unsynced lyrics', () => {
    const plain = [new ShareSourceLine(0, 'One', null), new ShareSourceLine(0, 'Two', null)];
    const lines = pickShareLines(plain, [0, 1]);
    expect(lines.every((l) => l.startMs == null)).toBe(true);
    expect(shareTimeline(lines)).toBeNull();
  });

  it('holds a card to 250 characters of words', () => {
    expect(fitsOnShareCard(240, 'x'.repeat(10))).toBe(true);
    expect(fitsOnShareCard(240, 'x'.repeat(11))).toBe(false);
    expect(fitsOnShareCard(250, null)).toBe(true);
  });
});

describe('the share card', () => {
  const sung = line(1000, 'Hold on to me', {
    words: [
      { startMs: 1000, endMs: 1400, text: 'Hold', syllables: [] },
      { startMs: 1500, endMs: 1700, text: 'on', syllables: [] },
      { startMs: 1800, endMs: 2000, text: 'to', syllables: [] },
      {
        startMs: 2000,
        endMs: 2400,
        text: 'me',
        syllables: [
          { startMs: 2000, endMs: 2200, startChar: 0, endChar: 1 },
          { startMs: 2200, endMs: 2400, startChar: 1, endChar: 2 },
        ],
      },
    ],
  });

  it('sweeps each word at its own pace and waits between words', () => {
    expect(revealedChars(sung, 900)).toBe(0);
    expect(revealedChars(sung, 1200)).toBe(2);
    expect(revealedChars(sung, 1450)).toBe(4);
    expect(revealedChars(sung, 1600)).toBe(6);
    expect(revealedChars(sung, 2300)).toBe(12.5);
    expect(revealedChars(sung, 5000)).toBe(13);
  });

  it('wraps at spaces, and inside a line that has none', () => {
    const chars = (s: string) => s.length;
    // As breakText then lastIndexOf(' ') on the phone: the break walks back to the last space inside the fit.
    expect(wrap('Hold on to me now', 10, chars)).toEqual(['Hold on', 'to me now']);
    expect(wrap('Hold on to me now', 12, chars)).toEqual(['Hold on to', 'me now']);
    expect(wrap('抱きしめて離さないで', 4, chars)).toEqual(['抱きしめ', 'て離さな', 'いで']);
    expect(wrap('   ', 10, chars)).toEqual(['']);
  });
});

describe('the share video', () => {
  it('records MP4 where it can, else WebM', () => {
    expect(videoType(() => true)?.extension).toBe('mp4');
    expect(videoType((t) => t.startsWith('video/webm'))).toEqual({ mimeType: 'video/webm;codecs=vp9,opus', extension: 'webm' });
    expect(videoType(() => false)).toBeNull();
  });
});
