import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { HeaderInfo, Track } from './types';

const calls: { endpoint: string; args: Record<string, unknown> }[] = [];
let answer: (endpoint: string, args: Record<string, unknown>) => unknown = () => ({});
vi.mock('@/playback/youtube/innertube', () => ({
  getSignedInnertube: async () => ({
    actions: {
      execute: async (endpoint: string, args: Record<string, unknown>) => {
        calls.push({ endpoint, args });
        return { data: answer(endpoint, args) };
      },
    },
  }),
}));
vi.mock('@/lib/queryClient', () => ({ queryClient: {} }));

const { deletePlaylist, fetchPlaylistEntries, isOwnedPlaylist, moveBatches, parseEntriesPage, renamePlaylist, reorderPlaylist, reorderedTracks } =
  await import('./playlistEdit');

// A real playlist page (web/fixtures, captured signed out): its rows carry their set-video-ids all the same.
const fixture = JSON.parse(readFileSync(fileURLToPath(new URL('../../../../fixtures/innertube/browse-playlist.json', import.meta.url)), 'utf8'));

beforeEach(() => {
  calls.length = 0;
  answer = () => ({});
});

describe('playlist entries', () => {
  it("reads the playlist's own rows, not the page's related shelves", () => {
    const page = parseEntriesPage(fixture)!;
    expect(page.entries.length).toBe(17);
    expect(page.entries[0]).toMatchObject({ setVideoId: '56B44F6D10557CC6', videoId: 'FntjAArPFeQ' });
    expect(page.entries[0]!.title).toMatch(/Subterranean Homesick Alien/);
    expect(page.entries.every((e) => e.thumbnail?.startsWith('https:'))).toBe(true);
    // The section's own continuation is the page's related shelves, not more songs.
    expect(page.next).toBeNull();
  });

  const row = (id: string) => ({ musicResponsiveListItemRenderer: { playlistItemData: { playlistSetVideoId: `set-${id}`, videoId: id } } });
  const more = (token: string) => ({
    continuationItemRenderer: { continuationEndpoint: { continuationCommand: { token } } },
  });

  it('follows a long playlist through its appended pages', async () => {
    answer = (_endpoint, args) =>
      args.continuation === 'page-2'
        ? { onResponseReceivedActions: [{ appendContinuationItemsAction: { continuationItems: [row('c')] } }] }
        : {
            contents: {
              twoColumnBrowseResultsRenderer: {
                secondaryContents: { sectionListRenderer: { contents: [{ musicPlaylistShelfRenderer: { contents: [row('a'), row('b'), more('page-2')] } }] } },
              },
            },
          };
    const entries = await fetchPlaylistEntries('PLmine');
    expect(entries.map((e) => e.setVideoId)).toEqual(['set-a', 'set-b', 'set-c']);
    expect(calls.map((c) => c.args)).toEqual([
      { browseId: 'VLPLmine', client: 'YTMUSIC' },
      { continuation: 'page-2', client: 'YTMUSIC' },
    ]);
  });

  it('refuses a row it could not move', () => {
    const broken = { continuationContents: { musicPlaylistShelfContinuation: { contents: [{ musicResponsiveListItemRenderer: {} }] } } };
    expect(() => parseEntriesPage(broken)).toThrow();
  });
});

describe('playlist edits (Innertube.kt bodies)', () => {
  it('moves the fewest songs, each before its new neighbour or to the end', async () => {
    await reorderPlaylist('VLPLmine', ['a', 'b', 'c', 'd'], ['b', 'c', 'a', 'd']);
    expect(calls).toEqual([
      {
        endpoint: '/browse/edit_playlist',
        args: { playlistId: 'PLmine', actions: [{ action: 'ACTION_MOVE_VIDEO_BEFORE', setVideoId: 'a', movedSetVideoIdSuccessor: 'd' }], client: 'YTMUSIC' },
      },
    ]);
    expect(moveBatches(['a', 'b', 'c', 'd'], ['b', 'c', 'd', 'a'])[0]).toEqual([{ action: 'ACTION_MOVE_VIDEO_BEFORE', setVideoId: 'a' }]);
  });

  it('sends a big rearrangement fifty moves at a time', () => {
    const ids = Array.from({ length: 120 }, (_, i) => `s${i}`);
    expect(moveBatches(ids, [...ids].reverse()).map((b) => b.length)).toEqual([50, 50, 19]);
  });

  it('renames and deletes', async () => {
    await renamePlaylist('VLPLmine', 'Road trip');
    await deletePlaylist('VLPLmine');
    expect(calls).toEqual([
      { endpoint: '/browse/edit_playlist', args: { playlistId: 'PLmine', actions: [{ action: 'ACTION_SET_PLAYLIST_NAME', playlistName: 'Road trip' }], client: 'YTMUSIC' } },
      { endpoint: '/playlist/delete', args: { playlistId: 'PLmine', client: 'YTMUSIC' } },
    ]);
  });

  it('fails when YouTube refuses in the body', async () => {
    answer = () => ({ status: 'STATUS_FAILED' });
    await expect(renamePlaylist('PLtheirs', 'Mine now')).rejects.toThrow(/refused/);
  });
});

describe('ownership and the open page', () => {
  const header = { library: null } as HeaderInfo;
  it('offers editing only on a signed-in, unsaveable, editable playlist', () => {
    expect(isOwnedPlaylist('VLPLmine', header, true)).toBe(true);
    expect(isOwnedPlaylist('VLPLmine', header, false)).toBe(false);
    expect(isOwnedPlaylist('VLPLtheirs', { library: { playlistId: 'PLtheirs', saved: true } } as HeaderInfo, true)).toBe(false);
    expect(isOwnedPlaylist('VLLM', header, true)).toBe(false);
    expect(isOwnedPlaylist('VLRDCLAK5uy', header, true)).toBe(false);
  });

  it('puts the loaded rows in the new order, telling repeats apart', () => {
    const track = (videoId: string, title: string) => ({ videoId, title }) as Track;
    const rows = [track('x', 'first x'), track('y', 'y'), track('x', 'second x')];
    const entry = (videoId: string, setVideoId: string) => ({ videoId, setVideoId, title: '', byline: '', thumbnail: null });
    const order = [entry('y', '2'), entry('x', '1'), entry('x', '3')];
    expect(reorderedTracks(rows, order)?.map((t) => t.title)).toEqual(['y', 'first x', 'second x']);
    expect(reorderedTracks(rows.slice(0, 2), order)).toBeNull();
  });
});
