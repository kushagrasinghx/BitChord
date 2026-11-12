/**
 * declarativeNetRequest rules that let the site talk to YouTube from the page
 * itself (prompt §4.2). Audio bytes and InnerTube responses go straight to the
 * page; nothing passes through the extension's messaging.
 *
 * Every rule is scoped to requests the site initiates (`initiatorDomains`), so
 * YouTube's own tabs and every other site are untouched.
 *
 * What the endpoints accept was measured, not assumed (web/fixtures/README.md,
 * web/docs/kira-notes.md):
 * - youtubei answers CORS preflights with 400, so the page sends text/plain
 *   "simple" requests and these rules supply the YouTube headers.
 * - www.youtube.com/api/jnn (BotGuard) already allows any origin, and refuses
 *   a rewritten YouTube Origin. It is deliberately not matched here.
 */

type Rule = Browser.declarativeNetRequest.Rule;
type HeaderInfo = Browser.declarativeNetRequest.ModifyHeaderInfo;

const XHR: `${Browser.declarativeNetRequest.ResourceType}`[] = ['xmlhttprequest'];
const XHR_AND_MEDIA: `${Browser.declarativeNetRequest.ResourceType}`[] = ['xmlhttprequest', 'media'];

const set = (header: string, value: string): HeaderInfo => ({ header, operation: 'set', value });
const remove = (header: string): HeaderInfo => ({ header, operation: 'remove' });

/** Requests are anonymous (no credentials), so `*` is a valid answer for every site origin. */
const CORS_RESPONSE: HeaderInfo[] = [
  set('Access-Control-Allow-Origin', '*'),
  set('Access-Control-Expose-Headers', '*'),
];

function impersonate(origin: string): HeaderInfo[] {
  return [set('Origin', origin), set('Referer', `${origin}/`), set('X-Origin', origin)];
}

/** Rule ids 1–99 are this module's; it replaces all of them on every install and startup. */
export function youtubeRules(initiatorDomains: string[]): Rule[] {
  const rule = (
    id: number,
    urlFilter: string,
    requestHeaders: HeaderInfo[] | null,
    resourceTypes = XHR,
    priority = 1,
  ): Rule => ({
    id,
    priority,
    condition: { urlFilter, initiatorDomains, resourceTypes },
    action: {
      type: 'modifyHeaders',
      ...(requestHeaders && { requestHeaders }),
      responseHeaders: CORS_RESPONSE,
    },
  });

  return [
    // InnerTube, as the YouTube Music and YouTube web clients.
    rule(1, '||music.youtube.com/youtubei/', impersonate('https://music.youtube.com')),
    rule(2, '||www.youtube.com/youtubei/', impersonate('https://www.youtube.com')),
    // What YouTube.js reads to build a session and decipher stream URLs.
    rule(3, '||www.youtube.com/sw.js_data', null),
    rule(4, '||www.youtube.com/iframe_api', null),
    rule(5, '||www.youtube.com/s/player/', null),
    // SABR and media: googlevideo only serves the YouTube origin.
    rule(6, '||googlevideo.com/', impersonate('https://www.youtube.com').slice(0, 2), XHR_AND_MEDIA),
    // Direct URLs minted for VISIONOS are fetched with no Origin or Referer
    // at all, as InnerTubeX does on Android (buildHeaders sends nothing for
    // VISIONOS); a YouTube web Origin on them is refused with 403 mid-track.
    rule(7, '||googlevideo.com/*c=VISIONOS', [remove('Origin'), remove('Referer')], XHR_AND_MEDIA, 2),
  ];
}

export const RULE_IDS = Array.from({ length: 99 }, (_, i) => i + 1);

/** The hostnames of the site origins, which is what `initiatorDomains` matches on. */
export function initiatorDomainsFor(origins: string[]): string[] {
  return [...new Set(origins.map((o) => new URL(o).hostname))];
}

export async function installRules(origins: string[]): Promise<void> {
  await browser.declarativeNetRequest.updateDynamicRules({
    removeRuleIds: RULE_IDS,
    addRules: youtubeRules(initiatorDomainsFor(origins)),
  });
}

/**
 * Requests the extension makes itself (`yt.fetch`, and signed-in calls),
 * dressed as YouTube's own web clients. Scoped to non-tab requests (tabId -1,
 * which only session rules can match), so no page or tab is affected. Needed
 * where a page can't send the request: BotGuard's GenerateIT has to come from
 * YouTube's origin (as on Android, where BotGuard runs in a youtube.com
 * WebView, and in Kira), and a request with that Origin can't pass a CORS
 * preflight from the page. Signed-in requests need it too: the SAPISIDHASH is
 * computed over the origin, so the request has to carry that origin.
 */
export const EXTENSION_FETCH_RULE_ID = 100;
const EXTENSION_MUSIC_FETCH_RULE_ID = 101;
/** s.youtube.com, where the play-reporting pings go, as YouTube Music's own. */
const EXTENSION_STATS_FETCH_RULE_ID = 102;

export async function installExtensionFetchRule(): Promise<void> {
  const own = (id: number, host: string, origin: string): Rule => ({
    id,
    priority: 1,
    condition: { urlFilter: `||${host}/`, tabIds: [-1], resourceTypes: ['xmlhttprequest'] },
    action: { type: 'modifyHeaders', requestHeaders: impersonate(origin).slice(0, 2) },
  });
  await browser.declarativeNetRequest.updateSessionRules({
    removeRuleIds: [EXTENSION_FETCH_RULE_ID, EXTENSION_MUSIC_FETCH_RULE_ID, EXTENSION_STATS_FETCH_RULE_ID],
    addRules: [
      own(EXTENSION_FETCH_RULE_ID, 'www.youtube.com', 'https://www.youtube.com'),
      own(EXTENSION_MUSIC_FETCH_RULE_ID, 'music.youtube.com', 'https://music.youtube.com'),
      own(EXTENSION_STATS_FETCH_RULE_ID, 's.youtube.com', 'https://music.youtube.com'),
    ],
  });
}

/**
 * Spotify, as its own web player: the extension's requests to the token,
 * client-token and Pathfinder hosts carry open.spotify.com's Origin and
 * Referer, as the player's do. Non-tab requests only (tabId -1).
 */
const SPOTIFY_RULE_IDS = [300, 301, 302];

export async function installSpotifyFetchRule(): Promise<void> {
  const hosts = ['open.spotify.com', 'api-partner.spotify.com', 'clienttoken.spotify.com'];
  await browser.declarativeNetRequest.updateSessionRules({
    removeRuleIds: SPOTIFY_RULE_IDS,
    addRules: hosts.map((host, i) => ({
      id: SPOTIFY_RULE_IDS[i]!,
      priority: 1,
      condition: { urlFilter: `||${host}/`, tabIds: [-1], resourceTypes: ['xmlhttprequest'] },
      action: { type: 'modifyHeaders', requestHeaders: impersonate('https://open.spotify.com').slice(0, 2) },
    })),
  });
}

/** Discord's REST API, as its own web client sees it (the extension's requests only). */
const DISCORD_RULE_ID = 400;

export async function installDiscordFetchRule(): Promise<void> {
  await browser.declarativeNetRequest.updateSessionRules({
    removeRuleIds: [DISCORD_RULE_ID],
    addRules: [
      {
        id: DISCORD_RULE_ID,
        priority: 1,
        condition: { urlFilter: '||discord.com/api/', tabIds: [-1], resourceTypes: ['xmlhttprequest'] },
        action: { type: 'modifyHeaders', requestHeaders: impersonate('https://discord.com').slice(0, 2) },
      },
    ],
  });
}
