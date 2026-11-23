/**
 * BitChord Pomodoro Bridge Content Script
 * Relays messages between extension background/sidepanel and MAIN world agent.
 */

import { matchPatternsFor, SITE_ORIGINS } from '../site.config';

export default defineContentScript({
  matches: matchPatternsFor(SITE_ORIGINS),
  runAt: 'document_start',
  main() {
    console.log('[BitChord Pomodoro] Bridge content script initialized.');

    let lastStatus: any = null;

    // Forward status messages from page agent to extension
    window.addEventListener('message', (event: MessageEvent) => {
      if (event.source !== window || !event.data || event.data.channel !== 'POMODORO_PAGE_STATUS') return;

      lastStatus = event.data.status;
      browser.runtime
        .sendMessage({
          action: 'bitchord_status_change',
          status: lastStatus,
        })
        .catch(() => {});
    });

    // Handle commands from background service worker or popup
    browser.runtime.onMessage.addListener((request: any, sender: any, sendResponse: (res: any) => void) => {
      if (request.action === 'bitchord_command') {
        console.log(`[BitChord Pomodoro Bridge] Forwarding command to MAIN world: ${request.command}`);

        // Forward exclusively to MAIN world agent without duplicate DOM clicking
        window.postMessage(
          {
            channel: 'POMODORO_MAIN_COMMAND',
            command: request.command,
          },
          '*',
        );

        setTimeout(() => {
          sendResponse({ success: true, status: lastStatus });
        }, 150);
        return true;
      }

      if (request.action === 'bitchord_get_status') {
        window.postMessage({ channel: 'POMODORO_MAIN_COMMAND', command: 'get_status' }, '*');
        sendResponse(lastStatus || { connected: true, isPlaying: false, title: 'BitChord Player', artist: 'Ready' });
        return true;
      }
    });
  },
});
