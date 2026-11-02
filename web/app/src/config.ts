/** Build-time configuration, from Vite's `VITE_*` environment variables. */
export const config = {
  /** Pinned in development by the manifest `key` (web/extension/wxt.config.ts). */
  chromiumExtensionId: import.meta.env.VITE_EXTENSION_ID_CHROMIUM || DEV_EXTENSION_ID,
  /** The extension packages (web/extension: `pnpm package` copies them into public/downloads). */
  extensionChromeUrl: import.meta.env.VITE_EXTENSION_CHROME_URL || '/downloads/bitchord-extension-chrome.zip',
  extensionFirefoxUrl: import.meta.env.VITE_EXTENSION_FIREFOX_URL || '/downloads/bitchord-extension-firefox.zip',
  /** The version the zips above hold, for the "update available" notice. */
  extensionManifestUrl: import.meta.env.VITE_EXTENSION_MANIFEST_URL || '/downloads/extension.json',
  androidAppUrl: import.meta.env.VITE_ANDROID_APP_URL || 'https://github.com/kushagrasinghx/BitChord/releases/latest',
  /** BitChord's Listen Together server. vite.config.ts refuses a production build without it. */
  partyServerUrl: import.meta.env.VITE_PARTY_SERVER_URL ?? '',
} as const;
