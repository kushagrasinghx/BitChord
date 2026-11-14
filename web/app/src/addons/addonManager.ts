import type { Track } from '@/data/music/types';
import type { AddonManifest, ConfiguredAddon, ResolvedAddonStream } from './types';

const STORAGE_KEY = 'bitchord:sources:addons';
const streamCache = new Map<string, { stream: ResolvedAddonStream; timestamp: number }>();
const CACHE_TTL_MS = 10 * 60 * 1000; // 10 minutes
const MAX_CACHE_ENTRIES = 100;

// Track in-flight search requests to eliminate duplicate network calls (thundering herd)
const inFlightSearches = new Map<string, Promise<ResolvedAddonStream | null>>();

// Circuit breaker: track failures per addon to avoid blocking playback if an addon server degrades
const addonFailures = new Map<string, { count: number; cooldownUntil: number }>();
const MAX_CONSECUTIVE_FAILURES = 3;
const FAILURE_COOLDOWN_MS = 60 * 1000; // 1 minute cooldown

export const DEFAULT_ADDONS: ConfiguredAddon[] = [];

async function safeFetchJson<T = any>(url: string, timeoutMs = 2500): Promise<T> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const res = await fetch(url, { cache: 'no-cache', signal: controller.signal });
    if (res.ok) {
      return (await res.json()) as T;
    }
  } catch {
    // Direct fetch failed or timed out
  } finally {
    clearTimeout(timer);
  }

  throw new Error(`Failed to fetch from ${url}`);
}

export class AddonManager {
  static getAddons(): ConfiguredAddon[] {
    try {
      const data = localStorage.getItem(STORAGE_KEY);
      if (data) {
        const parsed = JSON.parse(data);
        if (Array.isArray(parsed)) return parsed;
      }
      return DEFAULT_ADDONS;
    } catch {
      return DEFAULT_ADDONS;
    }
  }

  static saveAddons(addons: ConfiguredAddon[]): void {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(addons));
    } catch {}
  }

  static async addAddon(rawUrl: string): Promise<ConfiguredAddon> {
    let cleanUrl = rawUrl.trim();
    if (!cleanUrl.startsWith('http://') && !cleanUrl.startsWith('https://')) {
      cleanUrl = `https://${cleanUrl}`;
    }
    if (!cleanUrl.endsWith('.json') && !cleanUrl.endsWith('/manifest')) {
      cleanUrl = cleanUrl.endsWith('/') ? `${cleanUrl}manifest.json` : `${cleanUrl}/manifest.json`;
    }

    const manifest = await safeFetchJson<AddonManifest>(cleanUrl, 4000);
    const urlObj = new URL(cleanUrl);
    const baseUrl = `${urlObj.protocol}//${urlObj.host}`;

    const stream = manifest.endpoints?.stream || manifest.streamEndpoint || manifest.streamUrl || '/stream';
    const search = manifest.endpoints?.search || manifest.searchEndpoint || '/search';
    const health = manifest.endpoints?.health;

    const newAddon: ConfiguredAddon = {
      id: manifest.id || `addon_${Date.now()}_${Math.random().toString(36).substring(2, 7)}`,
      name: manifest.name || urlObj.hostname,
      manifestUrl: cleanUrl,
      baseUrl,
      streamEndpoint: stream,
      searchEndpoint: search,
      healthEndpoint: health,
      enabled: true,
      preferredQuality: 'LOSSLESS',
      health: 'online',
      lastChecked: Date.now(),
    };

    const current = this.getAddons().filter((a) => a.manifestUrl !== cleanUrl);
    const updated = [...current, newAddon];
    this.saveAddons(updated);
    return newAddon;
  }

  static toggleAddon(id: string, enabled: boolean): void {
    const updated = this.getAddons().map((a) => (a.id === id ? { ...a, enabled } : a));
    this.saveAddons(updated);
  }

  static removeAddon(id: string): void {
    const updated = this.getAddons().filter((a) => a.id !== id);
    this.saveAddons(updated);
    addonFailures.delete(id);
  }

  static moveAddon(id: string, delta: number): void {
    const list = [...this.getAddons()];
    const index = list.findIndex((a) => a.id === id);
    if (index < 0) return;
    const target = index + delta;
    if (target < 0 || target >= list.length) return;
    const [item] = list.splice(index, 1);
    list.splice(target, 0, item!);
    this.saveAddons(list);
  }

  /**
   * Fast byte-range verification of audio stream URL.
   * Checks that:
   * 1. Status is 200 or 206 (NOT 502/500/404/403).
   * 2. The response body is real audio data, NOT an HTML error page (e.g. Cloudflare Bad Gateway).
   */
  static async verifyStreamUrl(url: string, timeoutMs = 1800): Promise<boolean> {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), timeoutMs);
    try {
      const res = await fetch(url, {
        method: 'GET',
        headers: { Range: 'bytes=0-256', Accept: 'audio/*;q=0.9, */*;q=0.8' },
        signal: controller.signal,
      });
      clearTimeout(timer);

      // Must be 200 OK or 206 Partial Content
      if (!res.ok && res.status !== 206) {
        return false;
      }

      // Read initial bytes to verify it is NOT an HTML error page (e.g. Cloudflare Bad Gateway)
      let textSample = '';
      if (typeof res.clone === 'function') {
        textSample = await res.clone().text().catch(() => '');
      } else if (typeof (res as any).text === 'function') {
        textSample = await (res as any).text().catch(() => '');
      }
      const trimmed = textSample.trimStart().toLowerCase();
      if (trimmed.startsWith('<!doctype') || trimmed.startsWith('<html') || trimmed.startsWith('<head')) {
        return false;
      }

      return true;
    } catch {
      clearTimeout(timer);
      return false;
    }
  }

  static prefetchStream(track: Track): void {
    if (!track?.title) return;
    void this.resolveStream(track);
  }

  static clearCache(): void {
    streamCache.clear();
    inFlightSearches.clear();
    addonFailures.clear();
  }

  static recordAddonSuccess(addonId: string): void {
    addonFailures.delete(addonId);
  }

  static recordAddonFailure(addonId: string): void {
    const current = addonFailures.get(addonId) || { count: 0, cooldownUntil: 0 };
    const nextCount = current.count + 1;
    if (nextCount >= MAX_CONSECUTIVE_FAILURES) {
      addonFailures.set(addonId, {
        count: nextCount,
        cooldownUntil: Date.now() + FAILURE_COOLDOWN_MS,
      });
    } else {
      addonFailures.set(addonId, {
        count: nextCount,
        cooldownUntil: 0,
      });
    }
  }

  static isAddonInCooldown(addonId: string): boolean {
    const failure = addonFailures.get(addonId);
    if (!failure || failure.cooldownUntil <= 0) return false;
    if (Date.now() > failure.cooldownUntil) {
      addonFailures.delete(addonId);
      return false;
    }
    return true;
  }

  static async resolveStream(track: Track): Promise<ResolvedAddonStream | null> {
    const rawTitle = (track.title || '').trim();
    if (!rawTitle) return null;

    const artistList = track.artists || [];
    const rawArtist = artistList.map((a) => a.name).join(', ').trim();
    const videoId = track.videoId || '';
    const cacheKey = videoId || `${rawTitle}:::${rawArtist}`;

    // 1. Check in-memory cache
    if (streamCache.has(cacheKey)) {
      const cached = streamCache.get(cacheKey)!;
      if (Date.now() - cached.timestamp < CACHE_TTL_MS) {
        return cached.stream;
      }
      streamCache.delete(cacheKey);
    }

    // 2. Check in-flight search promise to deduplicate simultaneous requests
    if (inFlightSearches.has(cacheKey)) {
      return inFlightSearches.get(cacheKey)!;
    }

    const searchPromise = this.doResolveStream(track, cacheKey, rawTitle, rawArtist, artistList, videoId);
    inFlightSearches.set(cacheKey, searchPromise);

    try {
      return await searchPromise;
    } finally {
      inFlightSearches.delete(cacheKey);
    }
  }

  private static async doResolveStream(
    track: Track,
    cacheKey: string,
    rawTitle: string,
    rawArtist: string,
    artistList: Array<{ name: string; id?: string | null }>,
    videoId: string,
  ): Promise<ResolvedAddonStream | null> {
    const addons = this.getAddons().filter((a) => a.enabled && !this.isAddonInCooldown(a.id));
    if (!addons.length) return null;

    const saveAndReturn = (stream: ResolvedAddonStream): ResolvedAddonStream => {
      // LRU eviction if cache grows
      if (streamCache.size >= MAX_CACHE_ENTRIES) {
        const oldestKey = streamCache.keys().next().value;
        if (oldestKey) streamCache.delete(oldestKey);
      }
      streamCache.set(cacheKey, { stream, timestamp: Date.now() });
      return stream;
    };

    const stripFluff = (s: string) =>
      s
        .replace(/\s*[\(\[](?:official\s*(?:video|audio|music\s*video|lyric\s*video)?|lyric\s*video|visualizer|audio|4k|hd|remastered|explicit)[\)\]]/gi, '')
        .replace(/\s*-\s*official\s*(?:video|audio|music\s*video)/gi, '')
        .trim();

    const cleanStr = (s: string) =>
      stripFluff(s)
        .toLowerCase()
        .replace(/[\(\)\[\]\-–—\.,!]/g, ' ')
        .replace(/\s+/g, ' ')
        .trim();

    const cleanTitle = stripFluff(rawTitle) || rawTitle;
    const query = `${cleanTitle} ${rawArtist}`.trim() || cleanTitle;
    const targetDuration = track.durationSec;

    const isMatch = (candidateTitle: string, candidateArtist?: string, candidateDuration?: number): boolean => {
      const candTitle = cleanStr(candidateTitle);
      const targetTitle = cleanStr(rawTitle);
      if (!candTitle || !targetTitle) return false;

      // Title match
      const titleMatches =
        candTitle.includes(targetTitle) ||
        targetTitle.includes(candTitle) ||
        candTitle.split(' ')[0] === targetTitle.split(' ')[0];
      if (!titleMatches) return false;

      // Artist check: if target track has artists, candidate should match or at least not clash
      if (artistList.length > 0 && candidateArtist) {
        const candArtist = cleanStr(candidateArtist);
        const artistMatches = artistList.some((a) => {
          const normA = cleanStr(a.name);
          return normA && (candArtist.includes(normA) || normA.includes(candArtist) || candTitle.includes(normA));
        });
        if (!artistMatches) return false;
      }

      // Duration check: if both target and candidate have durations, prevent previews (<45s) or wild mismatches
      if (targetDuration && candidateDuration) {
        const diff = Math.abs(targetDuration - candidateDuration);
        if (diff > 45) return false;
      }

      return true;
    };

    for (const addon of addons) {
      try {
        // 1. BitChord Standard Protocol: /search?q=... -> /stream/{id}
        const searchPath = addon.searchEndpoint || '/search';
        const searchUrl = searchPath.startsWith('http')
          ? `${searchPath}${searchPath.includes('?') ? '&' : '?'}q=${encodeURIComponent(query)}&quality=${addon.preferredQuality.toLowerCase()}`
          : `${addon.baseUrl}${searchPath.startsWith('/') ? '' : '/'}${searchPath}?q=${encodeURIComponent(query)}&quality=${addon.preferredQuality.toLowerCase()}`;

        try {
          const searchRes = await safeFetchJson<{
            tracks?: Array<{ id: string; title: string; artist?: string; duration?: number; duration_sec?: number; streamURL?: string }>;
            results?: Array<{ id: string; title: string; artist?: string; duration?: number; duration_sec?: number; streamURL?: string }>;
            data?: Array<{ id: string; title: string; artist?: string; duration?: number; duration_sec?: number; streamURL?: string }>;
          }>(searchUrl, 2500);

          const list = searchRes.tracks || searchRes.results || searchRes.data || [];
          const matched = list.find((t) => isMatch(t.title, t.artist, t.duration_sec ?? t.duration));

          if (matched) {
            if (matched.streamURL) {
              const works = await this.verifyStreamUrl(matched.streamURL);
              if (works) {
                this.recordAddonSuccess(addon.id);
                return saveAndReturn({
                  streamUrl: matched.streamURL,
                  addonName: addon.name,
                  format: 'Lossless Audio',
                  codec: 'FLAC',
                  bitrate: 1411,
                });
              }
            }

            // Fetch stream for track id
            const streamPath = addon.streamEndpoint.includes('{id}')
              ? addon.streamEndpoint.replace('{id}', encodeURIComponent(matched.id))
              : `/stream/${encodeURIComponent(matched.id)}`;

            const streamUrl = streamPath.startsWith('http')
              ? streamPath
              : `${addon.baseUrl}${streamPath.startsWith('/') ? '' : '/'}${streamPath}?quality=${addon.preferredQuality.toLowerCase()}`;

            const streamRes = await safeFetchJson<{ url?: string; streamUrl?: string; format?: string; codec?: string; bitrate?: number }>(streamUrl, 2000);
            const resolvedUrl = streamRes.url || streamRes.streamUrl;
            if (resolvedUrl) {
              const works = await this.verifyStreamUrl(resolvedUrl);
              if (works) {
                this.recordAddonSuccess(addon.id);
                return saveAndReturn({
                  streamUrl: resolvedUrl,
                  addonName: addon.name,
                  format: streamRes.format || 'Lossless Audio',
                  codec: streamRes.codec || 'FLAC',
                  bitrate: streamRes.bitrate || 1411,
                });
              }
            }
          }
        } catch {
          // Standard /search protocol missed or failed; fall back to template stream URL
        }

        // 2. Fallback: Template or direct stream endpoint
        let directStreamUrl = addon.streamEndpoint.startsWith('http')
          ? addon.streamEndpoint
          : `${addon.baseUrl}${addon.streamEndpoint.startsWith('/') ? '' : '/'}${addon.streamEndpoint}`;

        if (directStreamUrl.includes('{id}') || directStreamUrl.includes('{title}')) {
          directStreamUrl = directStreamUrl
            .replace('{id}', encodeURIComponent(videoId))
            .replace('{title}', encodeURIComponent(rawTitle))
            .replace('{artist}', encodeURIComponent(rawArtist));
        } else {
          const sep = directStreamUrl.includes('?') ? '&' : '?';
          directStreamUrl = `${directStreamUrl}${sep}id=${encodeURIComponent(videoId)}&title=${encodeURIComponent(rawTitle)}&artist=${encodeURIComponent(rawArtist)}&quality=${addon.preferredQuality.toLowerCase()}`;
        }

        const controller = new AbortController();
        const timeout = setTimeout(() => controller.abort(), 1500);
        try {
          const headRes = await fetch(directStreamUrl, {
            method: 'GET',
            headers: { Accept: 'application/json, audio/*;q=0.9, */*;q=0.8' },
            signal: controller.signal,
          });
          clearTimeout(timeout);

          if (!headRes.ok) {
            this.recordAddonFailure(addon.id);
            continue;
          }

          const contentType = headRes.headers.get('content-type') || '';
          let streamUrlToUse = directStreamUrl;
          let streamFormat = 'High Quality Audio';
          let streamCodec: string | undefined = undefined;
          let streamBitrate: number | undefined = undefined;

          if (contentType.includes('application/json')) {
            const data = (await headRes.json()) as {
              url?: string;
              streamUrl?: string;
              format?: string;
              codec?: string;
              bitrate?: number;
            };
            const resolvedUrl = data.url || data.streamUrl;
            if (!resolvedUrl || typeof resolvedUrl !== 'string') {
              this.recordAddonFailure(addon.id);
              continue;
            }
            streamUrlToUse = resolvedUrl;
            streamFormat = data.format ? String(data.format).toUpperCase() : 'Lossless Audio';
            streamCodec = data.codec ? String(data.codec).toUpperCase() : undefined;
            streamBitrate = data.bitrate ? Math.round(Number(data.bitrate) / (data.bitrate > 10000 ? 1000 : 1)) : undefined;

            const works = await this.verifyStreamUrl(streamUrlToUse);
            if (!works) {
              // Crucial: record this failure so bad endpoints don't get stuck
              this.recordAddonFailure(addon.id);
              continue;
            }
          } else {
            const isAudio =
              contentType.startsWith('audio/') ||
              contentType.includes('flac') ||
              contentType.includes('mpeg') ||
              contentType.includes('ogg') ||
              contentType.includes('webm') ||
              /\.(flac|mp3|m4a|aac|ogg|opus|wav)($|\?)/i.test(streamUrlToUse);

            if (!isAudio) {
              this.recordAddonFailure(addon.id);
              continue;
            }
            const works = await this.verifyStreamUrl(streamUrlToUse);
            if (!works) {
              this.recordAddonFailure(addon.id);
              continue;
            }
          }

          this.recordAddonSuccess(addon.id);
          return saveAndReturn({
            streamUrl: streamUrlToUse,
            addonName: addon.name,
            format: streamFormat,
            codec: streamCodec || 'FLAC',
            bitrate: streamBitrate || 1411,
          });
        } catch {
          clearTimeout(timeout);
          this.recordAddonFailure(addon.id);
        }
      } catch {
        this.recordAddonFailure(addon.id);
      }
    }

    return null;
  }
}
