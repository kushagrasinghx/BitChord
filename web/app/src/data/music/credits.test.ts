import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { creditsBrowse, songCredits } from '@bitchord/shared-kotlin';
import { describe, expect, it } from 'vitest';

const fixture = (name: string) =>
  readFileSync(fileURLToPath(new URL(`../../../../fixtures/innertube/${name}.json`, import.meta.url)), 'utf8');
const read = (page: unknown) => songCredits(JSON.stringify(page)).map((c) => [c.role, [...c.names]]);
const runs = (text: string) => ({ runs: [{ text }] });

// SongCredits.kt, compiled for the web: shared/src/jvmTest/.../SongCreditsParserTest.kt's cases, and the same capture.
describe('song credits (SongCredits.kt), from the web', () => {
  it('reads a captured credits page', () => {
    const credits = read(JSON.parse(fixture('browse-credits')));
    expect(credits.map(([role]) => role)).toEqual(['Performed by', 'Written by', 'Produced by', 'Music metadata provided by']);
    expect(credits[1]![1]).toEqual(['Thom Yorke', 'Colin Greenwood', "Ed O'Brien", 'Jonny Greenwood', 'Phil Selway']);
  });

  it('finds the credits page a captured `next` links to', () => {
    expect(creditsBrowse(fixture('next-radio'), 'QQnc-hM80UQ')?.browseId).toBe('MPTCQQnc-hM80UQ');
  });

  it('reads credits from responsive list rows, splitting names at a comma before a capital', () => {
    const row = (role: string, names: string) => ({
      musicResponsiveListItemRenderer: {
        flexColumns: [role, names].map((t) => ({ musicResponsiveListItemFlexColumnRenderer: { text: runs(t) } })),
      },
    });
    const page = {
      contents: { sectionListRenderer: { contents: [{ musicShelfRenderer: { contents: [
        row('Performed by', 'Eminem'),
        row('Written by', 'Jeff Bass, Luis Resto & Marshall Mathers'),
        row('Produced by', 'Eminem'),
      ] } }] } },
    };
    expect(read(page)).toEqual([
      ['Performed by', ['Eminem']],
      ['Written by', ['Jeff Bass', 'Luis Resto & Marshall Mathers']],
      ['Produced by', ['Eminem']],
    ]);
  });

  it('reads credits from dialog sections, one name per line', () => {
    const section = (role: string, ...names: string[]) => ({
      dismissableDialogContentSectionRenderer: {
        title: runs(role),
        subtitle: { runs: names.flatMap((text, i) => (i ? [{ text: '\n' }, { text }] : [{ text }])) },
      },
    });
    const credits = read({
      title: 'Song credits',
      sections: [
        section('Performed by', 'Eminem'),
        section('Written by', 'Jeff Bass', 'Luis Resto', 'Marshall Mathers'),
        section('Produced by', 'Eminem'),
        section('Music metadata provided by', 'Eminem Catalog PS'),
      ],
    });
    expect(credits.map(([role]) => role)).toEqual(['Performed by', 'Written by', 'Produced by', 'Music metadata provided by']);
    expect(credits[1]![1]).toEqual(['Jeff Bass', 'Luis Resto', 'Marshall Mathers']);
    expect(credits[3]![1]).toEqual(['Eminem Catalog PS']);
  });

  it('finds the credits endpoint by its page type', () => {
    const nav = (text: string, browseEndpoint: object) => ({ menuNavigationItemRenderer: { text: runs(text), navigationEndpoint: { browseEndpoint } } });
    const next = {
      contents: { playlistPanelRenderer: { contents: [{ playlistPanelVideoRenderer: {
        videoId: 'abcdefghijk',
        menu: { menuRenderer: { items: [
          nav('Go to album', { browseId: 'MPREb_x' }),
          nav('View song credits', {
            browseId: 'MPTC_x',
            browseEndpointContextSupportedConfigs: { browseEndpointContextMusicConfig: { pageType: 'MUSIC_PAGE_TYPE_TRACK_CREDITS' } },
          }),
        ] } },
      } }] } },
    };
    expect(creditsBrowse(JSON.stringify(next), 'abcdefghijk')?.browseId).toBe('MPTC_x');
  });
});
