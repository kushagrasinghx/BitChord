import { readFileSync } from 'node:fs';
import { defineConfig } from 'wxt';
import { LYRIC_ORIGINS, patternOf } from './lib/lyricHosts';
import { matchPatternsFor, SITE_ORIGINS } from './site.config';

/** BITCHORD_STORE_BUILD=1 for a Chrome Web Store upload. */
const STORE_BUILD = process.env.BITCHORD_STORE_BUILD === '1';

/**
 * Discord presence is user-token automation (prompt §7.3), so it is a build
 * flag: on for the site's own zips, off for a Web Store upload unless
 * DISCORD_PRESENCE=1 says otherwise.
 */
const DISCORD_PRESENCE = process.env.DISCORD_PRESENCE ? process.env.DISCORD_PRESENCE === '1' : !STORE_BUILD;
const DISCORD_ORIGINS = ['https://discord.com/*'];

const devPublicKey = readFileSync(new URL('./dev-key/public-key.txt', import.meta.url), 'utf8').trim();

export default defineConfig({
  manifestVersion: 3,
  vite: () => ({
    define: {
      // Checked against every sender at runtime (entrypoints/background.ts).
      __SITE_ORIGINS__: JSON.stringify(SITE_ORIGINS),
      __DISCORD_PRESENCE__: JSON.stringify(DISCORD_PRESENCE),
    },
  }),
  manifest: ({ browser }) => ({
    name: 'BitChord',
    description: 'Connects the BitChord web player to YouTube Music, Spotify and Discord.',
    homepage_url: SITE_ORIGINS[0],
    // `cookies` reads the YouTube session for signed-in requests (phase 5);
    // the cookies themselves never leave the extension (lib/ytAuth.ts).
    // `scripting` reads the Discord token from a discord.com tab, and only on
    // the user's "Connect Discord" (lib/discord.ts).
    permissions: ['storage', 'declarativeNetRequest', 'cookies', ...(DISCORD_PRESENCE ? ['scripting'] : [])],
    // YouTube (phases 3 and 5) and Spotify (phase 9; the sp_dc cookie is read
    // and used here only, lib/spotify.ts). The site's own origins are listed
    // because header rules need host access to the initiator as well as the
    // target.
    host_permissions: [
      'https://music.youtube.com/*',
      'https://www.youtube.com/*',
      'https://*.youtube.com/*',
      'https://*.googlevideo.com/*',
      'https://open.spotify.com/*',
      'https://accounts.spotify.com/*',
      'https://api-partner.spotify.com/*',
      'https://clienttoken.spotify.com/*',
      'https://open.spotifycdn.com/*',
      ...matchPatternsFor(SITE_ORIGINS),
    ],
    // Granted at use time, on the extension's own pages: lyric providers the
    // page can't reach itself (grant.html), and discord.com when the user
    // connects Discord (discord.html).
    optional_host_permissions: [...LYRIC_ORIGINS.map(patternOf), ...(DISCORD_PRESENCE ? DISCORD_ORIGINS : [])],
    ...(browser === 'firefox'
      ? {
          browser_specific_settings: {
            gecko: {
              id: 'extension@bitchord.kushagrasingh.in',
              strict_min_version: '128.0',
              data_collection_permissions: { required: ['none'] },
            },
          },
        }
      : {
          // Chromium pages message the extension directly.
          externally_connectable: { matches: matchPatternsFor(SITE_ORIGINS) },
          // Pins the unpacked extension's id so the page can address it
          // (scripts/gen-dev-key.mjs). The Web Store assigns its own id and
          // refuses a `key`, so store builds leave it out.
          ...(!STORE_BUILD && { key: devPublicKey }),
        }),
  }),
  // The connect page only exists in builds that include Discord.
  filterEntrypoints: DISCORD_PRESENCE ? undefined : ['background', 'popup', 'grant', 'relay'],
  hooks: {
    // Firefox only: the relay content script runs on the site's origins.
    'build:manifestGenerated': (wxt, manifest) => {
      for (const script of manifest.content_scripts ?? []) {
        if (script.js?.some((file) => file.includes('relay'))) {
          script.matches = matchPatternsFor(SITE_ORIGINS);
        }
      }
    },
  },
});
