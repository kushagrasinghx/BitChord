/**
 * BitChord Pomodoro Page Agent
 * Runs in the MAIN world to interface directly with the BitChord Web Player's store,
 * MediaSession API, and transport controls.
 */

import { matchPatternsFor, SITE_ORIGINS } from '../site.config';

export default defineContentScript({
  matches: matchPatternsFor(SITE_ORIGINS),
  world: 'MAIN',
  runAt: 'document_start',
  main() {
    if ((window as any).__bitchord_pomodoro_agent_injected) return;
    (window as any).__bitchord_pomodoro_agent_injected = true;

    console.log('[BitChord Pomodoro Page Agent] Initializing in MAIN world...');

    const sessionHandlers: Record<string, Function> = {};

    // 1. Intercept navigator.mediaSession.setActionHandler
    if ('mediaSession' in navigator) {
      const origSetActionHandler = navigator.mediaSession.setActionHandler.bind(navigator.mediaSession);
      navigator.mediaSession.setActionHandler = function (action: any, handler: any) {
        if (handler) {
          sessionHandlers[action] = handler;
        } else {
          delete sessionHandlers[action];
        }
        return origSetActionHandler(action, handler);
      };
    }

    function getAudioElement(): HTMLAudioElement | null {
      const audios = Array.from(document.querySelectorAll('audio'));
      const playing = audios.find((a) => !a.paused && a.currentTime > 0);
      if (playing) return playing;
      return audios.find((a) => a.src && a.src.length > 0) || audios[0] || null;
    }

    function getPlayerStatus() {
      let title = 'BitChord Player';
      let artist = 'Ready to stream';
      let artwork = '';
      let isPlaying = false;

      // Check mediaSession metadata
      if ('mediaSession' in navigator) {
        if (navigator.mediaSession.playbackState) {
          isPlaying = navigator.mediaSession.playbackState === 'playing';
        }
        const meta = navigator.mediaSession.metadata;
        if (meta) {
          if (meta.title) title = meta.title;
          if (meta.artist) artist = meta.artist;
          if (meta.artwork && meta.artwork.length > 0) {
            artwork = meta.artwork[meta.artwork.length - 1].src;
          }
        }
      }

      // Check DOM button state for Pause / Play
      const pauseBtn = document.querySelector('button[aria-label="Pause"]:not([disabled])');
      const playBtn = document.querySelector('button[aria-label="Play"]:not([disabled])');
      if (pauseBtn) {
        isPlaying = true;
      } else if (playBtn) {
        isPlaying = false;
      }

      // Check audio elements
      const audio = getAudioElement();
      if (audio && !audio.paused && audio.currentTime > 0) {
        isPlaying = true;
      }

      // Fallback: check Player region LCD title
      if (title === 'BitChord Player') {
        const playerRegion = document.querySelector('[role="region"][aria-label="Player"]');
        if (playerRegion) {
          const titleEl = playerRegion.querySelector('.type-body-emphasized') || playerRegion.querySelector('a, button');
          if (titleEl && titleEl.textContent) title = titleEl.textContent.trim();
          const imgEl = playerRegion.querySelector('img');
          if (imgEl && imgEl.src) artwork = imgEl.src;
        }
      }

      return {
        connected: true,
        isPlaying,
        title,
        artist,
        artwork,
        url: window.location.href,
      };
    }

    function broadcastStatus() {
      const status = getPlayerStatus();
      window.postMessage({ channel: 'POMODORO_PAGE_STATUS', status }, '*');
    }

    // 2. Control Commands Execution
    function executePlay() {
      console.log('[BitChord Pomodoro Page Agent] Executing PLAY...');

      // Check if already playing
      const audio = getAudioElement();
      if (audio && !audio.paused && audio.currentTime > 0) {
        broadcastStatus();
        return true;
      }

      // If MediaSession handler captured from player.ts, invoke it directly
      if (typeof sessionHandlers['play'] === 'function') {
        try {
          sessionHandlers['play']({ action: 'play' });
          setTimeout(broadcastStatus, 200);
          return true;
        } catch (err) {
          console.warn('[BitChord Pomodoro Page Agent] Error calling sessionHandlers[play]:', err);
        }
      }

      // Check if PlayerBar's Play button is enabled
      const playBtn = document.querySelector<HTMLButtonElement>('button[aria-label="Play"]:not([disabled])');
      if (playBtn) {
        playBtn.click();
        setTimeout(broadcastStatus, 200);
        return true;
      }

      // Dispatch Space key to window (caught by useKeyboardShortcuts.ts)
      window.dispatchEvent(
        new KeyboardEvent('keydown', {
          key: ' ',
          code: 'Space',
          keyCode: 32,
          which: 32,
          bubbles: true,
          cancelable: true,
        }),
      );

      // Cold start on /home: If no track loaded yet, click first playable shelf item
      const shelfPlayBtn =
        document.querySelector<HTMLButtonElement>('button[aria-label^="Play "]:not([disabled])') ||
        document.querySelector<HTMLButtonElement>('.group\\/track button:not([disabled])') ||
        document.querySelector<HTMLButtonElement>('.group\\/art button:not([disabled])') ||
        document.querySelector<HTMLButtonElement>('button[aria-label*="Play"]:not([disabled])');

      if (shelfPlayBtn) {
        console.log('[BitChord Pomodoro Page Agent] Cold-starting from shelf item:', shelfPlayBtn.getAttribute('aria-label') || 'track');
        shelfPlayBtn.click();
        setTimeout(broadcastStatus, 500);
        return true;
      }

      if (audio) {
        audio.play().then(() => broadcastStatus()).catch(() => {});
        return true;
      }

      setTimeout(broadcastStatus, 300);
      return false;
    }

    function executePause() {
      console.log('[BitChord Pomodoro Page Agent] Executing PAUSE...');

      if (typeof sessionHandlers['pause'] === 'function') {
        try {
          sessionHandlers['pause']({ action: 'pause' });
          setTimeout(broadcastStatus, 200);
          return true;
        } catch (err) {
          console.warn('[BitChord Pomodoro Page Agent] Error calling sessionHandlers[pause]:', err);
        }
      }

      const pauseBtn = document.querySelector<HTMLButtonElement>('button[aria-label="Pause"]:not([disabled])');
      if (pauseBtn) {
        pauseBtn.click();
        setTimeout(broadcastStatus, 200);
        return true;
      }

      const audio = getAudioElement();
      if (audio && !audio.paused) {
        window.dispatchEvent(
          new KeyboardEvent('keydown', {
            key: ' ',
            code: 'Space',
            keyCode: 32,
            which: 32,
            bubbles: true,
            cancelable: true,
          }),
        );
      }

      document.querySelectorAll('audio').forEach((a) => {
        if (!a.paused) a.pause();
      });

      setTimeout(broadcastStatus, 200);
      return true;
    }

    function executeNext() {
      console.log('[BitChord Pomodoro Page Agent] Executing NEXT...');
      if (typeof sessionHandlers['nexttrack'] === 'function') {
        try {
          sessionHandlers['nexttrack']({ action: 'nexttrack' });
          setTimeout(broadcastStatus, 300);
          return true;
        } catch {}
      }
      const nextBtn = document.querySelector<HTMLButtonElement>('button[aria-label="Next"]:not([disabled])');
      if (nextBtn) {
        nextBtn.click();
        setTimeout(broadcastStatus, 300);
        return true;
      }
      window.dispatchEvent(
        new KeyboardEvent('keydown', {
          key: 'ArrowRight',
          code: 'ArrowRight',
          ctrlKey: true,
          bubbles: true,
          cancelable: true,
        }),
      );
      setTimeout(broadcastStatus, 300);
      return true;
    }

    function executePrev() {
      console.log('[BitChord Pomodoro Page Agent] Executing PREVIOUS...');
      if (typeof sessionHandlers['previoustrack'] === 'function') {
        try {
          sessionHandlers['previoustrack']({ action: 'previoustrack' });
          setTimeout(broadcastStatus, 300);
          return true;
        } catch {}
      }
      const prevBtn = document.querySelector<HTMLButtonElement>('button[aria-label="Previous"]:not([disabled])');
      if (prevBtn) {
        prevBtn.click();
        setTimeout(broadcastStatus, 300);
        return true;
      }
      window.dispatchEvent(
        new KeyboardEvent('keydown', {
          key: 'ArrowLeft',
          code: 'ArrowLeft',
          ctrlKey: true,
          bubbles: true,
          cancelable: true,
        }),
      );
      setTimeout(broadcastStatus, 300);
      return true;
    }

    // 3. Listen to commands relayed from bridge
    window.addEventListener('message', (event) => {
      if (event.source !== window || !event.data || event.data.channel !== 'POMODORO_MAIN_COMMAND') return;

      const { command } = event.data;
      if (command === 'play') executePlay();
      else if (command === 'pause') executePause();
      else if (command === 'next') executeNext();
      else if (command === 'previous') executePrev();
      else if (command === 'get_status') broadcastStatus();
    });

    // 4. Setup audio and playback observers
    function setupAudioListeners() {
      const bindAudio = (a: HTMLAudioElement) => {
        ['play', 'pause', 'timeupdate', 'ended', 'loadeddata'].forEach((evt) => {
          a.addEventListener(evt, () => broadcastStatus(), { passive: true });
        });
      };
      document.querySelectorAll('audio').forEach(bindAudio);
      const observer = new MutationObserver(() => {
        document.querySelectorAll('audio').forEach(bindAudio);
      });
      observer.observe(document.documentElement, { childList: true, subtree: true });
    }

    if (document.readyState === 'complete' || document.readyState === 'interactive') {
      setupAudioListeners();
      broadcastStatus();
    } else {
      window.addEventListener('DOMContentLoaded', () => {
        setupAudioListeners();
        broadcastStatus();
      });
    }

    setInterval(broadcastStatus, 2000);
  },
});
