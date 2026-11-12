import { describe, expect, it } from 'vitest';
import { albumIdOf, matches, motionUrls, normalizeForMatch, ranked, splitArtists } from './motion';

const song = (name: string, artistName: string, albumName: string, url = '') => ({ attributes: { name, artistName, albumName, url } });

describe('Apple motion artwork matching', () => {
  it('folds accents, case and punctuation, and keeps only a-z0-9 as the phone does', () => {
    expect(normalizeForMatch('Beyoncé - CRAZY IN LOVE (feat. JAY-Z)')).toBe('beyonce crazy in love feat jay z');
  });

  it('splits credit lines on every separator the services use', () => {
    expect(splitArtists('Anuv Jain, AP Dhillon')).toEqual(['anuv jain', 'ap dhillon']);
    expect(splitArtists('Calvin Harris feat. Rihanna')).toEqual(['calvin harris', 'rihanna']);
    expect(splitArtists('A x B & C')).toEqual(['a', 'b', 'c']);
  });

  it('ranks the exact hit first and drops another artist and compilations', () => {
    const hits = [
      song('Lavender Haze', 'Taylor Swift', 'Midnights'),
      song('Anti-Hero', 'Taylor Swift', 'Midnights'),
      song('Anti-Hero', 'Somebody Else', 'Midnights'),
      song('Anti-Hero', 'Taylor Swift', "Today's Hits"),
    ];
    const out = ranked(hits, 'Anti-Hero', 'Taylor Swift', 'Midnights');
    expect(out[0]).toBe(hits[1]);
    expect(out).not.toContain(hits[2]);
    expect(out).not.toContain(hits[3]);
    // Another song on the right album clears the bar, as on the phone; the re-check is what turns it away.
    expect(matches({ title: 'Lavender Haze', artist: 'Taylor Swift', album: 'Midnights' }, 'Anti-Hero', 'Taylor Swift', 'Midnights')).toBe(false);
  });

  it('prefers the plain album over a deluxe edition on an album search', () => {
    const plain = { attributes: { name: 'Midnights', artistName: 'Taylor Swift' } };
    const deluxe = { attributes: { name: 'Midnights (The Til Dawn Edition)', artistName: 'Taylor Swift' } };
    expect(ranked([deluxe, plain], 'Midnights', 'Taylor Swift', 'Midnights', true)[0]).toBe(plain);
  });

  it('takes the square rendition first, the next one as the retry', () => {
    expect(
      motionUrls({ motionDetailTall: { video: 'tall.m3u8' }, motionDetailSquare: { video: 'square.m3u8' } }),
    ).toEqual({ url: 'square.m3u8', fallbackUrl: 'tall.m3u8' });
    expect(motionUrls({})).toBeNull();
  });

  it('finds the album id in a relationship or at the end of the web URL', () => {
    expect(albumIdOf({ relationships: { albums: { data: [{ id: '1649434004' }] } } })).toBe('1649434004');
    expect(albumIdOf(song('x', 'y', 'z', 'https://music.apple.com/us/album/midnights/1649434004?i=1649434012'))).toBe('1649434004');
    expect(albumIdOf({ relationships: { albums: { data: [{ id: 'pl.123' }] } } })).toBeNull();
  });

  it('re-checks an answer: the album only counts when both sides know it', () => {
    const found = { title: 'Afsos', artist: 'Anuv Jain, AP Dhillon', album: 'Afsos - Single' };
    expect(matches(found, 'Afsos', 'Anuv Jain, AP Dhillon', null)).toBe(true);
    expect(matches(found, 'Afsos', 'Anuv Jain', 'Something Else')).toBe(false);
    expect(matches(found, 'Husn', 'Anuv Jain', null)).toBe(false);
  });
});
