import { describe, expect, it } from 'vitest';
import { artistCredits, creditSegments, isExactArtistMatch } from './artistCredits';

// The phone's cases (app/src/test/…/ArtistCreditsTest.kt), so the two agree.
const split = (line: string) => artistCredits(line, []);

describe('artistCredits', () => {
  it('keeps every stated artist with its own page', () => {
    const credits = artistCredits('', [
      { name: 'BedeoSa', id: 'UC_bedeosa' },
      { name: '2115', id: 'UC_2115' },
    ]);
    expect(credits.map((c) => c.id)).toEqual(['UC_bedeosa', 'UC_2115']);
  });

  it('uses stated credits even when the line says something else', () => {
    const credits = artistCredits('BedeoSa, 2115, Flexxy', [
      { name: 'BedeoSa', id: 'UC_a' },
      { name: '2115', id: 'UC_b' },
    ]);
    expect(credits.map((c) => c.name)).toEqual(['BedeoSa', '2115']);
  });

  it('drops blank stated names', () => {
    const names = artistCredits('', [
      { name: 'BedeoSa', id: null },
      { name: '   ', id: null },
      { name: '2115', id: null },
    ]).map((c) => c.name);
    expect(names).toEqual(['BedeoSa', '2115']);
  });

  it('splits a byline on its joiners only, with no channel invented', () => {
    expect(split('Beyoncé, Jay-Z & Kanye West').map((c) => c.name)).toEqual(['Beyoncé', 'Jay-Z', 'Kanye West']);
    expect(split('Drake ＆ 21 Savage').map((c) => c.name)).toEqual(['Drake', '21 Savage']);
    expect(split('AC/DC').map((c) => c.name)).toEqual(['AC/DC']);
    expect(split('Florence and the Machine').map((c) => c.name)).toEqual(['Florence and the Machine']);
    expect(split('Anitta & Shakira').every((c) => c.id === null)).toBe(true);
  });

  it('gives no empty credits for blank lines or dangling joiners', () => {
    expect(split('   ')).toEqual([]);
    for (const line of ['Anitta &', 'Anitta,', '& Shakira', ', ,']) {
      expect(split(line).every((c) => c.name.trim())).toBe(true);
    }
  });

  it('splits a whole byline held as one unlinked artist', () => {
    expect(artistCredits('Anitta, Shakira', [{ name: 'Anitta, Shakira', id: null }]).map((c) => c.name)).toEqual([
      'Anitta',
      'Shakira',
    ]);
  });
});

describe('creditSegments', () => {
  it('redraws the line character for character', () => {
    for (const line of ['2115, Bedoes 2115, White 2115', 'Bedoes 2115 and Lanek', 'Anitta & Shakira', 'BedeoSa']) {
      expect(creditSegments(line, split(line)).map((s) => s.text).join('')).toBe(line);
    }
  });

  it('links every name of a live collaboration to its own channel', () => {
    const segments = creditSegments('2115, Bedoes 2115, White 2115', [
      { name: '2115', id: 'UC_2115' },
      { name: 'Bedoes 2115', id: 'UC_bedoes' },
      { name: 'White 2115', id: 'UC_white' },
    ]);
    expect(segments.map((s) => s.text)).toEqual(['2115', ', ', 'Bedoes 2115', ', ', 'White 2115']);
    expect(segments.filter((s) => s.credit).map((s) => s.credit!.id)).toEqual(['UC_2115', 'UC_bedoes', 'UC_white']);
  });

  it('lets the longer name win', () => {
    const segments = creditSegments('2115 i Bedoes 2115', [
      { name: '2115', id: 'UC_a' },
      { name: 'Bedoes 2115', id: 'UC_b' },
    ]);
    expect(segments.map((s) => s.text)).toEqual(['2115', ' i ', 'Bedoes 2115']);
  });

  it("draws the line's own spelling and leaves the punctuation unlinked", () => {
    const segments = creditSegments('Bedeosa, 2115', [
      { name: 'BedeoSa', id: 'UC_a' },
      { name: '2115', id: 'UC_b' },
    ]);
    expect(segments.map((s) => s.text)).toEqual(['Bedeosa', ', ', '2115']);
    expect(segments[1]!.credit).toBeNull();
  });

  it('is one plain stretch with nothing credited, and nothing for an empty line', () => {
    expect(creditSegments('BedeoSa, 2115', [])).toEqual([{ text: 'BedeoSa, 2115', credit: null }]);
    expect(creditSegments('', [])).toEqual([]);
  });
});

describe('isExactArtistMatch', () => {
  it('ignores case, accents and spacing, and nothing else', () => {
    expect(isExactArtistMatch('Beyonce', 'beyoncé')).toBe(true);
    expect(isExactArtistMatch('Мата', 'Мата')).toBe(true);
    expect(isExactArtistMatch('Мета', 'Мата')).toBe(false);
    expect(isExactArtistMatch('', '  ')).toBe(false);
  });
});
