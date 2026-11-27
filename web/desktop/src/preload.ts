import { contextBridge, ipcRenderer } from 'electron';
import {
  BRIDGE_CHANNEL,
  errorResponse,
  isBridgeRequest,
  type BridgeEvent,
  type BridgeEventMessage,
} from '@bitchord/shared/bridge';

/**
 * The desktop app's half of the bridge's relay transport, the one the
 * Firefox extension's content script (web/extension/entrypoints/relay.content.ts)
 * provides: the page's window messages go to the main process over IPC, and
 * the answers and pushes come back as window messages. The page's own code
 * (detectExtension, relayBridge) is unchanged.
 *
 * It runs in an isolated world, so the page can't reach ipcRenderer.
 */

/**
 * Tells the site it's the desktop app (web/app/src/config.ts), so it doesn't
 * ask for the extension, and gives its Settings page the app's own settings
 * (web/app/src/desktop.ts).
 */
contextBridge.exposeInMainWorld('bitchordDesktop', {
  platform: process.platform,
  settings: {
    get: () => ipcRenderer.invoke('bitchord-desktop:settings'),
    set: (change: unknown) => ipcRenderer.invoke('bitchord-desktop:set-settings', change),
  },
});

window.addEventListener('message', (event: MessageEvent) => {
  if (event.source !== window || event.origin !== location.origin) return;
  if (!isBridgeRequest(event.data)) return;
  const request = event.data;
  ipcRenderer
    .invoke('bitchord-bridge:call', request)
    .then((response: unknown) => {
      if (response) window.postMessage(response, location.origin);
    })
    .catch((error: unknown) => window.postMessage(errorResponse(request.id, 'internal', String(error)), location.origin));
});

ipcRenderer.on('bitchord-bridge:event', (_event, payload: BridgeEvent) => {
  const message: BridgeEventMessage = { channel: BRIDGE_CHANNEL, direction: 'event', payload };
  window.postMessage(message, location.origin);
});
