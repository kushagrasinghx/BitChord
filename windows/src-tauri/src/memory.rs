//! Asks WebView2 to give memory back while BitChord is minimised, and to take
//! it again when the window returns. WebView2's "low" target trims the page's
//! caches and lets Windows page out what it can; music keeps playing, and the
//! page is back to normal as soon as it is on screen.

use std::sync::atomic::{AtomicBool, Ordering};

use tauri::{Manager, Runtime, Window};
use webview2_com::Microsoft::Web::WebView2::Win32::{
    ICoreWebView2_19, COREWEBVIEW2_MEMORY_USAGE_TARGET_LEVEL_LOW, COREWEBVIEW2_MEMORY_USAGE_TARGET_LEVEL_NORMAL,
};
use windows::core::Interface;

static LOW: AtomicBool = AtomicBool::new(false);

/// Called on every resize (minimising is one): sets the target only when the minimised state changes.
pub fn follow_window<R: Runtime>(window: &Window<R>) {
    let low = window.is_minimized().unwrap_or(false);
    if LOW.swap(low, Ordering::Relaxed) == low {
        return;
    }
    let Some(webview) = window.app_handle().get_webview_window("main") else { return };
    let _ = webview.with_webview(move |platform| {
        let level = if low { COREWEBVIEW2_MEMORY_USAGE_TARGET_LEVEL_LOW } else { COREWEBVIEW2_MEMORY_USAGE_TARGET_LEVEL_NORMAL };
        // Older runtimes lack ICoreWebView2_19; they simply keep the normal target.
        unsafe {
            if let Ok(core) = platform.controller().CoreWebView2() {
                if let Ok(core) = core.cast::<ICoreWebView2_19>() {
                    let _ = core.SetMemoryUsageTargetLevel(level);
                }
            }
        }
    });
}
