import { songSorts } from '@bitchord/shared-kotlin';
import { describe, expect, it, vi } from 'vitest';
import type { MusicItem, Track } from './types';

const session = { context: { client: { hl: 'de' } } };
const requests: { browseId: unknown; hl: string }[] = [];
// "Mixed for you", as the German page and the English one title the same cards.
const pages: Record<string, [string, string][]> = {
  de: [
    ['VLRDTMAK1', 'Mein Supermix'],
    ['VLRDTMAK2', 'Chill-Mix 1'],
    ['VLRDTMAK3', 'Chill-Mix 2'],
    ['VLRDTMAK4', 'Moderne Punjabi-Beats'],
  ],
  en: [
    ['VLRDTMAK1', 'My Supermix'],
    ['VLRDTMAK2', 'Chill Mix 1'],
    ['VLRDTMAK3', 'Chill Mix 2'],
    ['VLRDTMAK4', 'Modern Punjabi Beats'],
  ],
};
vi.mock('@/playback/youtube/innertube', () => ({
  getSignedInnertube: async () => ({
    session,
    actions: {
      execute: async (_endpoint: string, args: { browseId: string }) => {
        const hl = session.context.client.hl;
        requests.push({ browseId: args.browseId, hl });
        return { contents_memo: { getType: () => (pages[hl] ?? []).map(([id, title]) => ({ id, title })) } };
      },
    },
  }),
}));
vi.mock('./mappers', () => ({
  mapTwoRowItem: ({ id, title }: { id: string; title: string }) => ({ kind: 'playlist', id, title }) as MusicItem,
}));

const { fetchMixes, groupedMixes, SONG_SORTS, sortTracks } = await import('./library');

describe('Radio', () => {
  it('reads the page again in English to sort by, matched by playlist id', async () => {
    const mixes = await fetchMixes();
    expect(requests).toEqual([
      { browseId: 'FEmusic_mixed_for_you', hl: 'en' },
      { browseId: 'FEmusic_mixed_for_you', hl: 'de' },
    ]);
    // The session's own language is left as it was.
    expect(session.context.client.hl).toBe('de');
    expect(mixes.map((m) => [m.item.title, m.englishTitle])).toEqual([
      ['Mein Supermix', 'My Supermix'],
      ['Chill-Mix 1', 'Chill Mix 1'],
      ['Chill-Mix 2', 'Chill Mix 2'],
      ['Moderne Punjabi-Beats', 'Modern Punjabi Beats'],
    ]);

    // MixGroups.kt: the named mix as a tile, the mood as a row named in the shown language, the rest the listener's.
    const sections = groupedMixes(mixes);
    expect(sections.stations.map((i) => i.title)).toEqual(['Mein Supermix']);
    expect(sections.moods).toMatchObject([{ key: 'chill', name: 'Chill-Mix', items: [{ title: 'Chill-Mix 1' }, { title: 'Chill-Mix 2' }] }]);
    expect(sections.yours.map((i) => i.title)).toEqual(['Moderne Punjabi-Beats']);
  });

  it('asks once in English', async () => {
    session.context.client.hl = 'en-GB';
    requests.length = 0;
    pages['en-GB'] = pages.en!;
    await fetchMixes();
    expect(requests).toHaveLength(1);
  });
});

describe('song sorts', () => {
  const tracks = ['banana', 'Apple', 'cherry'].map((title) => ({ title }) as Track);

  it('offers every SongSort.kt order but track number, which needs a file’s tags', () => {
    expect(SONG_SORTS.map((s) => s.value).sort()).toEqual(songSorts().filter((s) => s !== 'TRACK_NUMBER').sort());
  });

  it('sorts as DetailSort.kt does: titles without case, dates by position', () => {
    const titles = (sort: Parameters<typeof sortTracks>[1]) => sortTracks(tracks, sort).map((t) => t.title);
    expect(titles('DEFAULT')).toEqual(['banana', 'Apple', 'cherry']);
    expect(titles('TITLE_ASC')).toEqual(['Apple', 'banana', 'cherry']);
    expect(titles('TITLE_DESC')).toEqual(['cherry', 'banana', 'Apple']);
    expect(titles('DATE_ADDED_ASC')).toEqual(['banana', 'Apple', 'cherry']);
    expect(titles('DATE_ADDED_DESC')).toEqual(['cherry', 'Apple', 'banana']);
  });
});
