/**
 * BitChord Pomodoro Page Agent
 * Runs directly in the MAIN world of BitChord web player.
 * Intercepts AudioContext, window.Audio, MediaSession handlers, hooks into player events,
 * and provides 10-Band Graphic Equalizer, DJ AutoMix / Queue Mixer controls, and reliable transport controls.
 */

import { matchPatternsFor, SITE_ORIGINS } from '../site.config';

export default defineContentScript({
  matches: matchPatternsFor(SITE_ORIGINS),
  world: 'MAIN',
  runAt: 'document_start',
  main() {
    if ((window as any).__bitchord_pomodoro_agent_injected) return;
    (window as any).__bitchord_pomodoro_agent_injected = true;

    console.log('[BitChord Pomodoro Page Agent] Initializing in MAIN world with 10-Band Equalizer & DJ Queue Mixer...');

    const sessionHandlers: Record<string, Function> = {};
    const capturedAudios: HTMLAudioElement[] = [];

    // --- 1. Apple Music Grade 10-Band Equalizer (Web Audio API) ---
    const EQ_FREQUENCIES = [32, 64, 125, 250, 500, 1000, 2000, 4000, 8000, 16000];
    let eqGains = [0, 0, 0, 0, 0, 0, 0, 0, 0, 0];
    let eqEnabled = true;
    let eqFilters: BiquadFilterNode[] = [];
    let eqCtx: AudioContext | null = null;

    function getOrCreateEqChain(ctx: AudioContext): BiquadFilterNode[] {
      if (eqFilters.length > 0 && eqCtx === ctx) return eqFilters;
      eqCtx = ctx;
      eqFilters = EQ_FREQUENCIES.map((freq, idx) => {
        let type: BiquadFilterType = 'peaking';
        if (idx === 0) type = 'lowshelf';
        else if (idx === EQ_FREQUENCIES.length - 1) type = 'highshelf';

        return new BiquadFilterNode(ctx, {
          type,
          frequency: freq,
          Q: 1.4,
          gain: eqEnabled ? (eqGains[idx] || 0) : 0,
        });
      });

      for (let i = 0; i < eqFilters.length - 1; i++) {
        eqFilters[i].connect(eqFilters[i + 1]);
      }
      eqFilters[eqFilters.length - 1].connect(ctx.destination);
      return eqFilters;
    }

    // Intercept AudioNode connect calls to route destination connections through our 10-band EQ
    const origAudioNodeConnect = AudioNode.prototype.connect;
    (AudioNode.prototype as any).connect = function (target: any, ...rest: any[]) {
      try {
        if (target && target === this.context?.destination && eqFilters.length > 0 && this === eqFilters[eqFilters.length - 1]) {
          return origAudioNodeConnect.call(this, target, ...rest);
        }
        if (target && target === this.context?.destination) {
          const chain = getOrCreateEqChain(this.context as AudioContext);
          if (chain && chain[0]) {
            return origAudioNodeConnect.call(this, chain[0], ...rest);
          }
        }
      } catch {}
      return origAudioNodeConnect.call(this, target, ...rest);
    };

    function applyEq(gains?: number[], enabled?: boolean) {
      if (Array.isArray(gains)) eqGains = gains;
      if (typeof enabled === 'boolean') eqEnabled = enabled;

      if (eqFilters.length > 0 && eqCtx) {
        const now = eqCtx.currentTime;
        eqFilters.forEach((f, i) => {
          const targetGain = eqEnabled ? (eqGains[i] || 0) : 0;
          try {
            f.gain.setTargetAtTime(targetGain, now, 0.02);
          } catch {
            f.gain.value = targetGain;
          }
        });
      }
      broadcastStatus();
    }
    (window as any).__setBitChordEq = applyEq;

    // --- 2. Intercept window.Audio to capture BitChord's internal audio elements ---
    const OrigAudio = window.Audio;
    if (OrigAudio) {
      (window as any).Audio = function (...args: any[]) {
        const audioInstance = new OrigAudio(...args);
        capturedAudios.push(audioInstance);
        ['play', 'pause', 'timeupdate', 'ended', 'loadeddata', 'seeking', 'seeked'].forEach((evt) => {
          audioInstance.addEventListener(evt, () => broadcastStatus(), { passive: true });
        });
        return audioInstance;
      };
      (window as any).Audio.prototype = OrigAudio.prototype;
    }

    // --- 3. Intercept navigator.mediaSession.setActionHandler ---
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

    // --- 4. Queue and Mixer Extractor ---
    function getMixerAndQueue() {
      let crossfadeSec = 0;
      let automix = false;
      let automixBars = 16;
      let queue: any[] = [];
      let currentIndex = 0;

      try {
        const sStr = localStorage.getItem('bitchord.player.settings');
        if (sStr) {
          const s = JSON.parse(sStr);
          if (s.crossfadeSec !== undefined) crossfadeSec = s.crossfadeSec;
          if (s.automix !== undefined) automix = !!s.automix;
          if (s.automixBars !== undefined) automixBars = s.automixBars;
        }
      } catch {}

      if ((window as any).__bcPlayer) {
        const st = (window as any).__bcPlayer.getState();
        if (st) {
          if (st.crossfadeSec !== undefined) crossfadeSec = st.crossfadeSec;
          if (st.automix !== undefined) automix = !!st.automix;
          if (st.automixBars !== undefined) automixBars = st.automixBars;
          if (Array.isArray(st.queue)) {
            queue = st.queue.slice(0, 35).map((entry: any, idx: number) => ({
              index: idx,
              title: entry.track?.title || entry.title || `Track ${idx + 1}`,
              artist: entry.track?.artist || entry.artist || '',
              artwork: entry.track?.artworkUrl || null,
              durationSec: entry.track?.durationSec || 0,
              isPlaying: idx === st.index,
            }));
          }
          currentIndex = st.index;
        }
      }

      return { crossfadeSec, automix, automixBars, queue, currentIndex };
    }

    // --- 5. Extract Full Player Status ---
    function getPlayerStatus() {
      let title = 'BitChord Player';
      let artist = 'Ready to stream';
      let artwork = '';
      let isPlaying = false;
      let currentTime = 0;
      let duration = 0;
      let volume = 1;
      let shuffle = false;
      let repeat = 'off';

      // A. Check audio element
      const audio = getAudioElement();
      if (audio) {
        if (!audio.paused && audio.currentTime > 0) isPlaying = true;
        currentTime = audio.currentTime || 0;
        duration = audio.duration || 0;
        volume = audio.volume !== undefined ? audio.volume : 1;
      }

      // B. Check MediaSession metadata
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

      // C. Check DOM buttons (authoritative over mediaSession)
      const pauseBtn = document.querySelector('button[aria-label="Pause"]:not([disabled])');
      const playBtn = document.querySelector('button[aria-label="Play"]:not([disabled])');
      if (pauseBtn) isPlaying = true;
      else if (playBtn) isPlaying = false;

      // D. Check Shuffle & Repeat
      const shuffleBtn = document.querySelector('button[aria-label="Shuffle"]');
      if (shuffleBtn) {
        shuffle = shuffleBtn.getAttribute('aria-pressed') === 'true';
      }

      const repeatBtn = document.querySelector('button[aria-label^="Repeat"]');
      if (repeatBtn) {
        const label = repeatBtn.getAttribute('aria-label') || '';
        if (label.includes('one')) repeat = 'one';
        else if (label.includes('all') || repeatBtn.getAttribute('aria-pressed') === 'true') repeat = 'all';
        else repeat = 'off';
      }

      // E. Check LCD DOM metadata fallback
      const titleEl = document.querySelector('.group\\/lcd .type-body-emphasized') || document.querySelector('[role="region"][aria-label="Player"] .type-body-emphasized');
      if (titleEl && titleEl.textContent && title === 'BitChord Player') title = titleEl.textContent.trim();

      const artistEl = document.querySelector('.group\\/lcd .type-callout-medium') || document.querySelector('[role="region"][aria-label="Player"] .type-callout-medium');
      if (artistEl && artistEl.textContent && artist === 'Ready to stream') artist = artistEl.textContent.trim();

      const imgEl = document.querySelector('.group\\/lcd button[aria-label="Open full screen player"] img') || document.querySelector('[role="region"][aria-label="Player"] img');
      if (imgEl && (imgEl as HTMLImageElement).src && !artwork) artwork = (imgEl as HTMLImageElement).src;

      // F. Lyrics check
      let hasLiveLyrics = false;
      const amLyrics = document.querySelector('am-lyrics');
      if (amLyrics && (amLyrics as any).ttml) hasLiveLyrics = true;

      // G. Mixer and Queue
      const mixerInfo = getMixerAndQueue();

      return {
        connected: true,
        isPlaying,
        title,
        artist,
        artwork,
        currentTime,
        duration,
        volume,
        shuffle,
        repeat,
        hasLiveLyrics,
        queue: mixerInfo.queue,
        crossfadeSec: mixerInfo.crossfadeSec,
        automix: mixerInfo.automix,
        automixBars: mixerInfo.automixBars,
        eq: {
          enabled: eqEnabled,
          gains: eqGains,
        },
        url: window.location.href,
      };
    }

    function broadcastStatus() {
      const status = getPlayerStatus();
      window.postMessage({ channel: 'POMODORO_PAGE_STATUS', status }, '*');
    }

    // --- 6. Reliable, Idempotent Player Controls ---
    function executeToggle() {
      console.log('[BitChord Pomodoro Page Agent] Executing TOGGLE...');

      // 1. If Pause button exists, click it -> pauses!
      const pauseBtn = document.querySelector<HTMLButtonElement>('button[aria-label="Pause"]:not([disabled])');
      if (pauseBtn) {
        pauseBtn.click();
        setTimeout(broadcastStatus, 150);
        return true;
      }

      // 2. If Play button exists, click it -> plays!
      const playBtn = document.querySelector<HTMLButtonElement>('button[aria-label="Play"]:not([disabled])');
      if (playBtn) {
        playBtn.click();
        setTimeout(broadcastStatus, 150);
        return true;
      }

      // 3. Audio toggle
      const audio = getAudioElement();
      if (audio) {
        if (audio.paused) audio.play().then(() => broadcastStatus()).catch(() => {});
        else { audio.pause(); broadcastStatus(); }
        return true;
      }

      // 4. Cold start: click shelf play button
      const shelfPlayBtn =
        document.querySelector<HTMLButtonElement>('button[aria-label^="Play "]:not([disabled])') ||
        document.querySelector<HTMLButtonElement>('.group\\/track button:not([disabled])') ||
        document.querySelector<HTMLButtonElement>('.group\\/art button:not([disabled])') ||
        document.querySelector<HTMLButtonElement>('button[aria-label*="Play"]:not([disabled])');

      if (shelfPlayBtn) {
        shelfPlayBtn.click();
        setTimeout(broadcastStatus, 500);
        return true;
      }

      // 5. Space key dispatch
      window.dispatchEvent(new KeyboardEvent('keydown', { key: ' ', code: 'Space', keyCode: 32, which: 32, bubbles: true }));
      setTimeout(broadcastStatus, 200);
      return true;
    }

    function executePlay() {
      console.log('[BitChord Pomodoro Page Agent] Executing PLAY...');
      const pauseBtn = document.querySelector('button[aria-label="Pause"]:not([disabled])');
      if (pauseBtn) {
        broadcastStatus();
        return true;
      }

      const playBtn = document.querySelector<HTMLButtonElement>('button[aria-label="Play"]:not([disabled])');
      if (playBtn) {
        playBtn.click();
        setTimeout(broadcastStatus, 150);
        return true;
      }

      const audio = getAudioElement();
      if (audio && audio.paused && audio.src) {
        audio.play().then(() => broadcastStatus()).catch(() => {});
        return true;
      }

      const shelfPlayBtn =
        document.querySelector<HTMLButtonElement>('button[aria-label^="Play "]:not([disabled])') ||
        document.querySelector<HTMLButtonElement>('.group\\/track button:not([disabled])') ||
        document.querySelector<HTMLButtonElement>('button[aria-label*="Play"]:not([disabled])');

      if (shelfPlayBtn) {
        shelfPlayBtn.click();
        setTimeout(broadcastStatus, 500);
        return true;
      }

      window.dispatchEvent(new KeyboardEvent('keydown', { key: ' ', code: 'Space', keyCode: 32, which: 32, bubbles: true }));
      setTimeout(broadcastStatus, 200);
      return false;
    }

    function executePause() {
      console.log('[BitChord Pomodoro Page Agent] Executing PAUSE...');
      const playBtn = document.querySelector('button[aria-label="Play"]:not([disabled])');
      if (playBtn) {
        broadcastStatus();
        return true;
      }

      const pauseBtn = document.querySelector<HTMLButtonElement>('button[aria-label="Pause"]:not([disabled])');
      if (pauseBtn) {
        pauseBtn.click();
        setTimeout(broadcastStatus, 150);
        return true;
      }

      capturedAudios.forEach((a) => { try { if (!a.paused) a.pause(); } catch {} });
      document.querySelectorAll('audio').forEach((a) => { try { if (!a.paused) a.pause(); } catch {} });

      window.dispatchEvent(new KeyboardEvent('keydown', { key: ' ', code: 'Space', keyCode: 32, which: 32, bubbles: true }));
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
      window.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', code: 'ArrowRight', ctrlKey: true, bubbles: true }));
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
      window.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowLeft', code: 'ArrowLeft', ctrlKey: true, bubbles: true }));
      setTimeout(broadcastStatus, 300);
      return true;
    }

    function executeSeek(seconds: number) {
      if (typeof seconds !== 'number' || isNaN(seconds)) return;
      const audio = getAudioElement();
      if (audio) {
        audio.currentTime = Math.max(0, Math.min(seconds, audio.duration || seconds));
      }
      if (typeof sessionHandlers['seekto'] === 'function') {
        try { sessionHandlers['seekto']({ action: 'seekto', seekTime: seconds }); } catch {}
      }
      setTimeout(broadcastStatus, 150);
    }

    function executeToggleShuffle() {
      const btn = document.querySelector<HTMLButtonElement>('button[aria-label="Shuffle"]:not([disabled])');
      if (btn) btn.click();
      setTimeout(broadcastStatus, 150);
    }

    function executeCycleRepeat() {
      const btn = document.querySelector<HTMLButtonElement>('button[aria-label^="Repeat"]:not([disabled])');
      if (btn) btn.click();
      setTimeout(broadcastStatus, 150);
    }

    function executeSetVolume(volume: number) {
      if (typeof volume !== 'number') return;
      const audio = getAudioElement();
      if (audio) audio.volume = Math.max(0, Math.min(1, volume));
      capturedAudios.forEach((a) => { try { a.volume = Math.max(0, Math.min(1, volume)); } catch {} });
      setTimeout(broadcastStatus, 150);
    }

    // --- 7. Mixer & Queue Commands ---
    function executeSetCrossfade(sec: number) {
      console.log('[BitChord Pomodoro Page Agent] Setting crossfade to', sec);
      if ((window as any).__bcPlayer) {
        (window as any).__bcPlayer.getState().setCrossfade(sec);
      } else {
        try {
          const s = JSON.parse(localStorage.getItem('bitchord.player.settings') || '{}');
          s.crossfadeSec = sec;
          s.automix = false;
          localStorage.setItem('bitchord.player.settings', JSON.stringify(s));
        } catch {}
      }
      setTimeout(broadcastStatus, 200);
    }

    function executeSetAutomix(enabled: boolean, bars?: number) {
      console.log('[BitChord Pomodoro Page Agent] Setting automix to', enabled, bars);
      if ((window as any).__bcPlayer) {
        (window as any).__bcPlayer.getState().setAutomix(enabled);
        if (bars) (window as any).__bcPlayer.getState().setAutomixBars(bars);
      } else {
        try {
          const s = JSON.parse(localStorage.getItem('bitchord.player.settings') || '{}');
          s.automix = !!enabled;
          if (bars) s.automixBars = bars;
          localStorage.setItem('bitchord.player.settings', JSON.stringify(s));
        } catch {}
      }
      setTimeout(broadcastStatus, 200);
    }

    function executeQueueJump(index: number) {
      if ((window as any).__bcPlayer && typeof (window as any).__bcPlayer.getState().jumpTo === 'function') {
        (window as any).__bcPlayer.getState().jumpTo(index);
        setTimeout(broadcastStatus, 300);
      }
    }

    function executeQueueMove(from: number, to: number) {
      if ((window as any).__bcPlayer && typeof (window as any).__bcPlayer.getState().move === 'function') {
        (window as any).__bcPlayer.getState().move(from, to);
        setTimeout(broadcastStatus, 200);
      }
    }

    function executeQueueRemove(index: number) {
      if ((window as any).__bcPlayer && typeof (window as any).__bcPlayer.getState().removeAt === 'function') {
        (window as any).__bcPlayer.getState().removeAt(index);
        setTimeout(broadcastStatus, 200);
      }
    }

    function executeQueueClear() {
      if ((window as any).__bcPlayer && typeof (window as any).__bcPlayer.getState().clearUpNext === 'function') {
        (window as any).__bcPlayer.getState().clearUpNext();
        setTimeout(broadcastStatus, 200);
      }
    }

    // --- 8. Listen to Commands Relayed from Extension ---
    window.addEventListener('message', (event) => {
      if (event.source !== window || !event.data || event.data.channel !== 'POMODORO_MAIN_COMMAND') return;

      const { command, args } = event.data;
      if (command === 'toggle') executeToggle();
      else if (command === 'play') executePlay();
      else if (command === 'pause') executePause();
      else if (command === 'next') executeNext();
      else if (command === 'previous') executePrev();
      else if (command === 'seek' && args) executeSeek(args.seconds);
      else if (command === 'toggle_shuffle') executeToggleShuffle();
      else if (command === 'cycle_repeat') executeCycleRepeat();
      else if (command === 'set_volume' && args) executeSetVolume(args.volume);
      else if (command === 'set_eq' && args) applyEq(args.gains, args.enabled);
      else if (command === 'set_crossfade' && args) executeSetCrossfade(args.seconds);
      else if (command === 'set_automix' && args) executeSetAutomix(args.enabled, args.bars);
      else if (command === 'queue_jump' && args) executeQueueJump(args.index);
      else if (command === 'queue_move' && args) executeQueueMove(args.from, args.to);
      else if (command === 'queue_remove' && args) executeQueueRemove(args.index);
      else if (command === 'queue_clear') executeQueueClear();
      else if (command === 'get_status') broadcastStatus();
    });

    // --- 9. Observe DOM Mutations ---
    const observer = new MutationObserver(() => {
      broadcastStatus();
    });
    if (document.body) {
      observer.observe(document.body, { childList: true, subtree: true, attributes: true, attributeFilter: ['aria-label', 'disabled', 'class', 'aria-pressed'] });
    } else {
      document.addEventListener('DOMContentLoaded', () => {
        observer.observe(document.body, { childList: true, subtree: true, attributes: true, attributeFilter: ['aria-label', 'disabled', 'class', 'aria-pressed'] });
      });
    }

    // Initial broadcast and periodic heartbeat
    broadcastStatus();
    setInterval(broadcastStatus, 1500);
  },
});
