// @vitest-environment happy-dom
import { describe, expect, it } from 'vitest';
import { withBackgroundVocals } from './model';
import { decodeEntities, parseEnhancedLrc, parseKaraoke, parseLrc, parseProvider, parseTtml } from './parse';
import { writeTtml } from './ttml';

const APPLE = `<tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata">
<head><metadata><ttm:agent type="person" xml:id="v1"/><ttm:agent type="person" xml:id="v2"/></metadata></head>
<body><div>
<p begin="27.395" end="29.0" ttm:agent="v1"><span begin="27.395" end="27.549">I</span> <span begin="27.549" end="27.740">been</span> <span begin="27.8" end="28.0">e</span><span begin="28.0" end="28.6">nough</span><span ttm:role="x-bg"><span begin="28.6" end="29.0">(ooh)</span></span></p>
<p begin="31.0" end="32.0" ttm:agent="v2"><span begin="31.0" end="32.0">Hey</span></p>
</div></body></tt>`;

describe('TTML (TtmlLyrics)', () => {
  it('glues syllables into words and keeps their timings', () => {
    const lines = parseTtml(APPLE).filter((l) => l.text);
    expect(lines[0]!.text).toBe('I been enough');
    const enough = lines[0]!.words[2]!;
    expect(enough).toMatchObject({ text: 'enough', startMs: 27800, endMs: 28600 });
    expect(enough.syllables.map((s) => [s.startChar, s.endChar])).toEqual([
      [0, 1],
      [1, 6],
    ]);
  });
  it('keeps the answering vocal apart', () => {
    const [first] = parseTtml(APPLE).filter((l) => l.text);
    expect(first!.background?.text).toBe('(ooh)');
    expect(first!.words.map((w) => w.text)).not.toContain('(ooh)');
  });
  it('puts a second voice on the other side', () => {
    expect(parseTtml(APPLE).filter((l) => l.text).map((l) => l.alignment)).toEqual(['start', 'end']);
  });
  it('marks a long intro as a gap', () => {
    expect(parseTtml(APPLE)[0]).toMatchObject({ timeMs: 0, text: '' });
  });
});

describe('LRC', () => {
  it('reads enhanced (A2) LRC word stamps', () => {
    const lines = parseEnhancedLrc('[00:27.39]<00:27.39>I <00:27.54>been <00:27.74>tryna\n[00:30.00]<00:30.00>call');
    expect(lines.map((l) => l.text)).toEqual(['', 'I been tryna', 'call']);
    expect(lines[1]!.words[2]).toMatchObject({ startMs: 27740, endMs: 30000 });
  });
  it('sings a line with several stamps at each of them', () => {
    const lines = parseLrc('[00:12.00][00:45.00]chorus\n[00:20.00]verse').filter((l) => l.text);
    expect(lines.map((l) => [l.timeMs, l.text])).toEqual([
      [12_000, 'chorus'],
      [20_000, 'verse'],
      [45_000, 'chorus'],
    ]);
  });

  it('reads plain LRC, dropping metadata tags and short breaks', () => {
    const lines = parseLrc('[ar:Someone]\n[00:01.00]one\n[00:02.00]\n[00:03.50]two');
    expect(lines.map((l) => l.text)).toEqual(['one', 'two']);
  });
  it('decodes HTML entities', () => {
    expect(decodeEntities('don&#x27;t &amp; won&#39;t')).toBe("don't & won't");
  });
});

describe('karaoke (QRC/YRC)', () => {
  it('reads millisecond word ranges', () => {
    const lines = parseKaraoke('[1000,2000](1000,500,0)Hel(1500,500,0)lo (2000,1000,0)there');
    expect(lines[0]).toMatchObject({ timeMs: 1000, text: 'Hello there', sungUntilMs: 3000 });
  });
});

describe('provider envelopes (ProviderLyrics)', () => {
  it('unwraps JSON around TTML and keeps the original document', () => {
    const parsed = parseProvider(JSON.stringify({ ttml: APPLE }));
    expect(parsed?.ttml).toContain('<tt');
    expect(parsed?.lines.some((l) => l.words.length)).toBe(true);
  });
  it('treats an error envelope as nothing', () => {
    expect(parseProvider(JSON.stringify({ error: 'not found' }))).toBeNull();
  });
});

describe('background vocals (BackgroundVocals)', () => {
  it('splits a trailing bracket off a line-synced line', () => {
    const [l] = withBackgroundVocals(parseLrc("[00:01.00]I'm foolishly patient (Foolishly patient)"));
    expect(l).toMatchObject({ text: "I'm foolishly patient", background: { text: '(Foolishly patient)' } });
  });
});

describe('TTML writer', () => {
  it('round-trips word timings, syllables, background and sides', () => {
    const original = parseTtml(APPLE).filter((l) => l.text);
    const again = parseTtml(writeTtml(original)).filter((l) => l.text);
    expect(again.map((l) => l.text)).toEqual(original.map((l) => l.text));
    expect(again[0]!.words[2]!.syllables).toEqual(original[0]!.words[2]!.syllables);
    expect(again[0]!.background?.text).toBe('(ooh)');
    expect(again.map((l) => l.alignment)).toEqual(['start', 'end']);
  });
  it('writes untimed lyrics without times', () => {
    const ttml = writeTtml(parseProvider('first line\nsecond line')!.lines);
    expect(ttml).toContain('itunes:timing="None"');
    expect(ttml).not.toContain('begin=');
  });
});
