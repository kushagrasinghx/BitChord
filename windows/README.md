# BitChord for Windows 11

A native Windows app built from upstream's own web player (`web/app`) and its
companion extension (`web/extension`), **both unmodified**, hosted in one
[Tauri 2](https://tauri.app) window on the system WebView2 (Edge/Chromium).

Upstream retired its Compose desktop app in favour of the web player
(`e4dd63d8 chore: remove the desktop app now that the web player replaces it`),
so this port follows the code upstream actually maintains. In a browser, the
player needs the extension to reach YouTube, Spotify and lyric hosts. WebView2
loads the same MV3 build the Chrome zip contains, so nothing is reimplemented
and every upstream fix to the player or the extension arrives on the next build.

```
┌─ BitChord.exe (Tauri 2) ─────────────────────────────────────┐
│  WebView2 profile (cookies: YouTube / Spotify sign-in live here)
│  ├─ http://tauri.localhost  ← web/app/dist   (upstream, as-is)
│  └─ extension pkddff…kfih   ← web/extension  (upstream, as-is)
│        page ⇄ extension: externally_connectable, as in Chrome
└──────────────────────────────────────────────────────────────┘
```

Everything Windows-specific lives in this folder, plus its CI workflow
(`.github/workflows/windows.yml`). Nothing else in the repository is edited.

## Build

Needs Node 22+, Rust (stable, MSVC), and Visual Studio Build Tools with the
C++ workload. Windows 11 already ships WebView2.

```powershell
node windows/scripts/build.mjs          # installer → windows/src-tauri/target/release/bundle/nsis/
node windows/scripts/build.mjs --dev    # Vite dev server + live shell
```

The script:

1. builds the extension for Chrome with `BITCHORD_SITE_ORIGINS=http://tauri.localhost,http://localhost:5173`
   (the shell's origin and the dev server), and marks its optional hosts (lyric providers, Discord)
   as granted, because WebView2 has no permission prompt to ask with;
2. builds `web/app` (Listen Together server: `VITE_PARTY_SERVER_URL`, else `LISTEN_TOGETHER_SERVER`,
   else the phone's default);
3. builds the shell, versioned from `bitchordVersionName` in the root `build.gradle.kts`.

The installer is per-user NSIS (no admin prompt, ~27 MB, most of it the web
player's own assets). It installs the WebView2 runtime silently if it is ever
missing.

Verified on Windows 11 (26300) with v1.10: the extension loads with its pinned
id, the page reaches it over `externally_connectable`, search and playback work
(InnerTube and googlevideo through the extension's header rules), lyrics come
through `lyrics.fetch`, and the Google and Spotify sign-in pages open in in-app
popups that share the extension's cookie jar.

## Keeping it current

There is nothing to port per release. The page and the extension are built
from `web/` as it stands, the version comes from `bitchordVersionName`, and the
pnpm version from `web/package.json`, so a release of the web player is a
release of the Windows app: run the build.

Only a change to how the page and extension find each other would need work
here: a new origin rule in `web/extension/site.config.ts`, or a new extension
permission that WebView2 does not support.

## Known limits

- **Sign-in** goes through Google's and Spotify's own pages in an app popup, so the cookies
  land in the app's WebView2 profile, not your browser's. You sign in once inside the app.
- **Discord presence** needs the extension to script a `discord.com` tab; WebView2 extensions
  may not allow that, so it can be unavailable.
- WebView2 extension support is a subset of Chrome's (no toolbar popup). The player never
  needs the popup; everything it calls goes through the bridge.
