// Captures real responses into web/fixtures/ so parsers are written against
// what the services actually send (build prompt §0: probe before parsing).
//
//   node web/scripts/capture-fixtures.mjs            # everything
//   node web/scripts/capture-fixtures.mjs innertube  # one group
//
// Every request is anonymous: no cookies, no account. Each capture also
// records the status and the Access-Control-Allow-Origin a browser page at
// http://localhost:5173 would see, which decides whether the web app fetches
// a host directly or through the extension (prompt §10.1).

import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..', 'fixtures');
const SITE_ORIGIN = 'http://localhost:5173';
const UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36';

// The probe track: well known, on every catalogue, has synced lyrics.
const TRACK = {
  title: 'Let Down',
  artist: 'Radiohead',
  album: 'OK Computer',
  durationSec: 299,
  appleId: '1097861834',
};

const index = [];

// The last run's entries. A failed capture never replaces a good one: hosts
// rate-limit and flap, and a 429 body is worthless as a parser fixture.
const indexPath = join(ROOT, 'index.json');
const previous = new Map();
try {
  for (const e of JSON.parse(await readFile(indexPath, 'utf8')).captures ?? []) previous.set(e.file, e);
} catch { /* first run */ }
const isGood = (e) => e && e.status >= 200 && e.status < 300;

async function save(rel, body) {
  const path = join(ROOT, rel);
  await mkdir(dirname(path), { recursive: true });
  await writeFile(path, body);
}

/** Drops the `)]}'` anti-hijacking prefix some Google hosts put before JSON. */
const stripGuard = (text) => text.replace(/^\)\]\}'\s*\n?/, '');

function pretty(text, contentType) {
  if (!/json/.test(contentType ?? '')) return text;
  try {
    // Some hosts prefix JSON with an anti-hijacking guard; keep it if present.
    const guard = text.match(/^\)\]\}'\s*\n?/)?.[0] ?? '';
    return guard + JSON.stringify(JSON.parse(text.slice(guard.length)), null, 2);
  } catch {
    return text;
  }
}

/**
 * Fetch, save the body, and record what a browser at SITE_ORIGIN would get.
 * `init.headers` are the headers BitChord's Kotlin client sends.
 */
async function capture(rel, url, init = {}) {
  const started = Date.now();
  let entry;
  try {
    const send = () => fetch(url, {
      ...init,
      headers: { 'User-Agent': UA, Origin: SITE_ORIGIN, ...init.headers },
      redirect: 'follow',
    });
    let res = await send();
    if (res.status === 429 || res.status === 503) {
      // One patient retry for rate limits and cold starts.
      await new Promise((r) => setTimeout(r, 4000));
      res = await send();
    }
    const contentType = res.headers.get('content-type');
    const text = await res.text();
    const before = previous.get(rel);
    if (!res.ok && isGood(before)) {
      entry = { ...before, lastFailure: { status: res.status, at: new Date().toISOString() } };
      console.log(`${res.status} ${rel}: kept the earlier ${before.status} capture`);
      const kept = await readFile(join(ROOT, rel), 'utf8');
      return { res, text: kept, json: () => JSON.parse(stripGuard(kept)) };
    }
    await save(rel, pretty(text, contentType));
    entry = {
      file: rel,
      url: url.replace(/key=[^&]+/, 'key=…'),
      method: init.method ?? 'GET',
      status: res.status,
      contentType,
      bytes: text.length,
      acao: res.headers.get('access-control-allow-origin'),
      ms: Date.now() - started,
    };
    console.log(`${res.status} ${rel} (${text.length}b, ACAO=${entry.acao ?? '-'})`);
    return { res, text, json: () => JSON.parse(stripGuard(text)) };
  } catch (err) {
    const before = previous.get(rel);
    entry = isGood(before)
      ? { ...before, lastFailure: { error: String(err), at: new Date().toISOString() } }
      : { file: rel, url, error: String(err), ms: Date.now() - started };
    console.log(`ERR ${rel}: ${err}`);
    return null;
  } finally {
    index.push(entry);
  }
}

/** Depth-first search for the first string matching `test` under `key`. */
function findValue(node, key, test = () => true) {
  if (Array.isArray(node)) {
    for (const child of node) {
      const hit = findValue(child, key, test);
      if (hit) return hit;
    }
  } else if (node && typeof node === 'object') {
    if (typeof node[key] === 'string' && test(node[key])) return node[key];
    for (const child of Object.values(node)) {
      const hit = findValue(child, key, test);
      if (hit) return hit;
    }
  }
  return null;
}

// ---- InnerTube (WEB_REMIX) -------------------------------------------------

async function innertube() {
  const home = await fetch('https://music.youtube.com/', { headers: { 'User-Agent': UA } }).then((r) => r.text());
  const clientVersion = home.match(/"INNERTUBE_CLIENT_VERSION":"([^"]+)"/)?.[1] ?? '1.20250101.01.00';
  console.log(`WEB_REMIX clientVersion ${clientVersion}`);
  let visitorData;

  // A CORS "simple" request: text/plain and no custom headers, because youtubei
  // answers the preflight with 400 (see web/docs/kira-notes.md). In the browser
  // the extension's DNR rule rewrites Origin/Referer; here we send them.
  const post = (rel, endpoint, body, host = 'music.youtube.com') =>
    capture(rel, `https://${host}/youtubei/v1/${endpoint}?prettyPrint=false`, {
      method: 'POST',
      headers: {
        'Content-Type': 'text/plain;charset=UTF-8',
        Origin: `https://${host}`,
        Referer: `https://${host}/`,
      },
      body: JSON.stringify({
        context: {
          client: { clientName: 'WEB_REMIX', clientVersion, hl: 'en', gl: 'US', ...(visitorData && { visitorData }) },
          user: { lockedSafetyMode: false },
          request: { useSsl: true },
        },
        ...body,
      }),
    });

  const homeRes = await post('innertube/browse-home.json', 'browse', { browseId: 'FEmusic_home' });
  visitorData = homeRes ? findValue(homeRes.json(), 'visitorData') : undefined;

  await post('innertube/browse-explore.json', 'browse', { browseId: 'FEmusic_explore' });
  await post('innertube/browse-new-releases.json', 'browse', { browseId: 'FEmusic_new_releases' });
  await post('innertube/browse-moods-and-genres.json', 'browse', { browseId: 'FEmusic_moods_and_genres' });
  await post('innertube/search-suggestions.json', 'music/get_search_suggestions', { input: 'radioh' });

  const search = await post('innertube/search-all.json', 'search', { query: 'radiohead' });
  // Filter chips: songs, albums (SearchFilter params in shared Models.kt).
  await post('innertube/search-songs.json', 'search', { query: 'radiohead let down', params: 'EgWKAQIIAWoKEAkQChAFEAMQBA==' });
  const albums = await post('innertube/search-albums.json', 'search', { query: 'ok computer radiohead', params: 'EgWKAQIYAWoKEAkQChAFEAMQBA==' });

  const s = search?.json();
  const artistId = s && findValue(s, 'browseId', (v) => v.startsWith('UC'));
  const albumId = albums && findValue(albums.json(), 'browseId', (v) => v.startsWith('MPREb_'));
  const playlistId = s && findValue(s, 'browseId', (v) => v.startsWith('VL'));
  const videoId = s && findValue(s, 'videoId');

  if (artistId) await post('innertube/browse-artist.json', 'browse', { browseId: artistId });
  if (albumId) await post('innertube/browse-album.json', 'browse', { browseId: albumId });
  if (playlistId) await post('innertube/browse-playlist.json', 'browse', { browseId: playlistId });

  if (videoId) {
    const next = await post('innertube/next-radio.json', 'next', {
      videoId,
      playlistId: `RDAMVM${videoId}`,
      isAudioOnly: true,
    });
    const lyricsBrowseId = next && findValue(next.json(), 'browseId', (v) => v.startsWith('MPLYt'));
    if (lyricsBrowseId) await post('lyrics/youtube-music-lyrics-tab.json', 'browse', { browseId: lyricsBrowseId });

    // Anonymous WEB_REMIX player without a PO token: records what the server
    // answers before BotGuard is wired up (phase 3 needs this baseline).
    await post('innertube/player-web-remix-no-potoken.json', 'player', {
      videoId,
      contentCheckOk: true,
      racyCheckOk: true,
      playbackContext: { contentPlaybackContext: { html5Preference: 'HTML5_PREF_WANTS' } },
    });
  }
}

// ---- Lyric providers ---------------------------------------------------------

async function lyrics() {
  const t = TRACK;
  const q = (base, params) => `${base}?${new URLSearchParams(params)}`;
  const json = { Accept: 'application/json' };

  // 2. BiniLyrics first: it is where the ISRC comes from.
  const bini = await capture('lyrics/binilyrics.json', q('https://lyrics-api.binimum.org/', {
    track: t.title, artist: t.artist, album: t.album, duration: t.durationSec,
  }), { headers: json });
  let isrc = null;
  try { isrc = findValue(bini?.json(), 'isrc'); } catch { /* not JSON */ }

  // 1. lrc.red: search, then the TTML document by ISRC.
  const red = await capture('lyrics/lrc-red-search.json', q('https://lrc.red/search.json', { q: `${t.title} ${t.artist}` }), { headers: json });
  try { isrc ??= findValue(red?.json(), 'isrc'); } catch { /* not JSON */ }
  if (isrc) await capture('lyrics/lrc-red-document.ttml', `https://lrc.red/s/${isrc}.ttml`);

  // 3–4. BetterLyrics, Apple and QQ (Portato).
  const better = { s: t.title, a: t.artist, d: t.durationSec, al: t.album };
  await capture('lyrics/betterlyrics.json', q('https://lyrics-api.boidu.dev/getLyrics', better), { headers: json });
  await capture('lyrics/betterlyrics-portato.json', q('https://lyrics-api.boidu.dev/qq/getLyrics', better), { headers: json });

  // 5. PaxSenix public Apple route, by Apple track id. (The app resolves the
  // id through amp-api.music.apple.com with a token scraped from the Apple web
  // player; that search needs Origin music.apple.com, so it is extension-only.)
  await capture('lyrics/paxsenix-apple.json', q('https://lyrics.paxsenix.org/apple-music/lyrics', { id: t.appleId, ttml: 'true' }), { headers: json });
  // 6–7. PaxSenix Spotify/Musixmatch need the user's API key: not captured.

  // 8. LyricsPlus: first mirror that answers.
  for (const host of [
    'https://lyricsplus.prjktla.my.id',
    'https://lyricsplus.atomix.one',
    'https://lyricsplus.binimum.org',
    'https://lyricsplus.prjktla.workers.dev',
    'https://lyricsplus-seven.vercel.app',
    'https://lyrics-plus-backend.vercel.app',
  ]) {
    const name = new URL(host).hostname;
    const r = await capture(`lyrics/lyricsplus-${name}.json`, q(`${host}/v2/lyrics/get`, {
      title: t.title, artist: t.artist, duration: t.durationSec, album: t.album, ...(isrc && { isrc }),
    }), { headers: json });
    if (r?.res.ok) break;
  }

  // 9. Unison.
  await capture('lyrics/unison.json', q('https://unison.boidu.dev/lyrics', {
    song: t.title, artist: t.artist, album: t.album, duration: t.durationSec,
  }), { headers: json });

  // 10. YouTube captions (get_transcript) is captured with the player in phase 3;
  // 11. the YouTube Music lyrics tab is captured by the innertube group.

  // 12. KuGou: song search, lyric candidates, then the LRC download.
  const keyword = `${t.artist} - ${t.title}`;
  await capture('lyrics/kugou-song-search.json', q('https://mobileservice.kugou.com/api/v3/search/song', {
    version: 9108, plat: 0, pagesize: 8, showtype: 0, keyword,
  }));
  const cands = await capture('lyrics/kugou-lyric-search.json', q('https://lyrics.kugou.com/search', {
    ver: 1, man: 'yes', client: 'pc', keyword, duration: t.durationSec * 1000,
  }));
  try {
    const c = cands?.json()?.candidates?.[0];
    if (c) await capture('lyrics/kugou-download.json', q('https://lyrics.kugou.com/download', {
      fmt: 'lrc', id: c.id, accesskey: c.accesskey, ver: 1, client: 'pc',
    }));
  } catch { /* not JSON */ }

  // 13. LRCLIB.
  const lrclib = { 'User-Agent': 'BitChord (https://github.com/bitchord)' };
  await capture('lyrics/lrclib-get.json', q('https://lrclib.net/api/get', {
    track_name: t.title, artist_name: t.artist, duration: t.durationSec,
  }), { headers: lrclib });
  await capture('lyrics/lrclib-search.json', q('https://lrclib.net/api/search', {
    track_name: t.title, artist_name: t.artist,
  }), { headers: lrclib });

  // 14. Musixmatch needs a signed usertoken (secret scraped from its web JS):
  //     captured when the provider is ported in phase 8.

  // 15. Genius search (the lyric page itself is HTML, scraped in phase 8).
  await capture('lyrics/genius-search.json', q('https://genius.com/api/search/multi', { q: `${t.title} ${t.artist}` }), {
    headers: { Accept: 'application/json' },
  });

  // Hidden providers, kept for completeness of the CORS table.
  await capture('lyrics/simpmusic.json', 'https://api-lyrics.simpmusic.org/v1/ZVgHPSyEIqk', { headers: json });

  // Translation and romanisation.
  await capture('lyrics/google-translate.json', q('https://translate.googleapis.com/translate_a/single', {
    client: 'gtx', sl: 'auto', tl: 'hi', dt: 't', q: 'Let down and hanging around',
  }));
}

/**
 * Spotify (prompt §7.1), with an anonymous web-player token: no sp_dc, so no
 * library or liked songs here, only what any visitor can read. The token
 * itself is never written to disk.
 */
async function spotify() {
  const html = await fetch('https://open.spotify.com/', { headers: { 'User-Agent': UA } }).then((r) => r.text());
  const bundle = html.match(/https:\/\/open\.spotifycdn\.com\/cdn\/build\/web-player\/web-player\.[0-9a-f]+\.js/)?.[0];
  if (!bundle) return console.log('ERR spotify: no web-player bundle on open.spotify.com');
  const js = await fetch(bundle, { headers: { 'User-Agent': UA } }).then((r) => r.text());
  // The same declarations extension/lib/spotify reads at run time.
  const secrets = [...js.matchAll(/\{secret:(['"])((?:\\.|(?!\1).)*)\1,version:(\d+)\}/g)]
    .map((m) => ({ secret: Function(`return ${m[1]}${m[2]}${m[1]}`)(), version: Number(m[3]) }))
    .sort((a, b) => b.version - a.version);
  // searchTracks lives in the search route's chunk, loaded on demand: its file is named by chunk id.
  const searchId = js.match(/(\d+):"xpui-routes-search"/)?.[1];
  let searchJs = '';
  for (const [, part] of js.matchAll(new RegExp(`\\b${searchId}:"([0-9a-f]{8})"`, 'g'))) {
    const res = await fetch(`https://open.spotifycdn.com/cdn/build/web-player/xpui-routes-search.${part}.js`, { headers: { 'User-Agent': UA } });
    if (res.ok) { searchJs = await res.text(); break; }
  }
  const hash = (op) => (js + searchJs).match(new RegExp(`"${op}","query","([0-9a-f]{64})"`))?.[1];
  const hashes = Object.fromEntries(['libraryV3', 'fetchPlaylist', 'fetchLibraryTracks', 'canvas', 'searchTracks'].map((op) => [op, hash(op)]));
  const config = JSON.parse(Buffer.from(html.match(/<script id="appServerConfig" type="text\/plain">([^<]+)</)[1], 'base64').toString());
  await save('spotify/web-player.json', JSON.stringify({ bundle, clientVersion: config.clientVersion, secretVersions: secrets.map((s) => s.version), hashes }, null, 2));
  console.log(`spotify: secrets v${secrets.map((s) => s.version).join(',')}; hashes`, hashes);

  const { createHmac } = await import('node:crypto');
  const key = Buffer.from(secrets[0].secret.split('').map((c, i) => c.charCodeAt(0) ^ ((i % 33) + 9)).join(''), 'utf8');
  const counter = Buffer.alloc(8);
  counter.writeBigUInt64BE(BigInt(Math.floor(Date.now() / 30_000)));
  const mac = createHmac('sha1', key).update(counter).digest();
  const o = mac[19] & 15;
  const totp = String(((mac.readUInt32BE(o) & 0x7fffffff) % 1_000_000)).padStart(6, '0');
  const token = await fetch(`https://open.spotify.com/api/token?${new URLSearchParams({ reason: 'init', productType: 'web-player', totp, totpServer: totp, totpVer: String(secrets[0].version) })}`, {
    headers: { 'User-Agent': UA, Referer: 'https://open.spotify.com/', 'App-Platform': 'WebPlayer' },
  }).then((r) => r.json());
  if (!token.accessToken) return console.log('ERR spotify: /api/token minted nothing', Object.keys(token));
  console.log(`spotify: anonymous token minted (isAnonymous=${token.isAnonymous})`);
  const client = await fetch('https://clienttoken.spotify.com/v1/clienttoken', {
    method: 'POST',
    headers: { 'User-Agent': UA, 'Content-Type': 'application/json', Accept: 'application/json' },
    body: JSON.stringify({ client_data: { client_version: config.clientVersion, client_id: token.clientId, js_sdk_data: {
      device_brand: 'unknown', device_model: 'unknown', os: 'windows', os_version: 'NT 10.0', device_id: crypto.randomUUID().replaceAll('-', ''), device_type: 'computer',
    } } }),
  }).then((r) => r.json());
  const headers = {
    Authorization: `Bearer ${token.accessToken}`,
    ...(client.granted_token?.token && { 'Client-Token': client.granted_token.token }),
    'App-Platform': 'WebPlayer',
    Accept: 'application/json',
    'Content-Type': 'application/json;charset=UTF-8',
    Origin: 'https://open.spotify.com',
    Referer: 'https://open.spotify.com/',
  };
  const gql = (rel, operationName, variables) =>
    capture(rel, 'https://api-partner.spotify.com/pathfinder/v2/query', {
      method: 'POST',
      headers,
      body: JSON.stringify({ variables, operationName, extensions: { persistedQuery: { version: 1, sha256Hash: hashes[operationName] } } }),
    });
  // "This Is Kanye West", an editorial playlist anyone can read.
  await gql('spotify/fetch-playlist.json', 'fetchPlaylist', { uri: 'spotify:playlist:37i9dQZF1DZ06evO3nMr04', offset: 0, limit: 25, enableWatchFeedEntrypoint: false });
  // api.spotify.com/v1/search answers the web player's token with 429 (signed in too); its own search is this.
  await gql('spotify/search-tracks.json', 'searchTracks', {
    searchTerm: `${TRACK.title} ${TRACK.artist}`, offset: 0, limit: 10, numberOfTopResults: 5,
    includeAudiobooks: false, includePreReleases: false, includeAuthors: false,
  });
  // Sabrina Carpenter, "Espresso": has a canvas, but only a signed-in token is shown it.
  await gql('spotify/canvas-anonymous.json', 'canvas', { trackUri: 'spotify:track:2qSkIjg1o9h3YT9RAgYN75' });
}

const groups = { innertube, lyrics, spotify };
const only = process.argv[2];
for (const [name, run] of Object.entries(groups)) {
  if (!only || only === name) await run();
}

// Merge into the existing index so a single-group run keeps the others.
const byFile = new Map(previous);
for (const e of index) byFile.set(e.file, e);
await save('index.json', JSON.stringify({
  capturedAt: new Date().toISOString(),
  siteOrigin: SITE_ORIGIN,
  captures: [...byFile.values()].sort((a, b) => a.file.localeCompare(b.file)),
}, null, 2));
