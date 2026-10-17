import { describe, expect, it } from 'vitest';
import {
  best,
  hasConflictingAlbums,
  matches,
  queries,
  romajiOf,
  uniquelyMostCreditedCloseMatch,
  type MatchCandidate,
} from './trackMatcher';

// Ported from app/src/test/…/SourcesTest.kt ("Cross-source matching" and
// "Choosing between candidates"); durations there are "m:ss" text.
const secs = (text?: string) => (text ? text.split(':').reduce((t, p) => t * 60 + Number(p), 0) : null);
const song = (title: string, artist: string, duration?: string, album: string | null = null): MatchCandidate => ({
  title,
  artist,
  durationSec: secs(duration),
  album,
});

describe('TrackMatcher', () => {
  it('matches the same recording across differing catalogue titles', () => {
    expect(matches(song('Bohemian Rhapsody (Remastered 2011)', 'Queen'), 'Bohemian Rhapsody', 'Queen')).toBe(true);
    expect(matches(song('Sunflower', 'Post Malone, Swae Lee'), 'Sunflower (feat. Swae Lee)', 'Post Malone')).toBe(true);
    expect(matches(song("Don't Stop Me Now", 'QUEEN'), 'Dont Stop Me Now', 'Queen')).toBe(true);
  });

  it('matches CJK titles across punctuation and spacing differences', () => {
    expect(matches(song('夜に駆ける', 'YOASOBI', '4:20'), '夜 に 駆ける！', 'YOASOBI', 261)).toBe(true);
    expect(matches(song('좋은 날', 'IU'), '좋은날', 'IU')).toBe(true);
  });

  it('keeps substring matching strict for short Unicode titles', () => {
    expect(matches(song('愛情', 'Artist'), '愛', 'Artist')).toBe(false);
    expect(matches(song('봄날', 'Artist'), '봄', 'Artist')).toBe(false);
  });

  it('matches a film credit against a bare catalogue listing', () => {
    expect(
      matches(song('Paniyon Sa', 'Atif Aslam, Tulsi Kumar', '4:07'), 'Paniyon Sa (From "Satyameva Jayate")', 'Atif Aslam', 247),
    ).toBe(true);
    expect(matches(song('Paniyon Sa (From "Satyameva Jayate")', 'Atif Aslam'), 'Paniyon Sa', 'Atif Aslam, Tulsi Kumar')).toBe(true);
  });

  it('strips upload labelling from either side', () => {
    expect(matches(song('Tum Hi Ho', 'Arijit Singh'), 'Tum Hi Ho Full Song', 'Arijit Singh')).toBe(true);
    expect(matches(song('Kesariya', 'Arijit Singh'), 'Kesariya - Brahmastra | Official Video', 'Arijit Singh')).toBe(true);
    expect(matches(song('Believer', 'Imagine Dragons'), 'Imagine Dragons - Believer', 'Imagine Dragons')).toBe(true);
  });

  it('refuses a different song by the same artist, and a cover by another', () => {
    expect(matches(song('The Show Must Go On', 'Queen'), 'Bohemian Rhapsody', 'Queen')).toBe(false);
    expect(matches(song('Hurt', 'Johnny Cash'), 'Hurt', 'Nine Inch Nails')).toBe(false);
  });

  it('accepts a shared artist when catalogues credit differently', () => {
    expect(matches(song('Numb / Encore', 'Jay-Z & Linkin Park'), 'Numb / Encore', 'Linkin Park')).toBe(true);
  });

  it('accepts a composer credit against a singer credit on an exact runtime, and only then', () => {
    expect(matches(song('Jhak Maar Ke', 'Neeraj Shridhar', '3:53'), 'Jhak Maar Ke', 'Pritam', 233)).toBe(true);
    expect(matches(song('Jhak Maar Ke', 'Leo Lz Mix', '4:01'), 'Jhak Maar Ke', 'Pritam', 233)).toBe(false);
    expect(matches(song('Jhak Maar Ke', 'Neeraj Shridhar'), 'Jhak Maar Ke', 'Pritam')).toBe(false);
    expect(matches(song('Jhak Maar Ke', 'Neeraj Shridhar', '3:53'), 'Jhak Maar Ke', 'Pritam', null)).toBe(false);
  });

  it('prefers a credited match over a runtime-vouched one', () => {
    const vouched = song('Jhak Maar Ke', 'Some Uploader', '3:53');
    const credited = song('Jhak Maar Ke', 'Neeraj Shridhar', '3:53');
    expect(best([vouched, credited], { title: 'Jhak Maar Ke', artist: 'Pritam, Neeraj Shridhar', durationSec: 233 })).toBe(credited);
  });

  it('refuses an artist whose name merely contains the one asked for', () => {
    expect(matches(song('No One Knows', 'Queens of the Stone Age'), 'No One Knows', 'Queen')).toBe(false);
  });

  it('refuses a different take of the same song', () => {
    expect(matches(song('Shape of You (Acoustic)', 'Ed Sheeran'), 'Shape of You', 'Ed Sheeran')).toBe(false);
    expect(matches(song('Shape of You', 'Ed Sheeran'), 'Shape of You (Acoustic)', 'Ed Sheeran')).toBe(false);
    expect(matches(song('Creep (Live)', 'Radiohead'), 'Creep', 'Radiohead')).toBe(false);
    expect(matches(song('Faded', 'Alan Walker'), 'Faded (Slowed + Reverb)', 'Alan Walker')).toBe(false);
    expect(
      matches(
        song('Apna Bana Le - Arijit Singh Vocals Only', 'Arijit Singh, Sachin-Jigar'),
        'Apna Bana Le (From "Bhediya")',
        'Arijit Singh',
      ),
    ).toBe(false);
    expect(matches(song('Kesariya (Instrumental)', 'Arijit Singh'), 'Kesariya', 'Arijit Singh')).toBe(false);
    expect(matches(song('Creep (Live)', 'Radiohead'), 'Creep [Live]', 'Radiohead')).toBe(true);
  });

  it('treats an album or radio version as the plain track', () => {
    expect(matches(song('Africa', 'Toto'), 'Africa (Album Version)', 'Toto')).toBe(true);
    expect(matches(song('Clocks', 'Coldplay'), 'Clocks (Radio Edit)', 'Coldplay')).toBe(true);
  });

  it('refuses a candidate whose runtime is nowhere near', () => {
    expect(matches(song('Levitating', 'Dua Lipa', '1:00:12'), 'Levitating', 'Dua Lipa', 203)).toBe(false);
    expect(matches(song('Levitating', 'Dua Lipa', '3:25'), 'Levitating', 'Dua Lipa', 203)).toBe(true);
  });

  it('falls back to title alone when no artist is known', () => {
    expect(matches(song('Clair de Lune', 'Debussy'), 'Clair de Lune', '')).toBe(true);
    expect(matches(song('Reverie', 'Debussy'), 'Clair de Lune', '')).toBe(false);
  });

  it('picks the closest candidate rather than the first acceptable one', () => {
    const wrongLength = song('Paniyon Sa', 'Atif Aslam', '4:32');
    const right = song('Paniyon Sa', 'Atif Aslam, Tulsi Kumar', '4:06');
    expect(best([wrongLength, right], { title: 'Paniyon Sa', artist: 'Atif Aslam', durationSec: 247 })).toBe(right);
  });

  it('uses the album to separate duplicate recordings', () => {
    const wanted = song('Brown Rang', 'Yo Yo Honey Singh', '2:59', 'International Villager');
    const closer = song('Brown Rang', 'Yo Yo Honey Singh', '2:54', 'Chaar Ikke');
    expect(
      best([closer, wanted], { title: 'Brown Rang', artist: 'Yo Yo Honey Singh', durationSec: 175, album: 'International Villager' }),
    ).toBe(wanted);
    expect(hasConflictingAlbums([closer, wanted], { title: 'Brown Rang', artist: 'Yo Yo Honey Singh', durationSec: 175 })).toBe(true);
  });

  it('resolves a collision only by a unique fullest credit', () => {
    const target = { title: 'Ek Dil Ek Jaan', artist: 'Shivam Pathak', durationSec: 220 };
    const full = song('Ek Dil Ek Jaan', 'Shivam Pathak, Shashwat Singh', '3:40', 'Padmaavat');
    const short = song('Ek Dil Ek Jaan', 'Shivam Pathak', '3:41', 'Romance');
    expect(uniquelyMostCreditedCloseMatch([short, full], target)).toBe(full);
  });

  it('transliterates kana and asks in the other script', () => {
    expect(romajiOf('コイコガレ')).toBe('koikogare');
    expect(romajiOf('しゃしん')).toBe('shashin');
    expect(romajiOf('がっこう')).toBe('gakkou');
    expect(queries({ title: 'コイコガレ', artist: 'milet' })).toEqual(['コイコガレ milet', 'コイコガレ', 'koikogare']);
    expect(queries({ title: 'Paniyon Sa (From "Satyameva Jayate")', artist: 'Atif Aslam, Tulsi Kumar' })).toEqual([
      'paniyon sa atif aslam',
      'paniyon sa',
    ]);
  });

  it('matches a bracketed CJK translation against its Latin filing', () => {
    expect(matches(song('Love', 'Singer', '2:53'), '愛(Love)', 'Singer', 174)).toBe(true);
    expect(matches(song('Love (愛)', 'Singer', '2:53'), '愛', 'Singer', 174)).toBe(true);
    // Same-script packaging must not rescue a different title.
    expect(matches(song('Film Name', 'Some Artist', '4:07'), 'Song Name (From "Film Name")', 'Singer', 247)).toBe(false);
  });
});
