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

Use Node 22 or newer and pnpm (the workspace pins `pnpm@12.11.2` in `package.json`). If pnpm is not installed, install it with `npm install --global pnpm@12.11.2`.

Run these commands from the repository root:

```bash
pnpm --dir web install --frozen-lockfile
```

If your terminal is already in `web/app`, run `pnpm install --frozen-lockfile` there; pnpm finds the workspace and installs its packages together.

Do not use `npm install` or `npm ci` for this workspace. The app and extension depend on `@bitchord/shared` through `workspace:*`, which npm does not support (`EUNSUPPORTEDPROTOCOL`). Keep those dependencies and use `pnpm-lock.yaml`, not an npm-generated `package-lock.json`.

Older Corepack versions can also fail to launch pnpm 12 with `Cannot find module .../bin/pnpm.cjs`. If that happens, use the installed `pnpm` command directly, as shown here.

## Run the site

```bash
pnpm --dir web/app dev
```

It serves on `http://localhost:5173`, and only there (`strictPort`): the extension trusts that exact origin.

## Run the extension

Build both targets:

```bash
pnpm --dir web/extension build
```

- **Chrome, Edge, Brave:** `chrome://extensions` → Developer mode → *Load unpacked* → `web/extension/.output/chrome-mv3`. The id is always `pkddffeebcmhhanjcigilehaeonkkfih`, pinned by the manifest `key` (`extension/dev-key/`), so the site finds it without configuration.
- **Firefox:** `about:debugging#/runtime/this-firefox` → *Load Temporary Add-on* → `web/extension/.output/firefox-mv3/manifest.json`.

To offer the extension as a download from the site's Extension page, package it:

```bash
pnpm --dir web/extension package
```

This builds both targets and writes three files to `app/public/downloads/` (git-ignored):

- `bitchord-extension-chrome.zip` and `bitchord-extension-firefox.zip`. Each holds a single folder (`bitchord-extension-<browser>/`), so extracting it in place leaves one folder to load unpacked.
- `extension.json`, with the version. The site compares it with the installed extension's version and shows **Update** in the sidebar and an update row on the Extension page when the zip is newer. Bump `version` in `extension/package.json` to publish an update.

`VITE_EXTENSION_CHROME_URL`, `VITE_EXTENSION_FIREFOX_URL` and `VITE_EXTENSION_MANIFEST_URL` override those links, for example to point at store listings.

The toolbar icons (`extension/public/icon/`) are rendered from `app/public/favicon.svg` by `pnpm --dir web/extension icons`.

Then open `http://localhost:5173/extension`. It shows "Installed", with the version and the transport: **Direct** on Chromium (`externally_connectable`), **Page relay** on Firefox (content script).

## Configuration

| Variable | Where | Default |
|---|---|---|
| `BITCHORD_SITE_ORIGINS` | extension build | `https://bitchord.in,https://bitchord-8bw.pages.dev,http://localhost:5173` (store builds leave out localhost). Comma-separated origins the extension serves; replaces the default |
| `BITCHORD_STORE_BUILD=1` | extension build | Leaves out the dev `key` for a Chrome Web Store upload |
| `VITE_EXTENSION_ID_CHROMIUM` | app (`app/.env.local`) | The pinned dev id. Set it for a store-built extension |
| `VITE_ANDROID_APP_URL` | app | The GitHub releases page |
| `VITE_PARTY_SERVER_URL` | app (`app/.env.local`, Pages env) | None. The Listen Together server; `vite build` fails without it |

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
pnpm --dir web -r typecheck
pnpm --dir web/app build
```
