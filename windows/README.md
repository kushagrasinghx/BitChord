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
through `lyrics.fetch`, and the Google and Spotify sign-in pages open in the
sign-in sheet, which closes itself once signed in.

## Sign-in

Google's sign-in (the page's "Sign In") and Spotify's (the extension's
`spotify.signIn`) open as a sheet over the player rather than a separate
window: `src/sign_in.rs` with `shell/sign-in.html`, styled as the web player's
own modals and drawn with `web/app/src/styles/tokens.css`, compiled in, so it
follows upstream's design and light/dark mode.

The provider's page inside the sheet is the real one, unmodified. The header
shows the site it is on and follows every navigation ("Continue with Google"
on Spotify's page changes it), so the padlock never names a site you are not
on. The sheet is a webview in the player's own profile, so the session lands in
the cookie jar the extension reads.

It closes itself as soon as sign-in is done: when the session cookies the
extension signs with appear or change (`SAPISID`, `SID` and their secure
variants on youtube.com; `sp_dc` on open.spotify.com), or when the provider
hands back to its own site, whichever comes first. The cookies matter because a
provider's page doesn't always move on: Google's "check your phone" step can sit
there after the approval. The values are only hashed and compared, never kept.
Other links the player opens go to the default browser.

Each provider lists the sites its sign-in passes through (`flow` in
`sign_in.rs`). If the page leaves them, for example through Help, Privacy or
Terms (which open in the sheet rather than a new window), the footer becomes
"You've left sign-in" with a **Back to sign-in** button in the player's
key-colour pill, which returns to the last sign-in step.

## Look

The window takes the web player's glass (the player pill, the sidebar):

- **Title bar:** Windows 11's Acrylic backdrop (`src/title_bar.rs`), the system's tinted, saturated
  blur, which is what the player's `glass-pill` and `glass-material` draw in CSS. The bar stays
  native (snap layouts, resize, caption buttons) and follows the system's light/dark setting.
- **Scrollbars:** a floating capsule with the pill's glass fill and 0.5px inner stroke on a
  transparent track (`shell/player.css`), added to the page at load as a constructed
  stylesheet, so upstream's page is untouched. Its colours are upstream's tokens.
- **Sidebar and Settings:** no "BitChord Extension" entry, since the extension is built in (same stylesheet).

## Keeping it current

There is nothing to port per release. Everything the app is made of comes from
upstream as it stands, on every build:

| What | From |
|---|---|
| The player | `web/app`, built as for the site |
| YouTube, Spotify, lyrics access | `web/extension`, the Chrome build |
| Version | `bitchordVersionName` / `bitchordBetaSuffix` in `build.gradle.kts` |
| pnpm | `packageManager` in `web/package.json` |
| Sign-in sheet, title bar, scrollbars | `web/app/src/styles/tokens.css` (colours, type, radii) |
| Releases | publishing a GitHub release (the workflow attaches the installer) |

The few places where an upstream change could break the app quietly, instead
of failing the build, are checked against upstream's files by
`src-tauri/src/upstream.rs`, which CI runs on every change to `web/`:

- Google's and Spotify's sign-in URLs still open in the sheet and hand back where it closes;
- every design token the shell draws with is still defined;
- the `/extension` route, sidebar entry and Settings row `shell/player.css` hides are where it looks;
- the player still reports through the Media Session (the media keys);
- the update check reads the same releases as the phone's `AppUpdateChecker.kt`.

`scripts/build.mjs` also fails if the built extension no longer serves the
shell's origin or loses its pinned id. A failure names what moved and the line
to change here. What no check can foresee is a new extension permission
WebView2 doesn't support; that shows up as a feature not working in the app.

## Releases and updates

Nothing to do by hand. Publishing a GitHub release runs `.github/workflows/windows.yml`,
which builds the installer from the release's tag and attaches it as
`BitChord-<tag>-windows-setup.exe`, next to the APKs.

The app checks for updates the way the phone does (`AppUpdateChecker.kt`): the
same "latest release" and the same version rule, at launch and every 12 hours
(`src/update.rs`). When a newer release carries the Windows installer, it shows a
Windows notification once for that version (or, where notifications are turned
off, a dialog over the player); its **Download** opens the installer,
which upgrades in place and keeps sign-ins and settings (they live in
`%LOCALAPPDATA%\com.music.bitchord.windows`). No signing key or secret is involved.

Installing also replaces upstream's retired Compose desktop app (1.7 to 1.8.x)
if it is there (`installer-hooks.nsh`), found by its MSI upgrade code. It
registered per machine, so Windows may ask for permission to remove it. Its own
settings and sign-ins don't carry over (different storage).

## Media controls

The media keys, the volume flyout and Quick Settings control the player as
**BitChord**. Chromium's own integration would register them for
`msedgewebview2.exe`, which Windows shows as "Unknown app", so it is turned off
(`HardwareMediaKeyHandling`). `src/media_session.rs` registers the controls for
BitChord's window instead, fed by `shell/media-session.js`, which mirrors the
player's Media Session (title, artist, album, artwork, state) and runs the
player's own handlers when a button is pressed. Windows takes the name and icon
from the installer's shortcuts, so a copy run without installing shows "Unknown
app" there.

## Known limits

- **Sign-in** lives in the app's WebView2 profile, not your browser's: you sign in once inside the app.
- The sheet uses Tauri's child webviews, which are behind its `unstable` feature flag.
- **Discord presence** needs the extension to script a `discord.com` tab; WebView2 extensions
  may not allow that, so it can be unavailable.
- WebView2 extension support is a subset of Chrome's (no toolbar popup). The player never
  needs the popup; everything it calls goes through the bridge.
