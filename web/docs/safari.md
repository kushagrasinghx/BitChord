# Safari development build

The macOS target uses the existing WebExtension bridge and an MV3 nonpersistent
background page. Discord presence is unavailable. Chromium and Firefox keep
their existing transports. Safari YouTube bootstrap and API calls use the
extension's anonymous request bridge because page requests failed CORS in live
testing. Media segments use the same bridge for direct DASH, SABR and audio
analysis. Binary requests and responses are base64 encoded. Cookies and account
signing remain inside the extension.

## Load locally

1. Run `pnpm --dir web/extension build:safari`.
2. In Safari Settings → Advanced, enable **Show features for web developers**.
3. In Settings → Developer, allow unsigned extensions and approve macOS authentication.
4. Use **Add Temporary Extension** to select `web/extension/.output/safari-mv3`.
5. Enable BitChord in Settings → Extensions and grant access to its declared
   websites using **Allow access to music services** in its toolbar popup.
   One-day access is sufficient for testing.
6. Open BitChord. Its Extension page should report **Safari**, **Page relay**,
   and the installed version.

After rebuilding, click **Reload** beside BitChord in Settings → Extensions,
then reload the BitChord page. Temporary installation is for development, not
permanent distribution. Safari can require enabling unsigned extensions again
after restarting.

## Development containing app

With full Xcode selected (`xcode-select -p`), run:

```sh
pnpm --dir web/extension package:safari
```

This generates a macOS Xcode project and an ad-hoc signed app under
`web/extension/.output/safari-native/`. The app and extension receive sandbox and
outgoing-network entitlements, and packaging verifies their resource signatures.
Without a sandbox entitlement macOS refuses to register the extension. Ad-hoc
signing provides local integrity checks, not an Apple developer identity.
Command Line Tools alone cannot build it.
The maintainer owns the final bundle identifier, Developer ID signing,
notarization and distribution. No App Store submission is part of this workflow.

Install the containing app in Applications and open it, then enable BitChord in
the Safari web app's Settings → Extensions. Temporary extensions loaded in Safari
do not appear in the standalone web app. Each app has its own unsigned-extension
setting; restarting can require enabling it again and authenticating with macOS.

The default local origin is `http://localhost:5173`. For another local address,
include its exact origin in `BITCHORD_SITE_ORIGINS` when building the extension
(the setting replaces the default list). The standalone test app at
`http://127.0.0.1:5183` was tested with:

```sh
BITCHORD_SITE_ORIGINS=https://bitchord.in,https://bitchord-8bw.pages.dev,http://localhost:5173,http://127.0.0.1:5183 pnpm --dir web/extension build
```

## Validation status

Live Safari 27.2 on macOS 27.2:

- Temporary installation, page relay/version detection, permission-request
  success and reconnection after extension/page reload were observed.
- YouTube account recognition, signed-in search and the Songs library page worked.
- Both direct DASH and SABR reached playing state. The visible clock advanced;
  seeking from about one minute to 3:04 resumed and advanced to 3:47.
  Pause/resume and synchronized lyrics worked. Physical speaker output was not
  independently measured.
- Anonymous API requests initially returned Google's automated-query HTTP 403;
  signed-in requests worked after refreshing the page following Safari login.
- Signed-in Spotify testing is left to the maintainer. The signed-out state was
  checked. Discord is deliberately unavailable in this target.
- The containing app built with Xcode 26.6 in macOS CI. Its original artifact
  failed signature validation and extension registration on the Mac. Applying
  the packaging script's ad-hoc signatures and sandbox entitlements to that
  artifact passed the native regression check and allowed registration.
  Developer ID signing, notarization and forced background suspension remain
  unverified. A fresh full-Xcode package build is validated by CI.

### Combined PWA validation (6 November 2026)

The heads of #1493 (PWA) and #1497 (Safari) were merged locally for this test;
neither PR depends on that temporary merge commit. All 172 app tests, workspace
typechecks and the production build passed with a placeholder party-server URL.

- Brave: extension detection, account recognition, search, playback,
  pause/resume and lyric seeking passed in the browser. Installed-PWA playback
  and pause/resume passed.
- Safari: extension detection, account recognition, search, playback,
  pause/resume and lyric seeking passed in the browser. The installed web app
  connected through the packaged extension; signed-out search and playback
  passed with an advancing clock.
- Both installed PWAs reloaded their app shell with the local server stopped.
  This does not provide offline music playback.
- A temporary worker-version change offered updates while the playback clocks
  continued advancing. Updates waited for explicit activation. Both apps
  reloaded; Safari's old-cache cleanup was inspected. Native automation did not
  dispatch the final Safari restore-button click, so that activation was tested
  through the same button's DOM click in Web Inspector. The normal build was
  restored, with no waiting worker in Safari.

Signed-in Spotify, physical audio output, long sessions and multi-window update
coordination were not tested in this follow-up.

Safari requires `declarativeNetRequestWithHostAccess` and rejects `X-Origin`
modifications. Page-side header rewriting still failed live CORS checks, so the
Safari player uses the extension transport. The bridge buffers each media
response; long-session memory use and high-bitrate video need further testing.
