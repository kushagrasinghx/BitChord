# BitChord Desktop

The web player (`web/app`) as an app for Windows, macOS and Linux, built with Electron. It needs no browser extension: the main process does the extension's work itself.

## How it works

| Piece | What it does |
|---|---|
| `app://bitchord/` | The site's build (`renderer/`, copied from `web/app/dist`), served from the app's own files. Every path that isn't a file gets `index.html`. |
| `src/preload.ts` | The bridge's relay, the same one the Firefox extension's content script provides. Page window messages go to the main process over IPC, and the answers come back the same way. The site's code is unchanged, and it detects the app as it detects the extension. It also sets `window.bitchordDesktop`, which `config.desktop` reads to hide the extension's pages and prompts. |
| `src/browserShim.ts` | The `browser` global that `web/extension/lib` is written against, backed by Electron: the app's cookies, a JSON file for `storage.local`, and windows for `tabs`/`scripting`. Permissions are always granted. The extension's request handling (`lib/dispatch.ts`) runs here unchanged. |
| `src/dnr.ts` | The extension's `declarativeNetRequest` header rules (`lib/rules.ts`), applied with `session.webRequest`. |
| `src/windows.ts` | The built-in browser. Google, Spotify and Discord sign-ins open in app windows that share the app's session, so the cookies they set are the ones the bridge uses. A window closes once its sign-in finishes. Other links open in the system browser. |

Listen Together: the party server accepts native clients, which send no `Origin`. The app removes `Origin` from requests to the party API, as the Android app does.

## Develop

```bash
corepack pnpm --dir web install
corepack pnpm --dir web/app dev        # the site on http://localhost:5173
corepack pnpm --dir web/desktop dev    # the app, pointed at that server, with DevTools
```

To run the built site instead, without Vite: set `VITE_PARTY_SERVER_URL`, then run `corepack pnpm --dir web/desktop build:app`, then `corepack pnpm --dir web/desktop exec electron .`.

## Package

```bash
VITE_PARTY_SERVER_URL=https://… corepack pnpm --dir web/desktop dist
```

This writes the installer for the current OS to `release/`. CI (`.github/workflows/desktop.yml`) builds all three OSes:

- **Windows**: NSIS installer (x64)
- **macOS**: DMG (Apple silicon and Intel), ad-hoc signed
- **Linux**: AppImage and .deb (x64)

A push to `main` or a pull request that touches `web/` builds all three and keeps the installers as run artifacts. *Run workflow* does the same on any branch. When a GitHub release is published, the workflow builds with the version from the tag (`v1.10` becomes `1.10.0`) and attaches the installers to that release. A release build needs the repository variable `VITE_PARTY_SERVER_URL`.

The icon is `build/icon.png`, rendered from `web/app/public/favicon.svg` by `corepack pnpm --dir web/desktop icon`.

## Unsigned builds

None of the builds is code-signed yet.

- **Windows**: SmartScreen warns on first run. Choose *More info*, then *Run anyway*.
- **macOS**: Gatekeeper blocks the first launch. Right-click the app, choose *Open*, or run `xattr -cr /Applications/BitChord.app`. A Developer ID and notarization would remove this.
- **Linux**: on distributions that restrict unprivileged user namespaces (Ubuntu 24.04 and later), the AppImage may refuse to start. In that case, use the .deb, or start the AppImage with `--no-sandbox`.
