import { describe, expect, it } from 'vitest';
import {
  isTokenError,
  LOVED_ID,
  parsePlaylistPage,
  parsePublicPlaylistsPage,
  parsePublicTracksPage,
  parseTrackPage,
  parseUserData,
} from '../../../../extension/lib/deezerParse';
import { createCookieGate, createSessionCache } from '../../../../extension/lib/sessionState';
import { pendingTrack } from './api';

describe('Deezer parsing and pendingTrack', () => {
  it('parses deezer.getUserData and rejects anonymous sessions', () => {
    const valid = parseUserData({
      error: [],
      results: {
        checkForm: 'token123',
        USER: {
          USER_ID: 424242,
          BLOG_NAME: 'Ada',
          USER_PICTURE: 'abcdef0123456789abcdef0123456789',
          LOVEDTRACKS_ID: '999999',
        },
      },
    });
    expect(valid).toEqual({
      userId: '424242',
      apiToken: 'token123',
      sessionId: null,
      lovedTracksId: '999999',
      name: 'Ada',
      avatarUrl: 'https://cdn-images.dzcdn.net/images/user/abcdef0123456789abcdef0123456789/64x64-000000-80-0-0.jpg',
    });

    const anonymous = parseUserData({
      error: [],
      results: {
        checkForm: 'token123',
        USER: { USER_ID: 0 },
      },
    });
    expect(anonymous).toBeNull();
  });

  it('parses gw-light.php playlists and public playlists', () => {
    const gwPage = parsePlaylistPage({
      error: [],
      results: {
        TAB: {
          playlists: {
            data: [
              {
                PLAYLIST_ID: '12345',
                TITLE: 'Synthwave Mix',
                PARENT_USERNAME: 'Ada',
                PLAYLIST_PICTURE: '0123456789abcdef0123456789abcdef',
                PICTURE_TYPE: 'playlist',
              },
            ],
            total: 1,
          },
        },
      },
    });
    expect(gwPage).toEqual({
      items: [
        {
          id: '12345',
          name: 'Synthwave Mix',
          owner: 'Ada',
          imageUrl: 'https://cdn-images.dzcdn.net/images/playlist/0123456789abcdef0123456789abcdef/500x500-000000-80-0-0.jpg',
        },
      ],
      total: 1,
      raw: 1,
    });

    const publicPage = parsePublicPlaylistsPage({
      data: [
        {
          id: 999999,
          title: 'Loved Tracks',
          is_loved_track: true,
          creator: { name: 'Ada' },
          picture_big: 'https://cdn-images.dzcdn.net/images/playlist/loved/500x500.jpg',
        },
      ],
      total: 1,
    });
    expect(publicPage.items[0]?.id).toBe(LOVED_ID);
    expect(publicPage.items[0]?.name).toBe('Favorite Tracks');
    expect(publicPage.lovedTracksId).toBe('999999');

    expect(() => parsePlaylistPage({ error: [], results: {} })).toThrow('Deezer library response was empty');
  });

  it('parses gw-light.php tracks and public tracks and builds pendingTrack', () => {
    const gwTracks = parseTrackPage({
      error: [],
      results: {
        data: [
          {
            SNG_ID: '3135556',
            SNG_TITLE: 'Harder, Better, Faster, Stronger',
            VERSION: '(Radio Edit)',
            ART_NAME: 'Daft Punk',
            ARTISTS: [{ ART_NAME: 'Daft Punk' }],
            ALB_TITLE: 'Discovery',
            ALB_PICTURE: '2e018122cb56986277102d2041a592c8',
            DURATION: '224',
          },
        ],
        total: 1,
      },
    });
    expect(gwTracks.items[0]).toEqual({
      id: '3135556',
      title: 'Harder, Better, Faster, Stronger (Radio Edit)',
      artist: 'Daft Punk',
      album: 'Discovery',
      durationMs: 224_000,
      imageUrl: 'https://cdn-images.dzcdn.net/images/cover/2e018122cb56986277102d2041a592c8/500x500-000000-80-0-0.jpg',
    });

    const publicTracks = parsePublicTracksPage({
      data: [
        {
          id: 3135556,
          title: 'One More Time',
          duration: 320,
          artist: { name: 'Daft Punk' },
          contributors: [{ name: 'Daft Punk' }, { name: 'Romanthony' }],
          album: {
            title: 'Discovery',
            cover_big: 'https://cdn-images.dzcdn.net/images/cover/2e018122cb56986277102d2041a592c8/500x500.jpg',
          },
        },
      ],
      total: 1,
    });
    expect(publicTracks.items[0]?.artist).toBe('Daft Punk, Romanthony');

    const pending = pendingTrack(gwTracks.items[0]!);
    expect(pending.videoId).toBe('deezer:3135556');
    expect(pending.durationSec).toBe(224);
    expect(pending.unavailable).toBe('matching');
  });
});

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((r) => (resolve = r));
  return { promise, resolve };
}

describe('Deezer session handling', () => {
  it('takes the sid checkForm belongs to from getUserData', () => {
    const parsed = parseUserData({
      results: { checkForm: 'tok', SESSION_ID: 'fr123', USER: { USER_ID: 1 } },
    });
    expect(parsed?.sessionId).toBe('fr123');
  });

  it('tells a refused token from a bad request', () => {
    expect(isTokenError({ VALID_TOKEN_REQUIRED: 'Invalid CSRF token' })).toBe(true);
    expect(isTokenError({ NEED_USER_AUTH_REQUIRED: 'Auth required' })).toBe(true);
    expect(isTokenError({ DATA_ERROR: 'playlist not found' })).toBe(false);
    expect(isTokenError([])).toBe(false);
  });

  it('cookie gate: same value shares the rule, another value waits for it to go', async () => {
    const log: string[] = [];
    const withCookie = createCookieGate(async (value) => {
      log.push(value === null ? 'remove' : `set ${value}`);
    });
    const a1 = deferred<void>();
    const a2 = deferred<void>();
    const first = withCookie('A', () => a1.promise.then(() => log.push('ran A1')));
    const second = withCookie('A', () => a2.promise.then(() => log.push('ran A2')));
    const other = withCookie('B', async () => log.push('ran B'));
    await Promise.resolve();
    a1.resolve();
    await first;
    // A2 is still in flight with A's cookie: B must not have replaced it.
    expect(log).toEqual(['set A', 'ran A1']);
    a2.resolve();
    await Promise.all([second, other]);
    expect(log).toEqual(['set A', 'ran A1', 'ran A2', 'remove', 'set B', 'ran B', 'remove']);
  });

  it('session cache never serves an open still running for the previous arl', async () => {
    const pending = new Map<string, ReturnType<typeof deferred<{ arl: string; expiresAt: number }>>>();
    const opened: string[] = [];
    const cache = createSessionCache((arl: string) => {
      opened.push(arl);
      const d = deferred<{ arl: string; expiresAt: number }>();
      pending.set(arl, d);
      return d.promise;
    });
    const forA = cache.get('A');
    const forB = cache.get('B');
    expect(opened).toEqual(['A', 'B']);
    pending.get('A')!.resolve({ arl: 'A', expiresAt: Infinity });
    pending.get('B')!.resolve({ arl: 'B', expiresAt: Infinity });
    expect((await forA).arl).toBe('A');
    expect((await forB).arl).toBe('B');
    // B is cached, and A's late result did not overwrite it.
    expect((await cache.get('B')).arl).toBe('B');
    expect(opened).toEqual(['A', 'B']);
  });

  it('session cache shares one open per arl and re-opens after expiry or invalidation', async () => {
    let clock = 0;
    let opens = 0;
    const cache = createSessionCache(
      async (arl: string) => ({ arl, n: ++opens, expiresAt: clock + 10 }),
      () => clock,
    );
    const [x, y] = await Promise.all([cache.get('A'), cache.get('A')]);
    expect(x).toBe(y);
    expect(opens).toBe(1);
    clock = 10;
    const z = await cache.get('A');
    expect(z.n).toBe(2);
    cache.invalidate(x); // stale handle: the newer session stays
    expect(await cache.get('A')).toBe(z);
    cache.invalidate(z);
    expect((await cache.get('A')).n).toBe(3);
  });
});
