# BitChord Web

The web player and its companion browser extension. The plan, and the decisions behind it, are in [`docs/web-app-build-prompt.md`](../docs/web-app-build-prompt.md).

| Package | What it is |
|---|---|
| `app/` | The site: Vite, React 19, TypeScript, React Router, TanStack Query, Zustand, Tailwind CSS v4 |
| `extension/` | Manifest V3 extension for Chromium and Firefox, built with WXT |
| `shared/` | The page ↔ extension bridge contract and client, used by both |
| `docs/` | `apple-music-dom.md` (the design measurements), `kira-notes.md` (how the reference YouTube web client plays), `youtube-playback.md`, `youtube-signin.md`, `queue-and-player.md`, `lyrics.md`, `spotify-and-discord.md` |
| `fixtures/` | Real captured responses for parser tests (see its README) |
| `scripts/` | `capture-fixtures.mjs` |

## Setup

Node 22 or newer. pnpm comes through corepack, so nothing needs a global install:

```bash
corepack pnpm --dir web install
```

## Run the site

```bash
corepack pnpm --dir web/app dev
```

It serves on `http://localhost:5173`, and only there (`strictPort`): the extension trusts that exact origin.

## Run the extension

Build both targets:

```bash
corepack pnpm --dir web/extension build
```

- **Chrome, Edge, Brave:** `chrome://extensions` → Developer mode → *Load unpacked* → `web/extension/.output/chrome-mv3`. The id is always `pkddffeebcmhhanjcigilehaeonkkfih`, pinned by the manifest `key` (`extension/dev-key/`), so the site finds it without configuration.
- **Firefox:** `about:debugging#/runtime/this-firefox` → *Load Temporary Add-on* → `web/extension/.output/firefox-mv3/manifest.json`.

To offer the extension as a download from the site's Extension page, package it:

```bash
corepack pnpm --dir web/extension package
```

This builds both targets and writes three files to `app/public/downloads/` (git-ignored):

- `bitchord-extension-chrome.zip` and `bitchord-extension-firefox.zip`. Each holds a single folder (`bitchord-extension-<browser>/`), so extracting it in place leaves one folder to load unpacked.
- `extension.json`, with the version. The site compares it with the installed extension's version and shows **Update** in the sidebar and an update row on the Extension page when the zip is newer. Bump `version` in `extension/package.json` to publish an update.

`VITE_EXTENSION_CHROME_URL`, `VITE_EXTENSION_FIREFOX_URL` and `VITE_EXTENSION_MANIFEST_URL` override those links, for example to point at store listings.

The toolbar icons (`extension/public/icon/`) are rendered from `app/public/favicon.svg` by `corepack pnpm --dir web/extension icons`.

Then open `http://localhost:5173/extension`. It shows "Installed", with the version and the transport: **Direct** on Chromium (`externally_connectable`), **Page relay** on Firefox (content script).

## Configuration

| Variable | Where | Default |
|---|---|---|
| `BITCHORD_SITE_ORIGINS` | extension build | `https://bitchord.in,https://bitchord-8bw.pages.dev,http://localhost:5173` (store builds leave out localhost). Comma-separated origins the extension serves; replaces the default |
| `BITCHORD_STORE_BUILD=1` | extension build | Leaves out the dev `key` for a Chrome Web Store upload |
| `VITE_EXTENSION_ID_CHROMIUM` | app (`app/.env.local`) | The pinned dev id. Set it for a store-built extension |
| `VITE_ANDROID_APP_URL` | app | The GitHub releases page |
| `VITE_PARTY_SERVER_URL` | app (`app/.env.local`, Pages env) | None. The Listen Together server; `vite build` fails without it |
| `VITE_GOOGLE_CLIENT_ID` | app (`app/.env.local`, Pages env) | None. The Google OAuth client for Google Drive backup (below); without it the Drive section is hidden |

### Google Drive backup

Settings › Backup & Restore can sync with Google Drive in the Android app's own format, so the browser shows up as one more device in the phone's device list and Replay adds up every device's listening. It needs an OAuth client:

1. In the Google Cloud project the Android app's OAuth client is in, create an OAuth client ID of type **Web application**. It has to be the same project: the `drive.appdata` folder is private to each Cloud project, and a client from another project sees an empty Drive.
2. Add every origin the site is served from as an **Authorized JavaScript origin**: `https://bitchord.in`, `https://bitchord-8bw.pages.dev`, and `http://localhost:5173` for development. No redirect URIs are needed (the browser uses Google Identity Services' token popup).
3. Set `VITE_GOOGLE_CLIENT_ID` to the client ID.

The only scope asked for is `https://www.googleapis.com/auth/drive.appdata`, already on the project's consent screen for the Android app. A browser gets no refresh token, so a sign-in lasts an hour: the app syncs when it opens and on the chosen schedule while that sign-in is live, and otherwise asks for "Sign In Again".

## Deploy (Cloudflare Pages)

| Setting | Value |
| --- | --- |
| Root directory | `web` |
| Build command | `pnpm build:site` |
| Output directory | `app/dist` |
| Environment | `NODE_VERSION=22`, `VITE_PARTY_SERVER_URL` |

`build:site` packages the extension first, so each deploy publishes the zips and `downloads/extension.json` with the version from `web/extension/package.json`. The Extension page polls that file and offers the update when it's newer than the installed one. Bump that version to make an update show.

## Checks

```bash
corepack pnpm --dir web -r typecheck
corepack pnpm --dir web/app build
```
