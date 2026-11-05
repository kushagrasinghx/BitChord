# Safari development build

The macOS target uses the existing WebExtension bridge and an MV3 nonpersistent
background page. Discord presence is unavailable. Chromium and Firefox keep
their existing transports. Safari YouTube bootstrap and API calls use the
extension's anonymous request bridge because page requests failed CORS in live
testing. Cookies and account signing remain inside the extension.

## Load locally

1. Run `pnpm --dir web/extension build:safari`.
2. In Safari Settings → Advanced, enable **Show features for web developers**.
3. In Settings → Developer, allow unsigned extensions and approve macOS authentication.
4. Use **Add Temporary Extension** to select `web/extension/.output/safari-mv3`.
5. Enable BitChord in Settings → Extensions and grant access to its declared
   websites. One-day access is sufficient for testing.
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

Live Safari 27.2 on macOS 27.2: temporary installation, page relay handshake,
version detection and YouTube bootstrap/player-script loading were observed.
Safari rejected `X-Origin` modification rules; the Safari target omits that
header. Google then returned HTTP 403 with an automated-query rejection for
anonymous API requests. Account refresh also returned 403 after Safari login.
Search, audio playback, seeking, signed-in library access, Spotify, lyrics and
background suspension/resumption still require successful live verification.
Full Xcode is absent from the local test machine; the containing app needs a
real Xcode build before distribution.
