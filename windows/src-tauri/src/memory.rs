//! Puts the player in the background while BitChord is minimised, and brings it
//! back with the window.
//! - WebView2 doesn't tell the page it was minimised: it stays "visible" and
//!   went on animating and decoding the video preview for nobody. So the page
//!   is told, through the standard Page Visibility API (`document.hidden` and a
//!   `visibilitychange` event), which it already follows the way it would in a
//!   minimised browser; music keeps playing.
//! - WebView2's memory target goes to "low", which trims the page's caches and
//!   lets Windows page out what it can.

use std::sync::atomic::{AtomicBool, Ordering};

use tauri::{Manager, Runtime, Window};
use webview2_com::Microsoft::Web::WebView2::Win32::{
    ICoreWebView2ExecuteScriptCompletedHandler, ICoreWebView2_19, COREWEBVIEW2_MEMORY_USAGE_TARGET_LEVEL_LOW,
    COREWEBVIEW2_MEMORY_USAGE_TARGET_LEVEL_NORMAL,
};
use windows::core::{Interface, HSTRING};

static BACKGROUND: AtomicBool = AtomicBool::new(false);

/// Hides the page (own properties shadowing Document's getters), or removes them to show it again.
fn visibility_script(hidden: bool) -> &'static str {
    if hidden {
        "Object.defineProperty(document, 'hidden', { configurable: true, get: () => true });\
         Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => 'hidden' });\
         document.dispatchEvent(new Event('visibilitychange'));"
    } else {
        "delete document.hidden; delete document.visibilityState;\
         document.dispatchEvent(new Event('visibilitychange'));"
    }
}

/// Called on every resize (minimising is one): acts only when the background state changes.
pub fn follow_window<R: Runtime>(window: &Window<R>) {
    let background = window.is_minimized().unwrap_or(false) || !window.is_visible().unwrap_or(true);
    set_background(window, background);
}

/// Explicitly toggles WebView2's hidden/low-memory state (used both on minimise and tray hide).
pub fn set_background<R: Runtime>(window: &Window<R>, background: bool) {
    if BACKGROUND.swap(background, Ordering::Relaxed) == background {
        return;
    }
    let Some(webview) = window.app_handle().get_webview_window("main") else { return };
    // Straight to WebView2, on its own thread: Tauri's eval doesn't run from inside this event.
    let _ = webview.with_webview(move |platform| unsafe {
        let Ok(core) = platform.controller().CoreWebView2() else { return };
        let _ = core.ExecuteScript(&HSTRING::from(visibility_script(background)), None::<&ICoreWebView2ExecuteScriptCompletedHandler>);
        // Older runtimes lack ICoreWebView2_19; they simply keep the normal target.
        if let Ok(core) = core.cast::<ICoreWebView2_19>() {
            let level = if background { COREWEBVIEW2_MEMORY_USAGE_TARGET_LEVEL_LOW } else { COREWEBVIEW2_MEMORY_USAGE_TARGET_LEVEL_NORMAL };
            let _ = core.SetMemoryUsageTargetLevel(level);
        }
    });
}
