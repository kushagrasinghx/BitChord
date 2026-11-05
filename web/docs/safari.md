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

## Unsigned containing app

With full Xcode selected (`xcode-select -p`), run:

```sh
pnpm --dir web/extension package:safari
```

This generates a macOS Xcode project and an unsigned app under
`web/extension/.output/safari-native/`. Command Line Tools alone cannot build it.
The maintainer owns the final bundle identifier, Developer ID signing,
notarization and distribution. No App Store submission is part of this workflow.

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
- The unsigned containing app built successfully with Xcode 26.6 in macOS CI.
  Signing, notarization, native-app installation and forced background
  suspension/resumption have not been verified.

Safari requires `declarativeNetRequestWithHostAccess` and rejects `X-Origin`
modifications. Page-side header rewriting still failed live CORS checks, so the
Safari player uses the extension transport. The bridge buffers each media
response; long-session memory use and high-bitrate video need further testing.
