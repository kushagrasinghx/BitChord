/**
 * Samsung Tizen OS TV Remote & Lifecycle Bridge for BitChord
 * Handles Samsung TV remote keys, D-Pad navigation, media controls, and application lifecycle.
 */
(function () {
  console.log('[BitChord Tizen] Initializing Samsung Tizen TV Adapter...');

  // 1. Register Tizen Remote Control Hardware Keys
  if (typeof tizen !== 'undefined' && tizen.tvinputdevice) {
    var tvKeys = [
      'MediaPlayPause',
      'MediaPlay',
      'MediaPause',
      'MediaStop',
      'MediaFastForward',
      'MediaRewind',
      'MediaTrackNext',
      'MediaTrackPrevious',
      'ColorF0Red',
      'ColorF1Green',
      'ColorF2Yellow',
      'ColorF3Blue',
      '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
    ];

    tvKeys.forEach(function (keyName) {
      try {
        tizen.tvinputdevice.registerKey(keyName);
      } catch (err) {
        // Ignored if key not supported on this TV model
      }
    });
    console.log('[BitChord Tizen] TV input hardware keys registered.');
  }

  // 2. Prevent TV Screen Saver during playback
  function setScreenSaverState(enabled) {
    if (typeof webapis !== 'undefined' && webapis.appcommon) {
      try {
        var state = enabled
          ? webapis.appcommon.AppCommonScreenSaverState.SCREEN_SAVER_ON
          : webapis.appcommon.AppCommonScreenSaverState.SCREEN_SAVER_OFF;
        webapis.appcommon.setScreenSaver(state);
      } catch (e) {}
    }
  }

  // Keep screen on when media plays
  document.addEventListener('play', function () { setScreenSaverState(false); }, true);
  document.addEventListener('pause', function () { setScreenSaverState(true); }, true);
  document.addEventListener('ended', function () { setScreenSaverState(true); }, true);

  // 3. Handle Tizen Return / Back Key
  function handleBackAction() {
    if (window.history.length > 1) {
      window.history.back();
    } else {
      if (typeof tizen !== 'undefined' && tizen.application) {
        try {
          tizen.application.getCurrentApplication().exit();
        } catch (e) {
          window.close();
        }
      }
    }
  }

  window.addEventListener('tizenhwkey', function (e) {
    if (e.keyName === 'back') {
      handleBackAction();
    }
  });

  // 4. Global D-Pad Remote & Media Keys Listener
  window.addEventListener('keydown', function (e) {
    var keyCode = e.keyCode || e.which;

    switch (keyCode) {
      case 10009: // Tizen Return Key
        e.preventDefault();
        handleBackAction();
        break;

      case 10252: // MediaPlayPause
      case 415:   // MediaPlay
      case 19:    // MediaPause
        e.preventDefault();
        // Dispatch synthetic spacebar / media toggle
        var playBtn = document.querySelector('[data-testid="play-pause-btn"]') ||
                      document.querySelector('button[aria-label*="Play"]') ||
                      document.querySelector('button[aria-label*="Pause"]');
        if (playBtn) playBtn.click();
        break;

      case 10232: // MediaTrackNext
      case 10233: // MediaTrackPrevious
        var nextBtn = document.querySelector('[data-testid="next-btn"]') ||
                      document.querySelector('button[aria-label*="Next"]');
        if (nextBtn && keyCode === 10232) nextBtn.click();
        var prevBtn = document.querySelector('[data-testid="prev-btn"]') ||
                      document.querySelector('button[aria-label*="Previous"]');
        if (prevBtn && keyCode === 10233) prevBtn.click();
        break;

      // D-Pad Focus Initialization (Arrow Keys)
      case 37: // Left
      case 38: // Up
      case 39: // Right
      case 40: // Down
        // If nothing is focused, auto-focus first interactive element
        if (!document.activeElement || document.activeElement === document.body) {
          var firstFocusable = document.querySelector('button, a, input, [tabindex="0"]');
          if (firstFocusable) {
            e.preventDefault();
            firstFocusable.focus();
          }
        }
        break;
    }
  });

  // Visual focus outline styling for TV remote navigation
  var style = document.createElement('style');
  style.textContent = `
    :focus-visible, [data-focused="true"] {
      outline: 3px solid rgba(255, 255, 255, 0.9) !important;
      outline-offset: 4px !important;
      box-shadow: 0 0 16px rgba(255, 255, 255, 0.4) !important;
      transition: outline 0.15s ease, box-shadow 0.15s ease !important;
    }
  `;
  document.head.appendChild(style);

  console.log('[BitChord Tizen] Adapter ready.');
})();

