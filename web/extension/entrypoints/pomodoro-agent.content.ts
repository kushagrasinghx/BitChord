/**
 * BitChord Pomodoro Page Agent
 * Runs in the MAIN world to interface directly with the BitChord Web Player's store,
 * MediaSession API, Audio elements, and transport controls.
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
    const capturedAudios: HTMLAudioElement[] = [];

    // 1. Intercept window.Audio to capture BitChord's internal audio elements
    const OrigAudio = window.Audio;
    if (OrigAudio) {
      (window as any).Audio = function (...args: any[]) {
        const audioInstance = new OrigAudio(...args);
        capturedAudios.push(audioInstance);
        ['play', 'pause', 'timeupdate', 'ended', 'loadeddata'].forEach((evt) => {
          audioInstance.addEventListener(evt, () => broadcastStatus(), { passive: true });
        });
        return audioInstance;
      };
      (window as any).Audio.prototype = OrigAudio.prototype;
    }

    // 2. Intercept navigator.mediaSession.setActionHandler
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
      const active = capturedAudios.find((a) => !a.paused && a.currentTime > 0);
      if (active) return active;
      const domAudios = Array.from(document.querySelectorAll('audio'));
      const domPlaying = domAudios.find((a) => !a.paused && a.currentTime > 0);
      if (domPlaying) return domPlaying;
      return capturedAudios[capturedAudios.length - 1] || domAudios[0] || null;
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

      // Check captured or DOM audio elements
      const audio = getAudioElement();
      if (audio && !audio.paused && audio.currentTime > 0) {
        isPlaying = true;
      }

      // Check DOM button state for Pause (playing) / Play (paused)
      const pauseBtn = document.querySelector('button[aria-label="Pause"]:not([disabled])');
      const playBtn = document.querySelector('button[aria-label="Play"]:not([disabled])');
      if (pauseBtn) {
        isPlaying = true;
      } else if (playBtn) {
        isPlaying = false;
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

    // 3. Robust, Idempotent Player Control Commands
    function executePlay() {
      console.log('[BitChord Pomodoro Page Agent] Executing PLAY...');

      // A. If already playing, do NOT toggle!
      const currentStatus = getPlayerStatus();
      if (currentStatus.isPlaying) {
        console.log('[BitChord Pomodoro Page Agent] Already playing. No-op.');
        broadcastStatus();
        return true;
      }

      // B. Check if PlayerBar's Play button is enabled
      const playBtn = document.querySelector<HTMLButtonElement>('button[aria-label="Play"]:not([disabled])');
      if (playBtn) {
        console.log('[BitChord Pomodoro Page Agent] Clicking PlayerBar Play button.');
        playBtn.click();
        setTimeout(broadcastStatus, 200);
        return true;
      }

      // C. Check MediaSession action handler
      if (typeof sessionHandlers['play'] === 'function') {
        try {
          console.log('[BitChord Pomodoro Page Agent] Calling MediaSession play handler.');
          sessionHandlers['play']({ action: 'play' });
          setTimeout(broadcastStatus, 200);
          return true;
        } catch (err) {
          console.warn('[BitChord Pomodoro Page Agent] Error calling sessionHandlers[play]:', err);
        }
      }

      // D. Direct audio resume if paused
      const audio = getAudioElement();
      if (audio && audio.paused && audio.src) {
        audio.play().then(() => broadcastStatus()).catch(() => {});
        return true;
      }

      // E. Cold start on /home: Click the first playable track or album button
      const shelfPlayBtn =
        document.querySelector<HTMLButtonElement>('button[aria-label^="Play "]:not([disabled])') ||
        document.querySelector<HTMLButtonElement>('.group\\/track button:not([disabled])') ||
        document.querySelector<HTMLButtonElement>('.group\\/art button:not([disabled])') ||
        document.querySelector<HTMLButtonElement>('button[aria-label*="Play"]:not([disabled])');

      if (shelfPlayBtn) {
        console.log('[BitChord Pomodoro Page Agent] Cold-starting from Home shelf item:', shelfPlayBtn.getAttribute('aria-label') || 'track');
        shelfPlayBtn.click();
        setTimeout(broadcastStatus, 500);
        return true;
      }

      // F. Fallback: dispatch Space key
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

      setTimeout(broadcastStatus, 300);
      return false;
    }

    function executePause() {
      console.log('[BitChord Pomodoro Page Agent] Executing PAUSE...');

      // A. If already paused, do NOT toggle!
      const currentStatus = getPlayerStatus();
      if (!currentStatus.isPlaying) {
        console.log('[BitChord Pomodoro Page Agent] Already paused. No-op.');
        broadcastStatus();
        return true;
      }

      // B. Check if PlayerBar's Pause button is visible
      const pauseBtn = document.querySelector<HTMLButtonElement>('button[aria-label="Pause"]:not([disabled])');
      if (pauseBtn) {
        console.log('[BitChord Pomodoro Page Agent] Clicking PlayerBar Pause button.');
        pauseBtn.click();
        setTimeout(broadcastStatus, 200);
        return true;
      }

      // C. Check MediaSession action handler
      if (typeof sessionHandlers['pause'] === 'function') {
        try {
          console.log('[BitChord Pomodoro Page Agent] Calling MediaSession pause handler.');
          sessionHandlers['pause']({ action: 'pause' });
          setTimeout(broadcastStatus, 200);
          return true;
        } catch (err) {
          console.warn('[BitChord Pomodoro Page Agent] Error calling sessionHandlers[pause]:', err);
        }
      }

      // D. Direct audio pause on all captured and DOM audios
      capturedAudios.forEach((a) => {
        try {
          if (!a.paused) a.pause();
        } catch {}
      });
      document.querySelectorAll('audio').forEach((a) => {
        try {
          if (!a.paused) a.pause();
        } catch {}
      });

      // E. Fallback: dispatch Space key
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

      setTimeout(broadcastStatus, 200);
      return true;
    }

    function executeNext() {
      console.log('[BitChord Pomodoro Page Agent] Executing NEXT...');
      const nextBtn = document.querySelector<HTMLButtonElement>('button[aria-label="Next"]:not([disabled])');
      if (nextBtn) {
        nextBtn.click();
        setTimeout(broadcastStatus, 300);
        return true;
      }
      if (typeof sessionHandlers['nexttrack'] === 'function') {
        try {
          sessionHandlers['nexttrack']({ action: 'nexttrack' });
          setTimeout(broadcastStatus, 300);
          return true;
        } catch (err) {}
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
      const prevBtn = document.querySelector<HTMLButtonElement>('button[aria-label="Previous"]:not([disabled])');
      if (prevBtn) {
        prevBtn.click();
        setTimeout(broadcastStatus, 300);
        return true;
      }
      if (typeof sessionHandlers['previoustrack'] === 'function') {
        try {
          sessionHandlers['previoustrack']({ action: 'previoustrack' });
          setTimeout(broadcastStatus, 300);
          return true;
        } catch (err) {}
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

    // 4. Listen to commands relayed from bitchord-bridge.js
    window.addEventListener('message', (event: MessageEvent) => {
      if (event.source !== window || !event.data || event.data.channel !== 'POMODORO_MAIN_COMMAND') return;

      const { command } = event.data;
      if (command === 'play') executePlay();
      else if (command === 'pause') executePause();
      else if (command === 'next') executeNext();
      else if (command === 'previous') executePrev();
      else if (command === 'get_status') broadcastStatus();
    });

    // 5. Observe DOM mutations to update status when UI changes
    const observer = new MutationObserver(() => {
      broadcastStatus();
    });
    if (document.body) {
      observer.observe(document.body, { childList: true, subtree: true, attributes: true, attributeFilter: ['aria-label', 'disabled', 'class'] });
    } else {
      document.addEventListener('DOMContentLoaded', () => {
        observer.observe(document.body, { childList: true, subtree: true, attributes: true, attributeFilter: ['aria-label', 'disabled', 'class'] });
      });
    }

    // Initial broadcast and periodic heartbeat
    broadcastStatus();
    setInterval(broadcastStatus, 2000);
  },
});
