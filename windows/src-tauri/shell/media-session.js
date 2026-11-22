// Mirrors the web player's Media Session to the shell (src/media_session.rs), which owns
// Windows' media controls so they show as BitChord rather than "Unknown app", and runs
// the player's own action handlers when a media key or the flyout's buttons are used.
// Added at document start by main.rs; upstream's player code is untouched.
(() => {
  const session = navigator.mediaSession;
  if (!session) return;
  const proto = Object.getPrototypeOf(session);
  const handlers = new Map();

  const largest = (artwork) =>
    [...(artwork ?? [])].sort((a, b) => size(b) - size(a))[0]?.src ?? null;
  const size = (image) => Math.max(0, ...String(image.sizes ?? '').split(/\s+/).map((s) => parseInt(s, 10) || 0));
  const httpUrl = (src) => {
    try {
      const url = new URL(src, location.href);
      return url.protocol === 'https:' || url.protocol === 'http:' ? url.href : null; // Windows can't load blob:/data:
    } catch {
      return null;
    }
  };

  let queued = false;
  const report = () => {
    if (queued) return;
    queued = true;
    queueMicrotask(() => {
      queued = false;
      const meta = session.metadata;
      const artwork = largest(meta?.artwork);
      window.__TAURI_INTERNALS__
        ?.invoke('media_session', {
          state: {
            title: meta?.title ?? '',
            artist: meta?.artist ?? '',
            album: meta?.album ?? '',
            artwork: artwork && httpUrl(artwork),
            playback: session.playbackState,
            actions: [...handlers.keys()],
          },
        })
        .catch(() => {});
    });
  };

  for (const key of ['metadata', 'playbackState']) {
    const native = Object.getOwnPropertyDescriptor(proto, key);
    Object.defineProperty(session, key, {
      configurable: true,
      get() {
        return native.get.call(this);
      },
      set(value) {
        native.set.call(this, value);
        report();
      },
    });
  }

  const setActionHandler = proto.setActionHandler;
  session.setActionHandler = function (action, handler) {
    if (handler) handlers.set(action, handler);
    else handlers.delete(action);
    report();
    return setActionHandler.call(this, action, handler);
  };

  // media_session.rs calls this for Windows' buttons: play, pause, nexttrack, previoustrack, stop.
  window.__bitchordMediaAction = (action) => handlers.get(action)?.({ action });
})();
