// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { Track } from '@/data/music/types';
import { AddonManager } from './addonManager';

describe('AddonManager', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.restoreAllMocks();
  });

  it('filters out tracks that do not match the song title or artist', async () => {
    localStorage.setItem(
      'bitchord:sources:addons',
      JSON.stringify([
        {
          id: 'test_addon',
          name: 'Test Addon',
          manifestUrl: 'https://example.com/manifest.json',
          baseUrl: 'https://example.com',
          streamEndpoint: '/stream/{id}',
          searchEndpoint: '/search',
          enabled: true,
          preferredQuality: 'LOSSLESS',
          health: 'online',
          lastChecked: Date.now(),
        },
      ]),
    );

    // Mock search returning a completely different track (e.g. Zak Abel or Halsey wrong track)
    const mockFetch = vi.fn().mockImplementation((url: string) => {
      if (url.includes('/search')) {
        return Promise.resolve({
          ok: true,
          json: () =>
            Promise.resolve({
              tracks: [
                {
                  id: '123',
                  title: 'Be Kind',
                  artist: 'Zak Abel',
                  streamURL: 'https://example.com/bad.flac',
                },
              ],
            }),
        });
      }
      return Promise.reject(new Error('Unknown url'));
    });

    vi.stubGlobal('fetch', mockFetch);

    const track = {
      videoId: 'ePao0cTGG-o',
      title: 'Be Kind',
      artists: [{ name: 'Marshmello', id: 'UC1' }, { name: 'Halsey', id: 'UC2' }],
      thumbnail: 'https://img.jpg',
      durationSec: 185,
    } as unknown as Track;

    const resolved = await AddonManager.resolveStream(track);
    // Because the candidate is by Zak Abel (does not match Marshmello or Halsey), it must reject
    expect(resolved).toBeNull();
  });

  it('rejects candidates whose stream URLs return 502/404 errors', async () => {
    localStorage.setItem(
      'bitchord:sources:addons',
      JSON.stringify([
        {
          id: 'test_addon',
          name: 'Test Addon',
          manifestUrl: 'https://example.com/manifest.json',
          baseUrl: 'https://example.com',
          streamEndpoint: '/stream/{id}',
          searchEndpoint: '/search',
          enabled: true,
          preferredQuality: 'LOSSLESS',
          health: 'online',
          lastChecked: Date.now(),
        },
      ]),
    );

    const mockFetch = vi.fn().mockImplementation((url: string, opts?: { method?: string }) => {
      if (url.includes('/search')) {
        return Promise.resolve({
          ok: true,
          json: () =>
            Promise.resolve({
              tracks: [
                {
                  id: '456',
                  title: 'Be Kind',
                  artist: 'Marshmello & Halsey',
                  streamURL: 'https://example.com/broken-502.flac',
                },
              ],
            }),
        });
      }
      if (url.includes('broken-502.flac')) {
        // Return 502 Bad Gateway
        return Promise.resolve({
          ok: false,
          status: 502,
          statusText: 'Bad Gateway',
        });
      }
      return Promise.reject(new Error('Not found'));
    });

    vi.stubGlobal('fetch', mockFetch);

    const track = {
      videoId: 'ePao0cTGG-o',
      title: 'Be Kind',
      artists: [{ name: 'Marshmello', id: 'UC1' }, { name: 'Halsey', id: 'UC2' }],
      thumbnail: 'https://img.jpg',
      durationSec: 185,
    } as unknown as Track;

    const resolved = await AddonManager.resolveStream(track);
    // Because the stream URL is 502 Bad Gateway, it must reject and return null
    expect(resolved).toBeNull();
  });

  it('accepts valid matching candidates with reachable stream URLs', async () => {
    localStorage.setItem(
      'bitchord:sources:addons',
      JSON.stringify([
        {
          id: 'test_addon',
          name: 'Test Addon',
          manifestUrl: 'https://example.com/manifest.json',
          baseUrl: 'https://example.com',
          streamEndpoint: '/stream/{id}',
          searchEndpoint: '/search',
          enabled: true,
          preferredQuality: 'LOSSLESS',
          health: 'online',
          lastChecked: Date.now(),
        },
      ]),
    );

    const mockFetch = vi.fn().mockImplementation((url: string) => {
      if (url.includes('/search')) {
        return Promise.resolve({
          ok: true,
          json: () =>
            Promise.resolve({
              tracks: [
                {
                  id: '789',
                  title: 'Be Kind',
                  artist: 'Marshmello, Halsey',
                  streamURL: 'https://example.com/working.flac',
                },
              ],
            }),
        });
      }
      if (url.includes('working.flac')) {
        return Promise.resolve({
          ok: true,
          status: 200,
        });
      }
      return Promise.reject(new Error('Not found'));
    });

    vi.stubGlobal('fetch', mockFetch);

    const track = {
      videoId: 'ePao0cTGG-o',
      title: 'Be Kind',
      artists: [{ name: 'Marshmello', id: 'UC1' }, { name: 'Halsey', id: 'UC2' }],
      thumbnail: 'https://img.jpg',
      durationSec: 185,
    } as unknown as Track;

    const resolved = await AddonManager.resolveStream(track);
    expect(resolved).not.toBeNull();
    expect(resolved?.streamUrl).toBe('https://example.com/working.flac');
    expect(resolved?.format).toBe('Lossless Audio');
  });

  it('matches tracks even when the YouTube title contains video fluff like (Official Music Video)', async () => {
    localStorage.setItem(
      'bitchord:sources:addons',
      JSON.stringify([
        {
          id: 'test_addon',
          name: 'Test Addon',
          manifestUrl: 'https://example.com/manifest.json',
          baseUrl: 'https://example.com',
          streamEndpoint: '/stream/{id}',
          searchEndpoint: '/search',
          enabled: true,
          preferredQuality: 'LOSSLESS',
          health: 'online',
          lastChecked: Date.now(),
        },
      ]),
    );

    const mockFetch = vi.fn().mockImplementation((url: string) => {
      if (url.includes('/search')) {
        return Promise.resolve({
          ok: true,
          json: () =>
            Promise.resolve({
              tracks: [
                {
                  id: 'starboy_1',
                  title: 'Starboy',
                  artist: 'The Weeknd, Daft Punk',
                  streamURL: 'https://example.com/starboy.flac',
                },
              ],
            }),
        });
      }
      if (url.includes('starboy.flac')) {
        return Promise.resolve({
          ok: true,
          status: 200,
        });
      }
      return Promise.reject(new Error('Not found'));
    });

    vi.stubGlobal('fetch', mockFetch);

    const track = {
      videoId: '34Na4j8AVgA',
      title: 'Starboy (Official Music Video)',
      artists: [{ name: 'The Weeknd', id: 'UC1' }],
      thumbnail: 'https://img.jpg',
      durationSec: 230,
    } as unknown as Track;

    const resolved = await AddonManager.resolveStream(track);
    expect(resolved).not.toBeNull();
    expect(resolved?.streamUrl).toBe('https://example.com/starboy.flac');
    expect(resolved?.codec).toBe('FLAC');
  });

  it('caches resolved streams for fast instantaneous return on subsequent plays', async () => {
    localStorage.setItem(
      'bitchord:sources:addons',
      JSON.stringify([
        {
          id: 'test_addon',
          name: 'Test Addon',
          manifestUrl: 'https://example.com/manifest.json',
          baseUrl: 'https://example.com',
          streamEndpoint: '/stream/{id}',
          searchEndpoint: '/search',
          enabled: true,
          preferredQuality: 'LOSSLESS',
          health: 'online',
          lastChecked: Date.now(),
        },
      ]),
    );

    let fetchCount = 0;
    const mockFetch = vi.fn().mockImplementation((url: string) => {
      fetchCount++;
      if (url.includes('/search')) {
        return Promise.resolve({
          ok: true,
          json: () =>
            Promise.resolve({
              tracks: [
                {
                  id: 'starboy_1',
                  title: 'Starboy',
                  artist: 'The Weeknd',
                  streamURL: 'https://example.com/starboy.flac',
                },
              ],
            }),
        });
      }
      if (url.includes('starboy.flac')) {
        return Promise.resolve({
          ok: true,
          status: 200,
        });
      }
      return Promise.reject(new Error('Not found'));
    });

    vi.stubGlobal('fetch', mockFetch);

    const track = {
      videoId: 'cached_test_id',
      title: 'Starboy',
      artists: [{ name: 'The Weeknd', id: 'UC1' }],
      thumbnail: 'https://img.jpg',
      durationSec: 230,
    } as unknown as Track;

    // First lookup: queries network
    const first = await AddonManager.resolveStream(track);
    expect(first).not.toBeNull();
    const countAfterFirst = fetchCount;

    // Second lookup: should be served from memory cache without new fetch calls
    const second = await AddonManager.resolveStream(track);
    expect(second).toEqual(first);
    expect(fetchCount).toBe(countAfterFirst);
  });

  it('rejects streams that return HTML error pages disguised as audio', async () => {
    localStorage.setItem(
      'bitchord:sources:addons',
      JSON.stringify([
        {
          id: 'test_addon',
          name: 'Test Addon',
          manifestUrl: 'https://example.com/manifest.json',
          baseUrl: 'https://example.com',
          streamEndpoint: '/stream/{id}',
          searchEndpoint: '/search',
          enabled: true,
          preferredQuality: 'LOSSLESS',
          health: 'online',
          lastChecked: Date.now(),
        },
      ]),
    );

    const mockFetch = vi.fn().mockImplementation((url: string) => {
      if (url.includes('/search')) {
        return Promise.resolve({
          ok: true,
          json: () =>
            Promise.resolve({
              tracks: [
                {
                  id: 'html_error_track',
                  title: 'Only Living Girl in LA',
                  artist: 'Halsey',
                  streamURL: 'https://example.com/bad-gateway.flac',
                },
              ],
            }),
        });
      }
      if (url.includes('bad-gateway.flac')) {
        return Promise.resolve({
          ok: true,
          status: 200,
          text: () => Promise.resolve('<!DOCTYPE html><html><head><title>502 Bad Gateway</title></head></html>'),
        });
      }
      return Promise.reject(new Error('Unknown url'));
    });

    vi.stubGlobal('fetch', mockFetch);

    const track = {
      videoId: 'test_html_err',
      title: 'Only Living Girl in LA',
      artists: [{ name: 'Halsey', id: 'UC2' }],
      thumbnail: 'https://img.jpg',
      durationSec: 375,
    } as unknown as Track;

    const resolved = await AddonManager.resolveStream(track);
    expect(resolved).toBeNull();
  });

  it('circuit breaker trips after 3 consecutive failures to avoid blocking playback', async () => {
    AddonManager.clearCache();
    const addonId = 'failing_addon';
    expect(AddonManager.isAddonInCooldown(addonId)).toBe(false);

    AddonManager.recordAddonFailure(addonId);
    AddonManager.recordAddonFailure(addonId);
    expect(AddonManager.isAddonInCooldown(addonId)).toBe(false);

    // 3rd failure trips the breaker into cooldown
    AddonManager.recordAddonFailure(addonId);
    expect(AddonManager.isAddonInCooldown(addonId)).toBe(true);

    // Success clears the breaker
    AddonManager.recordAddonSuccess(addonId);
    expect(AddonManager.isAddonInCooldown(addonId)).toBe(false);
  });

  it('deduplicates concurrent in-flight searches for the same track', async () => {
    AddonManager.clearCache();
    localStorage.setItem(
      'bitchord:sources:addons',
      JSON.stringify([
        {
          id: 'test_addon',
          name: 'Test Addon',
          manifestUrl: 'https://example.com/manifest.json',
          baseUrl: 'https://example.com',
          streamEndpoint: '/stream/{id}',
          searchEndpoint: '/search',
          enabled: true,
          preferredQuality: 'LOSSLESS',
          health: 'online',
          lastChecked: Date.now(),
        },
      ]),
    );

    let searchCount = 0;
    const mockFetch = vi.fn().mockImplementation(async (url: string) => {
      if (url.includes('/search')) {
        searchCount++;
        await new Promise((r) => setTimeout(r, 20));
        return {
          ok: true,
          json: () =>
            Promise.resolve({
              tracks: [
                {
                  id: 'dedup_1',
                  title: 'Levitating',
                  artist: 'Dua Lipa',
                  streamURL: 'https://example.com/levitating.flac',
                },
              ],
            }),
        };
      }
      if (url.includes('levitating.flac')) {
        return {
          ok: true,
          status: 200,
          text: () => Promise.resolve('fLaC binary data'),
        };
      }
      return Promise.reject(new Error('Unknown url'));
    });

    vi.stubGlobal('fetch', mockFetch);

    const track = {
      videoId: 'levitating_id',
      title: 'Levitating',
      artists: [{ name: 'Dua Lipa', id: 'UC3' }],
      thumbnail: 'https://img.jpg',
      durationSec: 203,
    } as unknown as Track;

    // Fire 3 simultaneous resolveStream calls
    const [res1, res2, res3] = await Promise.all([
      AddonManager.resolveStream(track),
      AddonManager.resolveStream(track),
      AddonManager.resolveStream(track),
    ]);

    expect(res1).not.toBeNull();
    expect(res2).toEqual(res1);
    expect(res3).toEqual(res1);
    // Only one search request should have been made to the network
    expect(searchCount).toBe(1);
  });

  it('rejects candidate tracks with wild duration mismatches (e.g. 30s preview vs full song)', async () => {
    AddonManager.clearCache();
    localStorage.setItem(
      'bitchord:sources:addons',
      JSON.stringify([
        {
          id: 'test_addon',
          name: 'Test Addon',
          manifestUrl: 'https://example.com/manifest.json',
          baseUrl: 'https://example.com',
          streamEndpoint: '/stream/{id}',
          searchEndpoint: '/search',
          enabled: true,
          preferredQuality: 'LOSSLESS',
          health: 'online',
          lastChecked: Date.now(),
        },
      ]),
    );

    const mockFetch = vi.fn().mockImplementation((url: string) => {
      if (url.includes('/search')) {
        return Promise.resolve({
          ok: true,
          json: () =>
            Promise.resolve({
              tracks: [
                {
                  id: 'preview_1',
                  title: 'As It Was',
                  artist: 'Harry Styles',
                  duration_sec: 30, // 30s preview!
                  streamURL: 'https://example.com/preview.flac',
                },
              ],
            }),
        });
      }
      return Promise.reject(new Error('Unknown url'));
    });

    vi.stubGlobal('fetch', mockFetch);

    const track = {
      videoId: 'as_it_was_id',
      title: 'As It Was',
      artists: [{ name: 'Harry Styles', id: 'UC4' }],
      durationSec: 167, // Full song is 2m47s
    } as unknown as Track;

    const resolved = await AddonManager.resolveStream(track);
    // Must be rejected because 30s preview does not match 167s full track
    expect(resolved).toBeNull();
  });
});
